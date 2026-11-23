package com.ssok.server.domain.conversation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssok.server.common.exception.AiServerException;
import com.ssok.server.domain.ai.client.AiServerClient;
import com.ssok.server.domain.ai.client.dto.AiChatRequest;
import com.ssok.server.domain.ai.client.dto.AiChatResponse;
import com.ssok.server.domain.ai.client.dto.AiSearchResponse.Result;
import com.ssok.server.domain.bookmark.entity.Bookmark;
import com.ssok.server.domain.bookmark.repository.BookmarkRepository;
import com.ssok.server.domain.conversation.repository.ConversationRepository;
import com.ssok.server.domain.space.entity.Space;
import com.ssok.server.domain.space.entity.SpaceMember;
import com.ssok.server.domain.space.entity.SpaceRole;
import com.ssok.server.domain.space.entity.SpaceType;
import com.ssok.server.domain.space.repository.SpaceMemberRepository;
import com.ssok.server.domain.space.repository.SpaceRepository;
import com.ssok.server.domain.user.entity.User;
import com.ssok.server.domain.user.repository.UserRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ConversationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SpaceRepository spaceRepository;

    @Autowired
    private SpaceMemberRepository spaceMemberRepository;

    @Autowired
    private BookmarkRepository bookmarkRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @MockitoBean
    private AiServerClient aiServerClient;

    private User me;
    private User other;
    private Space personal;
    private Space team;
    private Space othersSpace;
    private Bookmark react;
    private Bookmark teamDoc;
    private Bookmark secret;

    @BeforeEach
    void setUp() {
        me = user("chat-me@ssok.com");
        other = user("chat-other@ssok.com");
        personal = space("개인", me);
        team = space("팀", me);
        othersSpace = space("남의 스페이스", other);

        react = bookmark(personal, "https://react.dev", "React 19 vs 18");
        teamDoc = bookmark(team, "https://team.doc", "팀 문서");
        secret = bookmark(othersSpace, "https://secret.com", "비밀");
    }

    @Test
    void 새_대화를_시작하면_볼_수_있는_북마크만_카드로_보여준다() throws Exception {
        given(aiServerClient.chat(any())).willReturn(new AiChatResponse("이 글을 찾으시나요?", false, List.of(
                new Result(secret.getId(), 0.99),
                new Result(react.getId(), 0.9),
                new Result(999_999L, 0.8),
                new Result(teamDoc.getId(), 0.7))));

        ask(null, "리액트 버전 비교 글", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.conversationId").exists())
                .andExpect(jsonPath("$.data.title").value("리액트 버전 비교 글"))
                .andExpect(jsonPath("$.data.question.role").value("USER"))
                .andExpect(jsonPath("$.data.answer.role").value("ASSISTANT"))
                .andExpect(jsonPath("$.data.answer.content").value("이 글을 찾으시나요?"))
                .andExpect(jsonPath("$.data.answer.cards.length()").value(2))
                .andExpect(jsonPath("$.data.answer.cards[0].bookmarkId").value(react.getId()))
                .andExpect(jsonPath("$.data.answer.cards[0].spaceName").value("개인"))
                .andExpect(jsonPath("$.data.answer.cards[1].bookmarkId").value(teamDoc.getId()));

        AiChatRequest request = captureChatRequest();
        assertThat(request.spaceIds()).containsExactlyInAnyOrder(personal.getId(), team.getId());
        assertThat(request.messages()).containsExactly(new AiChatRequest.Message("user", "리액트 버전 비교 글"));
    }

    @Test
    void 스페이스를_지정하면_그_스페이스만_AI에_보낸다() throws Exception {
        given(aiServerClient.chat(any())).willReturn(new AiChatResponse("없어요", false, List.of()));

        ask(null, "팀 문서", team.getId()).andExpect(status().isCreated());

        assertThat(captureChatRequest().spaceIds()).containsExactly(team.getId());
    }

    @Test
    void 권한_없는_스페이스를_지정하면_403이고_AI를_호출하지_않는다() throws Exception {
        ask(null, "몰래", othersSpace.getId()).andExpect(status().isForbidden());

        verify(aiServerClient, never()).chat(any());
    }

    @Test
    void 되묻는_응답은_clarification으로_표시된다() throws Exception {
        given(aiServerClient.chat(any())).willReturn(new AiChatResponse("어떤 주제의 글인가요?", true, List.of()));

        ask(null, "그거", null)
                .andExpect(jsonPath("$.data.answer.clarification").value(true))
                .andExpect(jsonPath("$.data.answer.cards.length()").value(0));
    }

    @Test
    void 대화를_이어가면_이전_메시지가_함께_전달된다() throws Exception {
        given(aiServerClient.chat(any()))
                .willReturn(new AiChatResponse("어떤 주제인가요?", true, List.of()))
                .willReturn(new AiChatResponse("이 글이에요", false, List.of(new Result(react.getId(), 0.9))));
        Long conversationId = conversationId(ask(null, "그 글", null));

        ask(conversationId, "리액트요", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conversationId").value(conversationId))
                .andExpect(jsonPath("$.data.answer.cards[0].bookmarkId").value(react.getId()));

        ArgumentCaptor<AiChatRequest> captor = ArgumentCaptor.forClass(AiChatRequest.class);
        verify(aiServerClient, org.mockito.Mockito.times(2)).chat(captor.capture());
        assertThat(captor.getAllValues().get(1).messages()).containsExactly(
                new AiChatRequest.Message("user", "그 글"),
                new AiChatRequest.Message("assistant", "어떤 주제인가요?"),
                new AiChatRequest.Message("user", "리액트요"));
    }

    @Test
    void AI에는_최근_메시지_최대_10개만_보낸다() throws Exception {
        given(aiServerClient.chat(any())).willReturn(new AiChatResponse("응답", false, List.of()));
        Long conversationId = conversationId(ask(null, "질문 0", null));
        for (int i = 1; i <= 6; i++) {
            ask(conversationId, "질문 " + i, null);
        }

        ArgumentCaptor<AiChatRequest> captor = ArgumentCaptor.forClass(AiChatRequest.class);
        verify(aiServerClient, org.mockito.Mockito.times(7)).chat(captor.capture());
        List<AiChatRequest.Message> last = captor.getValue().messages();
        assertThat(last).hasSize(10);
        assertThat(last.get(9)).isEqualTo(new AiChatRequest.Message("user", "질문 6"));
        // 이전 12개 중 최근 9개(응답 1부터) + 이번 질문
        assertThat(last.get(0)).isEqualTo(new AiChatRequest.Message("assistant", "응답"));
        assertThat(last.get(1)).isEqualTo(new AiChatRequest.Message("user", "질문 2"));
    }

    @Test
    void AI_서버가_실패하면_502이고_대화가_저장되지_않는다() throws Exception {
        given(aiServerClient.chat(any())).willThrow(new AiServerException("down", null));
        long before = conversationRepository.count();

        ask(null, "질문", null).andExpect(status().isBadGateway());

        assertThat(conversationRepository.count()).isEqualTo(before);
    }

    @Test
    void 속한_스페이스가_없으면_AI를_호출하지_않고_안내한다() throws Exception {
        User lonely = user("chat-lonely@ssok.com");

        mockMvc.perform(post("/api/conversations").with(login(lonely))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("아무거나", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.answer.cards.length()").value(0))
                .andExpect(jsonPath("$.data.answer.content").exists());

        verify(aiServerClient, never()).chat(any());
    }

    @Test
    void 지난_대화를_열면_지금_권한이_없는_카드는_빠진다() throws Exception {
        given(aiServerClient.chat(any())).willReturn(new AiChatResponse("찾았어요", false, List.of(
                new Result(teamDoc.getId(), 0.9), new Result(react.getId(), 0.8))));
        Long conversationId = conversationId(ask(null, "문서", null));

        // 팀에서 나간다
        spaceMemberRepository.delete(spaceMemberRepository.findBySpaceIdAndUserId(team.getId(), me.getId()).orElseThrow());

        mockMvc.perform(get("/api/conversations/{id}", conversationId).with(login(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages.length()").value(2))
                .andExpect(jsonPath("$.data.messages[1].cards.length()").value(1))
                .andExpect(jsonPath("$.data.messages[1].cards[0].bookmarkId").value(react.getId()));
    }

    @Test
    void 다른_사람의_대화는_보거나_이어가거나_삭제할_수_없다() throws Exception {
        given(aiServerClient.chat(any())).willReturn(new AiChatResponse("응답", false, List.of()));
        Long conversationId = conversationId(ask(null, "내 대화", null));

        mockMvc.perform(get("/api/conversations/{id}", conversationId).with(login(other)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/conversations/{id}/messages", conversationId).with(login(other))
                        .contentType(MediaType.APPLICATION_JSON).content(body("끼어들기", null)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/conversations/{id}", conversationId).with(login(other)))
                .andExpect(status().isNotFound());
    }

    @Test
    void 보관함은_최근_대화순이고_키워드로_메시지_내용까지_검색한다() throws Exception {
        given(aiServerClient.chat(any()))
                .willReturn(new AiChatResponse("파이썬 자료입니다", false, List.of()))
                .willReturn(new AiChatResponse("리액트 자료입니다", false, List.of()))
                .willReturn(new AiChatResponse("100% 확실해요", false, List.of()));
        ask(null, "첫 번째 질문", null);
        ask(null, "두 번째 질문", null);
        ask(null, "세 번째 질문", null);

        mockMvc.perform(get("/api/conversations").with(login(me)))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.content[0].title").value("세 번째 질문"))
                .andExpect(jsonPath("$.data.content[0].lastMessagePreview").value("100% 확실해요"));

        mockMvc.perform(get("/api/conversations").param("keyword", "리액트").with(login(me)))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].title").value("두 번째 질문"));

        // %는 와일드카드가 아니라 글자로 검색된다
        mockMvc.perform(get("/api/conversations").param("keyword", "0%").with(login(me)))
                .andExpect(jsonPath("$.data.totalElements").value(1));
        mockMvc.perform(get("/api/conversations").param("keyword", "%").with(login(me)))
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(get("/api/conversations").with(login(other)))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void 대화를_삭제하면_보관함에서_사라진다() throws Exception {
        given(aiServerClient.chat(any())).willReturn(new AiChatResponse("응답", false, List.of()));
        Long conversationId = conversationId(ask(null, "지울 대화", null));

        mockMvc.perform(delete("/api/conversations/{id}", conversationId).with(login(me)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/conversations/{id}", conversationId).with(login(me)))
                .andExpect(status().isNotFound());
    }

    @Test
    void 빈_질문은_400을_반환한다() throws Exception {
        ask(null, "  ", null).andExpect(status().isBadRequest());
    }

    private ResultActions ask(Long conversationId, String message, Long spaceId) throws Exception {
        String url = conversationId == null ? "/api/conversations" : "/api/conversations/" + conversationId + "/messages";
        return mockMvc.perform(post(url).with(login(me))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(message, spaceId)));
    }

    private String body(String message, Long spaceId) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("message", message);
        body.put("spaceId", spaceId);
        return objectMapper.writeValueAsString(body);
    }

    private Long conversationId(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString())
                .path("data").path("conversationId").asLong();
    }

    private AiChatRequest captureChatRequest() {
        ArgumentCaptor<AiChatRequest> captor = ArgumentCaptor.forClass(AiChatRequest.class);
        verify(aiServerClient).chat(captor.capture());
        return captor.getValue();
    }

    private User user(String email) {
        return userRepository.save(User.builder().email(email).password("encoded").name(email).build());
    }

    private Space space(String name, User member) {
        Space space = spaceRepository.save(Space.builder().name(name).type(SpaceType.TEAM).owner(member).build());
        spaceMemberRepository.save(SpaceMember.builder().space(space).user(member).role(SpaceRole.OWNER).build());
        return space;
    }

    private Bookmark bookmark(Space space, String url, String title) {
        return bookmarkRepository.save(Bookmark.builder().url(url).title(title).space(space).savedBy(me).build());
    }

    private RequestPostProcessor login(User user) {
        return authentication(new UsernamePasswordAuthenticationToken(user.getId(), null, List.of()));
    }
}
