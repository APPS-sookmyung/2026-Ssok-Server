package com.ssok.server.domain.space.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ssok.server.common.exception.GlobalExceptionHandler;
import com.ssok.server.domain.bookmark.repository.BookmarkRepository;
import com.ssok.server.domain.space.entity.*;
import com.ssok.server.domain.space.repository.*;
import com.ssok.server.domain.space.service.*;
import com.ssok.server.domain.user.entity.User;
import com.ssok.server.domain.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 실제 컨트롤러·서비스·예외 처리기를 연결해 응답 계약을 검증합니다. */
class SpaceControllerTest {
    private final SpaceRepository spaces = mock(SpaceRepository.class);
    private final SpaceMemberRepository members = mock(SpaceMemberRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final BookmarkRepository bookmarks = mock(BookmarkRepository.class);
    private final UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(1L, null, List.of());
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new SpaceController(
                        new SpaceService(spaces, members, bookmarks, users),
                        new SpaceMemberService(spaces, members, users)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void duplicateInvitationReturns409() throws Exception {
        Space space = Space.builder().type(SpaceType.TEAM).build();
        User user = User.builder().email("member@test.com").build();
        ReflectionTestUtils.setField(user, "id", 2L);
        when(spaces.findById(10L)).thenReturn(Optional.of(space));
        when(members.findBySpaceIdAndUserId(10L, 1L)).thenReturn(Optional.of(
                SpaceMember.builder().role(SpaceRole.OWNER).build()));
        when(users.findByEmail("member@test.com")).thenReturn(Optional.of(user));
        when(members.existsBySpaceIdAndUserId(10L, 2L)).thenReturn(true);

        mvc.perform(post("/api/spaces/10/invite").principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"member@test.com\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.success").value(false));
        verify(members, never()).save(any());
    }

    @Test
    void nonMemberCannotListMembers() throws Exception {
        when(spaces.existsById(10L)).thenReturn(true);
        mvc.perform(get("/api/spaces/10/members").principal(authentication))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        verify(members, never()).findAllBySpaceId(any());
    }

    @Test
    void removingMemberFromMissingSpaceReturns404BeforeCheckingMembership() throws Exception {
        mvc.perform(delete("/api/spaces/10/members/2").principal(authentication))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        verifyNoInteractions(members);
    }

    @Test
    void creatingSpaceReturnsSpaceName() throws Exception {
        User owner = User.builder().name("owner").build();
        when(users.findById(1L)).thenReturn(Optional.of(owner));
        when(spaces.save(any(Space.class))).thenAnswer(invocation -> invocation.getArgument(0));
        mvc.perform(post("/api/spaces").principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"spaceName\":\"team\",\"type\":\"TEAM\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.spaceName").value("team"))
                .andExpect(jsonPath("$.data.name").doesNotExist());
    }

    @Test
    void listAndDetailReturnSpaceName() throws Exception {
        User owner = User.builder().name("owner").build();
        ReflectionTestUtils.setField(owner, "id", 1L);
        Space space = Space.builder().name("team").type(SpaceType.TEAM).owner(owner).build();
        ReflectionTestUtils.setField(space, "id", 10L);
        ReflectionTestUtils.setField(space, "createdAt", LocalDateTime.of(2026, 1, 1, 0, 0));
        SpaceMember member = SpaceMember.builder().space(space).user(owner).role(SpaceRole.OWNER).build();
        when(members.findAllByUserId(1L)).thenReturn(List.of(member));
        when(spaces.findById(10L)).thenReturn(Optional.of(space));
        when(members.findBySpaceIdAndUserId(10L, 1L)).thenReturn(Optional.of(member));

        mvc.perform(get("/api/spaces").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].spaceName").value("team"))
                .andExpect(jsonPath("$.data[0].name").doesNotExist());
        mvc.perform(get("/api/spaces/10").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.spaceName").value("team"))
                .andExpect(jsonPath("$.data.name").doesNotExist());
    }
}
