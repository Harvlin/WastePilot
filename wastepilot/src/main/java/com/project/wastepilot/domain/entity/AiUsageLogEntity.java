package com.project.wastepilot.domain.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "ai_usage_logs")
@Getter
@Setter
public class AiUsageLogEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 120)
    private String userId;

    @Column(nullable = false, length = 32)
    private String feature;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(nullable = false)
    private Integer promptTokens;

    @Column(nullable = false)
    private Integer completionTokens;

    @Column(nullable = false)
    private Integer totalTokens;

    @Column(nullable = false)
    private boolean success;

    @Column(length = 500)
    private String errorCode;

    @Column(nullable = false)
    private Instant timestamp;
}
