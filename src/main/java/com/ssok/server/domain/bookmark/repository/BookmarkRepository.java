package com.ssok.server.domain.bookmark.repository;

import com.ssok.server.domain.bookmark.entity.Bookmark;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    Page<Bookmark> findAllBySpaceId(Long spaceId, Pageable pageable);

    long countBySpaceId(Long spaceId);

    Page<Bookmark> findByTagsContaining(String tag, Pageable pageable);

    List<Bookmark> findAllBySpaceIdIn(List<Long> spaceIds);

    List<Bookmark> findAllByIdInAndSpaceIdIn(List<Long> ids, List<Long> spaceIds);

    Page<Bookmark> findBySpaceIdInAndTagsContaining(List<Long> spaceIds, String tag, Pageable pageable);
}
