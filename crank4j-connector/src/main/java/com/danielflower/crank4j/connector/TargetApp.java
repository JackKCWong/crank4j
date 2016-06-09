package com.danielflower.crank4j.connector;

import com.danielflower.crank4j.sharedstuff.Action;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

public class TargetApp {
    private static final Logger log = LoggerFactory.getLogger(TargetApp.class);

    private final URI routerURI;
    private final URI targetURI;
    private final WebSocketClient webSocketClient = new WebSocketClient();
    private final HttpClient httpClient = new HttpClient();

    public TargetApp(URI routerURI, URI targetURI) {
        this.routerURI = routerURI;
        this.targetURI = targetURI;
    }

    public void start() throws Exception {
        httpClient.start();
        webSocketClient.start();
        TargetSocket socket = new TargetSocket(httpClient, targetURI);
        URI registerURI = routerURI.resolve("/register");
        ClientUpgradeRequest cur = new ClientUpgradeRequest();
        webSocketClient.connect(socket, registerURI, cur);
        log.info("Connecting to " + registerURI);
    }

    public void shutdown() {
        Action.silently(webSocketClient::stop);
    }
}
