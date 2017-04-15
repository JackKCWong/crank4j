package com.danielflower.crank4j.connector;

import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.websocket.api.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

/**
 * This receives chunks of content from the target server and passes it onto the websocket back to the router.
 * If the response is too large, it sends it back in chunks.
 */
class ResponseBodyPumper implements Response.AsyncContentListener {
    private static final int MAX_MESSAGE_SIZE_IN_BYTES = 32768;
    private static final Logger log = LoggerFactory.getLogger(ResponseBodyPumper.class);
    private final Session session;

    ResponseBodyPumper(Session session) {
        this.session = session;
    }

    @Override
    public void onContent(Response response, ByteBuffer byteBuffer, Callback callback) {

        ByteBuffer responseBytes = ByteBuffer.allocate(byteBuffer.capacity());
        responseBytes.put(byteBuffer);
        responseBytes.position(0);
        int position = 0;

        while (responseBytes.hasRemaining()) {
            int size = responseBytes.capacity() - position;
            responseBytes.limit(Math.min(MAX_MESSAGE_SIZE_IN_BYTES, responseBytes.capacity()));
            log.debug("Sending " + responseBytes.limit() + " bytes to router");
            try {
                // TODO: change this to async writes
                session.getRemote().sendBytes(responseBytes);
            } catch (Exception e) {
                log.warn("Error while sending bytes to router", e);
                callback.failed(e);
                return;
            }
            position += size;
            responseBytes.position(Math.min(position, responseBytes.limit()));
        }
        log.debug("Writing content completed");
        callback.succeeded();
    }
}
