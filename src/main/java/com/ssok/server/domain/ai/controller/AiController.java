package com.ssok.server.domain.ai.controller;

import com.ssok.server.common.exception.UnauthenticatedException;
import com.ssok.server.common.response.ApiResponse;
import com.ssok.server.domain.ai.dto.SemanticSearchRequest;
import com.ssok.server.domain.ai.dto.SemanticSearchResponse;
import com.ssok.server.domain.ai.dto.SimilarBookmarkResponse;
import com.ssok.server.domain.ai.dto.SummaryRequest;
import com.ssok.server.domain.ai.dto.SummaryResponse;
import com.ssok.server.domain.ai.service.AiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI", description = "AI 관련 API")
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;

    @Operation(summary = "AI 요약", description = "저장된 사이트의 AI 분석(요약/태그)을 다시 요청 (결과는 비동기로 반영)")
    @PostMapping("/summary")
    public ResponseEntity<ApiResponse<SummaryResponse>> summary(
            @RequestBody @Valid SummaryRequest request, Authentication authentication) {
        SummaryResponse response = aiService.summarize(requireUserId(authentication), request.bookmarkId());

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(HttpStatus.ACCEPTED.value(), "analysis requested", response));
    }

    @Operation(summary = "의미 기반 검색",
            description = "사용자가 입력한 자연어와 의미적으로 가장 유사한 저장 사이트 검색. "
                    + "spaceId를 생략하면 내가 속한 모든 스페이스, 지정하면 그 스페이스에서만 검색 (권한 없으면 403). "
                    + "savedFrom·savedTo로 저장 날짜를 제한할 수 있고(양 끝 포함), count가 '검색결과 N건'의 N")
    @PostMapping("/search")
    public ResponseEntity<ApiResponse<SemanticSearchResponse>> search(
            @RequestBody @Valid SemanticSearchRequest request, Authentication authentication) {
        SemanticSearchResponse response = aiService.search(requireUserId(authentication), request);

        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "semantic search completed", response));
    }

    @Operation(summary = "유사 사이트 추천", description = "현재 조회 중인 사이트와 의미적으로 유사한 저장 사이트를 추천")
    @GetMapping("/recommend")
    public ResponseEntity<ApiResponse<List<SimilarBookmarkResponse>>> recommend(
            @RequestParam Long bookmarkId, Authentication authentication) {
        List<SimilarBookmarkResponse> response = aiService.recommend(requireUserId(authentication), bookmarkId);

        return ResponseEntity.ok(
                ApiResponse.success(HttpStatus.OK.value(), "similar sites retrieved successfully", response));
    }

    private Long requireUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new UnauthenticatedException("authentication required");
        }

        return (Long) authentication.getPrincipal();
    }
}
