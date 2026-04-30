package com.project.wastepilot.domain.entity;

import com.project.wastepilot.domain.enums.*;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "audit_trail")
@Getter
@Setter
public class AuditTrailEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private EntityType entity;

    @Column(name = "entity_id", nullable = false, length = 64)
    private String entityId;

    @Column(nullable = false, length = 64)
    private String field;

    @Column(name = "old_value", length = 500)
    private String oldValue;

    @Column(name = "new_value", length = 500)
    private String newValue;

    @Column(nullable = false, length = 160)
    private String actor;

    @Column(nullable = false)
    private Instant timestamp;
}
