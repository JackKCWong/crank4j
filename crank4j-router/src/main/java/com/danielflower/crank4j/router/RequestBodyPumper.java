package com.danielflower.crank4j.router;

import com.danielflower.crank4j.sharedstuff.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.AsyncContext;
import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import java.io.IOException;

class RequestBodyPumper implements ReadListener {
    private static final Logger log = LoggerFactory.getLogger(RequestBodyPumper.class);
    private final byte[] buffer = new byte[2048];
    private final ServletInputStream requestInputStream;
    private final RouterSocket crankedSocket;
    private final AsyncContext asyncContext;

    public RequestBodyPumper(ServletInputStream requestInputStream, RouterSocket crankedSocket, AsyncContext asyncContext) {
        this.requestInputStream = requestInputStream;
        this.crankedSocket = crankedSocket;
        this.asyncContext = asyncContext;
    }

    @Override
    public void onDataAvailable() throws IOException {
        // I wrote this based on discussions at https://github.com/eclipse/jetty.project/issues/489
        while (requestInputStream.isReady()) {
            int read = requestInputStream.read(buffer);
            if (read == -1) {
                return;
            } else {
                log.info("Request data is available to send to the connector: " + read);
                crankedSocket.sendData(buffer, 0, read);
            }
        }
    }

    @Override
    public void onAllDataRead() throws IOException {
        log.info("All request data read");
        crankedSocket.sendText(Constants.REQUEST_ENDED_MARKER);
    }

    @Override
    public void onError(Throwable t) {
        log.info("Error reading request", t);
        asyncContext.complete();
    }
}
