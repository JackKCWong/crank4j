package com.danielflower.crank4j.router;

import org.eclipse.jetty.server.*;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Collections;

import static com.danielflower.crank4j.sharedstuff.Constants.MAX_REQUEST_HEADERS_SIZE;
import static com.danielflower.crank4j.sharedstuff.Constants.MAX_RESPONSE_HEADERS_SIZE;

public class RouterApp {
    private static final Logger log = LoggerFactory.getLogger(RouterApp.class);
    public final URI uri;
    private final Server httpServer;
    private Server registrationServer;
    public final URI registerUri;

    public RouterApp(int httpPort, int registrationWebSocketPort) {
        this.uri = URI.create("http://localhost:" + httpPort);
        this.registerUri = URI.create("ws://localhost:" + registrationWebSocketPort);
        httpServer = new Server();
        registrationServer = new Server(new InetSocketAddress(registerUri.getHost(), registerUri.getPort()));
        registrationServer.setStopAtShutdown(true);
    }

    public void start() throws Exception {
        WebSocketFarm webSocketFarm = new WebSocketFarm();

        registrationServer.setStopAtShutdown(true);
        registrationServer.setHandler(websocketHandler(webSocketFarm));
        registrationServer.start();
        log.info("Websocket registration URL started at " + registerUri);

        startServer(httpServer, new ReverseProxy(webSocketFarm));
        log.info("HTTP Server started at " + uri);

    }

    private void startServer(Server httpServer, Handler handler) throws Exception {
        HttpConfiguration config = new HttpConfiguration();
        config.setRequestHeaderSize(MAX_REQUEST_HEADERS_SIZE);
        config.setResponseHeaderSize(MAX_RESPONSE_HEADERS_SIZE);
        config.setSendServerVersion(false);

        ServerConnector connector = new ServerConnector(httpServer);
        connector.setConnectionFactories(Collections.singletonList(new HttpConnectionFactory(config)));
        connector.setPort(uri.getPort());
        connector.setHost(uri.getHost());

        httpServer.setConnectors(new Connector[]{connector});
        httpServer.setStopAtShutdown(true);
        httpServer.setHandler(handler);
        httpServer.start();
    }

    private Handler websocketHandler(WebSocketFarm webSocketFarm) {
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        ServletHolder holderEvents = new ServletHolder("ws-events", new WebSocketConfigurer(webSocketFarm));
        context.addServlet(holderEvents, "/register/*");
        return context;
    }

    public void shutdown() {

    }

}
