package com.ssok.server.domain.ai.dto;

import com.ssok.server.common.exception.InvalidRequestException;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 저장 날짜 조건. 날짜는 서버 시간대(Asia/Seoul) 기준이며 양 끝 날짜를 포함한다.
 *
 * @param from 포함 (null이면 제한 없음)
 * @param to   미포함 (null이면 제한 없음)
 */
public record SearchPeriod(LocalDateTime from, LocalDateTime to) {

    public static final SearchPeriod ALL = new SearchPeriod(null, null);

    public static SearchPeriod of(LocalDate savedFrom, LocalDate savedTo) {
        if (savedFrom != null && savedTo != null && savedFrom.isAfter(savedTo)) {
            throw new InvalidRequestException("savedFrom must be on or before savedTo");
        }
        return new SearchPeriod(
                savedFrom != null ? savedFrom.atStartOfDay() : null,
                savedTo != null ? savedTo.plusDays(1).atStartOfDay() : null);
    }

    public boolean contains(LocalDateTime savedAt) {
        return (from == null || !savedAt.isBefore(from)) && (to == null || savedAt.isBefore(to));
    }
}
