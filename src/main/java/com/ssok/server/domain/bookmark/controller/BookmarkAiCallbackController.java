package com.ssok.server.domain.bookmark.controller;

import com.ssok.server.common.config.AiServerProperties;
import com.ssok.server.common.exception.UnauthenticatedException;
import com.ssok.server.common.response.ApiResponse;
import com.ssok.server.domain.bookmark.dto.AiAnalysisResultRequest;
import com.ssok.server.domain.bookmark.service.AnalysisResultOutcome;
import com.ssok.server.domain.bookmark.service.BookmarkAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI 콜백", description = "AI 서버(FastAPI) 전용 콜백 API")
@RestController
@RequestMapping("/api/v1/bookmarks")
@RequiredArgsConstructor
public class BookmarkAiCallbackController {

    public static final String CALLBACK_SECRET_HEADER = "X-AI-Callback-Secret";

    private final BookmarkAnalysisService bookmarkAnalysisService;
    private final AiServerProperties aiServerProperties;

    @Operation(summary = "AI 분석 결과 수신",
            description = "AI 서버가 분석을 마친 뒤 제목/요약/카테고리/태그를 전달. "
                    + "삭제된 북마크, 이전 요청(analysis_request_id 불일치), 이미 처리한 결과는 반영하지 않고 200을 반환한다.")
    @PostMapping("/ai-result")
    public ResponseEntity<ApiResponse<Void>> receiveResult(
            @RequestHeader(value = CALLBACK_SECRET_HEADER, required = false) String secret,
            @RequestBody @Valid AiAnalysisResultRequest request) {
        verifySecret(secret);
        AnalysisResultOutcome outcome = bookmarkAnalysisService.applyResult(request);

        String message = outcome == AnalysisResultOutcome.APPLIED
                ? "analysis result applied"
                : "analysis result ignored: " + outcome.name().toLowerCase();
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), message, null));
    }

    // 시크릿 미설정은 로컬 개발에서만 허용된다. prod에서는 AiCallbackSecretValidator가 기동을 막는다.
    private void verifySecret(String secret) {
        if (!aiServerProperties.hasCallbackSecret()) {
            return;
        }
        String expected = aiServerProperties.callbackSecret();

        if (secret == null || !MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), secret.getBytes(StandardCharsets.UTF_8))) {
            throw new UnauthenticatedException("invalid callback secret");
        }
    }
}
