package com.danielflower.crank4j.router;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class WebSocketFarm {
    private static final Logger log = LoggerFactory.getLogger(WebSocketFarm.class);

    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<RouterSocket>> sockets = new ConcurrentHashMap<>();

    public void addWebSocket(RouterSocket socket) {
        String route = "/hello.txt";
        sockets.putIfAbsent(route, new ConcurrentLinkedQueue<>());
        ConcurrentLinkedQueue<RouterSocket> queue = sockets.get(route);
        queue.offer(socket);
        log.info("New socket added for " + route);
    }

    public RouterSocket acquireSocket(String target) throws InterruptedException {
        ConcurrentLinkedQueue<RouterSocket> routerSockets;
        while ((routerSockets = sockets.getOrDefault(target, null)) == null) {
            log.info("Waiting for route to " + target);
            Thread.sleep(500);
        }
        RouterSocket socket;
        while ((socket = routerSockets.poll()) == null) {
            log.info("Waiting for socket to " + target);
            Thread.sleep(500);
        }
        return socket;
    }

}
