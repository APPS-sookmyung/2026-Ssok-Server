package com.ssok.server.domain.file.entity;

import com.ssok.server.domain.bookmark.entity.Bookmark;
import com.ssok.server.domain.space.entity.Space;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "stored_files", indexes = @Index(name = "idx_file_space", columnList = "space_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoredFile {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "space_id", nullable = false)
    private Space space;
    // 계정 삭제 정책과 분리하기 위해 인증된 업로더 ID를 저장한다.
    @Column(nullable = false)
    private Long uploaderId;
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bookmark_id", unique = true)
    private Bookmark bookmark;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private FilePurpose purpose;
    @Column(nullable = false)
    private String originalName;
    @Column(nullable = false, length = 100)
    private String contentType;
    @Column(nullable = false)
    private long sizeBytes;
    @Column(nullable = false, length = 1024)
    private String storageId;
    @Column(nullable = false, length = 36, unique = true)
    private String objectKey;
    @CreationTimestamp @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public StoredFile(Space space, Long uploaderId, FilePurpose purpose, String originalName,
            String contentType, long sizeBytes, String storageId, String objectKey) {
        this.space = space;
        this.uploaderId = uploaderId;
        this.purpose = purpose;
        this.originalName = originalName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.storageId = storageId;
        this.objectKey = objectKey;
    }

    public void attachBookmark(Bookmark bookmark) {
        this.bookmark = bookmark;
        bookmark.attachFile(this);
    }
}
