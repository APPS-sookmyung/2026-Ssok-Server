package com.ssok.server.common.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public final class TimeFormatter {

    private TimeFormatter() {
    }

    public static String format(LocalDateTime time) {
        return time.truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}
