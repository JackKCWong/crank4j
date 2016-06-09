package com.danielflower.crank4j.connector;

import com.danielflower.crank4j.sharedstuff.Action;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

public class ConnectorApp {
    private static final Logger log = LoggerFactory.getLogger(ConnectorApp.class);

    private final URI routerURI;
    private final URI targetURI;
    private final WebSocketClient webSocketClient = new WebSocketClient();
    private final HttpClient httpClient = new HttpClient();

    public ConnectorApp(URI routerURI, URI targetURI) {
        this.routerURI = routerURI;
        this.targetURI = targetURI;
    }

    public void start() throws Exception {
        httpClient.setFollowRedirects(false);
        httpClient.setMaxConnectionsPerDestination(1000);

        httpClient.start();
        webSocketClient.start();
        ConnectorSocket socket = new ConnectorSocket(httpClient, targetURI);
        URI registerURI = routerURI.resolve("/register");
        ClientUpgradeRequest cur = new ClientUpgradeRequest();
        webSocketClient.connect(socket, registerURI, cur);
        log.info("Connecting to " + registerURI);
    }

    public void shutdown() {
        Action.silently(webSocketClient::stop);
    }
}
