package com.danielflower.crank4j.connector;

import com.danielflower.crank4j.protocol.CrankerProtocolRequest;
import com.danielflower.crank4j.protocol.CrankerProtocolResponseBuilder;
import com.danielflower.crank4j.protocol.HeadersBuilder;
import com.danielflower.crank4j.utils.CancelledException;
import com.danielflower.crank4j.utils.ConnectionMonitor;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.DeferredContentProvider;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.websocket.api.CloseStatus;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.eclipse.jetty.websocket.api.StatusCode.NORMAL;
import static org.eclipse.jetty.websocket.api.StatusCode.SERVER_ERROR;

@WebSocket
public class ConnectorSocket implements WebSocketListener {
	private static final Logger log = LoggerFactory.getLogger(ConnectorSocket.class);
	private static final HttpClient httpClient = ClientFactory.startedHttpClient();
	private final URI targetURI;
	private final URI sourceURI;
	private final ConnectionMonitor connectionMonitor;
	private Session session;
	private Request requestToTarget;
	private Runnable whenConsumedAction;
	private DeferredContentProvider targetRequestContentProvider;
	private ScheduledFuture pingPongTask;
	private volatile boolean requestComplete = false;
	private volatile boolean newSocketAdded = false;
	private static final ScheduledExecutorService pingPongScheduler = Executors.newScheduledThreadPool(1);

	public ConnectorSocket(URI sourceURI, URI targetURI, ConnectionMonitor connectionMonitor) {
		this.sourceURI = sourceURI;
		this.targetURI = targetURI;
		this.connectionMonitor = connectionMonitor;
	}

	public void whenConsumed(Runnable runnable) {
		this.whenConsumedAction = runnable;
	}

	public URI getSourceURI() {
		return this.sourceURI;
	}

	@Override
	public void onWebSocketBinary(byte[] payload, int offset, int len) {
		log.debug("Got binary  - " + len + " bytes");
		boolean added = targetRequestContentProvider.offer(ByteBuffer.wrap(payload, offset, len), new Callback() {
			@Override
			public void succeeded() {
				log.debug("Sent request content to target");
			}

			@Override
			public void failed(Throwable x) {
				log.warn("Error sending request content to target", x);
			}

		});
		if (!added) {
			log.warn("Content was not added! " + new String(payload, offset, len));
		}

	}

	@Override
	public void onWebSocketText(String msg) {
		log.debug("Got message " + msg);
		CrankerProtocolRequest protocolRequest = new CrankerProtocolRequest(msg);
		if (requestToTarget == null) {
			whenConsumedAction.run();
			newSocketAdded = true;
			newRequestToTarget(protocolRequest);
			sendRequestToTarget(protocolRequest);
		} else if (protocolRequest.requestBodyEnded()) {
			log.debug("No further request body coming");
			targetRequestContentProvider.close();
		}
	}

	private void sendRequestToTarget(CrankerProtocolRequest protocolRequest) {
		if (protocolRequest.requestBodyPending()) {
			targetRequestContentProvider = new DeferredContentProvider();
			requestToTarget.content(targetRequestContentProvider);
			log.debug("Request body pending");
		}
		protocolRequest.sendRequestToTarget(
				() -> {
					connectionMonitor.onConnectionStarted();
					requestToTarget.send(result -> {
						if (result.isSucceeded()) {
							requestComplete = true;
							log.debug("Closing websocket because response fully processed");
							connectionMonitor.onConnectionEnded();
							session.close(new CloseStatus(NORMAL, "Proxy complete"));
						} else {
							requestComplete = false;
							if (!(result.getFailure() instanceof CancelledException)) {
								log.warn("Failed for " + result.getResponse(), result.getFailure());
							}
							connectionMonitor.onConnectionEnded();
							if (session != null) {
								session.close(new CloseStatus(SERVER_ERROR, "Proxy failure"));
							}
						}
					});
				});
	}

