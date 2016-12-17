package com.danielflower.crank4j.router;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.UUID;

public class RequestValidatorHandler extends AbstractHandler {
    private static final Logger log = LoggerFactory.getLogger(RequestValidatorHandler.class);
    @Override
    public void handle(String s, Request request, HttpServletRequest httpServletRequest, HttpServletResponse response) throws IOException, ServletException {
        try {
            URI.create(request.getRequestURI()).normalize();
        } catch (Exception e) {
            String errorID = UUID.randomUUID().toString();
            response.sendError(400, "Invalid request. Error ID " + errorID);
            log.warn("ErrorID=" + errorID + ": invalid URL detected for " + request.getRequestURI());
            request.setHandled(true);
            return;
        }

        String httpMethod = request.getMethod();
        if ("chunked".equalsIgnoreCase(request.getHeader("Transfer-Encoding")) && request.getIntHeader("Content-Length") > 0) {
            response.sendError(400, "Invalid request: chunked request with Content-Length");
            request.setHandled(true);
            return;
        }

        if ("OPTIONS".equalsIgnoreCase(httpMethod)|| "TRACE".equalsIgnoreCase(httpMethod)) {
            response.sendError(405, "Method Not Allowed");
            request.setHandled(true);
            return;
        }

    }
}
