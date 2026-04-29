package com.project.wastepilot.domain.entity;

import com.project.wastepilot.domain.enums.*;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_logs")
@Getter
@Setter
public class InventoryLogEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    private BatchEntity batch;

    @Column(name = "material_name", nullable = false, length = 160)
    private String materialName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private InventoryType type;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal quantity;

    @Column(nullable = false, length = 16)
    private String unit;

    @Column(nullable = false, length = 32)
    private String source;

    @Column(nullable = false)
    private Instant timestamp;
}
