package com.ssok.server.domain.bookmark.controller;

import com.ssok.server.common.response.ApiResponse;
import com.ssok.server.domain.bookmark.dto.BookmarkDetailResponse;
import com.ssok.server.domain.bookmark.dto.BookmarkListResponse;
import com.ssok.server.domain.bookmark.dto.BookmarkSaveRequest;
import com.ssok.server.domain.bookmark.dto.BookmarkSaveResponse;
import com.ssok.server.domain.bookmark.service.BookmarkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "북마크", description = "북마크 관련 API")
@RestController
@RequestMapping("/api/bookmarks")
@RequiredArgsConstructor
public class BookmarkController {

    private final BookmarkService bookmarkService;

    @Operation(summary = "사이트 저장", description = "AI가 해당 사이트를 분석하여 북마크에 저장")
    @PostMapping
    public ResponseEntity<ApiResponse<BookmarkSaveResponse>> save(@RequestBody @Valid BookmarkSaveRequest request) {
        BookmarkSaveResponse response = bookmarkService.save(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED.value(), "bookmark saved successfully", response));
    }

    @Operation(summary = "저장 사이트 리스트", description = "저장된 사이트 목록 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<BookmarkListResponse>> list(
            @RequestParam Long spaceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        BookmarkListResponse response = bookmarkService.list(spaceId, page, size);

        return ResponseEntity.ok(
                ApiResponse.success(HttpStatus.OK.value(), "bookmark list retrieved successfully", response));
    }

    @Operation(summary = "저장 사이트 삭제", description = "저장된 사이트 삭제")
    @DeleteMapping("/{bookmarkId}")
    public ResponseEntity<ApiResponse<Boolean>> delete(@PathVariable Long bookmarkId) {
        bookmarkService.delete(bookmarkId);

        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "bookmark deleted successfully", true));
    }

    @Operation(summary = "저장 사이트 조회", description = "저장된 사이트의 상세 정보 조회")
    @GetMapping("/{bookmarkId}")
    public ResponseEntity<ApiResponse<BookmarkDetailResponse>> getDetail(@PathVariable Long bookmarkId) {
        BookmarkDetailResponse response = bookmarkService.getDetail(bookmarkId);

        return ResponseEntity.ok(
                ApiResponse.success(HttpStatus.OK.value(), "bookmark retrieved successfully", response));
    }
}
