package com.ssok.server.domain.bookmark.service;

import com.ssok.server.common.exception.BookmarkNotFoundException;
import com.ssok.server.common.exception.InvalidRequestException;
import com.ssok.server.common.exception.SpaceNotFoundException;
import com.ssok.server.common.exception.UnauthenticatedException;
import com.ssok.server.common.util.TimeFormatter;
import com.ssok.server.domain.ai.vector.VectorSyncService;
import com.ssok.server.domain.bookmark.dto.AnalysisStatusDto;
import com.ssok.server.domain.bookmark.dto.BookmarkBulkResponse;
import com.ssok.server.domain.bookmark.dto.BookmarkDetailResponse;
import com.ssok.server.domain.bookmark.dto.BookmarkListResponse;
import com.ssok.server.domain.bookmark.dto.BookmarkLookupDto;
import com.ssok.server.domain.bookmark.dto.BookmarkSaveRequest;
import com.ssok.server.domain.bookmark.dto.BookmarkSaveResponse;
import com.ssok.server.domain.bookmark.dto.BookmarkSaverDto;
import com.ssok.server.domain.bookmark.dto.BookmarkSort;
import com.ssok.server.domain.bookmark.dto.BookmarkSummaryDto;
import com.ssok.server.domain.bookmark.entity.Bookmark;
import com.ssok.server.domain.bookmark.event.BookmarkAnalysisRequestedEvent;
import com.ssok.server.domain.bookmark.repository.BookmarkRepository;
import com.ssok.server.domain.comment.repository.CommentRepository;
import com.ssok.server.domain.comment.service.CommentService;
import com.ssok.server.domain.space.entity.Space;
import com.ssok.server.domain.space.repository.SpaceRepository;
import com.ssok.server.domain.space.service.SpaceAccessService;
import com.ssok.server.domain.user.entity.User;
import com.ssok.server.domain.user.repository.UserRepository;
import java.net.URI;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookmarkService {

    public static final int MAX_PAGE_SIZE = 100;
    public static final int MAX_RECENT_SIZE = 50;
    public static final int MAX_STATUS_IDS = 50;

    private final BookmarkRepository bookmarkRepository;
    private final SpaceRepository spaceRepository;
    private final UserRepository userRepository;
    private final SpaceAccessService spaceAccessService;
    private final ApplicationEventPublisher eventPublisher;
    private final VectorSyncService vectorSyncService;
    private final CommentService commentService;
    private final CommentRepository commentRepository;

    @Transactional
    public BookmarkSaveResponse save(Long userId, BookmarkSaveRequest request) {
        Space space = spaceRepository.findById(request.spaceId())
                .orElseThrow(() -> new SpaceNotFoundException("space not found"));
        spaceAccessService.validateMember(space.getId(), userId);
        User saver = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthenticatedException("authentication required"));

        Bookmark bookmark = Bookmark.builder()
                .url(request.url())
                .title(request.title() != null && !request.title().isBlank()
                        ? request.title().strip()
                        : extractHost(request.url()))
                .space(space)
                .savedBy(saver)
                .build();

        Bookmark saved = bookmarkRepository.saveAndFlush(bookmark);
        eventPublisher.publishEvent(BookmarkAnalysisRequestedEvent.from(saved));

        return new BookmarkSaveResponse(
                saved.getId(), saved.getTitle(), saved.getAnalysisStatus(), TimeFormatter.format(saved.getCreatedAt()));
    }

    @Transactional(readOnly = true)
    public BookmarkListResponse list(Long userId, Long spaceId, int page, int size, String sort) {
        validatePage(page, size, MAX_PAGE_SIZE);
        BookmarkSort bookmarkSort = BookmarkSort.from(sort);
        spaceAccessService.validateMember(spaceId, userId);

        Page<Bookmark> result = bookmarkRepository.findAllBySpaceId(
                spaceId, PageRequest.of(page, size, bookmarkSort.toSort()));

        return new BookmarkListResponse(
                toSummaries(result.getContent()),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    /**
     * 내가 속한 모든 스페이스(개인 + 소속 팀)에서 최근 저장된 북마크.
     */
    @Transactional(readOnly = true)
    public List<BookmarkSummaryDto> recent(Long userId, int size) {
        validatePage(0, size, MAX_RECENT_SIZE);
        Set<Long> spaceIds = spaceAccessService.getAccessibleSpaceIds(userId);
        if (spaceIds.isEmpty()) {
            return List.of();
        }

        return toSummaries(bookmarkRepository.findBySpaceIdIn(
                spaceIds, PageRequest.of(0, size, BookmarkSort.LATEST.toSort())).getContent());
    }

    /**
     * 여러 북마크의 분석 상태. 삭제됐거나 볼 수 없는 북마크는 결과에서 빠진다(오류로 처리하지 않음).
     */
    @Transactional(readOnly = true)
    public List<AnalysisStatusDto> analysisStatuses(Long userId, List<Long> bookmarkIds) {
        if (bookmarkIds.isEmpty() || bookmarkIds.size() > MAX_STATUS_IDS) {
            throw new InvalidRequestException("ids must contain 1 to " + MAX_STATUS_IDS + " bookmark ids");
        }
        Set<Long> spaceIds = spaceAccessService.getAccessibleSpaceIds(userId);

        return bookmarkRepository.findWithSpaceByIdIn(new HashSet<>(bookmarkIds)).stream()
                .filter(bookmark -> spaceIds.contains(bookmark.getSpace().getId()))
                .map(AnalysisStatusDto::from)
                .toList();
    }

    /**
     * 내가 속한 스페이스에 같은 URL이 저장돼 있는지 찾는다. 끝의 '/' 유무는 같은 주소로 본다.
     */
    @Transactional(readOnly = true)
    public List<BookmarkLookupDto> lookup(Long userId, String url) {
        if (url == null || url.isBlank()) {
            throw new InvalidRequestException("invalid url");
        }
        Set<Long> spaceIds = spaceAccessService.getAccessibleSpaceIds(userId);
        if (spaceIds.isEmpty()) {
            return List.of();
        }

        String trimmed = url.strip();
        String withoutSlash = trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
        return bookmarkRepository.findByUrlInAndSpaceIdInOrderByIdDesc(Set.of(withoutSlash, withoutSlash + "/"), spaceIds)
                .stream()
                .map(BookmarkLookupDto::from)
                .toList();
    }

    @Transactional
    public void delete(Long userId, Long bookmarkId) {
        Bookmark bookmark = bookmarkRepository.findById(bookmarkId)
                .orElseThrow(() -> new BookmarkNotFoundException("bookmark not found"));
        spaceAccessService.validateMember(bookmark.getSpace().getId(), userId);

        bookmarkRepository.delete(bookmark);
        vectorSyncService.enqueueDeletion(List.of(bookmarkId));
    }

    /**
     * 여러 북마크를 한 번에 삭제한다. 하나라도 없거나 권한이 없으면 아무것도 삭제하지 않는다.
     */
    @Transactional
    public BookmarkBulkResponse deleteAll(Long userId, List<Long> bookmarkIds) {
        List<Bookmark> bookmarks = loadAccessible(userId, bookmarkIds);
        List<Long> ids = bookmarks.stream().map(Bookmark::getId).toList();

        bookmarkRepository.deleteAll(bookmarks);
        vectorSyncService.enqueueDeletion(ids);

        return BookmarkBulkResponse.of(ids);
    }

    /**
     * 여러 북마크를 다른 스페이스로 옮긴다. 원래 스페이스와 대상 스페이스 모두의 멤버여야 하며,
     * 하나라도 없거나 권한이 없으면 아무것도 옮기지 않는다. 이미 대상 스페이스에 있는 북마크는 건너뛴다.
     */
    @Transactional
    public BookmarkBulkResponse move(Long userId, List<Long> bookmarkIds, Long targetSpaceId) {
        spaceAccessService.validateMember(targetSpaceId, userId);
        Space target = spaceRepository.getReferenceById(targetSpaceId);
        List<Bookmark> bookmarks = loadAccessible(userId, bookmarkIds);

        List<Bookmark> moving = bookmarks.stream()
                .filter(bookmark -> !bookmark.getSpace().getId().equals(targetSpaceId))
                .toList();
        moving.forEach(bookmark -> bookmark.moveTo(target));

        List<Long> movedIds = moving.stream().map(Bookmark::getId).toList();
        vectorSyncService.enqueueSpaceUpdate(movedIds);

        return BookmarkBulkResponse.of(movedIds);
    }

    @Transactional
    public BookmarkDetailResponse getDetail(Long userId, Long bookmarkId) {
        Bookmark bookmark = bookmarkRepository.findById(bookmarkId)
                .orElseThrow(() -> new BookmarkNotFoundException("bookmark not found"));

        // 권한 검사를 통과한 경우에만 방문 기록을 남긴다.
        spaceAccessService.validateMember(bookmark.getSpace().getId(), userId);

        bookmark.recordVisit();

        return new BookmarkDetailResponse(
                bookmark.getId(),
                bookmark.getTitle(),
                bookmark.getUrl(),
                bookmark.getSummary(),
                bookmark.getVisitCount(),
                TimeFormatter.format(bookmark.getCreatedAt()),
                TimeFormatter.format(bookmark.getLastVisitedAt()),
                bookmark.getCategory(),
                bookmark.getAnalysisStatus(),
                bookmark.getTags(),
                bookmark.getSpace().getId(),
                bookmark.getSpace().getName(),
                BookmarkSaverDto.from(bookmark.getSavedBy()),
                commentRepository.countByBookmarkId(bookmark.getId()));
    }

    private List<BookmarkSummaryDto> toSummaries(List<Bookmark> bookmarks) {
        var stats = commentService.statsFor(bookmarks.stream().map(Bookmark::getId).toList());
        return bookmarks.stream().map(BookmarkSummaryDto.withComments(stats)).toList();
    }

    private List<Bookmark> loadAccessible(Long userId, Collection<Long> bookmarkIds) {
        Set<Long> ids = new HashSet<>(bookmarkIds);
        List<Bookmark> bookmarks = bookmarkRepository.findAllById(ids);
        if (bookmarks.size() != ids.size()) {
            throw new BookmarkNotFoundException("bookmark not found");
        }

        bookmarks.stream()
                .map(bookmark -> bookmark.getSpace().getId())
                .distinct()
                .forEach(spaceId -> spaceAccessService.validateMember(spaceId, userId));
        return bookmarks;
    }

    private void validatePage(int page, int size, int maxSize) {
        if (page < 0 || size < 1 || size > maxSize) {
            throw new InvalidRequestException("page must be >= 0 and size must be between 1 and " + maxSize);
        }
    }

    // AI 분석 결과로 제목이 오기 전까지는 URL의 호스트를 제목으로 사용한다.
    private String extractHost(String url) {
        try {
            String host = URI.create(url).getHost();
            if (host == null) {
                return url;
            }
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (IllegalArgumentException e) {
            return url;
        }
    }
}
