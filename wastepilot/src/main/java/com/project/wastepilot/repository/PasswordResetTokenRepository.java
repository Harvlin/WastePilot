package com.project.wastepilot.repository;

import com.project.wastepilot.domain.entity.PasswordResetTokenEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetTokenEntity, UUID> {

  Optional<PasswordResetTokenEntity> findByTokenHash(String tokenHash);

  /** Purge all existing unused tokens for a user before issuing a new one */
  @Modifying
  @Query("DELETE FROM PasswordResetTokenEntity t WHERE t.user.id = :userId AND t.usedAt IS NULL")
  void deleteUnusedByUserId(@Param("userId") UUID userId);
}
