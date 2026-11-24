package com.ssok.server.domain.space.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import com.ssok.server.domain.space.entity.*;
import com.ssok.server.domain.space.repository.*;
import com.ssok.server.domain.user.entity.User;
import com.ssok.server.domain.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SpaceInvitationTest {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private SpaceRepository spaces;
    @Autowired private SpaceMemberRepository members;
    @Autowired private SpaceInvitationRepository invitations;
    @Autowired private jakarta.persistence.EntityManager entityManager;
    private User owner, recipient, other;
    private Space team;

    @BeforeEach
    void setup() {
        owner = user("owner"); recipient = user("recipient"); other = user("other");
        team = spaces.save(Space.builder().name("invitation team").type(SpaceType.TEAM).owner(owner).build());
        members.save(SpaceMember.builder().space(team).user(owner).role(SpaceRole.OWNER).build());
    }

    @Test
    void invitationRequiresAcceptanceAndStoresOnlyHash() throws Exception {
        var issued = create("recipient@invitations.test", "VIEWER");
        assertThat(members.findBySpaceIdAndUserId(team.getId(), recipient.getId())).isEmpty();
        assertThat(invitations.findById(issued.id()).orElseThrow().getTokenHash()).hasSize(64).isNotEqualTo(issued.token());
        accept(recipient, issued.token(), 200);
        assertThat(members.findBySpaceIdAndUserId(team.getId(), recipient.getId()).orElseThrow().getRole()).isEqualTo(SpaceRole.VIEWER);
        accept(recipient, issued.token(), 409);
        mvc.perform(delete("/api/spaces/{s}/invitations/{i}", team.getId(), issued.id()).with(as(owner))).andExpect(status().isConflict());
    }

    @Test
    void differentEmailCannotAcceptAndAnonymousIsRejected() throws Exception {
        var issued = create("recipient@invitations.test", "EDITOR");
        accept(other, issued.token(), 403);
        mvc.perform(post("/api/spaces/invitations/accept").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + issued.token() + "\"}")).andExpect(status().isUnauthorized());
        assertThat(members.countBySpaceId(team.getId())).isEqualTo(1);
        accept(recipient, "invalid-token", 404);
        mvc.perform(post("/api/spaces/invitations/accept").with(as(recipient)).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isBadRequest());
    }

    @Test
    void duplicateInvitationAndExistingMemberAreRejected() throws Exception {
        create("recipient@invitations.test", "EDITOR");
        for (String email : List.of("recipient@invitations.test", "owner@invitations.test")) {
            mvc.perform(post("/api/spaces/{s}/invitations", team.getId()).with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"" + email + "\"}")).andExpect(status().isConflict());
        }
    }

    @Test
    void cancellationIsIdempotentAndInvalidatesToken() throws Exception {
        var issued = create("recipient@invitations.test", "EDITOR");
        for (int i = 0; i < 2; i++) {
            mvc.perform(delete("/api/spaces/{s}/invitations/{i}", team.getId(), issued.id()).with(as(owner))).andExpect(status().isOk());
        }
        accept(recipient, issued.token(), 409);
        mvc.perform(post("/api/spaces/{s}/invitations/{i}/resend", team.getId(), issued.id()).with(as(owner))).andExpect(status().isConflict());
    }

    @Test
    void expiryAndResendReplaceOldTokenAndExposeNoTokenInList() throws Exception {
        var issued = create("recipient@invitations.test", "EDITOR");
        ReflectionTestUtils.setField(invitations.findById(issued.id()).orElseThrow(), "expiresAt", Instant.now().minusSeconds(1));
        invitations.flush();
        accept(recipient, issued.token(), 409);
        mvc.perform(get("/api/spaces/{s}/invitations", team.getId()).with(as(owner)))
                .andExpect(jsonPath("$.data[0].status").value("EXPIRED"))
                .andExpect(jsonPath("$.data[0].invitationUrl").isEmpty());
        var response = mvc.perform(post("/api/spaces/{s}/invitations/{i}/resend", team.getId(), issued.id()).with(as(owner)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String url = JsonPath.read(response, "$.data.invitationUrl");
        String next = url.substring(url.indexOf("#token=") + 7);
        assertThat(next).isNotEqualTo(issued.token());
        accept(recipient, issued.token(), 404);
        accept(recipient, next, 200);
    }

    @Test
    void nonOwnerPersonalSpaceAndOwnerRoleInvitationsAreBlocked() throws Exception {
        members.save(SpaceMember.builder().space(team).user(other).role(SpaceRole.EDITOR).build());
        mvc.perform(post("/api/spaces/{s}/invitations", team.getId()).with(as(other)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"recipient@invitations.test\"}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/spaces/{s}/invitations", team.getId()).with(as(other))).andExpect(status().isForbidden());
        Space personal = spaces.save(Space.builder().name("personal").type(SpaceType.PERSONAL).owner(owner).build());
        members.save(SpaceMember.builder().space(personal).user(owner).role(SpaceRole.OWNER).build());
        mvc.perform(post("/api/spaces/{s}/invitations", personal.getId()).with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"recipient@invitations.test\"}")).andExpect(status().isForbidden());
        for (String body : List.of("{\"email\":\"recipient@invitations.test\",\"role\":\"OWNER\"}", "{\"email\":\"invalid\"}")) {
            mvc.perform(post("/api/spaces/{s}/invitations", team.getId()).with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                    .content(body)).andExpect(status().isBadRequest());
        }
    }

    @Test
    void acceptsAfterRegistrationAndSpaceDeletionRemovesInvitations() throws Exception {
        var issued = create("new@invitations.test", "EDITOR");
        accept(user("new"), issued.token(), 200);
        entityManager.flush();
        entityManager.clear();
        mvc.perform(delete("/api/spaces/{s}", team.getId()).with(as(owner))).andExpect(status().isOk());
        spaces.flush();
        assertThat(invitations.findById(issued.id())).isEmpty();
    }

    private Issued create(String email, String role) throws Exception {
        String response = mvc.perform(post("/api/spaces/{s}/invitations", team.getId()).with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"role\":\"" + role + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(response, "$.data.invitationId");
        String url = JsonPath.read(response, "$.data.invitationUrl");
        return new Issued(id.longValue(), url.substring(url.indexOf("#token=") + 7));
    }

    private void accept(User user, String token, int expected) throws Exception {
        mvc.perform(post("/api/spaces/invitations/accept").with(as(user)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}")).andExpect(status().is(expected));
    }
    private record Issued(Long id, String token) {}
    private User user(String name) { return users.save(User.builder().name(name).email(name + "@invitations.test").password("test").build()); }
    private RequestPostProcessor as(User user) { return authentication(new UsernamePasswordAuthenticationToken(user.getId(), null, List.of())); }
}
