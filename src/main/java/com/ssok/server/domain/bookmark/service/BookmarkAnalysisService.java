package com.ssok.server.domain.bookmark.service;

import java.net.URI;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 실제 AI 분석 대신 URL만으로 title/summary/tags를 만들어내는 mock 구현.
 * 추후 실제 LLM 호출로 교체 가능하도록 별도 서비스로 분리해둠.
 */
@Service
public class BookmarkAnalysisService {

    public BookmarkAnalysisResult analyze(String url) {
        String host = extractHost(url);
        String title = host;
        String summary = host + " 사이트에 대한 자동 생성 요약입니다.";
        List<String> tags = List.of(capitalize(firstLabel(host)), "Bookmark");

        return new BookmarkAnalysisResult(title, summary, tags);
    }

    private String extractHost(String url) {
        try {
            String host = URI.create(url).getHost();
            if (host == null) {
                return url;
            }
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (IllegalArgumentException e) {
            return url;
        }
    }

    private String firstLabel(String host) {
        int dotIndex = host.indexOf('.');
        return dotIndex > 0 ? host.substring(0, dotIndex) : host;
    }

    private String capitalize(String value) {
        if (value.isEmpty()) {
            return value;
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
