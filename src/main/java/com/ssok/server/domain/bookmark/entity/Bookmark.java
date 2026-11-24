package com.ssok.server.domain.bookmark.entity;

import com.ssok.server.domain.space.entity.Space;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@org.hibernate.annotations.DynamicUpdate
@Table(name = "bookmarks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Bookmark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 1000)
    private String url;

    @Column(nullable = false)
    private String title;

    @Column(length = 2000)
    private String summary;

    @Column
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnalysisStatus analysisStatus;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "bookmark_tags", joinColumns = @JoinColumn(name = "bookmark_id"))
    @Column(name = "tag")
    private List<String> tags = new ArrayList<>();

    @Column(nullable = false)
    private int visitCount;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime lastVisitedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "space_id", nullable = false)
    private Space space;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(16) default 'LINK'")
    private BookmarkType type = BookmarkType.LINK;

    @jakarta.persistence.OneToOne(mappedBy = "bookmark", cascade = jakarta.persistence.CascadeType.REMOVE, orphanRemoval = true)
    private com.ssok.server.domain.file.entity.StoredFile file;

    public void markAsFile() {
        type = BookmarkType.FILE;
        analysisStatus = AnalysisStatus.NOT_SUPPORTED;
    }

    public void attachFile(com.ssok.server.domain.file.entity.StoredFile file) { this.file = file; }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "library_id", foreignKey = @jakarta.persistence.ForeignKey(name = "fk_bookmark_library"))
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.SET_NULL)
    private com.ssok.server.domain.library.entity.Library library;

    public void changeLibrary(com.ssok.server.domain.library.entity.Library library) {
        if (library != null && !library.getSpace().getId().equals(space.getId())) {
            throw new IllegalArgumentException("library must belong to the bookmark space");
        }
        this.library = library;
    }

    @Builder
    public Bookmark(String url, String title, Space space) {
        this.url = url;
        this.title = title;
        this.space = space;
        this.visitCount = 0;
        this.analysisStatus = AnalysisStatus.PENDING;
    }

    public void requestAnalysis() {
        requireLinkAnalysis();
        this.analysisStatus = AnalysisStatus.PENDING;
    }

    public void completeAnalysis(String summary, String category, List<String> tags) {
        requireLinkAnalysis();
        this.summary = summary;
        this.category = category;
        this.tags.clear();
        if (tags != null) {
            this.tags.addAll(tags);
        }
        this.analysisStatus = AnalysisStatus.SUCCESS;
    }

    public void failAnalysis() {
        if (type == BookmarkType.FILE) return;
        this.analysisStatus = AnalysisStatus.FAILED;
    }

    private void requireLinkAnalysis() {
        if (type == BookmarkType.FILE) {
            throw new com.ssok.server.common.exception.InvalidRequestException("file AI analysis is not supported yet");
        }
    }

    public void recordVisit() {
        this.visitCount++;
        this.lastVisitedAt = LocalDateTime.now();
    }
}
