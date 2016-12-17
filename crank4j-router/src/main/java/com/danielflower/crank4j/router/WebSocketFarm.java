package com.danielflower.crank4j.router;

import com.danielflower.crank4j.utils.ConnectionMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class WebSocketFarm {
    private static final Logger log = LoggerFactory.getLogger(WebSocketFarm.class);

    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<RouterSocket>> sockets = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<RouterSocket> catchAll = new ConcurrentLinkedQueue<>();
    private final ConnectionMonitor connectionMonitor;

    public WebSocketFarm(ConnectionMonitor connectionMonitor) {
        this.connectionMonitor = connectionMonitor;
    }

    public void removeWebSocket(String route, RouterSocket socket) {
        if(route.equals("")){
        	catchAll.remove(socket);
        }else{
        	sockets.get(route).remove(socket);
        }
    }

    public void addWebSocket(String route, RouterSocket socket) {
        if(route.equals("")){
        	route="*";
        }
        ConcurrentLinkedQueue<RouterSocket> queue;
        if ("*".equals(route)) {
            queue = catchAll;
        } else {
            sockets.putIfAbsent(route, new ConcurrentLinkedQueue<>());
            queue = sockets.get(route);
        }
        queue.offer(socket);
        log.info("addWebSocket=" + route);
    }

    public RouterSocket acquireSocket(String target) throws InterruptedException {
        ConcurrentLinkedQueue<RouterSocket> routerSockets = sockets.getOrDefault(resolveRoute(target), catchAll);
        RouterSocket socket;

        connectionMonitor.reportWebsocketPoolSize(routerSockets.size());

        while ((socket = routerSockets.poll()) == null) {
            log.info("Waiting for socket to " + target);
            Thread.sleep(500);
        }

        return socket;
    }
    
    private String resolveRoute(String target){
    	if(target.split("/").length>=2){
    		return target.split("/")[1];
    	} else {
    	    // It's either root target, or blank target
            return "";
        }
    }

    public ConcurrentLinkedQueue<RouterSocket> getCatchAll() {
        return catchAll;
    }

    public ConcurrentHashMap<String, ConcurrentLinkedQueue<RouterSocket>> getSockets() {
        return sockets;
    }
}
