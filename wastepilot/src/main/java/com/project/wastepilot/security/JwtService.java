package com.project.wastepilot.security;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JwtService {

  private final JwtEncoder jwtEncoder;
  private final JwtDecoder jwtDecoder;
  private final JwtProperties jwtProperties;

  public String generateToken(String subject) {
    Instant now = Instant.now();
    JwsHeader jwsHeader = JwsHeader.with(MacAlgorithm.HS256).build();
    JwtClaimsSet claims = JwtClaimsSet.builder()
        .issuer("wastepilot")
        .subject(subject)
        .issuedAt(now)
        .expiresAt(now.plusSeconds(jwtProperties.accessTokenTtlSeconds()))
        .claim("type", "access")
        .build();
    return jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claims)).getTokenValue();
  }

  public String generateRefreshToken(String subject) {
    Instant now = Instant.now();
    JwsHeader jwsHeader = JwsHeader.with(MacAlgorithm.HS256).build();
    JwtClaimsSet claims = JwtClaimsSet.builder()
        .issuer("wastepilot")
        .subject(subject)
        .issuedAt(now)
        .expiresAt(now.plusSeconds(jwtProperties.refreshTokenTtlSeconds()))
        .claim("type", "refresh")
        .build();
    return jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claims)).getTokenValue();
  }

  public String validateRefreshToken(String token) {
    try {
      Jwt jwt = jwtDecoder.decode(token);
      if (!"refresh".equals(jwt.getClaimAsString("type"))) {
        throw new RuntimeException("Invalid token type");
      }
      return jwt.getSubject();
    } catch (JwtException ex) {
      throw new RuntimeException("Invalid refresh token", ex);
    }
  }
}
