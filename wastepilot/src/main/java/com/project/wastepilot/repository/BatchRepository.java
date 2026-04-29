package com.project.wastepilot.repository;

import com.project.wastepilot.domain.enums.*;

import com.project.wastepilot.domain.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

@Repository
public interface BatchRepository extends JpaRepository<BatchEntity, UUID> {
  List<BatchEntity> findByStatusOrderByStartedAtDesc(BatchStatus status);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select b from BatchEntity b where b.id = :id")
  Optional<BatchEntity> lockById(@Param("id") UUID id);
}
