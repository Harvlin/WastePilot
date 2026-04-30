package com.project.wastepilot.repository;

import com.project.wastepilot.domain.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;
import java.util.Optional;
import java.util.List;

@Repository
public interface TemplateRepository extends JpaRepository<TemplateEntity, UUID> {
  Optional<TemplateEntity> findBySkuIgnoreCase(String sku);
  Optional<TemplateEntity> findByNameIgnoreCase(String name);
}
