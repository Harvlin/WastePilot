package com.project.wastepilot.domain.entity;

import com.project.wastepilot.domain.enums.*;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "insights")
@Getter
@Setter
public class InsightEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 1500)
    private String content;

    @Column(name = "impact_category", nullable = false, length = 32)
    private String impactCategory;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private InsightStatus status;

    @Column(nullable = false)
    private Instant timestamp;
}
