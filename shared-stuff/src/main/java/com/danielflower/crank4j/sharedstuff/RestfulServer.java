package com.danielflower.crank4j.sharedstuff;

import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.io.Closeable;
import java.io.IOException;
import java.util.logging.LogManager;


public class RestfulServer {
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(RestfulServer.class);

    private final Server jettyServer;
    private final Object[] components;

    public RestfulServer(int port, Object... components) {
        this.components = components;
        makeJavaUtilLoggingRedirectToSlf4j();

        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");

        jettyServer = new Server();
        jettyServer.setHandler(context);
        ServerConnector connector = getServerConnector(jettyServer);
        connector.setPort(port);
        connector.setIdleTimeout(10000);
        jettyServer.addConnector(connector);

        ResourceConfig resourceConfig = new ResourceConfig();
        for (Object component : components) {
            resourceConfig.register(component);
        }

        context.addServlet(new ServletHolder(new ServletContainer(resourceConfig)), "/*");
    }

    private ServerConnector getServerConnector(Server server) {
        HttpConfiguration https = new HttpConfiguration();
        // This needs to be big enough to handle large Auth headers and other posts. The default
        // is 8192 but some auth headers can be much larger for users with lots of AD groups.
        // There are people on the web recommended 64kb so using that.
        https.setRequestHeaderSize(8 * 8192);
        https.setSendServerVersion(false);
        return new ServerConnector(server, new HttpConnectionFactory(https));
    }

    private static void makeJavaUtilLoggingRedirectToSlf4j() {
        LogManager.getLogManager().reset();
        SLF4JBridgeHandler.install();
    }

    public void start() throws Exception {
        jettyServer.start();

        int healthPort = jettyServer.getURI().getPort();
        log.info("RESTful health server started at http://0.0.0.0:" + healthPort + "/health");
    }

    public void stop() {
        try {
            jettyServer.stop();
        } catch (Exception e) {
            log.info("Fail to stop jetty server.");
        }
        for (Object component : components) {
            if (component instanceof Closeable) {
                try {
                    ((Closeable) component).close();
                } catch (IOException e) {
                    log.info("Error while closing " + component, e);
                }
            }
        }
    }
}
