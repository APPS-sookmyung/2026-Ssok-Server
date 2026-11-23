package com.ssok.server.domain.space.repository;

import com.ssok.server.domain.space.entity.SpaceInvitation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public interface SpaceInvitationRepository extends JpaRepository<SpaceInvitation, Long> {
    List<SpaceInvitation> findAllByEmailIgnoreCase(String email);
    Optional<SpaceInvitation> findByTokenHash(String hash);
    Optional<SpaceInvitation> findByIdAndSpaceId(Long id, Long spaceId);
    List<SpaceInvitation> findAllBySpaceIdOrderByIdDesc(Long spaceId);
    List<SpaceInvitation> findAllBySpaceIdAndEmailAndStatus(Long spaceId, String email, SpaceInvitation.Status status);

    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query("update SpaceInvitation i set i.delivery = :delivery where i.id = :id and i.tokenHash = :hash")
    int recordDelivery(@Param("id") Long id, @Param("hash") String hash,
            @Param("delivery") SpaceInvitation.Delivery delivery);
}
