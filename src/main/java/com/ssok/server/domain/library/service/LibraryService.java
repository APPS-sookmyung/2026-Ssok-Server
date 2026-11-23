package com.ssok.server.domain.library.service;

import com.ssok.server.common.exception.*;
import com.ssok.server.domain.bookmark.dto.BookmarkListResponse;
import com.ssok.server.domain.bookmark.dto.BookmarkSummaryDto;
import com.ssok.server.domain.bookmark.entity.Bookmark;
import com.ssok.server.domain.bookmark.repository.BookmarkRepository;
import com.ssok.server.domain.library.dto.*;
import com.ssok.server.domain.library.entity.Library;
import com.ssok.server.domain.library.repository.LibraryRepository;
import com.ssok.server.domain.space.entity.Space;
import com.ssok.server.domain.space.repository.SpaceRepository;
import com.ssok.server.domain.space.service.SpaceAccessService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.LockModeType;
import java.util.Map;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LibraryService {
    private final LibraryRepository libraries;
    private final BookmarkRepository bookmarks;
    private final SpaceRepository spaces;
    private final SpaceAccessService access;
    private final EntityManager entityManager;

    @Transactional
    public LibraryResponse create(Long userId, Long spaceId, String name) {
        Space space = writableSpace(userId, spaceId);
        requireUniqueName(spaceId, name, -1L);
        Library library = libraries.saveAndFlush(new Library(space, name));
        return LibraryResponse.from(library, 0);
    }

    public List<LibraryResponse> list(Long userId, Long spaceId, String query) {
        readableSpace(userId, spaceId);
        boolean search = query != null && !query.isBlank();
        if (search) access.requireSearch(userId, spaceId);
        var result = search ? libraries.findBySpaceIdAndNameContainingIgnoreCaseOrderByIdAsc(spaceId, query.strip())
                : libraries.findAllBySpaceIdOrderByIdAsc(spaceId);
        Map<Long, Long> counts = bookmarks.countByLibrariesInSpace(spaceId).stream()
                .collect(Collectors.toMap(BookmarkRepository.LibraryCount::getLibraryId, BookmarkRepository.LibraryCount::getBookmarkCount));
        return result.stream().map(library -> LibraryResponse.from(library, counts.getOrDefault(library.getId(), 0L))).toList();
    }

    public LibraryResponse detail(Long userId, Long spaceId, Long libraryId) {
        readableSpace(userId, spaceId);
        return LibraryResponse.from(find(spaceId, libraryId), bookmarks.countByLibraryId(libraryId));
    }

    @Transactional
    public LibraryResponse rename(Long userId, Long spaceId, Long libraryId, String name) {
        writableSpace(userId, spaceId);
        Library library = find(spaceId, libraryId);
        requireUniqueName(spaceId, name, libraryId);
        library.rename(name);
        return LibraryResponse.from(library, bookmarks.countByLibraryId(libraryId));
    }

    @Transactional
    public void delete(Long userId, Long spaceId, Long libraryId) {
        writableSpace(userId, spaceId);
        find(spaceId, libraryId);
        // 북마크·태그·최초 저장 시각·분석 상태는 건드리지 않는다. 벌크 갱신 후 관리 객체도 비운다.
        bookmarks.unlinkLibrary(libraryId);
        libraries.deleteById(libraryId);
    }

    public BookmarkListResponse bookmarks(Long userId, Long spaceId, Long libraryId, int page, int size, String sort) {
        readableSpace(userId, spaceId);
        find(spaceId, libraryId);
        var result = bookmarks.findAllBySpaceIdAndLibraryId(spaceId, libraryId, pageable(page, size, sort));
        return new BookmarkListResponse(result.getContent().stream().map(BookmarkSummaryDto::from).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public BookmarkSummaryDto moveBookmark(Long userId, Long bookmarkId, Long libraryId) {
        Bookmark bookmark = bookmarks.findById(bookmarkId).orElseThrow(() -> new BookmarkNotFoundException("bookmark not found"));
        writableSpace(userId, bookmark.getSpace().getId());
        try {
            entityManager.refresh(bookmark, LockModeType.PESSIMISTIC_WRITE);
        } catch (EntityNotFoundException e) {
            throw new BookmarkNotFoundException("bookmark not found");
        }
        Library library = libraryId == null ? null : find(bookmark.getSpace().getId(), libraryId);
        bookmark.changeLibrary(library);
        return BookmarkSummaryDto.from(bookmark);
    }

    public static PageRequest pageable(int page, int size, String sort) {
        if (page < 0 || size < 1 || size > 100) throw new InvalidRequestException("page must be >= 0 and size must be 1..100");
        Sort order = switch (sort) {
            case "recent" -> Sort.by(Sort.Direction.DESC, "createdAt", "id");
            case "oldest" -> Sort.by(Sort.Direction.ASC, "createdAt", "id");
            case "title" -> Sort.by("title", "id");
            default -> throw new InvalidRequestException("sort must be recent, oldest or title");
        };
        return PageRequest.of(page, size, order);
    }

    private Space writableSpace(Long userId, Long spaceId) {
        Space space = spaces.findLockedById(spaceId).orElseThrow(() -> new SpaceNotFoundException("space not found"));
        access.requireWrite(userId, spaceId);
        return space;
    }

    private void readableSpace(Long userId, Long spaceId) {
        if (!spaces.existsById(spaceId)) throw new SpaceNotFoundException("space not found");
        access.requireMember(userId, spaceId);
    }

    private Library find(Long spaceId, Long libraryId) {
        return libraries.findByIdAndSpaceId(libraryId, spaceId).orElseThrow(() -> new LibraryNotFoundException("library not found in this space"));
    }

    private void requireUniqueName(Long spaceId, String name, Long exceptId) {
        if (libraries.existsBySpaceIdAndNameKeyAndIdNot(spaceId, Library.key(name), exceptId)) {
            throw new DuplicateLibraryException("library name already exists in this space");
        }
    }
}
