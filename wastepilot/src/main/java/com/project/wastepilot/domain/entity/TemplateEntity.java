package com.project.wastepilot.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.UUID;
import java.util.List;

@Entity
@Table(name = "templates")
@Getter
@Setter
public class TemplateEntity extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, unique = true, length = 80)
    private String sku;

    @Column(name = "expected_waste_kg", nullable = false, precision = 14, scale = 3)
    private BigDecimal expectedWasteKg;

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TemplateLineEntity> lines = new ArrayList<>();
}
