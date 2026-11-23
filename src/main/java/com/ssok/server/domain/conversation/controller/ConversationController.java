package com.ssok.server.domain.conversation.controller;

import com.ssok.server.common.exception.UnauthenticatedException;
import com.ssok.server.common.response.ApiResponse;
import com.ssok.server.domain.conversation.dto.ChatResponse;
import com.ssok.server.domain.conversation.dto.ConversationDetailResponse;
import com.ssok.server.domain.conversation.dto.ConversationListResponse;
import com.ssok.server.domain.conversation.dto.ConversationMessageRequest;
import com.ssok.server.domain.conversation.service.ConversationService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "대화 검색", description = "대화로 저장한 사이트 찾기와 지난 대화 보관함")
@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    @Operation(summary = "새 대화 시작",
            description = "첫 질문을 보내 새 대화를 만들고 AI 응답(되묻기 또는 링크 카드)을 받는다. "
                    + "AI 서버 오류 시 502이며 질문은 저장되지 않는다.")
    @PostMapping
    public ResponseEntity<ApiResponse<ChatResponse>> start(
            @RequestBody @Valid ConversationMessageRequest request, Authentication authentication) {
        ChatResponse response =
                conversationService.ask(requireUserId(authentication), null, request.message(), request.spaceId());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED.value(), "conversation started", response));
    }

    @Operation(summary = "대화 이어가기", description = "지난 대화에 질문을 이어서 보낸다. 최근 메시지 최대 10개가 AI에 함께 전달된다.")
    @PostMapping("/{conversationId}/messages")
    public ResponseEntity<ApiResponse<ChatResponse>> send(
            @PathVariable Long conversationId,
            @RequestBody @Valid ConversationMessageRequest request,
            Authentication authentication) {
        ChatResponse response = conversationService.ask(
                requireUserId(authentication), conversationId, request.message(), request.spaceId());

        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "message sent", response));
    }

    @Operation(summary = "대화 보관함", description = "내 지난 대화 목록 (최근 대화순). keyword로 제목·메시지 내용 검색")
    @GetMapping
    public ResponseEntity<ApiResponse<ConversationListResponse>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기 (1~50)") @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        ConversationListResponse response =
                conversationService.list(requireUserId(authentication), keyword, page, size);

        return ResponseEntity.ok(
                ApiResponse.success(HttpStatus.OK.value(), "conversations retrieved successfully", response));
    }

    @Operation(summary = "지난 대화 열기",
            description = "대화의 전체 메시지. 링크 카드는 지금 볼 수 있는 북마크만 포함 (팀 탈퇴·북마크 삭제 반영)")
    @GetMapping("/{conversationId}")
    public ResponseEntity<ApiResponse<ConversationDetailResponse>> get(
            @PathVariable Long conversationId, Authentication authentication) {
        ConversationDetailResponse response = conversationService.get(requireUserId(authentication), conversationId);

        return ResponseEntity.ok(
                ApiResponse.success(HttpStatus.OK.value(), "conversation retrieved successfully", response));
    }

    @Operation(summary = "대화 삭제")
    @DeleteMapping("/{conversationId}")
    public ResponseEntity<ApiResponse<Boolean>> delete(
            @PathVariable Long conversationId, Authentication authentication) {
        conversationService.delete(requireUserId(authentication), conversationId);

        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "conversation deleted successfully", true));
    }

    private Long requireUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new UnauthenticatedException("authentication required");
        }

        return (Long) authentication.getPrincipal();
    }
}
