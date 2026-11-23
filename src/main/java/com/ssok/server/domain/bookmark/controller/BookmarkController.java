package com.ssok.server.domain.bookmark.controller;

import com.ssok.server.common.exception.UnauthenticatedException;
import com.ssok.server.common.response.ApiResponse;
import com.ssok.server.domain.bookmark.dto.BookmarkBulkDeleteRequest;
import com.ssok.server.domain.bookmark.dto.BookmarkBulkResponse;
import com.ssok.server.domain.bookmark.dto.BookmarkDetailResponse;
import com.ssok.server.domain.bookmark.dto.BookmarkListResponse;
import com.ssok.server.domain.bookmark.dto.BookmarkLookupDto;
import com.ssok.server.domain.bookmark.dto.BookmarkMoveRequest;
import com.ssok.server.domain.bookmark.dto.BookmarkSaveRequest;
import com.ssok.server.domain.bookmark.dto.BookmarkSaveResponse;
import com.ssok.server.domain.bookmark.dto.BookmarkSort;
import com.ssok.server.domain.bookmark.dto.BookmarkSummaryDto;
import com.ssok.server.domain.bookmark.service.BookmarkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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

    @Operation(summary = "사이트 저장",
            description = "북마크를 저장하고 AI 분석을 요청 (분석 결과는 비동기로 반영). "
                    + "브라우저 확장 프로그램은 현재 탭 제목을 title로 함께 보낼 수 있음")
    @PostMapping
    public ResponseEntity<ApiResponse<BookmarkSaveResponse>> save(
            @RequestBody @Valid BookmarkSaveRequest request, Authentication authentication) {
        BookmarkSaveResponse response = bookmarkService.save(requireUserId(authentication), request);

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(HttpStatus.ACCEPTED.value(), "bookmark saved, analysis in progress", response));
    }

    @Operation(summary = "저장 사이트 리스트", description = "스페이스의 저장된 사이트 목록을 정렬·페이지 단위로 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<BookmarkListResponse>> list(
            @RequestParam Long spaceId,
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기 (1~100)") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "정렬 기준: " + BookmarkSort.ALLOWED_VALUES)
            @RequestParam(defaultValue = "latest") String sort,
            Authentication authentication) {
        BookmarkListResponse response =
                bookmarkService.list(requireUserId(authentication), spaceId, page, size, sort);

        return ResponseEntity.ok(
                ApiResponse.success(HttpStatus.OK.value(), "bookmark list retrieved successfully", response));
    }

    @Operation(summary = "최근 저장 사이트", description = "내가 속한 모든 스페이스에서 최근 저장된 사이트 조회")
    @GetMapping("/recent")
    public ResponseEntity<ApiResponse<List<BookmarkSummaryDto>>> recent(
            @Parameter(description = "개수 (1~50)") @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {
        List<BookmarkSummaryDto> response = bookmarkService.recent(requireUserId(authentication), size);

        return ResponseEntity.ok(
                ApiResponse.success(HttpStatus.OK.value(), "recent bookmarks retrieved successfully", response));
    }

    @Operation(summary = "저장 여부 확인",
            description = "내가 속한 스페이스에 이 URL이 이미 저장돼 있는지 조회 (브라우저 확장 프로그램용). 없으면 빈 목록")
    @GetMapping("/lookup")
    public ResponseEntity<ApiResponse<List<BookmarkLookupDto>>> lookup(
            @RequestParam String url, Authentication authentication) {
        List<BookmarkLookupDto> response = bookmarkService.lookup(requireUserId(authentication), url);

        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "lookup completed", response));
    }

    @Operation(summary = "저장 사이트 여러 개 삭제",
            description = "선택한 사이트를 한 번에 삭제. 하나라도 없거나(404) 권한이 없으면(403) 아무것도 삭제하지 않음")
    @PostMapping("/bulk-delete")
    public ResponseEntity<ApiResponse<BookmarkBulkResponse>> deleteAll(
            @RequestBody @Valid BookmarkBulkDeleteRequest request, Authentication authentication) {
        BookmarkBulkResponse response = bookmarkService.deleteAll(requireUserId(authentication), request.bookmarkIds());

        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "bookmarks deleted successfully", response));
    }

    @Operation(summary = "저장 사이트 이동",
            description = "선택한 사이트를 다른 스페이스로 이동. 원래·대상 스페이스 모두의 멤버여야 하며, "
                    + "하나라도 없거나 권한이 없으면 아무것도 옮기지 않음. 이미 대상 스페이스에 있는 사이트는 건너뜀")
    @PatchMapping("/move")
    public ResponseEntity<ApiResponse<BookmarkBulkResponse>> move(
            @RequestBody @Valid BookmarkMoveRequest request, Authentication authentication) {
        BookmarkBulkResponse response = bookmarkService.move(
                requireUserId(authentication), request.bookmarkIds(), request.targetSpaceId());

        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "bookmarks moved successfully", response));
    }

    @Operation(summary = "저장 사이트 삭제", description = "저장된 사이트 삭제")
    @DeleteMapping("/{bookmarkId}")
    public ResponseEntity<ApiResponse<Boolean>> delete(
            @PathVariable Long bookmarkId, Authentication authentication) {
        bookmarkService.delete(requireUserId(authentication), bookmarkId);

        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "bookmark deleted successfully", true));
    }

    @Operation(summary = "저장 사이트 조회", description = "저장된 사이트의 상세 정보 조회")
    @GetMapping("/{bookmarkId}")
    public ResponseEntity<ApiResponse<BookmarkDetailResponse>> getDetail(
            @PathVariable Long bookmarkId, Authentication authentication) {
        BookmarkDetailResponse response = bookmarkService.getDetail(requireUserId(authentication), bookmarkId);

        return ResponseEntity.ok(
                ApiResponse.success(HttpStatus.OK.value(), "bookmark retrieved successfully", response));
    }

    private Long requireUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new UnauthenticatedException("authentication required");
        }

        return (Long) authentication.getPrincipal();
    }
}
