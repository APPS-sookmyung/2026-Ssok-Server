package com.ssok.server.domain.ai.controller;

import com.ssok.server.common.response.ApiResponse;
import com.ssok.server.domain.ai.dto.SemanticSearchRequest;
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

    @Operation(summary = "AI 요약", description = "저장된 사이트를 AI가 분석하여 요약 내용 생성")
    @PostMapping("/summary")
    public ResponseEntity<ApiResponse<SummaryResponse>> summary(@RequestBody @Valid SummaryRequest request) {
        SummaryResponse response = aiService.summarize(request.bookmarkId());

        return ResponseEntity.ok(
                ApiResponse.success(HttpStatus.OK.value(), "summary generated successfully", response));
    }

    @Operation(summary = "의미 기반 검색", description = "사용자가 입력한 자연어와 의미적으로 가장 유사한 저장 사이트 검색")
    @PostMapping("/search")
    public ResponseEntity<ApiResponse<List<SimilarBookmarkResponse>>> search(
            @RequestBody @Valid SemanticSearchRequest request) {
        List<SimilarBookmarkResponse> response = aiService.search(request.query());

        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "semantic search completed", response));
    }

    @Operation(summary = "유사 사이트 추천", description = "현재 조회 중인 사이트와 의미적으로 유사한 저장 사이트를 추천")
    @GetMapping("/recommend")
    public ResponseEntity<ApiResponse<List<SimilarBookmarkResponse>>> recommend(@RequestParam Long bookmarkId) {
        List<SimilarBookmarkResponse> response = aiService.recommend(bookmarkId);

        return ResponseEntity.ok(
                ApiResponse.success(HttpStatus.OK.value(), "similar sites retrieved successfully", response));
    }
}
