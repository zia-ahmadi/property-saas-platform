package com.example.property_core.auth;

import java.util.Locale;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final AuthRepository authRepository;
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public AuthService(
            AuthRepository authRepository,
            AuthenticationManager authenticationManager,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService) {
        this.authRepository = authRepository;
        this.authenticationManager = authenticationManager;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    @Transactional
    public JwtTokenService.IssuedToken register(
            String organizationName, String email, String password, String fullName) {
        String normalizedEmail = normalizeEmail(email);
        if (authRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyRegisteredException();
        }

        UUID organizationId = authRepository.createOrganization(organizationName.trim());
        UUID userId;
        try {
            userId = authRepository.createUser(
                    organizationId,
                    normalizedEmail,
                    passwordEncoder.encode(password),
                    fullName.trim());
        } catch (DuplicateKeyException exception) {
            throw new EmailAlreadyRegisteredException();
        }
        authRepository.assignRole(organizationId, userId, "ADMIN");

        AuthUser user = authRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new IllegalStateException("Registered user was not persisted"));
        return jwtTokenService.issue(user);
    }

    public JwtTokenService.IssuedToken login(String email, String password) {
        String normalizedEmail = normalizeEmail(email);
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(normalizedEmail, password));
        AuthUserDetails principal = (AuthUserDetails) authentication.getPrincipal();
        return jwtTokenService.issue(principal.user());
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public static class EmailAlreadyRegisteredException extends RuntimeException {
        public EmailAlreadyRegisteredException() {
            super("Email already registered");
        }
    }
}
