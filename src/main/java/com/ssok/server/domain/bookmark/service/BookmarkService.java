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

    @Transactional
    public BookmarkSaveResponse save(Long userId, BookmarkSaveRequest request) {
        Space space = spaceRepository.findById(request.spaceId())
                .orElseThrow(() -> new SpaceNotFoundException("space not found"));
        spaceAccessService.requireMember(userId, space.getId(), "not a member of this space");

        Bookmark bookmark = Bookmark.builder()
                .url(request.url())
                .title(extractHost(request.url()))
                .space(space)
                .build();

        Bookmark saved = bookmarkRepository.saveAndFlush(bookmark);
        eventPublisher.publishEvent(new BookmarkAnalysisRequestedEvent(saved.getId(), saved.getUrl()));

        return new BookmarkSaveResponse(
                saved.getId(), saved.getTitle(), saved.getAnalysisStatus(), TimeFormatter.format(saved.getCreatedAt()));
    }

    @Transactional(readOnly = true)
    public BookmarkListResponse list(Long userId, Long spaceId, int page, int size) {
        if (!spaceRepository.existsById(spaceId)) {
            throw new SpaceNotFoundException("space not found");
        }
        spaceAccessService.requireMember(userId, spaceId, "not a member of this space");

        Page<Bookmark> result = bookmarkRepository.findAllBySpaceId(spaceId, PageRequest.of(page, size));

        List<BookmarkSummaryDto> content = result.getContent().stream()
                .map(bookmark -> new BookmarkSummaryDto(
                        bookmark.getId(),
                        bookmark.getTitle(),
                        bookmark.getSummary(),
                        bookmark.getVisitCount(),
                        bookmark.getLastVisitedAt() != null ? TimeFormatter.format(bookmark.getLastVisitedAt()) : null,
                        bookmark.getCategory(),
                        bookmark.getAnalysisStatus(),
                        bookmark.getTags()))
                .toList();

        return new BookmarkListResponse(
                content, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public void delete(Long userId, Long bookmarkId) {
        Bookmark bookmark = bookmarkRepository.findById(bookmarkId)
                .orElseThrow(() -> new BookmarkNotFoundException("bookmark not found"));
        spaceAccessService.requireMember(userId, bookmark.getSpace().getId(), "not a member of this space");

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
                bookmark.getTags());
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
