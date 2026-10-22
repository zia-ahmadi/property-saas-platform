package com.example.property_core.auth;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {
    private final JwtEncoder jwtEncoder;
    private final String issuer;
    private final long lifetimeSeconds;

    public JwtTokenService(
            JwtEncoder jwtEncoder,
            @Value("${app.jwt.issuer}") String issuer,
            @Value("${app.jwt.access-token-ttl-seconds}") long lifetimeSeconds) {
        if (lifetimeSeconds <= 0 || lifetimeSeconds > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("JWT lifetime must be between 1 and 2147483647 seconds");
        }
        this.jwtEncoder = jwtEncoder;
        this.issuer = issuer;
        this.lifetimeSeconds = lifetimeSeconds;
    }

    public IssuedToken issue(AuthUser user) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(lifetimeSeconds);
        List<String> roles = user.roles();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(user.id().toString())
                .claim("tenantId", user.organizationId().toString())
                .claim("email", user.email())
                .claim("fullName", user.fullName())
                .claim("roles", roles)
                .build();

        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new IssuedToken(
                token,
                Math.toIntExact(lifetimeSeconds),
                user.id(),
                user.organizationId(),
                roles);
    }

    public record IssuedToken(
            String value, int expiresIn, UUID userId, UUID tenantId, List<String> roles) {
        public IssuedToken {
            roles = List.copyOf(roles);
        }
    }
}
