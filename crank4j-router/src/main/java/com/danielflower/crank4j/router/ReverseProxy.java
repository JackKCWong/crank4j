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

        baseRequest.setHandled(true);
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

            crankedSocket.sendText(createRequestLine(request));
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

            ServletInputStream requestInputStream = request.getInputStream();
            int contentLength = request.getIntHeader("Content-Length");
            requestInputStream.setReadListener(new RequestBodyPumper(requestInputStream, crankedSocket, asyncContext, contentLength));
        } catch (Exception e) {
            String id = UUID.randomUUID().toString();
            log.error("Error setting up. ErrorID=" + id, e);
            response.sendError(500, "Server ErrorID=" + id);
            asyncContext.complete();
        }

    }

    private static String createRequestLine(HttpServletRequest request) {
        // Request-Line   = Method SP Request-URI SP HTTP-Version CRLF
        String path = request.getRequestURI();
        String qs = request.getQueryString();
        qs = (qs == null) ? "" : "?" + qs;
        return request.getMethod() + " " + path + qs + " HTTP/1.1\r\n";
    }

}
