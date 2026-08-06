package com.project.wastepilot.repository;

import com.project.wastepilot.domain.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.UUID;
import java.util.Optional;
import java.util.List;
import java.util.Collection;

@Repository
public interface InventoryLogRepository extends JpaRepository<InventoryLogEntity, UUID> {
  List<InventoryLogEntity> findByBatchIdOrderByTimestampDesc(UUID batchId);
  List<InventoryLogEntity> findByMaterialNameIgnoreCaseOrderByTimestampDesc(String materialName);
  List<InventoryLogEntity> findTop200ByOrderByTimestampDesc();
  List<InventoryLogEntity> findAllByOrderByTimestampDesc();
  List<InventoryLogEntity> findByTimestampBetweenOrderByTimestampAsc(Instant from, Instant to);
  /** Batch-load all IN logs for a set of batch IDs — avoids N+1 in cross-validation. */
  List<InventoryLogEntity> findByBatch_IdInAndType(Collection<UUID> batchIds, com.project.wastepilot.domain.enums.InventoryType type);
}
