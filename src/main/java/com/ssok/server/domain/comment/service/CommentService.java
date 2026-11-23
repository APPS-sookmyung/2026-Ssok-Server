package com.ssok.server.domain.comment.service;

import com.ssok.server.common.exception.BookmarkNotFoundException;
import com.ssok.server.common.exception.CommentNotFoundException;
import com.ssok.server.common.exception.ForbiddenException;
import com.ssok.server.common.exception.InvalidRequestException;
import com.ssok.server.common.exception.UnauthenticatedException;
import com.ssok.server.domain.bookmark.entity.Bookmark;
import com.ssok.server.domain.bookmark.repository.BookmarkRepository;
import com.ssok.server.domain.comment.dto.CommentListResponse;
import com.ssok.server.domain.comment.dto.CommentPreviewDto;
import com.ssok.server.domain.comment.dto.CommentResponse;
import com.ssok.server.domain.comment.dto.CommentStats;
import com.ssok.server.domain.comment.entity.Comment;
import com.ssok.server.domain.comment.event.CommentCreatedEvent;
import com.ssok.server.domain.comment.repository.CommentRepository;
import com.ssok.server.domain.space.service.SpaceAccessService;
import com.ssok.server.domain.user.entity.User;
import com.ssok.server.domain.user.repository.UserRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 북마크 댓글. 북마크가 속한 스페이스의 멤버만 보고 쓸 수 있고, 수정·삭제는 작성자만 할 수 있다.
 */
@Service
@RequiredArgsConstructor
public class CommentService {

    private static final int MAX_PAGE_SIZE = 100;

    private final CommentRepository commentRepository;
    private final BookmarkRepository bookmarkRepository;
    private final UserRepository userRepository;
    private final SpaceAccessService spaceAccessService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public CommentListResponse list(Long userId, Long bookmarkId, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidRequestException("page must be >= 0 and size must be between 1 and " + MAX_PAGE_SIZE);
        }
        loadAccessibleBookmark(userId, bookmarkId);

        Page<Comment> result = commentRepository.findByBookmarkId(
                bookmarkId, PageRequest.of(page, size, Sort.by(Sort.Order.asc("id"))));

        return new CommentListResponse(
                result.getContent().stream().map(comment -> CommentResponse.of(comment, userId)).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public CommentResponse create(Long userId, Long bookmarkId, String content) {
        Bookmark bookmark = loadAccessibleBookmark(userId, bookmarkId);
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthenticatedException("authentication required"));

        Comment comment = commentRepository.save(
                Comment.builder().bookmark(bookmark).author(author).content(content.strip()).build());

        eventPublisher.publishEvent(new CommentCreatedEvent(
                comment.getId(), bookmark.getId(), bookmark.getSpace().getId(), userId,
                bookmark.getSavedBy() != null ? bookmark.getSavedBy().getId() : null));

        return CommentResponse.of(comment, userId);
    }

    @Transactional
    public CommentResponse update(Long userId, Long bookmarkId, Long commentId, String content) {
        Comment comment = loadOwnComment(userId, bookmarkId, commentId);
        comment.edit(content.strip());
        return CommentResponse.of(comment, userId);
    }

    @Transactional
    public void delete(Long userId, Long bookmarkId, Long commentId) {
        commentRepository.delete(loadOwnComment(userId, bookmarkId, commentId));
    }

    /**
     * 북마크 카드에 붙일 댓글 개수와 최근 댓글. 쿼리 2번으로 여러 북마크를 한꺼번에 조회한다.
     */
    @Transactional(readOnly = true)
    public Map<Long, CommentStats> statsFor(Collection<Long> bookmarkIds) {
        Map<Long, CommentStats> stats = new HashMap<>();
        if (bookmarkIds.isEmpty()) {
            return stats;
        }

        Map<Long, Long> counts = new HashMap<>();
        commentRepository.countByBookmarkIds(bookmarkIds)
                .forEach(row -> counts.put(row.getBookmarkId(), row.getCount()));
        commentRepository.findLatestByBookmarkIds(bookmarkIds).forEach(latest -> {
            Long bookmarkId = latest.getBookmark().getId();
            stats.put(bookmarkId, new CommentStats(counts.getOrDefault(bookmarkId, 0L), CommentPreviewDto.from(latest)));
        });
        return stats;
    }

    private Bookmark loadAccessibleBookmark(Long userId, Long bookmarkId) {
        Bookmark bookmark = bookmarkRepository.findById(bookmarkId)
                .orElseThrow(() -> new BookmarkNotFoundException("bookmark not found"));
        spaceAccessService.validateMember(bookmark.getSpace().getId(), userId);
        return bookmark;
    }

    // 스페이스에서 나간 작성자는 자기 댓글이라도 더 이상 수정·삭제할 수 없다.
    private Comment loadOwnComment(Long userId, Long bookmarkId, Long commentId) {
        loadAccessibleBookmark(userId, bookmarkId);
        Comment comment = commentRepository.findByIdAndBookmarkId(commentId, bookmarkId)
                .orElseThrow(() -> new CommentNotFoundException("comment not found"));
        if (!comment.isWrittenBy(userId)) {
            throw new ForbiddenException("only the author can modify this comment");
        }
        return comment;
    }
}
