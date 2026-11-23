package com.ssok.server.domain.library.entity;

import com.ssok.server.domain.space.entity.Space;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "libraries", uniqueConstraints = @UniqueConstraint(name = "uk_library_space_name", columnNames = {"space_id", "name_key"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Library {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "space_id", nullable = false)
    private Space space;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(name = "name_key", nullable = false, length = 100)
    private String nameKey;
    @CreationTimestamp @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Library(Space space, String name) {
        this.space = space;
        rename(name);
    }

    public void rename(String name) {
        this.name = name.strip();
        this.nameKey = key(name);
    }

    public static String key(String name) {
        return name.strip().toLowerCase(Locale.ROOT);
    }
}
