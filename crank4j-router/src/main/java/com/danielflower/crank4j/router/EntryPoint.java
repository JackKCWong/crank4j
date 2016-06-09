package com.danielflower.crank4j.router;

import com.danielflower.crank4j.sharedstuff.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;

public class EntryPoint {
    private static final Logger log = LoggerFactory.getLogger(EntryPoint.class);
    public static void main(String[] args) throws IOException {
        Config config = Config.load(args);
        int port = config.getInt("router.port");
        URI target = URI.create(config.get("router.target"));
        try {
            RouterApp app = new RouterApp(port, target);
            app.start();
            Runtime.getRuntime().addShutdownHook(new Thread(app::shutdown));
        } catch (Throwable t) {
            log.error("Error during startup", t);
            System.exit(1);
        }
    }
}
