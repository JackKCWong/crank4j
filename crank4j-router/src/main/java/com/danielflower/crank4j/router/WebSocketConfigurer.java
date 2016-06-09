package com.danielflower.crank4j.router;

import org.eclipse.jetty.websocket.servlet.*;

public class WebSocketConfigurer extends WebSocketServlet {

    private final WebSocketFarm webSocketFarm;

    public WebSocketConfigurer(WebSocketFarm webSocketFarm) {
        this.webSocketFarm = webSocketFarm;
    }

    @Override
    public void configure(WebSocketServletFactory factory) {
        factory.setCreator(new WebSocketCreator() {
            @Override
            public Object createWebSocket(ServletUpgradeRequest servletUpgradeRequest, ServletUpgradeResponse servletUpgradeResponse) {
                RouterSocket routerSocket = new RouterSocket();
                routerSocket.setOnReadyForAction(() -> webSocketFarm.addWebSocket(routerSocket));
                return routerSocket;
            }
        });
    }
}
