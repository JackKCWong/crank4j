package com.danielflower.crank4j.protocol;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CrankerProtocol {
	public static final String CRANKER_PROTOCOL_VERSION_1_0 = "1.0";
	public static final String SUPPORTING_HTTP_VERSION_1_1 = "HTTP/1.1";
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
	 *** [BINARY BODY]
	 * =====msg with body part 3=======
	 *** endmarker
	 *
	 *
	 * response msg format:
	 *
	 *** HTTP/1.1 200 OK\n
	 *** [headers]\n
	 *** \n
	 */

	public static boolean validateCrankerProtocolVersion(String version, Logger log) {
		if (version == null) {
			throw new CrankerProtocolVersionNotFoundException("version is null");
		} else if (!version.equals(CRANKER_PROTOCOL_VERSION_1_0)) {
			throw new CrankerProtocolVersionNotSupportedException("cannot support cranker protocol version: " + version);
		} else {
			log.debug("I can establish connection with Cranker Protocol " + version + ", currently support " + SUPPORTING_HTTP_VERSION_1_1);
			return true;
		}
	}

	public static class CrankerProtocolVersionNotSupportedException  extends RuntimeException {
		public CrankerProtocolVersionNotSupportedException (String reason) {
			super(reason);
		}
	}

	static class CrankerProtocolVersionNotFoundException extends RuntimeException {
		public CrankerProtocolVersionNotFoundException (String reason) {
			super(reason);
		}
	}
}
