package com.danielflower.crank4j.sharedstuff;

import java.util.concurrent.TimeUnit;

public class Constants {
    public static final String REQUEST_BODY_PENDING_MARKER = "_1";
    public static final String REQUEST_HAS_NO_BODY_MARKER = "_2";
    public static final String REQUEST_BODY_ENDED_MARKER = "_3";
    public static final int MAX_REQUEST_HEADERS_SIZE = 4 * 8192;
    public static final int MAX_RESPONSE_HEADERS_SIZE = 8192;
    public static final long MAX_TOTAL_TIME = TimeUnit.HOURS.toMillis(24);
}
