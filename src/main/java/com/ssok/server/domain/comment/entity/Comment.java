package com.ssok.server.domain.comment.entity;

import com.ssok.server.domain.bookmark.entity.Bookmark;
import com.ssok.server.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "comments", indexes = @Index(name = "idx_comment_bookmark", columnList = "bookmark_id, id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Comment {

    public static final int MAX_CONTENT_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 북마크가 삭제되면(단건·다중·스페이스 삭제 모두) DB가 댓글도 함께 지운다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bookmark_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Bookmark bookmark;

    // 작성자가 탈퇴해도 댓글은 남기고 작성자만 비운다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private User author;

    @Column(nullable = false, length = MAX_CONTENT_LENGTH)
    private String content;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 내용을 수정했을 때만 기록한다.
    @Column
    private LocalDateTime editedAt;

    @Builder
    public Comment(Bookmark bookmark, User author, String content) {
        this.bookmark = bookmark;
        this.author = author;
        this.content = content;
    }

    public boolean isWrittenBy(Long userId) {
        return author != null && author.getId().equals(userId);
    }

    public void edit(String content) {
        this.content = content;
        this.editedAt = LocalDateTime.now();
    }
}
