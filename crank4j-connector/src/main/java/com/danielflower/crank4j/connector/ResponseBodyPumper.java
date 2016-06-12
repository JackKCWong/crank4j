package com.danielflower.crank4j.connector;

import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WriteCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
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
            session.getRemote().sendBytes(responseBytes, new WriteCallback() {
                @Override
                public void writeFailed(Throwable throwable) {
                    log.info("Failed to send response data", throwable);
                    callback.failed(throwable);
                }

                @Override
                public void writeSuccess() {
                    callback.succeeded();
                }
            });
            position += size;
            responseBytes.position(Math.min(position, responseBytes.limit()));
            // TODO should the next loop happen only when the callback has completed? probably...?
        }
        try {
            session.getRemote().flush();
        } catch (IOException e) {
            log.warn("Error while flushing target response data to router", e);
        }
    }
}
