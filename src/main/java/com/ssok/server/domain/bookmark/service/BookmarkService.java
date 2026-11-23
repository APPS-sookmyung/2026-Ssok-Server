package com.ssok.server.domain.bookmark.service;

import com.ssok.server.common.exception.BookmarkNotFoundException;
import com.ssok.server.common.exception.SpaceNotFoundException;
import com.ssok.server.common.util.TimeFormatter;
import com.ssok.server.domain.bookmark.dto.BookmarkDetailResponse;
import com.ssok.server.domain.bookmark.dto.BookmarkListResponse;
import com.ssok.server.domain.bookmark.dto.BookmarkSaveRequest;
import com.ssok.server.domain.bookmark.dto.BookmarkSaveResponse;
import com.ssok.server.domain.bookmark.dto.BookmarkSummaryDto;
import com.ssok.server.domain.bookmark.entity.Bookmark;
import com.ssok.server.domain.bookmark.event.BookmarkAnalysisRequestedEvent;
import com.ssok.server.domain.bookmark.repository.BookmarkRepository;
import com.ssok.server.domain.space.entity.Space;
import com.ssok.server.domain.space.service.SpaceAccessService;
import com.ssok.server.domain.space.repository.SpaceRepository;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookmarkService {

    private final BookmarkRepository bookmarkRepository;
    private final SpaceRepository spaceRepository;
    private final SpaceAccessService spaceAccessService;
    private final ApplicationEventPublisher eventPublisher;
    private final com.ssok.server.domain.library.repository.LibraryRepository libraryRepository;

    @Transactional
    public BookmarkSaveResponse save(Long userId, BookmarkSaveRequest request) {
        Space space = spaceRepository.findLockedById(request.spaceId())
                .orElseThrow(() -> new SpaceNotFoundException("space not found"));
        spaceAccessService.requireWrite(userId, space.getId());

        Bookmark bookmark = Bookmark.builder()
                .url(request.url())
                .title(extractHost(request.url()))
                .space(space)
                .build();

        if (request.libraryId() != null) {
            bookmark.changeLibrary(libraryRepository.findByIdAndSpaceId(request.libraryId(), space.getId())
                    .orElseThrow(() -> new com.ssok.server.common.exception.LibraryNotFoundException("library not found in this space")));
        }

        Bookmark saved = bookmarkRepository.saveAndFlush(bookmark);
        eventPublisher.publishEvent(new BookmarkAnalysisRequestedEvent(saved.getId(), saved.getUrl()));

        return new BookmarkSaveResponse(
                saved.getId(), saved.getTitle(), saved.getAnalysisStatus(), TimeFormatter.format(saved.getCreatedAt()));
    }

    @Transactional(readOnly = true)
    public BookmarkListResponse list(Long userId, Long spaceId, int page, int size) {
        return list(userId, spaceId, page, size, null, false);
    }

    @Transactional(readOnly = true)
    public BookmarkListResponse list(Long userId, Long spaceId, int page, int size, Long libraryId, boolean unclassified) {
        if (!spaceRepository.existsById(spaceId)) {
            throw new SpaceNotFoundException("space not found");
        }
        spaceAccessService.requireMember(userId, spaceId, "not a member of this space");

        if (libraryId != null && unclassified) {
            throw new com.ssok.server.common.exception.InvalidRequestException("libraryId and unclassified cannot be combined");
        }
        var pageable = com.ssok.server.domain.library.service.LibraryService.pageable(page, size, "recent");
        Page<Bookmark> result;
        if (libraryId != null) {
            libraryRepository.findByIdAndSpaceId(libraryId, spaceId)
                    .orElseThrow(() -> new com.ssok.server.common.exception.LibraryNotFoundException("library not found in this space"));
            result = bookmarkRepository.findAllBySpaceIdAndLibraryId(spaceId, libraryId, pageable);
        } else if (unclassified) {
            result = bookmarkRepository.findAllBySpaceIdAndLibraryIsNull(spaceId, pageable);
        } else {
            result = bookmarkRepository.findAllBySpaceId(spaceId, pageable);
        }

        List<BookmarkSummaryDto> content = result.getContent().stream()
                .map(BookmarkSummaryDto::from)
                .toList();

        return new BookmarkListResponse(
                content, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void delete(Long userId, Long bookmarkId) {
        Bookmark bookmark = bookmarkRepository.findById(bookmarkId)
                .orElseThrow(() -> new BookmarkNotFoundException("bookmark not found"));
        spaceRepository.findLockedById(bookmark.getSpace().getId())
                .orElseThrow(() -> new SpaceNotFoundException("space not found"));
        spaceAccessService.requireWrite(userId, bookmark.getSpace().getId());

        bookmarkRepository.delete(bookmark);
    }

    @Transactional
    public BookmarkDetailResponse getDetail(Long userId, Long bookmarkId) {
        Bookmark bookmark = bookmarkRepository.findById(bookmarkId)
                .orElseThrow(() -> new BookmarkNotFoundException("bookmark not found"));

        // 권한 검사를 통과한 경우에만 방문 기록을 남긴다.
        spaceAccessService.requireMember(userId, bookmark.getSpace().getId(), "not a member of this space");

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
                bookmark.getLibrary() == null ? null : bookmark.getLibrary().getId());
    }

    // AI 분석 결과에는 제목이 없으므로 URL의 호스트를 제목으로 사용한다.
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
