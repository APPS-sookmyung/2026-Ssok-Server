package com.ssok.server.domain.bookmark.repository;

import com.ssok.server.domain.bookmark.entity.Bookmark;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    Page<Bookmark> findAllBySpaceIdAndLibraryId(Long spaceId, Long libraryId, Pageable pageable);
    Page<Bookmark> findAllBySpaceIdAndLibraryIsNull(Long spaceId, Pageable pageable);
    long countByLibraryId(Long libraryId);

    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true, clearAutomatically = true)
    @org.springframework.data.jpa.repository.Query("update Bookmark b set b.library = null where b.library.id = :libraryId")
    int unlinkLibrary(@org.springframework.data.repository.query.Param("libraryId") Long libraryId);

    interface LibraryCount {
        Long getLibraryId();
        long getBookmarkCount();
    }

    @org.springframework.data.jpa.repository.Query("select b.library.id as libraryId, count(b) as bookmarkCount from Bookmark b where b.space.id = :spaceId and b.library is not null group by b.library.id")
    List<LibraryCount> countByLibrariesInSpace(@org.springframework.data.repository.query.Param("spaceId") Long spaceId);

    Page<Bookmark> findAllBySpaceId(Long spaceId, Pageable pageable);

    long countBySpaceId(Long spaceId);

    Page<Bookmark> findByTagsContaining(String tag, Pageable pageable);

    List<Bookmark> findAllBySpaceIdIn(List<Long> spaceIds);

    List<Bookmark> findAllByIdInAndSpaceIdIn(List<Long> ids, List<Long> spaceIds);

    Page<Bookmark> findBySpaceIdInAndTagsContaining(List<Long> spaceIds, String tag, Pageable pageable);
}
