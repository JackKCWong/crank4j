package com.danielflower.crank4j.router;

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

import static java.util.Arrays.asList;

public class RouterSocket implements WebSocketListener {
    private static final Logger log = LoggerFactory.getLogger(RouterSocket.class);
    public static List<String> RESPONSE_HEADERS_TO_NOT_SEND_BACK = asList("content-length", "server");

    private Session outbound;
    private HttpServletResponse response;
    private AsyncContext asyncContext;
    private boolean statusReceived = false;
    private ServletOutputStream responseOutputStream;
    private Runnable onReadyForAction;
    private String remoteAddress;


    @Override
    public void onWebSocketClose(int statusCode, String reason) {
        log.debug("Router side socket closed - ending request");
        this.outbound = null;
        try {
            asyncContext.complete();
        } catch (IllegalStateException e) {
            log.info("Tried to complete a request, but it is probably already closed.", e);
        }
    }

    @Override
    public void onWebSocketConnect(Session session) {
        this.outbound = session;
        remoteAddress = session.getRemoteAddress().toString();
        outbound.setIdleTimeout(Long.MAX_VALUE);
        onReadyForAction.run();
    }

    @Override
    public void onWebSocketError(Throwable cause) {
        log.info("WebSocketError", cause);
    }

    @Override
    public void onWebSocketText(String message) {
        if (!statusReceived) {
            statusReceived = true;
            String[] bits = message.split(" ");
            int status = Integer.parseInt(bits[1]);
            log.debug("Client response status " + status);
            response.setStatus(status);
        } else {
            int pos = message.indexOf(':');
            if (pos > 0) {
                String header = message.substring(0, pos);
                if (!RESPONSE_HEADERS_TO_NOT_SEND_BACK.contains(header.toLowerCase())) {
                    String value = message.substring(pos + 1).trim();
                    if (log.isDebugEnabled()) log.debug("Sending Client response header " + header + "=" + value);
                    response.addHeader(header, value);
                }
            } else {
                response.addHeader("Via", "1.1 crnk");
                log.debug("All headers received");
            }
        }
    }

    @Override
    public void onWebSocketBinary(byte[] payload, int offset, int len) {
        if (len == 0) {
            log.warn("Recieved 0 bytes to send to " + remoteAddress + " - " + response);
        } else {
            log.debug("Sending " + len + " bytes to client");
            try {
                responseOutputStream.write(payload, offset, len);
            } catch (IOException e) {
                log.warn("Couldn't write to client response", e);
            }
        }
    }

    public void sendText(String message) throws IOException {
        outbound.getRemote().sendString(message);
    }

    public void sendData(byte[] buffer, int offset, int len) throws IOException {
        outbound.getRemote().sendBytes(ByteBuffer.wrap(buffer, offset, len));
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
}
