package com.project.wastepilot.repository;

import com.project.wastepilot.domain.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.UUID;
import java.util.List;

@Repository
public interface ActivityLogRepository extends JpaRepository<ActivityLogEntity, UUID> {
  List<ActivityLogEntity> findTop200ByOrderByTimestampDesc();
  List<ActivityLogEntity> findAllByOrderByTimestampDesc();
  List<ActivityLogEntity> findByTimestampBetweenOrderByTimestampAsc(Instant from, Instant to);
}
