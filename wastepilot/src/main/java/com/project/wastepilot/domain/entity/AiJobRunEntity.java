package com.project.wastepilot.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "ai_job_runs")
@Getter
@Setter
public class AiJobRunEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 64)
    private String jobName;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(nullable = false)
    private Instant startedAt;

    @Column
    private Instant finishedAt;

    @Column(length = 500)
    private String errorMessage;
}
