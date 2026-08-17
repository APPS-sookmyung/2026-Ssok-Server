package com.ssok.server.domain.space.service;

import com.ssok.server.common.exception.MemberNotFoundException;
import com.ssok.server.common.exception.SpaceNotFoundException;
import com.ssok.server.common.exception.UserNotFoundException;
import com.ssok.server.domain.space.dto.MemberDto;
import com.ssok.server.domain.space.dto.MemberInviteResponse;
import com.ssok.server.domain.space.entity.Space;
import com.ssok.server.domain.space.entity.SpaceMember;
import com.ssok.server.domain.space.entity.SpaceRole;
import com.ssok.server.domain.space.repository.SpaceMemberRepository;
import com.ssok.server.domain.space.repository.SpaceRepository;
import com.ssok.server.domain.user.entity.User;
import com.ssok.server.domain.user.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ssok.server.common.exception.InvalidRequestException;
import com.ssok.server.domain.space.entity.SpaceType;

@Service
@RequiredArgsConstructor
public class SpaceMemberService {

    private final SpaceRepository spaceRepository;
    private final SpaceMemberRepository spaceMemberRepository;
    private final UserRepository userRepository;

    @Transactional
    public MemberInviteResponse invite(
            Long requesterId,
            Long spaceId,
            String email
    ) {
        Space space = spaceRepository.findById(spaceId)
                .orElseThrow(() ->
                        new SpaceNotFoundException("space not found"));

        SpaceMember requester = spaceMemberRepository
                .findBySpaceIdAndUserId(spaceId, requesterId)
                .orElseThrow(() ->
                        new InvalidRequestException("space access denied"));

        if (requester.getRole() != SpaceRole.OWNER) {
            throw new InvalidRequestException(
                    "only owner can invite members");
        }

        if (space.getType() != SpaceType.TEAM) {
            throw new InvalidRequestException(
                    "members can only be invited to team spaces");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new UserNotFoundException("user not found"));

        if (spaceMemberRepository.existsBySpaceIdAndUserId(
                spaceId,
                user.getId()
        )) {
            throw new InvalidRequestException(
                    "user is already a member");
        }

        SpaceMember member = SpaceMember.builder()
                .space(space)
                .user(user)
                .role(SpaceRole.MEMBER)
                .build();

        SpaceMember saved = spaceMemberRepository.save(member);

        return new MemberInviteResponse(
                saved.getId(),
                user.getEmail()
        );
    }

    @Transactional(readOnly = true)
    public List<MemberDto> list(Long spaceId) {
        if (!spaceRepository.existsById(spaceId)) {
            throw new SpaceNotFoundException("space not found");
        }

        return spaceMemberRepository.findAllBySpaceId(spaceId).stream()
                .map(member -> new MemberDto(
                        member.getId(),
                        member.getUser().getId(),
                        member.getUser().getName(),
                        member.getUser().getEmail(),
                        member.getRole().name()))
                .toList();
    }

    @Transactional
    public void remove(Long spaceId, Long memberId) {
        SpaceMember member = spaceMemberRepository.findBySpaceIdAndId(spaceId, memberId)
                .orElseThrow(() -> new MemberNotFoundException("member not found"));

        spaceMemberRepository.delete(member);
    }
}
