package com.project.wastepilot.repository;

import com.project.wastepilot.domain.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;
import java.util.Optional;
import java.util.List;

@Repository
public interface InventoryLogRepository extends JpaRepository<InventoryLogEntity, UUID> {
  List<InventoryLogEntity> findByBatchIdOrderByTimestampDesc(UUID batchId);
  List<InventoryLogEntity> findByMaterialNameIgnoreCaseOrderByTimestampDesc(String materialName);
  List<InventoryLogEntity> findTop200ByOrderByTimestampDesc();
}
