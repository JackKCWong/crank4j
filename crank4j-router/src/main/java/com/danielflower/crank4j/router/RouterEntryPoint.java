package com.danielflower.crank4j.router;

import com.danielflower.crank4j.sharedstuff.Config;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static com.danielflower.crank4j.sharedstuff.Dirs.dirPath;

public class RouterEntryPoint {
    private static final Logger log = LoggerFactory.getLogger(RouterEntryPoint.class);
    public static void main(String[] args) throws IOException {
        Config config = Config.load(args);
        int port = config.getInt("router.port");
        String webServerInterface = config.get("router.interface", "0.0.0.0");
        int registrationPort = config.getInt("router.registration.port");
        String webSocketInterface = config.get("router.registration.interface", "0.0.0.0");
        SslContextFactory sslContextFactory;
        if (config.hasItem("router.keystore.path")) {
            sslContextFactory = new SslContextFactory();
            sslContextFactory.setKeyStorePath(dirPath(config.getFile("router.keystore.path")));
            sslContextFactory.setKeyStorePassword(config.get("router.keystore.password"));
            sslContextFactory.setKeyManagerPassword(config.get("router.keymanager.password"));
        } else {
            sslContextFactory = null;
        }
        try {
            RouterApp app = new RouterApp(port, registrationPort, sslContextFactory, webServerInterface, webSocketInterface);
            app.start();
            Runtime.getRuntime().addShutdownHook(new Thread(app::shutdown));
        } catch (Throwable t) {
            log.error("Error during startup", t);
            System.exit(1);
        }
    }
}
