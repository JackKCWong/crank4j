package com.danielflower.crank4j.router;

import org.eclipse.jetty.server.*;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

import static com.danielflower.crank4j.sharedstuff.Action.silently;
import static com.danielflower.crank4j.sharedstuff.Constants.MAX_REQUEST_HEADERS_SIZE;
import static com.danielflower.crank4j.sharedstuff.Constants.MAX_RESPONSE_HEADERS_SIZE;

public class RouterApp {
    private static final Logger log = LoggerFactory.getLogger(RouterApp.class);
    public final URI uri;
    private Server httpServer;
    private final SslContextFactory sslContextFactory;
    private Server registrationServer;
    public final URI registerUri;

    public RouterApp(int httpPort, int registrationWebSocketPort, SslContextFactory sslContextFactory, String webServerInterface, String webSocketInterface) {
        this.sslContextFactory = sslContextFactory;
        String theSecureS = sslContextFactory != null ? "s" : "";
        this.uri = URI.create("http" + theSecureS + "://" + webServerInterface + ":" + httpPort);
        this.registerUri = URI.create("ws" + theSecureS + "://" + webSocketInterface + ":" + registrationWebSocketPort);
    }

    public void start() throws Exception {
        WebSocketFarm webSocketFarm = new WebSocketFarm();

        registrationServer = createAndStartServer(registerUri, websocketHandler(webSocketFarm), new HttpConfiguration(), sslContextFactory);
        log.info("Websocket registration URL started at " + registerUri);

        httpServer = createAndStartHttpServer(uri, new ReverseProxy(webSocketFarm));
        log.info("HTTP Server started at " + uri);
    }

    private Server createAndStartHttpServer(URI uri, Handler handler) throws Exception {
        HttpConfiguration config = new HttpConfiguration();
        config.setRequestHeaderSize(MAX_REQUEST_HEADERS_SIZE);
        config.setResponseHeaderSize(MAX_RESPONSE_HEADERS_SIZE);
        config.setSendServerVersion(false);
        config.setSendDateHeader(false);
        return createAndStartServer(uri, handler, config, sslContextFactory);
    }

    private Server createAndStartServer(URI uri, Handler handler, HttpConfiguration config, SslContextFactory sslContextFactory) throws Exception {
        Server httpServer = new Server();
        httpServer.setStopAtShutdown(true);
        ServerConnector connector = new ServerConnector(httpServer, sslContextFactory, new HttpConnectionFactory(config));
        connector.setPort(uri.getPort());
        connector.setHost(uri.getHost());

        httpServer.setConnectors(new Connector[]{connector});
        httpServer.setStopAtShutdown(true);
        httpServer.setHandler(handler);
        httpServer.start();
        return httpServer;
    }

    private Handler websocketHandler(WebSocketFarm webSocketFarm) {
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        ServletHolder holderEvents = new ServletHolder("ws-events", new WebSocketConfigurer(webSocketFarm));
        context.addServlet(holderEvents, "/register/*");
        return context;
    }

    public void shutdown() {
        silently(httpServer::stop);
        silently(registrationServer::stop);
    }

}
