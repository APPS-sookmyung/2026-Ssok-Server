package com.ssok.server.domain.ai.client;

import com.ssok.server.common.exception.AiServerException;
import com.ssok.server.domain.ai.client.dto.AiAnalyzeRequest;
import com.ssok.server.domain.ai.client.dto.AiSearchRequest;
import com.ssok.server.domain.ai.client.dto.AiSearchResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

/**
 * FastAPI AI 서버 호출 클라이언트.
 * 분석 결과는 응답이 아니라 콜백(POST /api/v1/bookmarks/ai-result)으로 전달된다.
 */
@Component
@RequiredArgsConstructor
public class AiServerClient {

    private final RestClient aiRestClient;

    public void requestAnalysis(AiAnalyzeRequest request) {
        try {
            aiRestClient.post()
                    .uri("/api/v1/ai/analyze")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new AiServerException("ai analysis request failed", e);
        }
    }

    public List<AiSearchResponse.Result> search(String query, int topK, List<Long> spaceIds) {
        try {
            AiSearchResponse response = aiRestClient.post()
                    .uri("/api/v1/ai/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new AiSearchRequest(query, topK, spaceIds))
                    .retrieve()
                    .body(AiSearchResponse.class);

            return response != null && response.results() != null ? response.results() : List.of();
        } catch (RestClientException e) {
            throw new AiServerException("ai search request failed", e);
        }
    }

    /**
     * 북마크 벡터를 삭제한다. 이미 없는 벡터(404)도 삭제된 것으로 본다.
     */
    public void deleteVector(Long bookmarkId) {
        try {
            aiRestClient.delete()
                    .uri("/api/v1/ai/bookmarks/{bookmarkId}", bookmarkId)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return;
            }
            throw new AiServerException("ai vector deletion failed", e);
        } catch (RestClientException e) {
            throw new AiServerException("ai vector deletion failed", e);
        }
    }
}
