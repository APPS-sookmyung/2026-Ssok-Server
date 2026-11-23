package com.ssok.server.domain.comment.repository;

import com.ssok.server.domain.comment.entity.Comment;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    @EntityGraph(attributePaths = "author")
    Page<Comment> findByBookmarkId(Long bookmarkId, Pageable pageable);

    @EntityGraph(attributePaths = {"author", "bookmark"})
    Optional<Comment> findByIdAndBookmarkId(Long id, Long bookmarkId);

    long countByBookmarkId(Long bookmarkId);

    @Query("""
            select c.bookmark.id as bookmarkId, count(c) as count
            from Comment c
            where c.bookmark.id in :bookmarkIds
            group by c.bookmark.id
            """)
    List<BookmarkCommentCount> countByBookmarkIds(@Param("bookmarkIds") Collection<Long> bookmarkIds);

    // 북마크별 가장 최근 댓글
    @Query("""
            select c from Comment c left join fetch c.author
            where c.id in (
                select max(c2.id) from Comment c2 where c2.bookmark.id in :bookmarkIds group by c2.bookmark.id
            )
            """)
    List<Comment> findLatestByBookmarkIds(@Param("bookmarkIds") Collection<Long> bookmarkIds);

    interface BookmarkCommentCount {
        Long getBookmarkId();

        long getCount();
    }
}
