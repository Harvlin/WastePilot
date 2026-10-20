package com.project.wastepilot.security;

import com.project.wastepilot.exception.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Validates sensor ingest API keys.
 *
 * <p>Supports two modes:
 * <ol>
 *   <li><b>Per-device</b>: if a {@code deviceId} is supplied and a matching key exists at
 *       {@code wastepilot.sensor.device-keys.{deviceId}}, that key is checked.
 *       This lets you distinguish individual sensors.</li>
 *   <li><b>Shared (legacy)</b>: if no {@code deviceId} is supplied, falls back to the
 *       global {@code wastepilot.sensor.api-key} value.</li>
 * </ol>
 * Key comparison is time-constant (MessageDigest.isEqual) to prevent timing attacks.
 */
@Component
public class SensorApiKeyValidator {

  private static final String PER_DEVICE_KEY_PREFIX = "wastepilot.sensor.device-keys.";

  private final byte[] globalKey;
  private final Environment environment;

  public SensorApiKeyValidator(
      @Value("${wastepilot.sensor.api-key:}") String globalKey,
      Environment environment
  ) {
    this.globalKey = globalKey == null ? new byte[0] : globalKey.getBytes(StandardCharsets.UTF_8);
    this.environment = environment;
  }

  /**
   * Validates the supplied API key for the given device.
   *
   * @param deviceId    the device identifier from the request (may be null for legacy mode)
   * @param suppliedKey the key sent by the sensor device
   * @throws ApiException with SENSOR_AUTHENTICATION_FAILED if validation fails
   */
  public void validate(String deviceId, String suppliedKey) {
    byte[] expectedKey = resolveExpectedKey(deviceId);
    byte[] candidate = suppliedKey == null ? new byte[0] : suppliedKey.getBytes(StandardCharsets.UTF_8);
    if (expectedKey.length == 0 || !MessageDigest.isEqual(expectedKey, candidate)) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "SENSOR_AUTHENTICATION_FAILED",
          "A valid sensor API key is required.");
    }
  }

  private byte[] resolveExpectedKey(String deviceId) {
    if (deviceId != null && !deviceId.isBlank()) {
      String perDeviceKey = environment.getProperty(PER_DEVICE_KEY_PREFIX + deviceId.trim(), "");
      if (!perDeviceKey.isBlank()) {
        return perDeviceKey.getBytes(StandardCharsets.UTF_8);
      }
      // deviceId provided but no per-device key configured — fall back to global key.
      // Callers can choose to treat this as a hard failure by setting globalKey to empty.
    }
    return globalKey;
  }
}
