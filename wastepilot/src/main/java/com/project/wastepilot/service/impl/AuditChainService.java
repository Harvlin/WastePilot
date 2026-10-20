package com.project.wastepilot.service.impl;

import com.project.wastepilot.domain.dto.integrity.AuditChainVerificationResponse;
import com.project.wastepilot.domain.entity.AuditChainHeadEntity;
import com.project.wastepilot.domain.entity.AuditTrailEntity;
import com.project.wastepilot.repository.AuditChainHeadRepository;
import com.project.wastepilot.repository.AuditTrailRepository;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tamper-evident audit chain using HMAC-SHA256.
 *
 * <p>The HMAC key must be stored <em>outside</em> the database (environment variable or vault)
 * so that DB-only access is insufficient to recompute the chain. Set {@code AUDIT_CHAIN_HMAC_KEY}
 * in your environment (see {@code .env.example}).
 *
 * <p>A development-only fallback key is accepted for {@code local} and {@code test} profiles only.
 * Any other profile (including no profile) that omits the key will fail at startup.
 */
@Service
@Slf4j
public class AuditChainService {

  public static final String GENESIS_HASH = "0".repeat(64);
  private static final long HEAD_ID = 1L;
  private static final String HMAC_ALGORITHM = "HmacSHA256";
  private static final String DEV_HMAC_KEY = "wastepilot-audit-dev-hmac-key-for-local-only";

  private final AuditTrailRepository auditTrailRepository;
  private final AuditChainHeadRepository headRepository;
  private final Environment environment;
  private final byte[] hmacKey;

  public AuditChainService(
      AuditTrailRepository auditTrailRepository,
      AuditChainHeadRepository headRepository,
      Environment environment,
      @Value("${wastepilot.audit.hmac-key:}") String hmacKeyValue
  ) {
    this.auditTrailRepository = auditTrailRepository;
    this.headRepository = headRepository;
    this.environment = environment;

    if (hmacKeyValue == null || hmacKeyValue.isBlank()) {
      boolean isSafeProfile = List.of(environment.getActiveProfiles()).contains("local")
          || List.of(environment.getActiveProfiles()).contains("test");
      if (!isSafeProfile) {
        throw new IllegalStateException(
            "[SECURITY] AUDIT_CHAIN_HMAC_KEY must be set outside local/test profiles. " +
            "The audit chain cannot use the dev fallback key in production. " +
            "Generate with: openssl rand -hex 32");
      }
      log.warn("[SECURITY] Using development-only HMAC key for audit chain — DO NOT use in production.");
      this.hmacKey = DEV_HMAC_KEY.getBytes(StandardCharsets.UTF_8);
    } else {
      this.hmacKey = hmacKeyValue.getBytes(StandardCharsets.UTF_8);
    }
  }

  @Transactional
  public void initialize() {
    AuditChainHeadEntity head = lockHead();
    if (head.getLastSequence() != 0 || auditTrailRepository.count() == 0) {
      return;
    }
    backfillLegacyEntries(head);
  }

  @Transactional
  public void append(AuditTrailEntity entry) {
    AuditChainHeadEntity head = lockHead();
    if (head.getLastSequence() == 0 && auditTrailRepository.count() > 0) {
      backfillLegacyEntries(head);
    }

    long sequence = head.getLastSequence() + 1;
    String previousHash = head.getLastHash();
    entry.setChainSequence(sequence);
    entry.setPreviousHash(previousHash);
    entry.setEntryHash(null);
    AuditTrailEntity savedEntry = auditTrailRepository.saveAndFlush(entry);
    AuditTrailEntity persistedEntry = auditTrailRepository.findById(savedEntry.getId())
        .orElseThrow(() -> new IllegalStateException("Audit entry disappeared while appending to the chain."));
    persistedEntry.setEntryHash(hmac(persistedEntry, sequence, previousHash));
    auditTrailRepository.save(persistedEntry);
    head.setLastSequence(sequence);
    head.setLastHash(persistedEntry.getEntryHash());
    headRepository.save(head);
  }

