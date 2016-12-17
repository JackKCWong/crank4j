package com.danielflower.crank4j.router;


import com.danielflower.crank4j.protocol.CrankerProtocol;
import com.danielflower.crank4j.utils.ConnectionMonitor;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RouterWebSocketConfigurer extends WebSocketServlet {
    private static final Logger log = LoggerFactory.getLogger(RouterWebSocketConfigurer.class);

    private final WebSocketFarm webSocketFarm;
    private final ConnectionMonitor connectionMonitor;

    public RouterWebSocketConfigurer(WebSocketFarm webSocketFarm, ConnectionMonitor connectionMonitor) {
        this.webSocketFarm = webSocketFarm;
        this.connectionMonitor = connectionMonitor;
    }

    @Override
    public void configure(WebSocketServletFactory factory) {
        factory.setCreator((servletUpgradeRequest, servletUpgradeResponse) -> {
            String crankerProtocolVersion = servletUpgradeRequest.getHeader("CrankerProtocol");
            log.debug("Hi Router, Can you talk to me with Cranker Protocol " + crankerProtocolVersion + "?");
            if (CrankerProtocol.validateCrankerProtocolVersion(crankerProtocolVersion, log)) {
                servletUpgradeResponse.setHeader("CrankerProtocol", CrankerProtocol.CRANKER_PROTOCOL_VERSION_1_0);
                String route = servletUpgradeRequest.getHeader("Route");
                RouterSocket routerSocket = new RouterSocket(route, connectionMonitor, webSocketFarm);
                routerSocket.setOnReadyForAction(() -> webSocketFarm.addWebSocket(route, routerSocket));
                return routerSocket;
            }
            throw new RuntimeException("Failed to establish websocket connection to cranker connector because of not supported cranker protocol version found: " + crankerProtocolVersion);
        });
    }
}
