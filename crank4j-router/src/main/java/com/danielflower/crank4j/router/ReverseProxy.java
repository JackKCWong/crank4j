package com.danielflower.crank4j.router;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Enumeration;
import java.util.UUID;

class ReverseProxy extends AbstractHandler {
    private static final Logger log = LoggerFactory.getLogger(ReverseProxy.class);

    private final WebSocketFarm webSocketFarm;

    public ReverseProxy(WebSocketFarm webSocketFarm) {
        this.webSocketFarm = webSocketFarm;
    }

    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {

        AsyncContext asyncContext = baseRequest.startAsync(request, response);

        RouterSocket crankedSocket;
        try {
            crankedSocket = webSocketFarm.acquireSocket(target);
        } catch (InterruptedException e) {
            response.sendError(503, "No crankers available");
            baseRequest.setHandled(true);
            return;
        }
        crankedSocket.setResponse(response, asyncContext);
        log.info("Proxying " + target + " with " + crankedSocket);

        try {

            crankedSocket.sendText(request.getMethod() + " " + request.getRequestURI() + " HTTP/1.1\r\n");
            Enumeration<String> headerNames = request.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String header = headerNames.nextElement();
                Enumeration<String> values = request.getHeaders(header);
                while (values.hasMoreElements()) {
                    String value = values.nextElement();
                    crankedSocket.sendText(header + ": " + value + "\r\n");
                }
            }
            crankedSocket.sendText("\r\n");

            sendInputToConnector(request, crankedSocket, asyncContext);
        } catch (Exception e) {
            String id = UUID.randomUUID().toString();
            log.error("Error setting up. ErrorID=" + id, e);
            response.sendError(500, "Server ErrorID=" + id);
            asyncContext.complete();
        } finally {
            baseRequest.setHandled(true);
        }

    }

    private static void sendInputToConnector(HttpServletRequest request, final RouterSocket crankedSocket, final AsyncContext asyncContext) throws IOException {
        ServletInputStream requestInputStream = request.getInputStream();
        requestInputStream.setReadListener(new ReadListener() {
            @Override
            public void onDataAvailable() throws IOException {
                byte[] buffer = new byte[requestInputStream.available()];
                int read = requestInputStream.read(buffer);
                crankedSocket.sendData(buffer, 0, read);
            }

            @Override
            public void onAllDataRead() throws IOException {
                log.info("All request data read");
            }

            @Override
            public void onError(Throwable t) {
                log.info("Error reading request", t);
                asyncContext.complete();
            }
        });
    }
}
