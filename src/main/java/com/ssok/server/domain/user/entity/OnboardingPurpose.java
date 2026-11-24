package com.ssok.server.domain.user.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum OnboardingPurpose {
    REFERENCE("reference"), ARCHIVE("archive"), TEAM_SHARE("team-share"), PERSONAL("personal"), ETC("etc");
    private final String value;
    OnboardingPurpose(String value) { this.value = value; }
    @JsonValue public String value() { return value; }
    @JsonCreator public static OnboardingPurpose from(String value) {
        for (var purpose : values()) if (purpose.value.equals(value)) return purpose;
        throw new IllegalArgumentException("invalid onboarding purpose");
    }
}
