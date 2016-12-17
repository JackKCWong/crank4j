package com.danielflower.crank4j.protocol;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Response from connector to router
 */
public class CrankerProtocolResponseBuilder {
	/**
	 * CRANKER_PROTOCOL_VERSION_1_0
	 *
	 * response msg format:
	 *
	 * =====Part 1===================
	 *** HTTP/1.1 200 OK\n
	 *** [headers]\n
	 *** \n
	 * ===== Part 2 (if msg with body)======
	 * **Binary Content
	 */
	private static final String HTTP1_1 = "HTTP/1.1";
	private int status;
	private String reason;
	private HeadersBuilder headers;

	public static CrankerProtocolResponseBuilder newBuilder() {
		return new CrankerProtocolResponseBuilder();
	}

	public CrankerProtocolResponseBuilder withResponseStatus(int status) {
		this.status = status;
		return this;
	}

	public CrankerProtocolResponseBuilder withResponseReason(String reason) {
		this.reason = reason;
		return this;
	}

	public CrankerProtocolResponseBuilder withResponseHeaders(HeadersBuilder headers) throws IOException {
		this.headers = headers;
		return this;
	}

	public String build() {
		return HTTP1_1 + " " + status + " " + reason + "\n" + headers.toString();
	}
}
