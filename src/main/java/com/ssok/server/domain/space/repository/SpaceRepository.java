package com.ssok.server.domain.space.repository;

import com.ssok.server.domain.space.entity.Space;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpaceRepository extends JpaRepository<Space, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Space s where s.id = :id")
    Optional<Space> findLockedById(@Param("id") Long id);
}
