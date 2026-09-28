package com.ssok.server.domain.bookmark.repository;

import com.ssok.server.domain.bookmark.entity.Bookmark;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    Page<Bookmark> findAllBySpaceId(Long spaceId, Pageable pageable);

    long countBySpaceId(Long spaceId);

    Page<Bookmark> findByTagsContaining(String tag, Pageable pageable);
}