	private void newRequestToTarget(CrankerProtocolRequest protocolRequest) {
		CrankerProtocolResponseBuilder protocolResponse = CrankerProtocolResponseBuilder.newBuilder();

		URI dest = targetURI.resolve(protocolRequest.dest);
		log.info("Going to " + protocolRequest + " " + dest);
		requestToTarget = httpClient.newRequest(dest)
				.method(protocolRequest.httpMethod);
		requestToTarget = putHeadersTo(requestToTarget, protocolRequest);
		requestToTarget.onResponseBegin(response -> {
			log.debug("Sending status to router " + response.getStatus());
			protocolResponse.withResponseStatus(response.getStatus())
					.withResponseReason(response.getReason());
		})
				.onResponseHeaders(response -> {
					try {
						protocolResponse.withResponseHeaders(parseHeaders(response.getHeaders()));
						session.getRemote().sendString(protocolResponse.build());
					} catch (IOException e) {
						log.warn("Error while sending header back to router", e);
					}
				})
				.onResponseContentAsync(new ResponseBodyPumper(session));
	}

	private HeadersBuilder parseHeaders(HttpFields headerFields) throws IOException {
		HeadersBuilder headers = new HeadersBuilder();
		for (HttpField header : headerFields) {
			String name = header.getName();
			String value = header.getValue();
			if (log.isDebugEnabled())
				log.debug("Sending response header to router " + name + "=" + value);
			headers.appendHeader(name, value);
		}
		return headers;
	}

	private Request putHeadersTo(Request requestToTarget, CrankerProtocolRequest crankerProtocolRequest) {
		for (String line : crankerProtocolRequest.headers) {
			int pos = line.indexOf(':');
			if (pos > 0) {
				String header = line.substring(0, pos);
				String value = line.substring(pos + 1);
				if (log.isDebugEnabled()) log.debug("Target request header " + header + "=" + value);
				requestToTarget.header(header, value);
			}
		}
		requestToTarget.header("Via", "1.1 crnk");
		return requestToTarget;
	}

	@Override
	public void onWebSocketClose(int statusCode, String reason) {
		log.debug("Connection closed: {} - {}", statusCode, reason);
		this.session = null;
		if (pingPongTask != null) {
			pingPongTask.cancel(true);
		}
		if (!newSocketAdded) {
			log.debug("going to reconnect to router. websocket close code: {}", statusCode);
			whenConsumedAction.run();
			newSocketAdded = true;
		}
		if (!requestComplete && requestToTarget != null) {
			if (statusCode != SERVER_ERROR) {
				log.info("The websocket closed before the target response was processed. This may be because the user closed their browser. Going to cancel request to target " + requestToTarget.getURI());
			}
			requestToTarget.abort(new CancelledException("Socket to Router closed"));
		}
	}

	@Override
	public void onWebSocketConnect(Session session) {
		if (log.isDebugEnabled()) log.debug("Connected to " + session.getRemoteAddress());
		this.session = session;
		pingPongTask = pingPongScheduler.scheduleAtFixedRate((Runnable) () -> {
			try {
				session.getRemote().sendPing(ByteBuffer.wrap("*ping*_%".getBytes()));
			} catch (IOException e) {
				log.error("can not send ping to router {}", e);
			}
		}, 0, 10, TimeUnit.MINUTES);
	}

	@Override
	public void onWebSocketError(Throwable cause) {
		log.warn("Websocket error, " + sourceURI + " to " + targetURI, cause.getCause());
		try {
			if (pingPongTask != null) {
				pingPongTask.cancel(true);
			}
			Thread.sleep(500);
		} catch (InterruptedException e) {
			log.error("unexpected exceptions {}", e);
		}
		if (!newSocketAdded) {
			log.debug("going to reconnect to router.");
			whenConsumedAction.run();
			newSocketAdded = true;
		}
	}
}
