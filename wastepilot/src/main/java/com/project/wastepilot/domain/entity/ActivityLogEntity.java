package com.project.wastepilot.domain.entity;

import com.project.wastepilot.domain.enums.*;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "activity_logs")
@Getter
@Setter
public class ActivityLogEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private EntityType entity;

    @Column(name = "entity_id", nullable = false, length = 64)
    private String entityId;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(nullable = false, length = 160)
    private String actor;

    @Column(nullable = false, length = 500)
    private String detail;

    @Column(nullable = false)
    private Instant timestamp;
}