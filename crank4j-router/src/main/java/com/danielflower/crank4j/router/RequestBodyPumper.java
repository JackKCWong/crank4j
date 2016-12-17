package com.danielflower.crank4j.router;

import com.danielflower.crank4j.protocol.CrankerProtocolRequestBuilder;
import com.danielflower.crank4j.utils.ConnectionMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.AsyncContext;
import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import java.io.IOException;

class RequestBodyPumper implements ReadListener {
    private static final Logger log = LoggerFactory.getLogger(RequestBodyPumper.class);
    private final byte[] buffer;
    private final ServletInputStream requestInputStream;
    private final RouterSocket crankedSocket;
    private final AsyncContext asyncContext;
    private final ConnectionMonitor connectionMonitor;

    public RequestBodyPumper(ServletInputStream requestInputStream, RouterSocket crankedSocket, AsyncContext asyncContext, int contentLength, ConnectionMonitor connectionMonitor) {
        this.requestInputStream = requestInputStream;
        this.crankedSocket = crankedSocket;
        this.asyncContext = asyncContext;
        this.connectionMonitor = connectionMonitor;

        int bufferSize = ((contentLength < 1) || (contentLength > 2048)) ? 2048 : contentLength;
        buffer = new byte[bufferSize];
    }

    @Override
    public void onDataAvailable() throws IOException {
        // I wrote this based on discussions at https://github.com/eclipse/jetty.project/issues/489
        while (requestInputStream.isReady()) {
            int read = requestInputStream.read(buffer);
            if (read == -1) {
                return;
            } else {
                log.debug("About to send " + read + " bytes to connector");
                crankedSocket.sendData(buffer, 0, read);
            }
        }
    }

    @Override
    public void onAllDataRead() throws IOException {
        log.info("All request data read");
        String bodyEndedRequestMsg = CrankerProtocolRequestBuilder.newBuilder().withRequestBodyEnded().build();
        crankedSocket.sendText(bodyEndedRequestMsg);
    }

    @Override
    public void onError(Throwable t) {
        log.info("Error reading request", t);
        asyncContext.complete();
        connectionMonitor.onConnectionEnded();
    }
}
