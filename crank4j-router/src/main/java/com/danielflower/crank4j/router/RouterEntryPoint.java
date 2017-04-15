package com.danielflower.crank4j.router;

import com.danielflower.crank4j.sharedstuff.CertificatesChecker;
import com.danielflower.crank4j.sharedstuff.Config;
import com.danielflower.crank4j.utils.ConnectionMonitor;
import com.timgroup.statsd.NonBlockingStatsDClient;
import com.timgroup.statsd.StatsDClient;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static com.danielflower.crank4j.sharedstuff.Dirs.dirPath;

public class RouterEntryPoint {
    private static final Logger log = LoggerFactory.getLogger(RouterEntryPoint.class);

    public static void main(String[] args) throws IOException {
        Config config = Config.load(args);
        Integer[] ports = config.getIntArray("router.port");
        String webServerInterface = config.get("router.interface", "0.0.0.0");
        int registrationPort = config.getInt("router.registration.port");
        int healthPort = config.getInt("router.health.port");
        String webSocketInterface = config.get("router.registration.interface", "0.0.0.0");
        SslContextFactory sslContextFactory;
        CertificatesChecker certificatesChecker;
        StatsDClient statsDClient = new NonBlockingStatsDClient(config.get("statsd.prefix", "prefix"), config.get("statsd.host", "localhost"), Integer.valueOf(config.get("statsd.port", "8125")));
        ConnectionMonitor.DataPublishHandler dataPublishHandler = (a, b) -> statsDClient.gauge(a, b);
        ConnectionMonitor connectionMonitor = new ConnectionMonitor(new ConnectionMonitor.DataPublishHandler[]{dataPublishHandler});
        if (config.hasItem("router.keystore.path")) {
            sslContextFactory = new SslContextFactory();
            sslContextFactory.setTrustStoreType("JCEKS");
            sslContextFactory.setKeyStoreType("JCEKS");
            sslContextFactory.setKeyStorePath(dirPath(config.getFile("router.keystore.path")));
            sslContextFactory.setKeyStorePassword(config.get("router.keystore.password"));
            sslContextFactory.setKeyManagerPassword(config.get("router.keymanager.password"));
            sslContextFactory.addExcludeProtocols("SSLv3", "SSLv2", "SSLv2Hello");
            sslContextFactory.setExcludeCipherSuites(".*MD5$", ".*RSA.*128.*SHA$");

            certificatesChecker = CertificatesChecker.getCertificatesChecker(dirPath(config.getFile("router.keystore.path")), config.get("router.keystore.password"));
        } else {
            sslContextFactory = null;
            certificatesChecker = null;
        }
        try {
            RouterApp app = new RouterApp(ports, registrationPort, healthPort, sslContextFactory, webServerInterface, webSocketInterface, connectionMonitor, certificatesChecker);
            app.start();
            Runtime.getRuntime().addShutdownHook(new Thread(app::shutdown));
        } catch (Throwable t) {
            log.error("Error during startup", t);
            System.exit(1);
        }
    }
}