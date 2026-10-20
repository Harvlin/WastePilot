package com.project.wastepilot.security;

import com.project.wastepilot.service.impl.AuditChainService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class AuditChainInitializer implements ApplicationRunner {
  private final AuditChainService auditChainService;

  @Override
  public void run(ApplicationArguments args) {
    auditChainService.initialize();
  }
}
