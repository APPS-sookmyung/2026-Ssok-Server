package com.ssok.server.domain.user.service;

import com.ssok.server.common.exception.UnauthenticatedException;
import com.ssok.server.common.util.TimeFormatter;
import com.ssok.server.domain.user.dto.ProfileUpdateRequest;
import com.ssok.server.domain.user.dto.ProfileUpdateResponse;
import com.ssok.server.domain.user.entity.User;
import com.ssok.server.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional
    public ProfileUpdateResponse updateProfile(Long userId, ProfileUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthenticatedException("authentication required"));

        user.updateProfile(request.name(), request.profileImage());
        userRepository.saveAndFlush(user);

        return new ProfileUpdateResponse(user.getId(), user.getName(), TimeFormatter.format(user.getUpdatedAt()));
    }
}
