package com.ssok.server.domain.ai.client;

import com.ssok.server.common.exception.AiServerException;
import com.ssok.server.domain.ai.client.dto.AiAnalyzeRequest;
import com.ssok.server.domain.ai.client.dto.AiChatRequest;
import com.ssok.server.domain.ai.client.dto.AiChatResponse;
import com.ssok.server.domain.ai.client.dto.AiSearchRequest;
import com.ssok.server.domain.ai.client.dto.AiSearchResponse;
import com.ssok.server.domain.ai.client.dto.AiVectorSpaceUpdateRequest;
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

    public List<AiSearchResponse.Result> search(AiSearchRequest request) {
        try {
            AiSearchResponse response = aiRestClient.post()
                    .uri("/api/v1/ai/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(AiSearchResponse.class);

            return response != null && response.results() != null ? response.results() : List.of();
        } catch (RestClientException e) {
            throw new AiServerException("ai search request failed", e);
        }
    }

    /**
     * 대화 검색. AI가 질문을 해석해 되묻거나 후보 북마크를 찾는다.
     */
    public AiChatResponse chat(AiChatRequest request) {
        try {
            AiChatResponse response = aiRestClient.post()
                    .uri("/api/v1/ai/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(AiChatResponse.class);
            if (response == null || response.reply() == null) {
                throw new AiServerException("ai chat returned empty response", null);
            }
            return response;
        } catch (RestClientException e) {
            throw new AiServerException("ai chat request failed", e);
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

    /**
     * 이동한 북마크 벡터의 space_id를 갱신한다.
     * 아직 벡터가 없으면(404, 분석 진행 중) 분석 결과 반영 시 다시 맞추므로 성공으로 본다.
     */
    public void updateVectorSpace(Long bookmarkId, Long spaceId) {
        try {
            aiRestClient.patch()
                    .uri("/api/v1/ai/bookmarks/{bookmarkId}", bookmarkId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new AiVectorSpaceUpdateRequest(spaceId))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return;
            }
            throw new AiServerException("ai vector space update failed", e);
        } catch (RestClientException e) {
            throw new AiServerException("ai vector space update failed", e);
        }
    }
}
