package com.project.wastepilot.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "audit_chain_head")
@Getter
@Setter
public class AuditChainHeadEntity {
  @Id
  private Long id;

  @Column(name = "last_sequence", nullable = false)
  private long lastSequence;

  @Column(name = "last_hash", nullable = false, length = 64)
  private String lastHash;
}
