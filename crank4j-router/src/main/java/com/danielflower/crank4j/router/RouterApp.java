package com.danielflower.crank4j.router;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.URI;

public class RouterApp {
    private static final Logger log = LoggerFactory.getLogger(RouterApp.class);
    public final URI uri;
    private Server httpServer;
    private Server registrationServer;
    public final URI registerUri;

    public RouterApp(int httpPort, int registrationWebSocketPort) {
        this.uri = URI.create("http://localhost:" + httpPort);
        this.registerUri = URI.create("ws://localhost:" + registrationWebSocketPort);
        httpServer = new Server(new InetSocketAddress(uri.getHost(), uri.getPort()));
        httpServer.setStopAtShutdown(true);
        registrationServer = new Server(new InetSocketAddress(registerUri.getHost(), registerUri.getPort()));
        registrationServer.setStopAtShutdown(true);
    }

    public void start() throws Exception {
        startServer(registrationServer, websocketHandler());
        log.info("Websocket registration URL started at " + registerUri);
        startServer(httpServer, new ReverseProxy());
        log.info("HTTP Server started at " + uri);

    }

    private static void startServer(Server httpServer, Handler handler) throws Exception {
        HandlerList handlers = new HandlerList();
        handlers.addHandler(handler);
        httpServer.setHandler(handlers);
        httpServer.start();
    }

    private Handler websocketHandler() {
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        ServletHolder holderEvents = new ServletHolder("ws-events", new WebSocketConfigurer());
        context.addServlet(holderEvents, "/register/*");
        return context;
    }

    public void shutdown() {

    }

}
