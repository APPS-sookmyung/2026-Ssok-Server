package com.ssok.server.domain.auth.repository;

import com.ssok.server.domain.auth.entity.AuthSession;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface AuthSessionRepository extends JpaRepository<AuthSession, String> {
    Optional<AuthSession> findByIdAndUserId(String id, Long userId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AuthSession s where s.id = :id")
    Optional<AuthSession> findLockedById(@Param("id") String id);
}
