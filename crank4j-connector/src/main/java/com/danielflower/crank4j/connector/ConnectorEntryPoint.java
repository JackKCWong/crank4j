package com.danielflower.crank4j.connector;

import com.danielflower.crank4j.sharedstuff.Config;
import com.danielflower.crank4j.utils.ConnectionMonitor;
import com.timgroup.statsd.NonBlockingStatsDClient;
import com.timgroup.statsd.StatsDClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;

public class ConnectorEntryPoint {
    private static final Logger log = LoggerFactory.getLogger(ConnectorEntryPoint.class);
    public static void main(String[] args) throws IOException {
        Config config = Config.load(args);
        String[] URIs = config.get("router.uri").split(",");
        URI[] routerURIs = new URI[URIs.length];
        for(int i=0; i<URIs.length;i++)
        	routerURIs[i] = URI.create(URIs[i]);
        URI targetURI = URI.create(config.get("target.uri"));
        int healthPort = config.getInt("connector.health.port");
        String targetServiceName = config.get("target.service.name");
        String socketSize = config.get("connector.pool.size");

        StatsDClient statsDClient = new NonBlockingStatsDClient(config.get("statsd.prefix", "prefix"), config.get("statsd.host", "localhost"), Integer.valueOf(config.get("statsd.port", "8125")));
        ConnectionMonitor.DataPublishHandler dataPublishHandler = (a, b) -> statsDClient.gauge(a, b);
        try {
            ConnectorApp app = new ConnectorApp(routerURIs, targetURI, targetServiceName, healthPort, Integer.valueOf(socketSize), new ConnectionMonitor.DataPublishHandler[]{dataPublishHandler});
            app.start();
            Runtime.getRuntime().addShutdownHook(new Thread(app::shutdown));
        } catch (Throwable t) {
            log.error("Error during startup", t);
            System.exit(1);
        }
    }
}
