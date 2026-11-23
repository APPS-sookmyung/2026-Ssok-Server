package com.ssok.server.domain.library.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Positive;

public record BookmarkLibraryRequest(@JsonProperty(required = true) @Positive Long libraryId) {
}
