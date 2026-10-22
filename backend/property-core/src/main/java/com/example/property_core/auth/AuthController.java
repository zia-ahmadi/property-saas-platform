package com.example.property_core.auth;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/auth", produces = MediaType.APPLICATION_JSON_VALUE)
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping(path = "/register", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse register(@Valid @RequestBody RegisterRequest request) {
        JwtTokenService.IssuedToken token = authService.register(
                request.organizationName(), request.email(), request.password(), request.fullName());
        return tokenResponse(token);
    }

    @PostMapping(path = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return tokenResponse(authService.login(request.email(), request.password()));
    }

    @GetMapping("/me")
    public CurrentUser currentUser(@AuthenticationPrincipal Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return new CurrentUser(
                UUID.fromString(jwt.getSubject()),
                UUID.fromString(jwt.getClaimAsString("tenantId")),
                jwt.getClaimAsString("email"),
                jwt.getClaimAsString("fullName"),
                roles == null ? List.of() : roles);
    }

    private TokenResponse tokenResponse(JwtTokenService.IssuedToken token) {
        return new TokenResponse(
                token.value(), token.expiresIn(), token.userId(), token.tenantId(), token.roles());
    }

    public record RegisterRequest(
            @NotBlank @Size(min = 2, max = 120) String organizationName,
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(min = 8) String password,
            @NotBlank @Size(max = 120) String fullName) {
    }

    public record LoginRequest(@NotBlank @Email @Size(max = 320) String email, @NotBlank String password) {
    }

    public record TokenResponse(
            String accessToken, int expiresIn, UUID userId, UUID tenantId, List<String> roles) {
        public TokenResponse {
            roles = List.copyOf(roles);
        }
    }

    public record CurrentUser(UUID userId, UUID tenantId, String email, String fullName, List<String> roles) {
        public CurrentUser {
            roles = List.copyOf(roles);
        }
    }
}
