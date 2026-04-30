package com.project.wastepilot.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

  private final AuthenticationEntryPoint authEntryPoint;
  private final AccessDeniedHandler accessDeniedHandler;
  private final MockAuthFilter mockAuthFilter;

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
      .authorizeHttpRequests(auth -> auth
        .requestMatchers("/actuator/health", "/v3/api-docs/**", "/swagger-ui/**").permitAll()
        .requestMatchers("/api/v1/**").permitAll() // TEMPORARY: Allow local frontend development without token
        .anyRequest().authenticated()
      )
      .addFilterBefore(mockAuthFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }
}
