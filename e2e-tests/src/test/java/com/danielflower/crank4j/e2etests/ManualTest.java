package com.danielflower.crank4j.e2etests;

import com.danielflower.crank4j.connector.ConnectorEntryPoint;
import com.danielflower.crank4j.router.RouterEntryPoint;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scaffolding.FileFinder;

import java.io.IOException;

import static com.danielflower.crank4j.sharedstuff.Dirs.dirPath;

public class ManualTest {
    private static final Logger log = LoggerFactory.getLogger(ManualTest.class);

    public static void main(String[] args) throws IOException {
        System.setProperty("router.keystore.path", dirPath(FileFinder.testFile("keystore.jks")));
        log.info("Starting router");
        RouterEntryPoint.main(config("manual-test-router.properties"));
        log.info("Starting connector");
        ConnectorEntryPoint.main(config("manual-test-connector.properties"));
        log.info("Ready for manual testing");
    }

    private static String[] config(String filename) {
        return new String[] {dirPath(FileFinder.testFile(filename))};
    }

    public static SslContextFactory testSslContextFactory() {
        SslContextFactory ssl = new SslContextFactory();
        ssl.setKeyStorePath(dirPath(FileFinder.testFile("keystore.jks")));
        ssl.setKeyStorePassword("password");
        ssl.setKeyManagerPassword("password");
        return ssl;
    }

}
