package com.danielflower.crank4j.router;

import com.danielflower.crank4j.sharedstuff.Constants;
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
        if ("chunked".equalsIgnoreCase(request.getHeader("Transfer-Encoding")) && request.getIntHeader("Content-Length") > 0) {
            response.sendError(400, "Invalid request: chunked request with Content-Length");
            return;
        }

        AsyncContext asyncContext = baseRequest.startAsync(request, response);
        asyncContext.setTimeout(Constants.MAX_TOTAL_TIME);

        RouterSocket crankedSocket;
        try {
            crankedSocket = webSocketFarm.acquireSocket(target);
        } catch (InterruptedException e) {
            response.sendError(503, "No crankers available");
            baseRequest.setHandled(true);
            return;
        }
        crankedSocket.setResponse(response, asyncContext);
        log.info("Proxying " + target + " to " + crankedSocket.remoteAddress());

        try {
            crankedSocket.sendText(createRequestLine(request));
            List<String> connectionHeaders = Collections.list(request.getHeaders("Connection"));
            Enumeration<String> headerNames = request.getHeaderNames();
            boolean hasContentLength = false, hasTransferEncodingHeader = false;
            while (headerNames.hasMoreElements()) {
                String header = headerNames.nextElement();
                hasContentLength = hasContentLength || header.equalsIgnoreCase("Content-Length");
                hasTransferEncodingHeader = hasTransferEncodingHeader || header.equalsIgnoreCase("Transfer-Encoding");
                if (shouldSendHeader(header, connectionHeaders)) {
                    Enumeration<String> values = request.getHeaders(header);
                    while (values.hasMoreElements()) {
                        String value = values.nextElement();
                        appendHeader(crankedSocket, header, value);
                    }
                }
            }
            addProxyForwardingHeaders(crankedSocket, request);

            crankedSocket.sendText("\r\n");

            if (hasContentLength || hasTransferEncodingHeader) {
                // Stream the body
                ServletInputStream requestInputStream = request.getInputStream();
                int contentLength = request.getIntHeader("Content-Length");
                requestInputStream.setReadListener(new RequestBodyPumper(requestInputStream, crankedSocket, asyncContext, contentLength));
            } else {
                // No request body
                crankedSocket.sendText(Constants.REQUEST_ENDED_MARKER);
            }
        } catch (Exception e) {
            String id = UUID.randomUUID().toString();
            log.error("Error setting up. ErrorID=" + id, e);
            response.sendError(500, "Server ErrorID=" + id);
            asyncContext.complete();
        }

    }

    private void addProxyForwardingHeaders(RouterSocket socketToConnector, HttpServletRequest request) throws IOException {
        String xfor = request.getRemoteAddr();
        String proto = request.getScheme();
        String host = request.getHeader("Host");
        String by = request.getLocalName();
        appendHeader(socketToConnector, "Forwarded", "for=" + xfor + ";proto=" + proto + ";host=" + host + ";by=" + by);
        if (request.getHeader("X-Forwarded-For") == null) {
            appendHeader(socketToConnector, "X-Forwarded-For", xfor);
        }
        if (request.getHeader("X-Forwarded-Proto") == null) {
            appendHeader(socketToConnector, "X-Forwarded-Proto", proto);
        }
        if (request.getHeader("X-Forwarded-Host") == null) {
            appendHeader(socketToConnector, "X-Forwarded-Host", host);
        }
        if (request.getHeader("X-Forwarded-Server") == null) {
            appendHeader(socketToConnector, "X-Forwarded-Server", by);
        }
    }

    private static void appendHeader(RouterSocket crankedSocket, String header, String value) throws IOException {
        crankedSocket.sendText(header + ": " + value + "\r\n");
    }

    private static boolean shouldSendHeader(String headerName, List<String> connectionHeaders) {
        if (HOP_BY_HOP_HEADER_FIELDS.contains(headerName))
            return false;
        if (connectionHeaders.contains(headerName))
            return false;
        return true;
    }

    private static String createRequestLine(HttpServletRequest request) {
        // Request-Line   = Method SP Request-URI SP HTTP-Version CRLF
        String path = request.getRequestURI();
        String qs = request.getQueryString();
        qs = (qs == null) ? "" : "?" + qs;
        return request.getMethod() + " " + path + qs + " HTTP/1.1\r\n";
    }

}
