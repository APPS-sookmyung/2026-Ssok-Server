package com.ssok.server.domain.ai.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ssok.server.common.exception.AiServerException;
import com.ssok.server.domain.ai.client.dto.AiAnalyzeRequest;
import com.ssok.server.domain.ai.client.dto.AiSearchResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AiServerClientTest {

    private MockRestServiceServer server;
    private AiServerClient aiServerClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai-server");
        server = MockRestServiceServer.bindTo(builder).build();
        aiServerClient = new AiServerClient(builder.build());
    }

    @Test
    void 분석_요청은_필수_필드를_포함한_snake_case_JSON으로_전송된다() {
        server.expect(requestTo("http://ai-server/api/v1/ai/analyze"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {
                          "bookmark_id": 123,
                          "url": "https://example.com/react-19-vs-18",
                          "space_id": 7,
                          "saved_at": "2026-11-23T10:15:30.120+09:00",
                          "callback_url": "https://api.ssok.test/api/v1/bookmarks/ai-result",
                          "analysis_request_id": "req-1"
                        }
                        """, true))
                .andRespond(withSuccess());

        aiServerClient.requestAnalysis(new AiAnalyzeRequest(123L, "https://example.com/react-19-vs-18", 7L,
                "2026-11-23T10:15:30.120+09:00", "https://api.ssok.test/api/v1/bookmarks/ai-result", "req-1"));

        server.verify();
    }

    @Test
    void 검색_요청에_space_ids를_포함하고_응답을_유사도_순서대로_파싱한다() {
        server.expect(requestTo("http://ai-server/api/v1/ai/search"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"query": "그 리액트 버전 비교하는 글 뭐였지", "top_k": 5, "space_ids": [1, 3]}
                        """, true))
                .andRespond(withSuccess("""
                        {
                          "query": "그 리액트 버전 비교하는 글 뭐였지",
                          "results": [
                            {"bookmark_id": 123, "similarity": 0.895},
                            {"bookmark_id": 74, "similarity": 0.712}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<AiSearchResponse.Result> results =
                aiServerClient.search("그 리액트 버전 비교하는 글 뭐였지", 5, List.of(1L, 3L));

        assertThat(results).extracting(AiSearchResponse.Result::bookmarkId).containsExactly(123L, 74L);
        assertThat(results.get(0).similarity()).isEqualTo(0.895);
    }

    @Test
    void AI_서버가_오류를_반환하면_AiServerException을_던진다() {
        server.expect(requestTo("http://ai-server/api/v1/ai/search")).andRespond(withServerError());

        assertThatThrownBy(() -> aiServerClient.search("query", 5, List.of(1L)))
                .isInstanceOf(AiServerException.class);
    }

    @Test
    void 벡터_삭제는_DELETE로_요청한다() {
        server.expect(requestTo("http://ai-server/api/v1/ai/bookmarks/123"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withNoContent());

        aiServerClient.deleteVector(123L);

        server.verify();
    }

    @Test
    void 이미_없는_벡터를_삭제하면_성공으로_본다() {
        server.expect(requestTo("http://ai-server/api/v1/ai/bookmarks/123")).andRespond(withResourceNotFound());

        assertThatCode(() -> aiServerClient.deleteVector(123L)).doesNotThrowAnyException();
    }

    @Test
    void 벡터_삭제가_서버_오류로_실패하면_AiServerException을_던진다() {
        server.expect(requestTo("http://ai-server/api/v1/ai/bookmarks/123")).andRespond(withServerError());

        assertThatThrownBy(() -> aiServerClient.deleteVector(123L)).isInstanceOf(AiServerException.class);
    }
}
