package com.project.wastepilot.domain.entity;

import com.project.wastepilot.domain.enums.*;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "waste_logs")
@Getter
@Setter
public class WasteLogEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private BatchEntity batch;

    @Column(name = "material_name", nullable = false, length = 160)
    private String materialName;

    @Column(name = "quantity_kg", nullable = false, precision = 14, scale = 3)
    private BigDecimal quantityKg;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private WasteDestination destination;

    @Enumerated(EnumType.STRING)
    @Column(name = "recovery_status", nullable = false, length = 24)
    private RecoveryStatus recoveryStatus;

    @Column(name = "ai_suggested_action", length = 500)
    private String aiSuggestedAction;

    @Column(nullable = false)
    private Instant timestamp;
}
