package com.ssok.server.domain.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import com.ssok.server.common.exception.FileStorageException;
import com.ssok.server.common.security.JwtTokenProvider;
import com.ssok.server.domain.ai.client.AiServerClient;
import com.ssok.server.domain.bookmark.entity.*;
import com.ssok.server.domain.bookmark.repository.BookmarkRepository;
import com.ssok.server.domain.file.entity.*;
import com.ssok.server.domain.file.repository.*;
import com.ssok.server.domain.file.service.*;
import com.ssok.server.domain.file.storage.LocalObjectStorage;
import com.ssok.server.domain.library.entity.Library;
import com.ssok.server.domain.library.repository.LibraryRepository;
import com.ssok.server.domain.space.entity.*;
import com.ssok.server.domain.space.repository.*;
import com.ssok.server.domain.space.service.SpaceService;
import com.ssok.server.domain.user.entity.User;
import com.ssok.server.domain.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = "file.cleanup.enabled=false")
@AutoConfigureMockMvc
class FileIntegrationTest {
    @Autowired private com.ssok.server.domain.auth.service.SessionService testSessions;
    private static final Path ROOT = Path.of(System.getProperty("java.io.tmpdir"), "ssok-files-" + UUID.randomUUID());
    private static final byte[] PDF = "%PDF-1.4\n1 0 obj\n<<>>\nendobj\n%%EOF".getBytes(StandardCharsets.US_ASCII);
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) { registry.add("file.storage.local-root", ROOT::toString); }
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private SpaceRepository spaces;
    @Autowired private SpaceMemberRepository members;
    @Autowired private LibraryRepository libraries;
    @Autowired private BookmarkRepository bookmarks;
    @Autowired private StoredFileRepository files;
    @Autowired private FileDeletionJobRepository jobs;
    @Autowired private FileService service;
    @Autowired private FileCleanupWorker worker;
    @Autowired private SpaceService spaceService;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JwtTokenProvider tokens;
    @MockitoSpyBean private LocalObjectStorage storage;
    @MockitoBean private AiServerClient ai;
    private User owner, editor, viewer, outsider;
    private Long spaceId, otherSpaceId, libraryId, foreignLibraryId;
    private TransactionTemplate tx;

    @BeforeEach
    void setup() {
        tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            String suffix = UUID.randomUUID().toString();
            owner = user("owner", suffix); editor = user("editor", suffix);
            viewer = user("viewer", suffix); outsider = user("outsider", suffix);
            Space team = spaces.save(Space.builder().name("files").type(SpaceType.TEAM).owner(owner).build());
            Space other = spaces.save(Space.builder().name("other").type(SpaceType.TEAM).owner(outsider).build());
            spaceId = team.getId(); otherSpaceId = other.getId();
            membership(team, owner, SpaceRole.OWNER); membership(team, editor, SpaceRole.EDITOR);
            membership(team, viewer, SpaceRole.VIEWER); membership(other, outsider, SpaceRole.OWNER);
            libraryId = libraries.save(new Library(team, "Files")).getId();
            foreignLibraryId = libraries.save(new Library(other, "Other files")).getId();
        });
    }

    @AfterEach
    void cleanup() {
        reset(storage);
        if (spaces.existsById(spaceId)) spaceService.delete(owner.getId(), spaceId);
        if (spaces.existsById(otherSpaceId)) spaceService.delete(outsider.getId(), otherSpaceId);
        for (FileDeletionJob job : jobs.findAll()) {
            if (job.getStorageId().equals(storage.storageId())) {
                forceDue(job.getId()); worker.process(job.getId());
            }
        }
        tx.executeWithoutResult(status -> {
            for (User user : List.of(owner, editor, viewer, outsider)) users.deleteById(user.getId());
        });
    }

    @AfterAll
    static void removeEmptyRoot() throws Exception { Files.deleteIfExists(ROOT); }

    @Test
    void uploadCreatesFileBookmarkWithoutAiAndJwtDownloadMatchesOriginal() throws Exception {
        String response = upload(editor, "BOOKMARK", libraryId, pdf("문서.pdf"));
        Long fileId = number(response, "$.data.fileId"), bookmarkId = number(response, "$.data.bookmarkId");
        assertThat(bookmarks.findById(bookmarkId).orElseThrow().getAnalysisStatus()).isEqualTo(AnalysisStatus.NOT_SUPPORTED);
        mvc.perform(get("/api/bookmarks/{id}", bookmarkId).with(as(viewer)))
                .andExpect(jsonPath("$.data.type").value("FILE")).andExpect(jsonPath("$.data.fileId").value(fileId));
        mvc.perform(get("/api/files/{id}", fileId).with(as(viewer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.libraryId").value(libraryId))
                .andExpect(jsonPath("$.data.storageId").doesNotExist()).andExpect(jsonPath("$.data.objectKey").doesNotExist());
        mvc.perform(get("/api/files/{id}/download", fileId).header("Authorization", "Bearer " + testSessions.open(viewer.getId()).accessToken()))
                .andExpect(status().isOk()).andExpect(content().bytes(PDF))
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "no-store"));
        assertThat(jobs.findAll().stream().filter(j -> j.getStorageId().equals(storage.storageId()))).isEmpty();
        verifyNoInteractions(ai);
    }

    @Test
    void viewersAndOutsidersCannotUploadAndNonMembersCannotDownload() throws Exception {
        for (User user : List.of(viewer, outsider)) {
            mvc.perform(multipart("/api/files").file(pdf("test.pdf")).param("spaceId", spaceId.toString()).with(as(user)))
                    .andExpect(status().isForbidden());
        }
        String response = upload(owner, "BOOKMARK", null, pdf("test.pdf"));
        Long id = number(response, "$.data.fileId");
        mvc.perform(get("/api/files/{id}/download", id).with(as(outsider))).andExpect(status().isForbidden());
        mvc.perform(get("/api/files/{id}/download", id)).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/files/{id}", id).with(as(viewer))).andExpect(status().isForbidden());
        mvc.perform(get("/api/files/{id}/download", id).header("Authorization", "Bearer " + tokens.generateRefreshToken(viewer.getId())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void chatAttachmentIsPrivateAndDoesNotCreateBookmark() throws Exception {
        String response = upload(editor, "CHAT_ATTACHMENT", null, pdf("private.pdf"));
        Long id = number(response, "$.data.fileId");
        assertThat(bookmarks.countBySpaceId(spaceId)).isZero();
        mvc.perform(get("/api/files/{id}/download", id).with(as(editor))).andExpect(status().isOk());
        for (User user : List.of(owner, viewer, outsider)) {
            mvc.perform(get("/api/files/{id}", id).with(as(user))).andExpect(status().isForbidden());
            mvc.perform(delete("/api/files/{id}", id).with(as(user))).andExpect(status().isForbidden());
        }
        tx.executeWithoutResult(status -> members.findBySpaceIdAndUserId(spaceId, editor.getId()).orElseThrow().updateRole(SpaceRole.VIEWER));
        mvc.perform(get("/api/files/{id}/download", id).with(as(editor))).andExpect(status().isForbidden());
    }

    @Test
    void invalidSignatureExtensionSizeAndEmptyFileAreRejected() throws Exception {
        for (MockMultipartFile bad : List.of(new MockMultipartFile("file", "fake.png", "image/png", PDF),
                new MockMultipartFile("file", "bad.html", "application/pdf", PDF))) {
            mvc.perform(multipart("/api/files").file(bad).param("spaceId", spaceId.toString()).with(as(owner))).andExpect(status().isUnsupportedMediaType());
        }
        mvc.perform(multipart("/api/files").file(new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]))
                .param("spaceId", spaceId.toString()).with(as(owner))).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/files").file(new MockMultipartFile("file", "large.pdf", "application/pdf", new byte[FileValidation.MAX_BYTES + 1]))
                .param("spaceId", spaceId.toString()).with(as(owner))).andExpect(status().isPayloadTooLarge());
        assertThat(files.findAllBySpaceId(spaceId)).isEmpty();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"png", "jpg"})
    void pngAndJpegAreDetectedFromContent(String format) throws Exception {
        var output = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_RGB), format, output);
        byte[] bytes = output.toByteArray();
        String response = upload(editor, "BOOKMARK", null,
                new MockMultipartFile("file", "image." + format, "application/octet-stream", bytes));
        mvc.perform(get("/api/files/{id}/download", number(response, "$.data.fileId")).with(as(viewer)))
                .andExpect(status().isOk()).andExpect(content().bytes(bytes))
                .andExpect(header().string("Content-Type", format.equals("png") ? "image/png" : "image/jpeg"));
    }

    @Test
    void removalFromSpaceImmediatelyRevokesFileAccess() throws Exception {
        String response = upload(owner, "BOOKMARK", null, pdf("access.pdf"));
        Long id = number(response, "$.data.fileId");
        tx.executeWithoutResult(status -> members.delete(members.findBySpaceIdAndUserId(spaceId, viewer.getId()).orElseThrow()));
        mvc.perform(get("/api/files/{id}/download", id).with(as(viewer))).andExpect(status().isForbidden());
        assertThat(files.findById(id)).isPresent();
    }

    @Test
    void filenameCannotControlStoragePathAndIdenticalNamesStaySeparate() throws Exception {
        String first = upload(owner, "BOOKMARK", null, pdf("../../same.pdf"));
        String second = upload(owner, "BOOKMARK", null, pdf("C:\\folder\\same.pdf"));
        assertThat(JsonPath.<String>read(first, "$.data.originalName")).isEqualTo("same.pdf");
        StoredFile a = files.findById(number(first, "$.data.fileId")).orElseThrow();
        StoredFile b = files.findById(number(second, "$.data.fileId")).orElseThrow();
        assertThat(a.getObjectKey()).isNotEqualTo(b.getObjectKey());
        assertThat(ROOT.resolve(a.getObjectKey())).exists();
        assertThat(ROOT.resolve(b.getObjectKey())).exists();
        mvc.perform(multipart("/api/files").file(pdf("bad\r\nname.pdf")).param("spaceId", spaceId.toString()).with(as(owner)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void crossSpaceFolderAndChatFolderAreRejectedAndFileAnalysisIsBlocked() throws Exception {
        mvc.perform(multipart("/api/files").file(pdf("file.pdf")).param("spaceId", spaceId.toString()).param("libraryId", foreignLibraryId.toString())
                .with(as(owner))).andExpect(status().isNotFound());
        mvc.perform(multipart("/api/files").file(pdf("file.pdf")).param("spaceId", spaceId.toString()).param("libraryId", libraryId.toString())
                .param("purpose", "CHAT_ATTACHMENT").with(as(owner))).andExpect(status().isBadRequest());
        String response = upload(owner, "BOOKMARK", null, pdf("file.pdf"));
        Long id = number(response, "$.data.bookmarkId");
        mvc.perform(post("/api/ai/summary").with(as(owner)).contentType("application/json").content("{\"bookmarkId\":" + id + "}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(ai);
    }

    @Test
    void deletingFileOrBookmarkRemovesMetadataAndPhysicalObject() throws Exception {
        for (boolean viaBookmark : List.of(false, true)) {
            String response = upload(editor, "BOOKMARK", null, pdf("delete.pdf"));
            Long id = number(response, "$.data.fileId"), bookmarkId = number(response, "$.data.bookmarkId");
            String key = files.findById(id).orElseThrow().getObjectKey();
            String path = viaBookmark ? "/api/bookmarks/" + bookmarkId : "/api/files/" + id;
            mvc.perform(delete(path).with(as(owner))).andExpect(status().isOk());
            assertThat(files.findById(id)).isEmpty();
            assertThat(bookmarks.findById(bookmarkId)).isEmpty();
            assertThat(ROOT.resolve(key)).doesNotExist();
        }
    }

    @Test
    void deletingLibraryKeepsFileAndDeletingSpaceRemovesBothPurposes() throws Exception {
        String response = upload(owner, "BOOKMARK", libraryId, pdf("keep.pdf"));
        Long id = number(response, "$.data.fileId");
        String key = files.findById(id).orElseThrow().getObjectKey();
        mvc.perform(delete("/api/spaces/{s}/libraries/{l}", spaceId, libraryId).with(as(owner))).andExpect(status().isOk());
        mvc.perform(get("/api/files/{id}", id).with(as(viewer))).andExpect(status().isOk()).andExpect(jsonPath("$.data.libraryId").isEmpty());
        assertThat(ROOT.resolve(key)).exists();
        upload(editor, "CHAT_ATTACHMENT", null, pdf("chat.pdf"));
        List<String> keys = files.findAllBySpaceId(spaceId).stream().map(StoredFile::getObjectKey).toList();
        mvc.perform(delete("/api/spaces/{s}", spaceId).with(as(owner))).andExpect(status().isOk());
        assertThat(files.findAllBySpaceId(spaceId)).isEmpty();
        keys.forEach(k -> assertThat(ROOT.resolve(k)).doesNotExist());
    }

    @Test
    void failedPhysicalDeletionRemainsInQueueAndCanRetry() throws Exception {
        String response = upload(owner, "BOOKMARK", null, pdf("retry.pdf"));
        Long id = number(response, "$.data.fileId");
        String key = files.findById(id).orElseThrow().getObjectKey();
        doThrow(new FileStorageException()).when(storage).delete(anyString());
        mvc.perform(delete("/api/files/{id}", id).with(as(owner))).andExpect(status().isOk());
        assertThat(files.findById(id)).isEmpty();
        assertThat(ROOT.resolve(key)).exists();
        var pending = jobs.findAll().stream().filter(j -> j.getObjectKey().equals(key)).findFirst().orElseThrow();
        assertThat(pending.getAttempts()).isEqualTo(1);
        reset(storage);
        forceDue(pending.getId()); worker.process(pending.getId());
        assertThat(jobs.findById(pending.getId())).isEmpty();
        assertThat(ROOT.resolve(key)).doesNotExist();
    }

    @Test
    void rolledBackUploadLeavesRecoverableIntentAndNoPublicMetadata() {
        tx.executeWithoutResult(status -> {
            service.upload(owner.getId(), spaceId, null, FilePurpose.BOOKMARK, pdf("rollback.pdf"));
            status.setRollbackOnly();
        });
        assertThat(files.findAllBySpaceId(spaceId)).isEmpty();
        var intent = jobs.findAll().stream().filter(j -> j.getStorageId().equals(storage.storageId())).findFirst().orElseThrow();
        assertThat(ROOT.resolve(intent.getObjectKey())).exists();
        forceDue(intent.getId()); worker.process(intent.getId());
        assertThat(ROOT.resolve(intent.getObjectKey())).doesNotExist();
    }

    private String upload(User user, String purpose, Long library, MockMultipartFile file) throws Exception {
        var request = multipart("/api/files").file(file).param("spaceId", spaceId.toString()).param("purpose", purpose).with(as(user));
        if (library != null) request.param("libraryId", library.toString());
        return mvc.perform(request).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }
    private void forceDue(Long id) { tx.executeWithoutResult(status -> ReflectionTestUtils.setField(jobs.findById(id).orElseThrow(), "nextAttemptAt", Instant.now().minusSeconds(1))); }
    private MockMultipartFile pdf(String name) { return new MockMultipartFile("file", name, "application/octet-stream", PDF); }
    private Long number(String json, String path) { return JsonPath.<Number>read(json, path).longValue(); }
    private User user(String name, String suffix) { return users.save(User.builder().name(name).email(name + suffix + "@files.test").password("test").build()); }
    private void membership(Space space, User user, SpaceRole role) { members.save(SpaceMember.builder().space(space).user(user).role(role).build()); }
    private RequestPostProcessor as(User user) { return authentication(new UsernamePasswordAuthenticationToken(user.getId(), null, List.of())); }
}
