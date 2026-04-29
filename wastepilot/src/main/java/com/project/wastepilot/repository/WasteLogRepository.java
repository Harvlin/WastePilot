package com.project.wastepilot.repository;

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
public interface WasteLogRepository extends JpaRepository<WasteLogEntity, UUID> {
  List<WasteLogEntity> findByBatchIdOrderByTimestampDesc(UUID batchId);
  List<WasteLogEntity> findTop200ByOrderByTimestampDesc();

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select w from WasteLogEntity w where w.id = :id")
  Optional<WasteLogEntity> lockById(@Param("id") UUID id);
}
