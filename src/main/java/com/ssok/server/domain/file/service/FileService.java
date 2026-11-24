package com.ssok.server.domain.file.service;

import com.ssok.server.common.exception.*;
import com.ssok.server.domain.bookmark.entity.Bookmark;
import com.ssok.server.domain.bookmark.repository.BookmarkRepository;
import com.ssok.server.domain.file.dto.FileResponse;
import com.ssok.server.domain.file.entity.*;
import com.ssok.server.domain.file.repository.*;
import com.ssok.server.domain.file.storage.ObjectStorage;
import com.ssok.server.domain.library.repository.LibraryRepository;
import com.ssok.server.domain.space.repository.SpaceRepository;
import com.ssok.server.domain.space.service.SpaceAccessService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class FileService {
    private final StoredFileRepository files;
    private final BookmarkRepository bookmarks;
    private final LibraryRepository libraries;
    private final SpaceRepository spaces;
    private final SpaceAccessService access;
    private final ObjectStorage storage;
    private final FileValidation validation;
    private final UploadIntentService uploadIntents;
    private final FileDeletionJobRepository jobs;
    private final FileCleanupService cleanup;
    private final EntityManager entityManager;

    @Transactional
    public FileResponse upload(Long userId, Long spaceId, Long libraryId, FilePurpose purpose, MultipartFile multipart) {
        var space = spaces.findLockedById(spaceId).orElseThrow(() -> new SpaceNotFoundException("space not found"));
        access.requireWrite(userId, spaceId);
        if (purpose == FilePurpose.CHAT_ATTACHMENT && libraryId != null) {
            throw new InvalidRequestException("chat attachment cannot belong to a library");
        }
        var library = libraryId == null ? null : libraries.findByIdAndSpaceId(libraryId, spaceId)
                .orElseThrow(() -> new LibraryNotFoundException("library not found in this space"));
        var content = validation.validate(multipart);
        String key = UUID.randomUUID().toString();
        // 먼저 별도 트랜잭션으로 정리 의도를 기록하고 파일을 쓴다. 성공 커밋 때만 의도를 제거한다.
        Long intentId = uploadIntents.prepare(storage.storageId(), key);
        storage.put(key, content.bytes());
        StoredFile file = files.saveAndFlush(new StoredFile(space, userId, purpose, content.name(), content.contentType(),
                content.bytes().length, storage.storageId(), key));
        if (purpose == FilePurpose.BOOKMARK) {
            Bookmark bookmark = Bookmark.builder().space(space).title(content.name()).url("/api/files/" + file.getId() + "/download").build();
            bookmark.markAsFile();
            bookmark.changeLibrary(library);
            bookmarks.saveAndFlush(bookmark);
            file.attachBookmark(bookmark);
        }
        // REQUIRES_NEW에서 방금 생성한 행은 MySQL RR 스냅샷 조회에서 안 보일 수 있다.
        // 먼저 조회하는 deleteById 대신 current-read DELETE를 사용해 같은 커밋에서 확실히 제거한다.
        if (jobs.cancelUploadIntent(intentId) != 1) throw new FileStorageException();
        files.flush();
        return FileResponse.from(file);
    }

    @Transactional(readOnly = true)
    public FileResponse metadata(Long userId, Long fileId) {
        StoredFile file = find(fileId);
        requireRead(userId, file);
        return FileResponse.from(file);
    }

    public record Download(FileResponse metadata, byte[] bytes) {}

    @Transactional(readOnly = true)
    public Download download(Long userId, Long fileId) {
        StoredFile file = find(fileId);
        requireRead(userId, file);
        if (!file.getStorageId().equals(storage.storageId())) throw new FileStorageException();
        byte[] bytes = storage.read(file.getObjectKey(), FileValidation.MAX_BYTES);
        if (bytes.length != file.getSizeBytes()) throw new FileStorageException();
        return new Download(FileResponse.from(file), bytes);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void delete(Long userId, Long fileId) {
        StoredFile file = find(fileId);
        Long spaceId = file.getSpace().getId();
        spaces.findLockedById(spaceId).orElseThrow(() -> new SpaceNotFoundException("space not found"));
        access.requireWrite(userId, spaceId);
        try { entityManager.refresh(file, LockModeType.PESSIMISTIC_WRITE); }
        catch (jakarta.persistence.EntityNotFoundException e) { throw new FileNotFoundException(); }
        if (file.getPurpose() == FilePurpose.CHAT_ATTACHMENT && !file.getUploaderId().equals(userId)) {
            throw new ForbiddenException("chat attachment belongs to another user");
        }
        cleanup.enqueue(file);
        if (file.getBookmark() != null) bookmarks.delete(file.getBookmark());
        else files.delete(file);
    }

    private StoredFile find(Long id) { return files.findById(id).orElseThrow(FileNotFoundException::new); }

    private void requireRead(Long userId, StoredFile file) {
        Long spaceId = file.getSpace().getId();
        access.requireMember(userId, spaceId);
        if (file.getPurpose() == FilePurpose.CHAT_ATTACHMENT) {
            access.requireSearch(userId, spaceId);
            if (!file.getUploaderId().equals(userId)) throw new ForbiddenException("chat attachment belongs to another user");
        }
    }
}
