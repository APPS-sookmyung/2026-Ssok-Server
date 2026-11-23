package com.ssok.server.domain.space.service;

import com.ssok.server.common.exception.ForbiddenException;
import com.ssok.server.common.exception.SpaceNotFoundException;
import com.ssok.server.domain.space.repository.SpaceMemberRepository;
import com.ssok.server.domain.space.repository.SpaceRepository;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 검색·추천·태그 등 여러 스페이스를 가로지르는 조회의 범위를 정한다.
 * 기본 범위는 사용자가 멤버인 모든 스페이스(개인 + 소속 팀)이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SpaceAccessService {

    private final SpaceRepository spaceRepository;
    private final SpaceMemberRepository spaceMemberRepository;

    public Set<Long> getAccessibleSpaceIds(Long userId) {
        return spaceMemberRepository.findAllByUserId(userId).stream()
                .map(member -> member.getSpace().getId())
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * spaceId가 없으면 접근 가능한 전체 스페이스, 있으면 그 스페이스만 반환한다.
     *
     * @throws SpaceNotFoundException 지정한 스페이스가 없을 때
     * @throws ForbiddenException     지정한 스페이스의 멤버가 아닐 때
     */
    public List<Long> resolveSearchScope(Long userId, Long spaceId) {
        if (spaceId == null) {
            return getAccessibleSpaceIds(userId).stream().sorted().toList();
        }
        validateMember(spaceId, userId);
        return List.of(spaceId);
    }

    public void validateMember(Long spaceId, Long userId) {
        if (!spaceRepository.existsById(spaceId)) {
            throw new SpaceNotFoundException("space not found");
        }
        if (!spaceMemberRepository.existsBySpaceIdAndUserId(spaceId, userId)) {
            throw new ForbiddenException("space access denied");
        }
    }
}
