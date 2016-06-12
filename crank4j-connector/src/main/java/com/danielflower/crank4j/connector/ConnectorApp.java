package com.danielflower.crank4j.connector;

import com.danielflower.crank4j.sharedstuff.Action;
import com.danielflower.crank4j.sharedstuff.Constants;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;

public class ConnectorApp {
    private static final Logger log = LoggerFactory.getLogger(ConnectorApp.class);
    private static final int WEB_SOCKET_BUFFER_SIZE = 100;

    private final URI routerURI;
    private final URI targetURI;
    private final WebSocketClient webSocketClient = new WebSocketClient(new SslContextFactory(true));

    public ConnectorApp(URI routerURI, URI targetURI) {
        this.routerURI = routerURI;
        this.targetURI = targetURI;
    }

    public void start() throws Exception {
        webSocketClient.setMaxBinaryMessageBufferSize(16384);
        webSocketClient.setMaxIdleTimeout(Constants.MAX_TOTAL_TIME);
        webSocketClient.start();
        URI registerURI = routerURI.resolve("/register");
        log.info("Connecting to " + registerURI);
        for (int i = 0; i < WEB_SOCKET_BUFFER_SIZE; i++) {
            connectToRouter(registerURI);
        }
    }

    private void connectToRouter(URI registerURI) throws IOException {
        ConnectorSocket socket = new ConnectorSocket(targetURI);
        socket.whenAcquired(() -> {
            try {
                log.debug("Adding another socket");
                connectToRouter(registerURI);
            } catch (IOException e) {
                log.error("Could not replace socket to " + registerURI);
            }
        });
        ClientUpgradeRequest cur = new ClientUpgradeRequest();
        webSocketClient.connect(socket, registerURI, cur);
    }

    public void shutdown() {
        Action.silently(webSocketClient::stop);
    }
}
