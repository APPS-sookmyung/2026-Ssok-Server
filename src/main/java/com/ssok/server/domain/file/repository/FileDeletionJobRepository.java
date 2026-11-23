package com.ssok.server.domain.file.repository;

import com.ssok.server.domain.file.entity.FileDeletionJob;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface FileDeletionJobRepository extends JpaRepository<FileDeletionJob, Long> {
    @Modifying(flushAutomatically = true)
    @Query("delete from FileDeletionJob j where j.id = :id")
    int cancelUploadIntent(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from FileDeletionJob j where j.id = :id")
    Optional<FileDeletionJob> findLockedById(@Param("id") Long id);

    @Query("select j.id from FileDeletionJob j where j.storageId = :storageId and j.nextAttemptAt <= :now order by j.id")
    List<Long> findDueIds(@Param("storageId") String storageId, @Param("now") Instant now, Pageable pageable);
}
