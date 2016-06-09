package com.danielflower.crank4j.router;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.URI;

public class RouterApp {
    private static final Logger log = LoggerFactory.getLogger(RouterApp.class);
    private final URI target;
    public final URI uri;
    private final Server jettyServer;

    public RouterApp(int port, URI target) {
        this.uri = URI.create("http://localhost:" + port);
        this.target = target;
        jettyServer = new Server(new InetSocketAddress(uri.getHost(), uri.getPort()));
        jettyServer.setStopAtShutdown(true);
    }

    public void start() throws Exception {
        HandlerList handlers = new HandlerList();
        HttpClient client = new HttpClient();
        client.start();
        handlers.addHandler(new ReverseProxy(client, target));
        jettyServer.setHandler(handlers);
        jettyServer.start();
        log.info("Started at " + uri);
    }

    public void shutdown() {

    }

}
