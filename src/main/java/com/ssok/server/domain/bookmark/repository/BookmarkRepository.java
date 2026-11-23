package com.ssok.server.domain.bookmark.repository;

import com.ssok.server.domain.bookmark.entity.Bookmark;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    Page<Bookmark> findAllBySpaceId(Long spaceId, Pageable pageable);

    long countBySpaceId(Long spaceId);

    List<Bookmark> findAllBySpaceIdIn(Collection<Long> spaceIds);

    Page<Bookmark> findByTagsContainingAndSpaceIdIn(String tag, Collection<Long> spaceIds, Pageable pageable);

    @Query("select b.id from Bookmark b where b.space.id = :spaceId")
    List<Long> findIdsBySpaceId(@Param("spaceId") Long spaceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Bookmark b where b.id = :id")
    Optional<Bookmark> findByIdForUpdate(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Bookmark b set b.analysisStatus = com.ssok.server.domain.bookmark.entity.AnalysisStatus.FAILED
            where b.analysisStatus = com.ssok.server.domain.bookmark.entity.AnalysisStatus.PENDING
              and b.analysisRequestedAt < :threshold
            """)
    int failPendingRequestedBefore(@Param("threshold") LocalDateTime threshold);
}
