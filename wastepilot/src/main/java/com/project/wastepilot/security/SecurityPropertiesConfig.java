package com.project.wastepilot.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SecurityProps.class)
public class SecurityPropertiesConfig {
}
