package com.danielflower.crank4j.router;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.UUID;

import static java.util.Arrays.asList;

class ReverseProxy extends AbstractHandler {
    private static final Logger log = LoggerFactory.getLogger(ReverseProxy.class);
    private static final List<String> HOP_BY_HOP_HEADER_FIELDS = /* see https://tools.ietf.org/html/draft-ietf-httpbis-p1-messaging-14#section-7.1.3 */
        asList("Connection", "Keep-Alive", "Proxy-Authenticate", "Proxy-Authorization", "TE", "Trailer", "Transfer-Encoding", "Upgrade");

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
            List<String> connectionHeaders = getConnectionHeaders(request);
            while (headerNames.hasMoreElements()) {
                String header = headerNames.nextElement();
                if (!HOP_BY_HOP_HEADER_FIELDS.contains(header) && !connectionHeaders.contains(header)) {
                    Enumeration<String> values = request.getHeaders(header);
                    while (values.hasMoreElements()) {
                        String value = values.nextElement();
                        crankedSocket.sendText(header + ": " + value + "\r\n");
                    }
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

    private static List<String> getConnectionHeaders(HttpServletRequest request) {
        String connection = request.getHeader("Connection");
        if (connection == null || connection.length() == 0) {
            return Collections.emptyList();
        }
        return asList(connection.split("\\s*,\\s*"));
    }

    private static String createRequestLine(HttpServletRequest request) {
        // Request-Line   = Method SP Request-URI SP HTTP-Version CRLF
        String path = request.getRequestURI();
        String qs = request.getQueryString();
        qs = (qs == null) ? "" : "?" + qs;
        return request.getMethod() + " " + path + qs + " HTTP/1.1\r\n";
    }

}
