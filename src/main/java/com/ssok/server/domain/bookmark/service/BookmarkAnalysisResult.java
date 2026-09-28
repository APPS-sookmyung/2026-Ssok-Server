package com.ssok.server.domain.bookmark.service;

import java.util.List;

public record BookmarkAnalysisResult(String title, String summary, List<String> tags) {
}
