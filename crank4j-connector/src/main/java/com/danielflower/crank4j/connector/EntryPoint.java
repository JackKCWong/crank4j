package com.danielflower.crank4j.connector;

import com.danielflower.crank4j.sharedstuff.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;

public class EntryPoint {
    private static final Logger log = LoggerFactory.getLogger(EntryPoint.class);
    public static void main(String[] args) throws IOException {
        Config config = Config.load(args);
        URI routerURI = URI.create(config.get("router.uri"));
        URI targetURI = URI.create(config.get("target.uri"));
        try {
            ConnectorApp app = new ConnectorApp(routerURI, targetURI);
            app.start();
            Runtime.getRuntime().addShutdownHook(new Thread(app::shutdown));
        } catch (Throwable t) {
            log.error("Error during startup", t);
            System.exit(1);
        }
    }
}
