package com.ssok.server.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String profileImage;

    @jakarta.persistence.ElementCollection
    @jakarta.persistence.CollectionTable(name = "user_onboarding_purposes", joinColumns = @jakarta.persistence.JoinColumn(name = "user_id"),
            uniqueConstraints = @jakarta.persistence.UniqueConstraint(name = "uk_user_onboarding_purpose", columnNames = {"user_id", "purpose"}))
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 32, columnDefinition = "varchar(32)")
    private java.util.Set<OnboardingPurpose> onboardingPurposes = new java.util.LinkedHashSet<>();

    @Column
    private java.time.Instant onboardingCompletedAt;

    public void completeOnboarding(java.util.Collection<OnboardingPurpose> purposes) {
        onboardingPurposes.clear();
        onboardingPurposes.addAll(purposes);
        if (onboardingCompletedAt == null) onboardingCompletedAt = java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    }

    @UpdateTimestamp
    @Column
    private LocalDateTime updatedAt;

    @Column
    private java.time.Instant lastLoginAt;

    public void recordLogin(java.time.Instant now) { lastLoginAt = now; }

    @Builder
    public User(String email, String password, String name) {
        this.email = email;
        this.password = password;
        this.name = name;
    }

    public void updateProfile(String name, String profileImage) {
        this.name = name;
        this.profileImage = profileImage;
    }
}
