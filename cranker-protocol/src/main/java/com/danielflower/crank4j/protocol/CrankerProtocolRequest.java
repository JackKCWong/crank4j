package com.danielflower.crank4j.protocol;

import com.danielflower.crank4j.protocol.utils.Marker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * Request from router to connector
 */
public class CrankerProtocolRequest {
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
	private static final Logger log = LoggerFactory.getLogger(CrankerProtocolRequest.class);
	public String httpMethod;
	public String dest;
	public String[] headers;
	private String endMarker;

	public CrankerProtocolRequest(String msg) {
		if (msg.equals(Marker.REQUEST_BODY_ENDED_MARKER)){
			this.endMarker = msg;
		} else {
			String[] msgArr = msg.split("\n");
			String request = msgArr[0];
			log.debug("request >>> " + request);
			String[] bits = request.split(" ");
			this.httpMethod = bits[0];
			this.dest = bits[1];
			String[] headersArr = Arrays.copyOfRange(msgArr, 1, msgArr.length - 1);
			log.debug("headers >>> " + Arrays.toString(headersArr));
			this.headers = headersArr;
			String marker = msgArr[msgArr.length - 1];
			log.debug("marker >>> " + marker);
			this.endMarker = marker;
		}
	}

	public boolean requestBodyPending() {
		return endMarker.equals(Marker.REQUEST_BODY_PENDING_MARKER);
	}

	public boolean requestBodyEnded() {
		return endMarker.equals(Marker.REQUEST_BODY_ENDED_MARKER);
	}

	public interface RequestCallback {
		void callback();
	}

	public void sendRequestToTarget(RequestCallback sendRequestCallback) {
		if (endMarker.equals(Marker.REQUEST_BODY_PENDING_MARKER) || endMarker.equals(Marker.REQUEST_HAS_NO_BODY_MARKER)) {
			log.debug("Request headers received");
			sendRequestCallback.callback();
			log.debug("Request body fully sent");
		}
	}
}
