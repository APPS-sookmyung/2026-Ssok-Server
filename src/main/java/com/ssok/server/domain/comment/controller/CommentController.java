package com.ssok.server.domain.comment.controller;

import com.ssok.server.common.exception.UnauthenticatedException;
import com.ssok.server.common.response.ApiResponse;
import com.ssok.server.domain.comment.dto.CommentListResponse;
import com.ssok.server.domain.comment.dto.CommentRequest;
import com.ssok.server.domain.comment.dto.CommentResponse;
import com.ssok.server.domain.comment.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

@Tag(name = "댓글", description = "북마크 댓글 API (북마크가 속한 스페이스의 멤버만 사용 가능)")
@RestController
@RequestMapping("/api/bookmarks/{bookmarkId}/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @Operation(summary = "댓글 목록", description = "작성 순으로 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<CommentListResponse>> list(
            @PathVariable Long bookmarkId,
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기 (1~100)") @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        CommentListResponse response = commentService.list(requireUserId(authentication), bookmarkId, page, size);

        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "comments retrieved successfully", response));
    }

    @Operation(summary = "댓글 작성")
    @PostMapping
    public ResponseEntity<ApiResponse<CommentResponse>> create(
            @PathVariable Long bookmarkId, @RequestBody @Valid CommentRequest request, Authentication authentication) {
        CommentResponse response = commentService.create(requireUserId(authentication), bookmarkId, request.content());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED.value(), "comment created successfully", response));
    }

    @Operation(summary = "댓글 수정", description = "작성자만 수정 가능 (아니면 403)")
    @PatchMapping("/{commentId}")
    public ResponseEntity<ApiResponse<CommentResponse>> update(
            @PathVariable Long bookmarkId, @PathVariable Long commentId,
            @RequestBody @Valid CommentRequest request, Authentication authentication) {
        CommentResponse response =
                commentService.update(requireUserId(authentication), bookmarkId, commentId, request.content());

        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "comment updated successfully", response));
    }

    @Operation(summary = "댓글 삭제", description = "작성자만 삭제 가능 (아니면 403)")
    @DeleteMapping("/{commentId}")
    public ResponseEntity<ApiResponse<Boolean>> delete(
            @PathVariable Long bookmarkId, @PathVariable Long commentId, Authentication authentication) {
        commentService.delete(requireUserId(authentication), bookmarkId, commentId);

        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "comment deleted successfully", true));
    }

    private Long requireUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new UnauthenticatedException("authentication required");
        }

        return (Long) authentication.getPrincipal();
    }
}
