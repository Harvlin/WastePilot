package com.project.wastepilot.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "auth_users")
@Getter
@Setter
public class AuthUserEntity extends AuditableEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, length = 160)
  private String fullName;

  @Column(nullable = false, unique = true, length = 160)
  private String email;

  @Column(nullable = false, length = 255)
  private String passwordHash;
}
