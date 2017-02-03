package com.danielflower.crank4j.router;

import com.danielflower.crank4j.protocol.CrankerProtocolResponse;
import com.danielflower.crank4j.utils.ConnectionMonitor;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.AsyncContext;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import static com.danielflower.crank4j.utils.Action.swallowException;
import static java.util.Arrays.asList;
import static javax.servlet.http.HttpServletResponse.SC_BAD_GATEWAY;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static org.eclipse.jetty.websocket.api.StatusCode.SERVER_ERROR;
import static org.eclipse.jetty.websocket.api.StatusCode.POLICY_VIOLATION;

public class RouterSocket implements WebSocketListener {
	private static final Logger log = LoggerFactory.getLogger(RouterSocket.class);

	private Session socketToConnector;
	private HttpServletResponse response;
	private AsyncContext asyncContext;
	private ServletOutputStream responseOutputStream;
	private Runnable onReadyForAction;
	private String remoteAddress;
	private ConnectionMonitor connectionMonitor;
	private WebSocketFarm webSocketFarm;
	private String route;
	public static List<String> RESPONSE_HEADERS_TO_NOT_SEND_BACK = asList("content-length", "server");

	public RouterSocket(String route, ConnectionMonitor connectionMonitor, WebSocketFarm webSocketFarm) {
		this.connectionMonitor = connectionMonitor;
		this.webSocketFarm = webSocketFarm;
		this.route = route;
	}


	@Override
	public void onWebSocketClose(int statusCode, String reason) {
		log.debug("Router side socket closed. statusCode=" + statusCode + ", reason: " + reason);
		this.socketToConnector = null;
        if (statusCode == SERVER_ERROR) {
            response.setStatus(SC_BAD_GATEWAY, "Bad Gateway");
            log.debug("Client response is " + response);
        } else if (statusCode == POLICY_VIOLATION) {
	        response.setStatus(SC_BAD_REQUEST, "Bad Request");
	        log.debug("Client response is " + response);
        }
		if (asyncContext != null) {
			try {
                asyncContext.complete();
			} catch (IllegalStateException e) {
				log.info("Tried to complete a request, but it is probably already closed.", e);
			} finally {
				connectionMonitor.onConnectionEnded();
			}
		}
		webSocketFarm.removeWebSocket(route, this);
	}

	@Override
	public void onWebSocketConnect(Session session) {
		this.socketToConnector = session;
		remoteAddress = session.getRemoteAddress().toString();
		socketToConnector.setIdleTimeout(Long.MAX_VALUE);
		onReadyForAction.run();
	}

	@Override
	public void onWebSocketError(Throwable cause) {
		log.info("WebSocketError", cause);
	}

	@Override
	public void onWebSocketText(String message) {
		log.debug("Router get the response message >>> " + message);
		CrankerProtocolResponse protocolResponse = new CrankerProtocolResponse(message);
		log.debug("Client response status " + protocolResponse.getStatus());
		response.setStatus(protocolResponse.getStatus());
		response = putHeadersTo(protocolResponse);
		log.debug("All headers received");
	}

	@Override
	public void onWebSocketBinary(byte[] payload, int offset, int len) {
		if (len == 0) {
			log.warn("Recieved 0 bytes to send to " + remoteAddress + " - " + response);
		} else {
			log.debug("Sending " + len + " bytes to client");
			try {
				responseOutputStream.write(payload, offset, len);
				responseOutputStream.flush();
			} catch (Exception e) {
				log.info("Couldn't write to client response (maybe the user closed their browser) so will cancel request. Error message: " + e.getMessage());
				swallowException(responseOutputStream::close);
				swallowException(socketToConnector::close);
			}
		}
	}

	public void sendText(String message) throws IOException {
		socketToConnector.getRemote().sendString(message);
	}

	public void sendData(byte[] buffer, int offset, int len) throws IOException {
		socketToConnector.getRemote().sendBytes(ByteBuffer.wrap(buffer, offset, len));
	}

	public void setResponse(HttpServletResponse response, AsyncContext asyncContext) throws IOException {
		this.response = response;
		this.asyncContext = asyncContext;
		this.responseOutputStream = response.getOutputStream();
	}

	public void setOnReadyForAction(Runnable onReadyForAction) {
		this.onReadyForAction = onReadyForAction;
	}

	public String remoteAddress() {
		return remoteAddress;
	}

	private HttpServletResponse putHeadersTo(CrankerProtocolResponse protocolResponse) {
		for (String line : protocolResponse.headers) {
			int pos = line.indexOf(':');
			if (pos > 0) {
				String header = line.substring(0, pos);
				if (!RESPONSE_HEADERS_TO_NOT_SEND_BACK.contains(header.toLowerCase())) {
					String value = line.substring(pos + 1);
					if (log.isDebugEnabled()) log.debug("Sending Client response header " + header + "=" + value);
					response.addHeader(header, value);
				}
			}
		}
		response.addHeader("Via", "1.1 crnk");
		return response;
	}
}
