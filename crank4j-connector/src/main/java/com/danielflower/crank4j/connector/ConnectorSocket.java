package com.danielflower.crank4j.connector;

import com.danielflower.crank4j.sharedstuff.Constants;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.DeferredContentProvider;
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

@WebSocket
public class ConnectorSocket implements WebSocketListener {
    private static final Logger log = LoggerFactory.getLogger(ConnectorSocket.class);

    private final HttpClient httpClient = ClientFactory.startedHttpClient();
    private final URI targetURI;


    private Session session;
    private Request requestToTarget;
    private Runnable whenAcquiredAction;
    private DeferredContentProvider targetRequestContentProvider;

    public ConnectorSocket(URI targetURI) {
        this.targetURI = targetURI;
    }

    public void whenAcquired(Runnable runnable) {
        this.whenAcquiredAction = runnable;
    }

    @Override
    public void onWebSocketBinary(byte[] payload, int offset, int len) {
        log.info("Got binary  - " + len + " bytes");
        boolean added = targetRequestContentProvider.offer(ByteBuffer.wrap(payload, offset, len), new Callback() {
            @Override
            public void succeeded() {
                log.info("Sent request content to target");
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
        if (requestToTarget == null) {
            whenAcquiredAction.run();
            String[] bits = msg.split(" ");
            URI dest = targetURI.resolve(bits[1]);
            String method = bits[0];
            log.info("Going to " + method + " " + dest);
            targetRequestContentProvider = new DeferredContentProvider();
            requestToTarget = httpClient.newRequest(dest)
                .method(method)
                .content(targetRequestContentProvider)
                .onResponseBegin(response -> {
                    try {
                        log.info("Sending status to router " + response.getStatus());
                        session.getRemote().sendString("HTTP/1.1 " + response.getStatus() + " " + response.getReason() + "\r\n");
                    } catch (IOException e) {
                        log.warn("Uh oh", e);
                        // TODO: close stuff?
                    }
                })
                .onResponseHeader((response, header) -> {
                    try {
                            String name = header.getName();
                            String value = header.getValue(); // header.getValues() breaks dates
                            log.info("Sending response header to router " + name + "=" + value);
                            session.getRemote().sendString(name + ": " + value + "\r\n");
                        session.getRemote().sendString("\r\n");
                    } catch (IOException e) {
                        log.warn("Oh on", e);
                    }
                    return true;
                })
                .onResponseContentAsync(new ResponseBodyPumper(session));
        } else if (msg.equals(Constants.REQUEST_ENDED_MARKER)) {
            targetRequestContentProvider.close();
            log.info("Request body fully sent");
        } else {
            int pos = msg.indexOf(':');
            if (pos > 0) {
                String header = msg.substring(0, pos);
                String value = msg.substring(pos + 1).trim();
                log.info("Target request header " + header + "=" + value);
                requestToTarget.header(header, value);
            } else {
                log.info("Request headers received");
                requestToTarget.send(result -> {
                    if (result.isSucceeded()) {
                        log.info("Closing websocket because response fully processed");
                        session.close(new CloseStatus(1000, "Proxy complete"));
                    } else {
                        log.warn("Failed for " + result.getResponse(), result.getFailure() + " for " + targetURI);
                        session.close(new CloseStatus(1011, result.getFailure().getMessage()));
                    }
                });
            }
        }

    }

    @Override
    public void onWebSocketClose(int statusCode, String reason) {
        System.out.printf("Connection closed: %d - %s%n", statusCode, reason);
        this.session = null;
    }

    @Override
    public void onWebSocketConnect(Session session) {
        log.info("Connected on " + session);
        this.session = session;
    }

    @Override
    public void onWebSocketError(Throwable cause) {
        log.warn("Websocket error detected for " + targetURI, cause);
    }
}
