package com.danielflower.crank4j.sharedstuff;

import com.danielflower.crank4j.utils.Crank4jException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;

public class Porter {
    private static final Logger log = LoggerFactory.getLogger(Porter.class);

    public static int getAFreePort() {
        try {
            try (ServerSocket serverSocket = new ServerSocket(0)) {
                int localPort = serverSocket.getLocalPort();
                log.info("Chose port " + localPort);
                return localPort;
            }
        } catch (IOException e) {
            throw new Crank4jException("Unable to get a port", e);
        }
    }




}
