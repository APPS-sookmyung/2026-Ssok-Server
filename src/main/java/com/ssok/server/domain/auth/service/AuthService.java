package com.ssok.server.domain.auth.service;

import com.ssok.server.common.exception.DuplicateEmailException;
import com.ssok.server.common.exception.InvalidCredentialsException;
import com.ssok.server.common.security.JwtTokenProvider;
import com.ssok.server.domain.auth.dto.LoginRequest;
import com.ssok.server.domain.auth.dto.LoginResponse;
import com.ssok.server.domain.auth.dto.RegisterRequest;
import com.ssok.server.domain.auth.dto.RegisterResponse;
import com.ssok.server.domain.user.entity.User;
import com.ssok.server.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionService sessions;
    private final org.springframework.context.ApplicationEventPublisher events;

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateEmailException("email already exists");
        }

        User user = User.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .name(request.name())
                .build();

        User saved;
        try {
            saved = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            if (e.getCause() instanceof ConstraintViolationException cve
                    && cve.getKind() == ConstraintViolationException.ConstraintKind.UNIQUE) {
                throw new DuplicateEmailException("email already exists");
            }
            throw e;
        }

        return new RegisterResponse(saved.getId(), saved.getEmail());
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findLockedByEmail(request.email())
                .orElseThrow(() -> new InvalidCredentialsException("invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new InvalidCredentialsException("invalid email or password");
        }

        events.publishEvent(new com.ssok.server.domain.notification.event.UserReturnedAfterAbsence(user.getId(), user.getLastLoginAt()));
        user.recordLogin(java.time.Instant.now());

        var pair = sessions.open(user.getId());

        return new LoginResponse(pair.accessToken(), pair.refreshToken(), user.getId(), user.getName(), user.getOnboardingCompletedAt() != null);
    }
}
