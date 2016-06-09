package com.danielflower.crank4j.router;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.AsyncContext;
import javax.servlet.ReadListener;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Enumeration;

class ReverseProxy extends AbstractHandler {
    private static final Logger log = LoggerFactory.getLogger(ReverseProxy.class);

    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {

        while (RouterSocket.instance == null) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        RouterSocket crankedSocket = RouterSocket.instance;
        crankedSocket.setResponse(response);

        log.info("Proxying " + target + " with " + crankedSocket);
        AsyncContext asyncContext = request.startAsync();

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
                    asyncContext.complete();
                }

                @Override
                public void onError(Throwable t) {
                    log.info("Error reading request", t);
                    asyncContext.complete();
                }
            });
        } catch (Exception e) {
            response.sendError(500, e.getMessage());
            asyncContext.complete();
        }

    }
}
