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

public class RouterSocket implements WebSocketListener {
    private static final Logger log = LoggerFactory.getLogger(RouterSocket.class);

    private Session outbound;
    private HttpServletResponse response;
    private AsyncContext asyncContext;
    private boolean statusReceived = false;
    private ServletOutputStream responseOutputStream;
    private Runnable onReadyForAction;


    @Override
    public void onWebSocketClose(int statusCode, String reason) {
        log.info("Router side socket closed - ending request");
        this.outbound = null;
        asyncContext.complete();
    }

    @Override
    public void onWebSocketConnect(Session session) {
        this.outbound = session;
        outbound.setIdleTimeout(Long.MAX_VALUE);
        onReadyForAction.run();
    }

    @Override
    public void onWebSocketError(Throwable cause) {
        log.info("WebSocketError", cause);
    }

    @Override
    public void onWebSocketText(String message) {
        log.info("Got message from connector " + message);
        if (!statusReceived) {
            statusReceived = true;
            String[] bits = message.split(" ");
            int status = Integer.parseInt(bits[1]);
            log.info("Client response status " + status);
            response.setStatus(status);
        } else {
            int pos = message.indexOf(':');
            if (pos > 0) {
                String header = message.substring(0, pos);
                if (!header.equals("Content-Length")) {
                    String value = message.substring(pos + 1).trim();
                    log.info("Sending Client response header " + header + "=" + value);
                    response.addHeader(header, value);
                }
            } else {
                response.addHeader("X-Yo-man", "Hi");
                log.info("All headers received");
            }
        }
    }

    @Override
    public void onWebSocketBinary(byte[] payload, int offset, int len) {
        log.info("Got binary " + offset + ":" + len + ": " + new String(payload, offset, len));
        try {
            responseOutputStream.write(payload, offset, len);
        } catch (IOException e) {
            log.warn("Couldn't write to client response", e);
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
}
