package com.ssok.server.domain.space.service;

import com.ssok.server.common.exception.DuplicateSpaceMemberException;
import com.ssok.server.common.exception.ForbiddenException;
import com.ssok.server.common.exception.InvalidRequestException;
import com.ssok.server.common.exception.MemberNotFoundException;
import com.ssok.server.common.exception.SpaceNotFoundException;
import com.ssok.server.common.exception.UserNotFoundException;
import com.ssok.server.domain.space.dto.MemberDto;
import com.ssok.server.domain.space.dto.MemberInviteResponse;
import com.ssok.server.domain.space.entity.Space;
import com.ssok.server.domain.space.entity.SpaceMember;
import com.ssok.server.domain.space.entity.SpaceRole;
import com.ssok.server.domain.space.entity.SpaceType;
import com.ssok.server.domain.space.repository.SpaceMemberRepository;
import com.ssok.server.domain.space.repository.SpaceRepository;
import com.ssok.server.domain.user.entity.User;
import com.ssok.server.domain.user.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SpaceMemberService {

    private final SpaceRepository spaceRepository;
    private final SpaceMemberRepository spaceMemberRepository;
    private final UserRepository userRepository;
    private final SpaceAccessService spaceAccessService;

    @Transactional
    public MemberInviteResponse invite(
            Long requesterId,
            Long spaceId,
            String email
    ) {
        Space space = spaceRepository.findLockedById(spaceId)
                .orElseThrow(() ->
                        new SpaceNotFoundException("space not found"));

        spaceAccessService.requireOwner(requesterId, spaceId, "only owner can invite members");

        if (space.getType() != SpaceType.TEAM) {
            throw new ForbiddenException(
                    "members can only be invited to team spaces");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new UserNotFoundException("user not found"));

        if (spaceMemberRepository.existsBySpaceIdAndUserId(
                spaceId,
                user.getId()
        )) {
            throw new DuplicateSpaceMemberException(
                    "user is already a member");
        }

        SpaceMember member = SpaceMember.builder()
                .space(space)
                .user(user)
                .role(SpaceRole.EDITOR)
                .build();

        SpaceMember saved = spaceMemberRepository.save(member);

        return new MemberInviteResponse(
                saved.getId(),
                user.getEmail()
        );
    }

    @Transactional(readOnly = true)
    public List<MemberDto> list(Long userId, Long spaceId) {
        if (!spaceRepository.existsById(spaceId)) {
            throw new SpaceNotFoundException("space not found");
        }

        spaceAccessService.requireWrite(userId, spaceId);

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
    public void remove(Long requesterId, Long spaceId, Long memberId) {
        lockSpace(spaceId);

        spaceAccessService.requireOwner(requesterId, spaceId, "only owner can remove members");

        SpaceMember target = spaceMemberRepository
                .findBySpaceIdAndId(spaceId, memberId)
                .orElseThrow(() ->
                        new MemberNotFoundException("member not found"));

        if (target.getRole() == SpaceRole.OWNER) {
            throw new ForbiddenException(
                    "owner cannot be removed");
        }

        spaceMemberRepository.delete(target);
    }

    @Transactional
    public MemberDto updateRole(Long requesterId, Long spaceId, Long memberId, SpaceRole role) {
        lockSpace(spaceId);
        spaceAccessService.requireOwner(requesterId, spaceId, "only owner can change member roles");
        if (role == null || role == SpaceRole.OWNER) {
            throw new InvalidRequestException("role must be EDITOR or VIEWER");
        }
        SpaceMember target = spaceMemberRepository.findBySpaceIdAndId(spaceId, memberId)
                .orElseThrow(() -> new MemberNotFoundException("member not found"));
        if (target.getRole() == SpaceRole.OWNER) {
            throw new ForbiddenException("owner role cannot be changed");
        }
        target.updateRole(role);
        return new MemberDto(target.getId(), target.getUser().getId(), target.getUser().getName(),
                target.getUser().getEmail(), target.getRole().name());
    }

    @Transactional
    public void leave(Long userId, Long spaceId) {
        Space space = lockSpace(spaceId);
        SpaceMember member = spaceAccessService.requireMember(userId, spaceId);
        requireTeam(space);
        if (member.getRole() == SpaceRole.OWNER) {
            throw new ForbiddenException("transfer ownership before leaving the space");
        }
        spaceMemberRepository.delete(member);
    }

    @Transactional
    public MemberDto transferOwnership(Long userId, Long spaceId, Long memberId) {
        Space space = lockSpace(spaceId);
        SpaceMember owner = spaceAccessService.requireOwner(userId, spaceId);
        requireTeam(space);
        SpaceMember target = spaceMemberRepository.findBySpaceIdAndId(spaceId, memberId)
                .orElseThrow(() -> new MemberNotFoundException("member not found"));
        if (target.getId().equals(owner.getId())) {
            throw new InvalidRequestException("cannot transfer ownership to yourself");
        }
        owner.updateRole(SpaceRole.EDITOR);
        target.updateRole(SpaceRole.OWNER);
        space.transferOwnership(target.getUser());
        return new MemberDto(target.getId(), target.getUser().getId(), target.getUser().getName(),
                target.getUser().getEmail(), target.getRole().name());
    }

    private Space lockSpace(Long spaceId) {
        return spaceRepository.findLockedById(spaceId)
                .orElseThrow(() -> new SpaceNotFoundException("space not found"));
    }

    private void requireTeam(Space space) {
        if (space.getType() != SpaceType.TEAM) {
            throw new ForbiddenException("personal spaces cannot be left or transferred");
        }
    }
}
