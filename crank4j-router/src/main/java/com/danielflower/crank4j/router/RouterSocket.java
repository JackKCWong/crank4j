package com.danielflower.crank4j.router;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.ByteBuffer;

public class RouterSocket implements WebSocketListener {
    public static volatile RouterSocket instance = null;

    private static final Logger log = LoggerFactory.getLogger(RouterSocket.class);

    private Session outbound;
    private HttpServletResponse response;
    private boolean statusReceived = false;
    private ServletOutputStream responseOutputStream;

    @Override
    public void onWebSocketClose(int statusCode, String reason) {
        this.outbound = null;
        try {
            responseOutputStream.close();
        } catch (IOException e) {
            log.warn("Can't close client response");
        }
    }

    @Override
    public void onWebSocketConnect(Session session) {
        instance = this;
        this.outbound = session;
    }

    @Override
    public void onWebSocketError(Throwable cause) {
        log.info("WebSocketError", cause);
    }

    @Override
    public void onWebSocketText(String message) {
        log.debug("Got message " + message);
        if (!statusReceived) {
            statusReceived = true;
            String[] bits = message.split(" ");
            int status = Integer.parseInt(bits[1]);
            log.info("Sending status " + status);
            response.setStatus(status);
        } else {
            int pos = message.indexOf(':');
            if (pos > 0) {
                String header = message.substring(0, pos);
                String value = message.substring(pos + 1).trim();
                log.info("Sending " + header + "=" + value);
                response.addHeader(header, value);
            } else {
                log.info("All headers received");
            }
        }
    }

    @Override
    public void onWebSocketBinary(byte[] payload, int offset, int len) {
        log.info("Got binary " + offset +":" + len +": " + new String(payload));
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

    public void setResponse(HttpServletResponse response) throws IOException {
        this.response = response;
        this.responseOutputStream = response.getOutputStream();
    }
}
