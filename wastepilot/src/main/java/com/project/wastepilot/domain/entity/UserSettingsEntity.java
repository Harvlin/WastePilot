package com.project.wastepilot.domain.entity;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "user_settings")
@Getter
@Setter
public class UserSettingsEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 160)
    private String userId;

    @Column(nullable = false, length = 200)
    private String company;

    @Column(nullable = false, length = 160)
    private String email;

    @Column(nullable = false, length = 80)
    private String role;

    @Column(nullable = false, length = 80)
    private String timezone;

    @Column(name = "daily_token_budget", nullable = false)
    private Integer dailyTokenBudget;

    @Column(name = "notify_anomalies", nullable = false)
    private boolean notifyAnomalies;

    @Column(name = "notify_recommendations", nullable = false)
    private boolean notifyRecommendations;

    @Column(name = "notify_overdue_batches", nullable = false)
    private boolean notifyOverdueBatches;
}