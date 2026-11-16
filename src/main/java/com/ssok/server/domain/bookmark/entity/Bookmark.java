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

    @Builder
    public Bookmark(String url, String title, Space space) {
        this.url = url;
        this.title = title;
        this.space = space;
        this.visitCount = 0;
        this.analysisStatus = AnalysisStatus.PENDING;
    }

    public void requestAnalysis() {
        this.analysisStatus = AnalysisStatus.PENDING;
    }

    public void completeAnalysis(String summary, String category, List<String> tags) {
        this.summary = summary;
        this.category = category;
        this.tags.clear();
        if (tags != null) {
            this.tags.addAll(tags);
        }
        this.analysisStatus = AnalysisStatus.SUCCESS;
    }

    public void failAnalysis() {
        this.analysisStatus = AnalysisStatus.FAILED;
    }

    public void recordVisit() {
        this.visitCount++;
        this.lastVisitedAt = LocalDateTime.now();
    }
}
