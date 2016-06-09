package com.danielflower.crank4j.router;

import org.eclipse.jetty.websocket.servlet.*;

public class WebSocketConfigurer extends WebSocketServlet {
    @Override
    public void configure(WebSocketServletFactory factory) {
        factory.setCreator(new WebSocketCreator() {
            @Override
            public Object createWebSocket(ServletUpgradeRequest servletUpgradeRequest, ServletUpgradeResponse servletUpgradeResponse) {
                return new RouterSocket();
            }
        });
    }
}
