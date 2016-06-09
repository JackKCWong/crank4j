package com.danielflower.crank4j.router;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.util.InputStreamContentProvider;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;

class ReverseProxy extends AbstractHandler {
    private static final Logger log = LoggerFactory.getLogger(ReverseProxy.class);

    private final HttpClient client;
    private final URI targetServer;

    ReverseProxy(HttpClient client, URI target) {
        this.client = client;
        this.targetServer = target;
    }

    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        log.info("Proxying " + target);
        AsyncContext asyncContext = request.startAsync();

        PrintWriter outputStream = response.getWriter();
        try {
            ContentResponse resp = client.newRequest(targetServer.resolve(target))
                .method(request.getMethod())
                .content(new InputStreamContentProvider(request.getInputStream()))
                .send();
            response.setStatus(resp.getStatus());
            outputStream.append(resp.getContentAsString());
            outputStream.close();
        } catch (Exception e) {
            response.sendError(500, e.getMessage());
        } finally {
            asyncContext.complete();
        }

    }
}
