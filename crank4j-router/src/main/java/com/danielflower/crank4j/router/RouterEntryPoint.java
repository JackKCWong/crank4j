package com.danielflower.crank4j.router;

import com.danielflower.crank4j.sharedstuff.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class RouterEntryPoint {
    private static final Logger log = LoggerFactory.getLogger(RouterEntryPoint.class);
    public static void main(String[] args) throws IOException {
        Config config = Config.load(args);
        int port = config.getInt("router.port");
        int registrationPort = config.getInt("router.registration.port");
        try {
            RouterApp app = new RouterApp(port, registrationPort);
            app.start();
            Runtime.getRuntime().addShutdownHook(new Thread(app::shutdown));
        } catch (Throwable t) {
            log.error("Error during startup", t);
            System.exit(1);
        }
    }
}
