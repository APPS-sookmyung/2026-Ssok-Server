package com.ssok.server.domain.space.repository;

import com.ssok.server.domain.space.entity.SpaceMember;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpaceMemberRepository extends JpaRepository<SpaceMember, Long> {

    List<SpaceMember> findAllByUserId(Long userId);

    List<SpaceMember> findAllBySpaceId(Long spaceId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_READ)
    @org.springframework.data.jpa.repository.Query("select m from SpaceMember m where m.space.id = :spaceId")
    List<SpaceMember> findAllLockedBySpaceId(@org.springframework.data.repository.query.Param("spaceId") Long spaceId);

    Optional<SpaceMember> findBySpaceIdAndId(Long spaceId, Long memberId);

    boolean existsBySpaceIdAndUserId(Long spaceId, Long userId);

    Optional<SpaceMember> findBySpaceIdAndUserId(
            Long spaceId,
            Long userId
    );

    long countBySpaceId(Long spaceId);
}
