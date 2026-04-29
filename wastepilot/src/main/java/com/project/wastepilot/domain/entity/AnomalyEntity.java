package com.project.wastepilot.domain.entity;

import com.project.wastepilot.domain.enums.*;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "anomalies")
@Getter
@Setter
public class AnomalyEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 120)
    private String process;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal zScore;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal wasteKg;

    @Column(nullable = false, length = 32)
    private String date;

    @Column(nullable = false, length = 1000)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private InsightStatus status;

    @Column(nullable = false)
    private Instant timestamp;
}