  @Transactional(readOnly = true)
  public AuditChainVerificationResponse verify() {
    AuditChainHeadEntity head = headRepository.findById(HEAD_ID)
        .orElseThrow(() -> new IllegalStateException("Audit chain head row is missing."));
    List<AuditTrailEntity> entries = auditTrailRepository.findAll().stream()
        .filter(entry -> entry.getChainSequence() != null)
        .sorted(Comparator.comparing(AuditTrailEntity::getChainSequence))
        .toList();

    String expectedPreviousHash = GENESIS_HASH;
    long expectedSequence = 1;
    for (AuditTrailEntity entry : entries) {
      Long sequence = entry.getChainSequence();
      String computedHash = hmac(entry, sequence, expectedPreviousHash);
      if (sequence != expectedSequence
          || !expectedPreviousHash.equals(entry.getPreviousHash())
          || !constantTimeEquals(computedHash, nullToEmpty(entry.getEntryHash()))) {
        return new AuditChainVerificationResponse(
            false, expectedSequence - 1, head.getLastSequence(), sequence,
            "Audit chain verification failed at sequence " + sequence + ".");
      }
      expectedPreviousHash = entry.getEntryHash();
      expectedSequence++;
    }

    long verifiedSequence = expectedSequence - 1;
    if (entries.size() != auditTrailRepository.count()
        || head.getLastSequence() != verifiedSequence
        || !expectedPreviousHash.equals(head.getLastHash())) {
      return new AuditChainVerificationResponse(
          false, verifiedSequence, head.getLastSequence(), verifiedSequence + 1,
          "Audit chain entries or persisted chain head do not match.");
    }

    return new AuditChainVerificationResponse(
        true, verifiedSequence, head.getLastSequence(), null,
        "All audit entries and the persisted chain head verified.");
  }

  /**
   * Periodic verification — every 4 hours. Logs WARN/ERROR on failure so any tampering is
   * captured in the application log without requiring a user to manually press "verify".
   */
  @Scheduled(fixedRate = 4 * 60 * 60 * 1000L, initialDelay = 60_000L)
  public void scheduledVerification() {
    try {
      AuditChainVerificationResponse result = verify();
      if (result.valid()) {
        log.info("[AUDIT] Scheduled audit chain verification passed: {} entries verified.",
            result.entriesChecked());
      } else {
        log.error("[AUDIT] Scheduled audit chain verification FAILED at sequence {}: {}",
            result.firstInvalidSequence(), result.message());
      }
    } catch (Exception e) {
      log.error("[AUDIT] Scheduled audit chain verification threw an exception", e);
    }
  }

  private void backfillLegacyEntries(AuditChainHeadEntity head) {
    List<AuditTrailEntity> legacyEntries = auditTrailRepository.findAll().stream()
        .sorted(Comparator.comparing(AuditTrailEntity::getTimestamp)
            .thenComparing(AuditTrailEntity::getId))
        .toList();

    long sequence = 0;
    String previousHash = GENESIS_HASH;
    for (AuditTrailEntity entry : legacyEntries) {
      sequence++;
      entry.setChainSequence(sequence);
      entry.setPreviousHash(previousHash);
      entry.setEntryHash(hmac(entry, sequence, previousHash));
      previousHash = entry.getEntryHash();
    }
    auditTrailRepository.saveAll(legacyEntries);
    head.setLastSequence(sequence);
    head.setLastHash(previousHash);
    headRepository.save(head);
  }

  private AuditChainHeadEntity lockHead() {
    return headRepository.findByIdForUpdate(HEAD_ID).orElseGet(() -> {
      boolean isTestProfile = List.of(environment.getActiveProfiles()).contains("test");
      if (!isTestProfile) {
        throw new IllegalStateException("Audit chain head row is missing.");
      }
      AuditChainHeadEntity head = new AuditChainHeadEntity();
      head.setId(HEAD_ID);
      head.setLastSequence(0);
      head.setLastHash(GENESIS_HASH);
      return headRepository.saveAndFlush(head);
    });
  }

  /**
   * HMAC-SHA256 over a canonical serialisation of the audit entry fields.
   * The external key means DB-only access cannot produce a valid chain recomputation.
   */
  private String hmac(AuditTrailEntity entry, long sequence, String previousHash) {
    try {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try (DataOutputStream output = new DataOutputStream(bytes)) {
        write(output, Long.toString(sequence));
        write(output, previousHash);
        write(output, entry.getId().toString());
        write(output, entry.getEntity().name());
        write(output, entry.getEntityId());
        write(output, entry.getField());
        write(output, entry.getOldValue());
        write(output, entry.getNewValue());
        write(output, entry.getActor());
        write(output, entry.getTimestamp().toString());
        write(output, entry.getReason());
      }
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(new SecretKeySpec(hmacKey, HMAC_ALGORITHM));
      return HexFormat.of().formatHex(mac.doFinal(bytes.toByteArray()));
    } catch (NoSuchAlgorithmException | InvalidKeyException | IOException e) {
      throw new IllegalStateException("Unable to compute the audit chain HMAC.", e);
    }
  }

  private void write(DataOutputStream output, String value) throws IOException {
    if (value == null) {
      output.writeInt(-1);
      return;
    }
    byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
    output.writeInt(encoded.length);
    output.write(encoded);
  }

  private String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  private boolean constantTimeEquals(String a, String b) {
    return MessageDigest.isEqual(
        a.getBytes(StandardCharsets.US_ASCII),
        b.getBytes(StandardCharsets.US_ASCII));
  }
}
