package com.danielflower.crank4j.router;

import com.danielflower.crank4j.sharedstuff.CertificatesChecker;
import com.danielflower.crank4j.sharedstuff.HealthServiceHandler;
import com.danielflower.crank4j.sharedstuff.RestfulServer;
import com.danielflower.crank4j.utils.ConnectionMonitor;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static com.danielflower.crank4j.sharedstuff.Constants.MAX_REQUEST_HEADERS_SIZE;
import static com.danielflower.crank4j.sharedstuff.Constants.MAX_RESPONSE_HEADERS_SIZE;
import static com.danielflower.crank4j.utils.Action.swallowException;

public class RouterApp {
    private static final Logger log = LoggerFactory.getLogger(RouterApp.class);

    public final List<URI> uri = new ArrayList<>();
    private Server httpServer;
    private final SslContextFactory sslContextFactory;
    private Server registrationServer;
    public final URI registerUri;
    private final ConnectionMonitor connectionMonitor;
    private final RouterHealthService healthService;
    private final RestfulServer server;
    public final URI healthUri;
    private WebSocketFarm webSocketFarm;

    public RouterApp(Integer[] httpPorts, int registrationWebSocketPort, int healthPort,
                     SslContextFactory sslContextFactory, String webServerInterface,
                     String webSocketInterface, ConnectionMonitor connectionMonitor,
                     CertificatesChecker certificatesChecker) {
        this.sslContextFactory = sslContextFactory;
        this.connectionMonitor = connectionMonitor;
        String theSecureS = sslContextFactory != null ? "s" : "";
        for (Integer port : httpPorts) {
            this.uri.add(URI.create("http" + theSecureS + "://" + webServerInterface + ":" + port));
        }
        this.registerUri = URI.create("ws" + theSecureS + "://" + webSocketInterface + ":" + registrationWebSocketPort);
        this.webSocketFarm = new WebSocketFarm(connectionMonitor);
        this.healthService = new RouterHealthService(connectionMonitor, certificatesChecker, webSocketFarm);
        this.server = new RestfulServer(healthPort, new HealthServiceHandler(healthService));
        this.healthUri = URI.create("http://" + webServerInterface + ":" + healthPort + "/health");
        try {
            RouterHealthService.registerMBean(healthService);
        } catch (Exception e) {
            log.warn("Cannot create Health Bean " + e.toString());
        }
    }

    public void start() throws Exception {
        List<URI> registerUris = new ArrayList<>();
        registerUris.add(registerUri);
        registrationServer = createAndStartServer(registerUris, websocketHandler(webSocketFarm), new HttpConfiguration(), sslContextFactory);
        log.info("Websocket registration URL started at " + registerUri);

        HandlerList handlerList = new HandlerList();
        handlerList.addHandler(new RequestValidatorHandler());
        handlerList.addHandler(new ReverseProxy(webSocketFarm, connectionMonitor));
        httpServer = createAndStartHttpServer(uri, handlerList);
        for (URI u : uri) {
            log.info("HTTP Server started at " + u);
        }

        // Start RESTful health service
        server.start();
        healthService.scheduleHealthCheck();
    }

    private Server createAndStartHttpServer(List<URI> uri, Handler handler) throws Exception {
        HttpConfiguration config = new HttpConfiguration();
        config.setRequestHeaderSize(MAX_REQUEST_HEADERS_SIZE);
        config.setResponseHeaderSize(MAX_RESPONSE_HEADERS_SIZE);
        config.addCustomizer(new SecureRequestCustomizer());
        return createAndStartServer(uri, handler, config, sslContextFactory);
    }

    private Server createAndStartServer(List<URI> uri, Handler handler, HttpConfiguration config, SslContextFactory sslContextFactory) throws Exception {
        config.setSendDateHeader(false);
        config.setSendServerVersion(false);
        Server httpServer = new Server();
        httpServer.setStopAtShutdown(true);
        for (URI u : uri) {
            /**
             * This means the router can only support HTTP 1.1 request coming from the Internet.
             */
            ServerConnector connector = new ServerConnector(httpServer, new SslConnectionFactory(sslContextFactory, "HTTP/1.1"), new HttpConnectionFactory(config));
            connector.setPort(u.getPort());
            httpServer.addConnector(connector);
        }
        httpServer.setStopAtShutdown(true);
        httpServer.setHandler(handler);
        httpServer.start();
        return httpServer;
    }

    private Handler websocketHandler(WebSocketFarm webSocketFarm) {
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
	    Constraint constraint = new Constraint();
	    constraint.setName("Disable TRACE");
	    constraint.setAuthenticate(true);

	    ConstraintMapping mapping = new ConstraintMapping();
	    mapping.setConstraint(constraint);
	    mapping.setMethod("TRACE");
	    mapping.setPathSpec("/"); // this did not work same this mapping.setPathSpec("/*");

	    ConstraintSecurityHandler securityHandler = new ConstraintSecurityHandler();
	    securityHandler.addConstraintMapping(mapping);
	    context.setSecurityHandler(securityHandler);
        ServletHolder holderEvents = new ServletHolder("ws-events", new RouterWebSocketConfigurer(webSocketFarm, connectionMonitor));
        context.addServlet(holderEvents, "/register/*");
        return context;
    }

    public void shutdown() {
        swallowException(server::stop);
        swallowException(httpServer::stop);
        swallowException(registrationServer::stop);
    }

}