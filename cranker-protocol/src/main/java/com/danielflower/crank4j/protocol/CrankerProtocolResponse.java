package com.danielflower.crank4j.protocol;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * Response from connector for router
 */
public class CrankerProtocolResponse {
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
	private int status;
	public String[] headers;

	public CrankerProtocolResponse(String message) {
		String[] messageArr = message.split("\n");
		String[] bits = messageArr[0].split(" ");
		this.status = Integer.parseInt(bits[1]);
		this.headers = Arrays.copyOfRange(messageArr, 1, messageArr.length);
	}

	public int getStatus() {
		return status;
	}
}
