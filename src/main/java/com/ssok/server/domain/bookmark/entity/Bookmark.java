package com.ssok.server.domain.bookmark.entity;

import com.ssok.server.domain.space.entity.Space;
import com.ssok.server.domain.user.entity.User;
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
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "bookmarks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Bookmark {

    private static final int MAX_TITLE_LENGTH = 255;

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

    // 가장 최근 분석 요청의 ID. 이 값과 다른 ID로 온 콜백(이전 요청의 결과)은 무시한다.
    @Column(length = 36)
    private String analysisRequestId;

    @Column
    private LocalDateTime analysisRequestedAt;

    // 마지막 분석 요청 때 AI에 보낸 space_id. 분석 중 이동했다면 결과 반영 후 벡터 space_id를 다시 맞춘다.
    @Column
    private Long analysisSpaceId;

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

    // 저장한 사용자. 기존 데이터와 탈퇴한 사용자를 위해 null을 허용한다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "saved_by_user_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private User savedBy;

    @Builder
    public Bookmark(String url, String title, Space space, User savedBy) {
        this.url = url;
        this.title = title;
        this.space = space;
        this.savedBy = savedBy;
        this.visitCount = 0;
        requestAnalysis();
    }

    /**
     * 새 분석 요청 ID를 발급한다. 재분석 시에도 호출되며, 이전 요청 ID의 콜백은 이후 무시된다.
     */
    public String requestAnalysis() {
        this.analysisStatus = AnalysisStatus.PENDING;
        this.analysisRequestId = UUID.randomUUID().toString();
        this.analysisRequestedAt = LocalDateTime.now();
        this.analysisSpaceId = space != null ? space.getId() : null;
        return this.analysisRequestId;
    }

    /**
     * 분석 요청 이후 다른 스페이스로 옮겨졌는지. 그렇다면 AI가 저장한 벡터의 space_id가 어긋나 있다.
     */
    public boolean isMovedSinceAnalysisRequest() {
        return analysisSpaceId != null && !analysisSpaceId.equals(space.getId());
    }

    /**
     * 다른 스페이스로 공유할 복사본. 원본의 분석 결과를 미리 보여주되,
     * 대상 스페이스용 벡터가 따로 필요하므로 새 분석 요청 상태(PENDING)로 만든다.
     */
    public Bookmark copyTo(Space target, User sharer) {
        Bookmark copy = new Bookmark(url, title, target, sharer);
        if (summary != null || category != null || !tags.isEmpty()) {
            copy.completeAnalysis(summary, category, tags);
            copy.requestAnalysis();
        }
        return copy;
    }

    public void moveTo(Space target) {
        this.space = target;
    }

    public boolean isCurrentAnalysisRequest(String requestId) {
        return analysisRequestId != null && analysisRequestId.equals(requestId);
    }

    /**
     * AI가 추출한 제목이 유효하면 반영한다. 비어 있으면 기존 제목(URL 호스트)을 유지한다.
     */
    public void updateTitle(String title) {
        if (title == null || title.isBlank()) {
            return;
        }
        String trimmed = title.strip();
        this.title = trimmed.length() > MAX_TITLE_LENGTH ? trimmed.substring(0, MAX_TITLE_LENGTH) : trimmed;
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
