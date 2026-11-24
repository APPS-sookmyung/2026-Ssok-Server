package com.ssok.server.domain.library.controller;

import com.ssok.server.common.exception.UnauthenticatedException;
import com.ssok.server.common.response.ApiResponse;
import com.ssok.server.domain.bookmark.dto.BookmarkListResponse;
import com.ssok.server.domain.bookmark.dto.BookmarkSummaryDto;
import com.ssok.server.domain.library.dto.*;
import com.ssok.server.domain.library.service.LibraryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "라이브러리", description = "스페이스의 1단계 폴더. 삭제 시 북마크 유지")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class LibraryController {
    private final LibraryService libraries;

    @PostMapping("/spaces/{spaceId}/libraries")
    public ResponseEntity<ApiResponse<LibraryResponse>> create(Authentication auth, @PathVariable Long spaceId,
            @RequestBody @Valid LibraryRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.success(201, "library created", libraries.create(userId(auth), spaceId, request.name())));
    }

    @GetMapping("/spaces/{spaceId}/libraries")
    public ApiResponse<List<LibraryResponse>> list(Authentication auth, @PathVariable Long spaceId,
            @RequestParam(required = false) String query) {
        return ApiResponse.success(200, "libraries retrieved", libraries.list(userId(auth), spaceId, query));
    }

    @GetMapping("/spaces/{spaceId}/libraries/{libraryId}")
    public ApiResponse<LibraryResponse> detail(Authentication auth, @PathVariable Long spaceId, @PathVariable Long libraryId) {
        return ApiResponse.success(200, "library retrieved", libraries.detail(userId(auth), spaceId, libraryId));
    }

    @PatchMapping("/spaces/{spaceId}/libraries/{libraryId}")
    public ApiResponse<LibraryResponse> rename(Authentication auth, @PathVariable Long spaceId, @PathVariable Long libraryId,
            @RequestBody @Valid LibraryRequest request) {
        return ApiResponse.success(200, "library updated", libraries.rename(userId(auth), spaceId, libraryId, request.name()));
    }

    @DeleteMapping("/spaces/{spaceId}/libraries/{libraryId}")
    public ApiResponse<Boolean> delete(Authentication auth, @PathVariable Long spaceId, @PathVariable Long libraryId) {
        libraries.delete(userId(auth), spaceId, libraryId);
        return ApiResponse.success(200, "library deleted; bookmarks preserved", true);
    }

    @GetMapping("/spaces/{spaceId}/libraries/{libraryId}/bookmarks")
    public ApiResponse<BookmarkListResponse> bookmarks(Authentication auth, @PathVariable Long spaceId, @PathVariable Long libraryId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "recent") String sort) {
        return ApiResponse.success(200, "library bookmarks retrieved", libraries.bookmarks(userId(auth), spaceId, libraryId, page, size, sort));
    }

    @PatchMapping("/bookmarks/{bookmarkId}/library")
    public ApiResponse<BookmarkSummaryDto> move(Authentication auth, @PathVariable Long bookmarkId,
            @RequestBody @Valid BookmarkLibraryRequest request) {
        return ApiResponse.success(200, "bookmark library updated", libraries.moveBookmark(userId(auth), bookmarkId, request.libraryId()));
    }

    private Long userId(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof Long id)) throw new UnauthenticatedException("authentication required");
        return id;
    }
}
