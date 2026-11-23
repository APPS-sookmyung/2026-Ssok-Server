package com.ssok.server.domain.ai.vector;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VectorDeletionTaskRepository extends JpaRepository<VectorDeletionTask, Long> {

    List<VectorDeletionTask> findAllByBookmarkIdIn(Collection<Long> bookmarkIds);

    @Query("select t from VectorDeletionTask t where t.nextAttemptAt <= :now order by t.nextAttemptAt asc")
    List<VectorDeletionTask> findDue(@Param("now") LocalDateTime now, Pageable pageable);
}
