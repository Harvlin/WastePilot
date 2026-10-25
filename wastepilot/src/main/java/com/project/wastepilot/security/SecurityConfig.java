package com.project.wastepilot.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

@Configuration
@EnableMethodSecurity          // enables @PreAuthorize / @PostAuthorize on controller methods
@RequiredArgsConstructor
public class SecurityConfig {

  private final AuthenticationEntryPoint authEntryPoint;
  private final AccessDeniedHandler accessDeniedHandler;
  private final AuthRateLimitFilter authRateLimitFilter;
  private final RoleClaimConverter roleClaimConverter;
  private final org.springframework.core.env.Environment environment;

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
      .csrf(csrf -> csrf.disable())
      .cors(Customizer.withDefaults())
      .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
      .headers(h -> h
        .contentTypeOptions(Customizer.withDefaults())
        .frameOptions(f -> f.deny())
        .xssProtection(Customizer.withDefaults())
        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
      )
      .exceptionHandling(e -> e
        .authenticationEntryPoint(authEntryPoint)
        .accessDeniedHandler(accessDeniedHandler)
      )
      .authorizeHttpRequests(auth -> {
        auth
          .requestMatchers("/actuator/health", "/v3/api-docs/**", "/swagger-ui/**").permitAll()
          .requestMatchers("/actuator/info", "/actuator/metrics/**", "/actuator/prometheus").authenticated()
          .requestMatchers("/api/v1/auth/login", "/api/v1/auth/signup", "/api/v1/auth/register",
              "/api/v1/auth/refresh",
              "/api/v1/auth/forgot-password", "/api/v1/auth/reset-password").permitAll()
          .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/sensors/ingest").permitAll();

        // Gated local development demo-seeding endpoint
        if (environment.acceptsProfiles(org.springframework.core.env.Profiles.of("local"))) {
          auth.requestMatchers("/api/v1/dev/**").permitAll();
        }

        auth
          .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/operations/batch-close").hasRole("SUPERVISOR")
          .requestMatchers(org.springframework.http.HttpMethod.PATCH, "/api/v1/operations/batches/*/output-units").hasRole("SUPERVISOR")
          .requestMatchers(org.springframework.http.HttpMethod.PATCH, "/api/v1/ai/anomalies/*/status").hasRole("SUPERVISOR")
          .requestMatchers(org.springframework.http.HttpMethod.PATCH, "/api/v1/ai/insights/*/status").hasRole("SUPERVISOR")
          .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/integrity/pattern-review").hasRole("SUPERVISOR")
          .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/integrity/cross-validation").hasRole("SUPERVISOR")
          .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/integrity/audit-trail/verify").hasRole("SUPERVISOR")
          .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/operations/floor-view").authenticated()
          // All other /api/ endpoints require authentication (read-only operations, etc.)
          .requestMatchers("/api/**").authenticated()
          // Deny-by-default: any request not matched above is rejected. This ensures
          // new endpoints cannot accidentally be exposed without explicit authorization rules.
          .anyRequest().denyAll();
      })
      .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(roleClaimConverter)))
      .addFilterBefore(authRateLimitFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}
