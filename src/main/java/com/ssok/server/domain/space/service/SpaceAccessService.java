package com.ssok.server.domain.space.service;

import com.ssok.server.common.exception.ForbiddenException;
import com.ssok.server.common.exception.SpaceNotFoundException;
import com.ssok.server.common.exception.UnauthenticatedException;
import com.ssok.server.domain.space.entity.SpaceMember;
import com.ssok.server.domain.space.entity.SpaceRole;
import com.ssok.server.domain.space.repository.SpaceMemberRepository;
import com.ssok.server.domain.space.repository.SpaceRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 스페이스 멤버십과 검색 범위를 확인하는 공통 정책. 사용자 ID는 인증 정보에서 전달한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SpaceAccessService {

    private final SpaceMemberRepository spaceMemberRepository;
    private final SpaceRepository spaceRepository;

    /** 현재 멤버십이 있는 개인·팀 스페이스 ID만 반환한다. 결과는 캐시하지 않는다. */
    public List<Long> getAccessibleSpaceIds(Long userId) {
        requireUserId(userId);
        return spaceMemberRepository.findAllByUserId(userId).stream()
                .map(member -> member.getSpace().getId())
                .distinct()
                .toList();
    }

    public SpaceMember requireMember(Long userId, Long spaceId) {
        return requireMember(userId, spaceId, "space access denied");
    }

    /** 호출 API의 기존 오류 메시지를 유지할 때 사용한다. */
    public SpaceMember requireMember(Long userId, Long spaceId, String message) {
        requireUserId(userId);
        return spaceMemberRepository.findBySpaceIdAndUserId(spaceId, userId)
                .orElseThrow(() -> new ForbiddenException(message));
    }

    public SpaceMember requireOwner(Long userId, Long spaceId) {
        return requireOwner(userId, spaceId, "only owner can manage the space");
    }

    public SpaceMember requireWrite(Long userId, Long spaceId) {
        SpaceMember member = requireMember(userId, spaceId);
        if (!member.getRole().canWrite()) {
            throw new ForbiddenException("viewer cannot modify space data");
        }
        return member;
    }

    public SpaceMember requireSearch(Long userId, Long spaceId) {
        SpaceMember member = requireMember(userId, spaceId);
        if (!member.getRole().canSearch()) {
            throw new ForbiddenException("viewer cannot use search or AI");
        }
        return member;
    }

    /** 조회 전용 VIEWER 스페이스는 검색·AI 대상에 포함하지 않는다. */
    public List<Long> getSearchableSpaceIds(Long userId) {
        requireUserId(userId);
        return spaceMemberRepository.findAllByUserId(userId).stream()
                .filter(member -> member.getRole().canSearch())
                .map(member -> member.getSpace().getId())
                .distinct()
                .toList();
    }

    /** 기존 API별 오류 메시지를 유지하면서 OWNER 정책을 공유한다. */
    public SpaceMember requireOwner(Long userId, Long spaceId, String message) {
        SpaceMember member = requireMember(userId, spaceId);
        if (member.getRole() != SpaceRole.OWNER) {
            throw new ForbiddenException(message);
        }
        return member;
    }

    /** null이면 전체 허용 공간, 지정된 ID는 존재 여부와 멤버십을 확인한다. */
    public List<Long> resolveSearchSpaceIds(Long userId, Long spaceId) {
        requireUserId(userId);
        if (spaceId == null) {
            return getSearchableSpaceIds(userId);
        }
        if (!spaceRepository.existsById(spaceId)) {
            throw new SpaceNotFoundException("space not found");
        }
        requireSearch(userId, spaceId);
        return List.of(spaceId);
    }

    private void requireUserId(Long userId) {
        if (userId == null) {
            throw new UnauthenticatedException("authentication required");
        }
    }
}
