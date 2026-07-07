package com.ssok.server.domain.space.repository;

import com.ssok.server.domain.space.entity.SpaceMember;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpaceMemberRepository extends JpaRepository<SpaceMember, Long> {

    List<SpaceMember> findAllByUserId(Long userId);

    List<SpaceMember> findAllBySpaceId(Long spaceId);

    Optional<SpaceMember> findBySpaceIdAndId(Long spaceId, Long memberId);
}
