package com.danielflower.crank4j.protocol;

import com.danielflower.crank4j.protocol.utils.Marker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Request from router to connector
 */
public class CrankerProtocolRequestBuilder {
	/**
	 * CRANKER_PROTOCOL_VERSION_1_0
	 * request msg format:
	 *
	 * ======msg without body========
	 *** GET /some/path HTTP/1.1\n
	 *** [headers]\n
	 *** \n
	 *** endmarker
	 *
	 *
	 * OR
	 *
	 * =====msg with body part 1=======
	 *** GET /some/path HTTP/1.1\n
	 *** [headers]\n
	 *** \n
	 *** endmarker
	 * =====msg with body part 2=========
	 *** [BINRAY BODY]
	 * =====msg with body part 3=======
	 *** endmarker
	 */
	private String requestLine;
	private HeadersBuilder headers;
	private String endMarker;

	public static CrankerProtocolRequestBuilder newBuilder() {
		return new CrankerProtocolRequestBuilder();
	}

	public CrankerProtocolRequestBuilder withRequestLine(String requestLine) {
		this.requestLine = requestLine;
		return this;
	}

	public CrankerProtocolRequestBuilder withRequestHeaders(HeadersBuilder headers) throws IOException {
		this.headers = headers;
		return this;
	}

	public CrankerProtocolRequestBuilder withRequestBodyPending() throws IOException {
		this.endMarker = Marker.REQUEST_BODY_PENDING_MARKER;
		return this;
	}

	public CrankerProtocolRequestBuilder withRequestHasNoBody() throws IOException {
		this.endMarker = Marker.REQUEST_HAS_NO_BODY_MARKER;
		return this;
	}

	public CrankerProtocolRequestBuilder withRequestBodyEnded() throws IOException {
		this.endMarker = Marker.REQUEST_BODY_ENDED_MARKER;
		return this;
	}

	public String build() {
		if (requestLine !=null && headers != null) {
			return requestLine + "\n" + headers.toString() + "\n" + this.endMarker;
		} else {
			return this.endMarker;
		}
	}
}
