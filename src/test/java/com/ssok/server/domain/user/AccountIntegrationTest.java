package com.ssok.server.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import com.ssok.server.common.exception.*;
import com.ssok.server.common.security.JwtTokenProvider;
import com.ssok.server.domain.ai.client.AiServerClient;
import com.ssok.server.domain.auth.repository.AuthSessionRepository;
import com.ssok.server.domain.auth.service.*;
import com.ssok.server.domain.file.entity.*;
import com.ssok.server.domain.file.repository.*;
import com.ssok.server.domain.file.service.FileCleanupWorker;
import com.ssok.server.domain.file.storage.LocalObjectStorage;
import com.ssok.server.domain.notification.repository.NotificationRepository;
import com.ssok.server.domain.space.dto.InvitationCreateRequest;
import com.ssok.server.domain.space.entity.*;
import com.ssok.server.domain.space.repository.*;
import com.ssok.server.domain.space.service.*;
import com.ssok.server.domain.user.entity.User;
import com.ssok.server.domain.user.repository.*;
import com.ssok.server.domain.user.service.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {"file.cleanup.enabled=false", "account.vector-cleanup.enabled=false"})
@AutoConfigureMockMvc
class AccountIntegrationTest {
    static final Path ROOT = Path.of(System.getProperty("java.io.tmpdir"), "ssok-account-files-" + UUID.randomUUID());
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) { r.add("file.storage.local-root", ROOT::toString); }
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired SpaceRepository spaces;
    @Autowired SpaceMemberRepository members;
    @Autowired SpaceService spaceService;
    @Autowired SpaceMemberService membership;
    @Autowired SpaceInvitationService invitations;
    @Autowired SpaceInvitationRepository invitationRepository;
    @Autowired StoredFileRepository files;
    @Autowired FileDeletionJobRepository fileJobs;
    @Autowired FileCleanupWorker fileWorker;
    @Autowired LocalObjectStorage storage;
    @Autowired SessionService sessions;
    @Autowired AuthSessionRepository sessionRepository;
    @Autowired JwtTokenProvider tokens;
    @Autowired NotificationRepository notifications;
    @Autowired AccountVectorDeletionRepository vectorJobs;
    @Autowired AccountVectorCleanupWorker vectorWorker;
    @Autowired AccountDeletionService accounts;
    @Autowired PasswordEncoder encoder;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean AiServerClient ai;
    @MockitoBean JavaMailSender mail;
    TransactionTemplate tx;
    User subject, teamOwner;
    Long personalId, teamId;

    @BeforeEach void setup() {
        tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            subject = user("subject"); teamOwner = user("owner");
            personalId = space(subject, SpaceType.PERSONAL).getId();
            Space team = space(teamOwner, SpaceType.TEAM); teamId = team.getId();
            members.save(SpaceMember.builder().space(team).user(subject).role(SpaceRole.EDITOR).build());
        });
    }

    @AfterEach void cleanup() {
        reset(ai);
        for (User user : List.of(subject, teamOwner)) {
            for (var space : spaces.findAllByOwnerId(user.getId())) spaceService.delete(user.getId(), space.getId());
        }
        for (var job : fileJobs.findAll()) if (job.getStorageId().equals(storage.storageId())) {
            tx.executeWithoutResult(status -> ReflectionTestUtils.setField(fileJobs.findById(job.getId()).orElseThrow(), "nextAttemptAt", Instant.EPOCH));
            fileWorker.process(job.getId());
        }
        vectorJobs.deleteAll();
        tx.executeWithoutResult(status -> {
            for (User user : List.of(subject, teamOwner)) if (users.existsById(user.getId())) users.deleteById(user.getId());
        });
    }
    @AfterAll static void cleanRoot() throws Exception { Files.deleteIfExists(ROOT); }

    @Test void onboardingUsesFrontendIdsAndPreservesFirstCompletionTime() throws Exception {
        var pair = sessions.open(subject.getId());
        mvc.perform(get("/api/users/me").header("Authorization", bearer(pair))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onboardingCompleted").value(false)).andExpect(jsonPath("$.data.password").doesNotExist());
        mvc.perform(put("/api/users/me/onboarding").header("Authorization", bearer(pair)).contentType("application/json")
                .content("{\"purposes\":[\"reference\",\"team-share\",\"reference\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.purposes.length()").value(2));
        Instant completedAt = users.findById(subject.getId()).orElseThrow().getOnboardingCompletedAt();
        mvc.perform(put("/api/users/me/onboarding").header("Authorization", bearer(pair)).contentType("application/json")
                .content("{\"purposes\":[\"etc\"]}")).andExpect(status().isOk());
        assertThat(users.findById(subject.getId()).orElseThrow().getOnboardingCompletedAt()).isEqualTo(completedAt);
        mvc.perform(get("/api/users/me/onboarding").header("Authorization", bearer(pair)))
                .andExpect(jsonPath("$.data.completed").value(true)).andExpect(jsonPath("$.data.purposes[0]").value("etc"));
        for (String body : List.of("{\"purposes\":[]}", "{\"purposes\":[\"unknown\"]}", "{\"purposes\":[null]}")) {
            mvc.perform(put("/api/users/me/onboarding").header("Authorization", bearer(pair)).contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test void profileCanChangeNameWithoutRequiringAnAvatarAndLoginShowsCompletion() throws Exception {
        var pair = sessions.open(subject.getId());
        mvc.perform(put("/api/users/profile").header("Authorization", bearer(pair)).contentType("application/json")
                .content("{\"name\":\"  New name  \",\"profileImage\":null}")).andExpect(status().isOk());
        mvc.perform(get("/api/users/me").header("Authorization", bearer(pair))).andExpect(jsonPath("$.data.name").value("New name"));
        mvc.perform(post("/api/auth/login").contentType("application/json").content(loginBody(subject)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.onboardingCompleted").value(false));
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
    }

    @Test void logoutRevokesOnlyCurrentSessionAndRefreshTokenIsHashed() throws Exception {
        var a = sessions.open(subject.getId()); var b = sessions.open(subject.getId());
        var data = tokens.refreshData(a.refreshToken());
        assertThat(sessionRepository.findById(data.sessionId()).orElseThrow().getRefreshHash()).hasSize(64).isNotEqualTo(a.refreshToken());
        mvc.perform(post("/api/auth/logout").header("Authorization", bearer(a))).andExpect(status().isOk());
        mvc.perform(get("/api/users/me").header("Authorization", bearer(a))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/me").header("Authorization", bearer(b))).andExpect(status().isOk());
        refresh(a.refreshToken(), 401);
    }

    @Test void refreshRotatesOnceAndRejectsWrongTokenTypeOrExpiredSession() throws Exception {
        var a = sessions.open(subject.getId());
        String json = refresh(a.refreshToken(), 200);
        String next = JsonPath.read(json, "$.data.refreshToken");
        assertThat(next).isNotEqualTo(a.refreshToken());
        refresh(a.refreshToken(), 401); refresh(a.accessToken(), 401);
        var data = tokens.refreshData(next);
        tx.executeWithoutResult(status -> ReflectionTestUtils.setField(sessionRepository.findById(data.sessionId()).orElseThrow(), "expiresAt", Instant.now().minusSeconds(1)));
        refresh(next, 401);
        mvc.perform(get("/api/users/me").header("Authorization", bearer(a))).andExpect(status().isUnauthorized());
    }

    @Test void simultaneousRefreshHasOnlyOneWinnerWithoutRevokingOtherDevices() throws Exception {
        var a = sessions.open(subject.getId()); var b = sessions.open(subject.getId());
        var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<SessionService.Tokens> action = () -> {
                ready.countDown(); if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
                try { return sessions.refresh(a.refreshToken()); } catch (UnauthenticatedException expected) { return null; }
            };
            var x = executor.submit(action); var y = executor.submit(action);
            try { assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); } finally { start.countDown(); }
            var results = Arrays.asList(x.get(20, TimeUnit.SECONDS), y.get(20, TimeUnit.SECONDS));
            assertThat(results.stream().filter(Objects::nonNull)).hasSize(1);
        }
        mvc.perform(get("/api/users/me").header("Authorization", bearer(b))).andExpect(status().isOk());
    }

    @Test void legacyOrNonexistentSessionTokensCannotAuthenticate() throws Exception {
        for (String token : List.of(tokens.generateAccessToken(subject.getId()), tokens.generateAccessToken(subject.getId(), UUID.randomUUID().toString()),
                sessions.open(subject.getId()).refreshToken())) {
            mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        }
    }

    @Test void teamOwnerMustTransferAndWrongPasswordHasNoSideEffects() throws Exception {
        var pair = sessions.open(subject.getId());
        mvc.perform(delete("/api/users/me").header("Authorization", bearer(pair)).contentType("application/json").content("{\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
        tx.executeWithoutResult(status -> space(subject, SpaceType.TEAM));
        mvc.perform(delete("/api/users/me").header("Authorization", bearer(pair)).contentType("application/json").content("{\"password\":\"test-password\"}"))
                .andExpect(status().isConflict());
        assertThat(users.existsById(subject.getId())).isTrue(); assertThat(spaces.existsById(personalId)).isTrue();
        assertThat(vectorJobs.count()).isZero();
        mvc.perform(get("/api/users/me").header("Authorization", bearer(pair))).andExpect(status().isOk());
    }

    @Test void deletionRemovesPrivateDataKeepsSharedFilesAndRevokesEverySession() throws Exception {
        var pair = sessions.open(subject.getId()); var second = sessions.open(subject.getId()); var ownerSession = sessions.open(teamOwner.getId());
        Long ownFile = upload(pair, personalId, "BOOKMARK");
        Long sharedFile = upload(pair, teamId, "BOOKMARK");
        Long chat = upload(pair, teamId, "CHAT_ATTACHMENT");
        Long otherChat = upload(ownerSession, teamId, "CHAT_ATTACHMENT");
        String ownKey = files.findById(ownFile).orElseThrow().getObjectKey(), chatKey = files.findById(chat).orElseThrow().getObjectKey();
        String sharedKey = files.findById(sharedFile).orElseThrow().getObjectKey();
        Long invitedSpace = tx.execute(status -> space(teamOwner, SpaceType.TEAM).getId());
        var invitation = invitations.create(teamOwner.getId(), invitedSpace, new InvitationCreateRequest(subject.getEmail(), null));
        mvc.perform(put("/api/users/me/onboarding").header("Authorization", bearer(pair)).contentType("application/json").content("{\"purposes\":[\"personal\"]}"))
                .andExpect(status().isOk());
        deleteAccount(pair);
        assertThat(users.existsById(subject.getId())).isFalse(); assertThat(spaces.existsById(personalId)).isFalse();
        assertThat(spaces.existsById(teamId)).isTrue(); assertThat(members.findAllByUserId(subject.getId())).isEmpty();
        assertThat(files.findById(ownFile)).isEmpty(); assertThat(files.findById(chat)).isEmpty(); assertThat(files.findById(otherChat)).isPresent();
        assertThat(files.findById(sharedFile).orElseThrow().getUploaderId()).isNull();
        assertThat(ROOT.resolve(ownKey)).doesNotExist(); assertThat(ROOT.resolve(chatKey)).doesNotExist(); assertThat(ROOT.resolve(sharedKey)).exists();
        assertThat(jdbc.queryForObject("select count(*) from user_onboarding_purposes where user_id=?", Long.class, subject.getId())).isZero();
        assertThat(notifications.countByRecipientIdAndReadAtIsNull(subject.getId())).isZero();
        var cleanedInvite = invitationRepository.findById(invitation.invitationId()).orElseThrow();
        assertThat(cleanedInvite.getStatus()).isEqualTo(SpaceInvitation.Status.CANCELLED); assertThat(cleanedInvite.getEmail()).isNotEqualTo(subject.getEmail());
        mvc.perform(get("/api/users/me").header("Authorization", bearer(second))).andExpect(status().isUnauthorized()); refresh(second.refreshToken(), 401);
        mvc.perform(get("/api/files/{id}/download", sharedFile).header("Authorization", bearer(ownerSession))).andExpect(status().isOk());
        verify(ai).deleteSpaceVectors(personalId); verify(ai, never()).deleteSpaceVectors(teamId);
    }

    @Test void vectorFailureDoesNotRestoreAccountAndDurableJobRetries() throws Exception {
        doThrow(new AiServerException("test failure", null)).when(ai).deleteSpaceVectors(personalId);
        var pair = sessions.open(subject.getId()); deleteAccount(pair);
        assertThat(users.existsById(subject.getId())).isFalse();
        var job = vectorJobs.findAll().getFirst(); assertThat(job.getAttempts()).isEqualTo(1);
        reset(ai);
        tx.executeWithoutResult(status -> ReflectionTestUtils.setField(vectorJobs.findById(job.getId()).orElseThrow(), "nextAttemptAt", Instant.EPOCH));
        vectorWorker.process(job.getId()); assertThat(vectorJobs.findById(job.getId())).isEmpty();
    }

    @Test void deletionAfterLeavingTeamStillCleansOwnChatAndPreservesSharedFile() throws Exception {
        var pair = sessions.open(subject.getId());
        Long chat = upload(pair, teamId, "CHAT_ATTACHMENT"), shared = upload(pair, teamId, "BOOKMARK");
        membership.leave(subject.getId(), teamId);
        deleteAccount(pair);
        assertThat(files.findById(chat)).isEmpty();
        assertThat(files.findById(shared).orElseThrow().getUploaderId()).isNull();
        assertThat(spaces.existsById(teamId)).isTrue();
    }

    @Test void rollbackRestoresAccountSessionsAndPrivateFilesWithoutDeletingVectors() throws Exception {
        var pair = sessions.open(subject.getId());
        Long fileId = upload(pair, personalId, "BOOKMARK");
        String key = files.findById(fileId).orElseThrow().getObjectKey();
        tx.executeWithoutResult(status -> {
            accounts.delete(subject.getId(), "test-password");
            status.setRollbackOnly();
        });
        assertThat(users.existsById(subject.getId())).isTrue();
        assertThat(files.findById(fileId)).isPresent();
        assertThat(ROOT.resolve(key)).exists();
        assertThat(vectorJobs.count()).isZero();
        mvc.perform(get("/api/users/me").header("Authorization", bearer(pair))).andExpect(status().isOk());
        verifyNoInteractions(ai);
    }

    @Test void transferringTeamOwnershipAllowsDeletionWithoutRemovingTeam() throws Exception {
        Long[] target = tx.execute(status -> {
            var team = space(subject, SpaceType.TEAM);
            var next = members.save(SpaceMember.builder().space(team).user(teamOwner).role(SpaceRole.EDITOR).build());
            return new Long[]{team.getId(), next.getId()};
        });
        membership.transferOwnership(subject.getId(), target[0], target[1]);
        deleteAccount(sessions.open(subject.getId()));
        assertThat(spaces.existsById(target[0])).isTrue();
        Long remainingOwner = tx.execute(status -> spaces.findById(target[0]).orElseThrow().getOwner().getId());
        assertThat(remainingOwner).isEqualTo(teamOwner.getId());
    }

    private void deleteAccount(SessionService.Tokens pair) throws Exception {
        mvc.perform(delete("/api/users/me").header("Authorization", bearer(pair)).contentType("application/json").content("{\"password\":\"test-password\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.accountDeleted").value(true));
    }
    private Long upload(SessionService.Tokens pair, Long spaceId, String purpose) throws Exception {
        String json = mvc.perform(multipart("/api/files").file(new MockMultipartFile("file", "test.pdf", "application/pdf", "%PDF-1.4\n%%EOF".getBytes(StandardCharsets.US_ASCII)))
                .param("spaceId", spaceId.toString()).param("purpose", purpose).header("Authorization", bearer(pair)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.<Number>read(json, "$.data.fileId").longValue();
    }
    private String refresh(String token, int expected) throws Exception {
        return mvc.perform(post("/api/auth/refresh").contentType("application/json").content("{\"refreshToken\":\"" + token + "\"}"))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
    }
    private String bearer(SessionService.Tokens pair) { return "Bearer " + pair.accessToken(); }
    private String loginBody(User user) { return "{\"email\":\"" + user.getEmail() + "\",\"password\":\"test-password\"}"; }
    private User user(String name) { return users.save(User.builder().name(name).email(name + UUID.randomUUID() + "@account.test").password(encoder.encode("test-password")).build()); }
    private Space space(User user, SpaceType type) {
        var space = spaces.save(Space.builder().name("space").type(type).owner(user).build());
        members.save(SpaceMember.builder().space(space).user(user).role(SpaceRole.OWNER).build()); return space;
    }
}
