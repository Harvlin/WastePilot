package com.project.wastepilot.domain.entity;

import com.project.wastepilot.domain.enums.*;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "batches")
@Getter
@Setter
public class BatchEntity extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "template_name", nullable = false, length = 160)
    private String templateName;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "output_units", nullable = false, precision = 14, scale = 3)
    private BigDecimal outputUnits;

    @Column(name = "waste_kg", nullable = false, precision = 14, scale = 3)
    private BigDecimal wasteKg;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BatchStatus status;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closed_by", length = 120)
    private String closedBy;

    @Column(name = "close_reason", length = 500)
    private String closeReason;
}
