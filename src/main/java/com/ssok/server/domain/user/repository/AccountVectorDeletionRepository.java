package com.ssok.server.domain.user.repository;

import com.ssok.server.domain.user.entity.AccountVectorDeletionJob;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface AccountVectorDeletionRepository extends JpaRepository<AccountVectorDeletionJob, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from AccountVectorDeletionJob j where j.id = :id")
    Optional<AccountVectorDeletionJob> findLockedById(@Param("id") Long id);
    @Query("select j.id from AccountVectorDeletionJob j where j.nextAttemptAt <= :now order by j.id")
    List<Long> findDueIds(@Param("now") Instant now, Pageable pageable);
}
