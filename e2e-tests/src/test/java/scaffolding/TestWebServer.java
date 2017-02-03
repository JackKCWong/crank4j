package scaffolding;

import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;
import org.eclipse.jetty.util.resource.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.danielflower.crank4j.sharedstuff.Constants.MAX_REQUEST_HEADERS_SIZE;
import static com.danielflower.crank4j.sharedstuff.Constants.MAX_RESPONSE_HEADERS_SIZE;

public class TestWebServer implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(TestWebServer.class);

    public final URI uri;
    private final Server jettyServer;
    private final List<Handler> testHandlers = new CopyOnWriteArrayList<>();

    public TestWebServer(int port) {
        this.uri = URI.create("http://localhost:" + port);
        jettyServer = new Server();

        HttpConfiguration config = new HttpConfiguration();
        config.setRequestHeaderSize(MAX_REQUEST_HEADERS_SIZE);
        config.setResponseHeaderSize(MAX_RESPONSE_HEADERS_SIZE);

        ServerConnector connector = new ServerConnector(jettyServer);
        connector.setConnectionFactories(Collections.singletonList(new HttpConnectionFactory(config)));
        connector.setPort(uri.getPort());
        connector.setHost(uri.getHost());

        jettyServer.setConnectors(new Connector[]{connector});
        jettyServer.setStopAtShutdown(true);
    }

    public void start() throws Exception {
        HandlerList handlers = new HandlerList();
        handlers.addHandler(getTestHandler());
        handlers.addHandler(noCompressionResourceHandler());
        handlers.addHandler(gzippedResourceHandler());
        jettyServer.setHandler(handlers);
        jettyServer.start();
        log.info("Started at " + uri);
    }

    public void registerHandler(Handler handler) {
        testHandlers.add(handler);
    }

    private AbstractHandler getTestHandler() {
        return new AbstractHandler() {
            @Override
            public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
                for (Handler testHandler : testHandlers) {
                    testHandler.handle(target, baseRequest, request, response);
                    if (baseRequest.isHandled()) {
                        return;
                    }
                }
            }
        };
    }

    private static Handler noCompressionResourceHandler() {
        ResourceHandler resourceHandler = new ResourceHandler();
        resourceHandler.setBaseResource(Resource.newClassPathResource("/web", true, false));
        ContextHandler ctx = new ContextHandler("/static");
        ctx.setHandler(resourceHandler);
        return ctx;
    }
    private static Handler gzippedResourceHandler() {
        ResourceHandler resourceHandler = new ResourceHandler();
        resourceHandler.setBaseResource(Resource.newClassPathResource("/web", true, false));
        GzipHandler gzipHandler = new GzipHandler();
        gzipHandler.setIncludedMimeTypes("text/html", "text/plain", "text/xml",
            "text/css", "application/javascript", "text/javascript");
        gzipHandler.setHandler(resourceHandler);
        ContextHandler ctx = new ContextHandler("/gzipped-static");
        ctx.setHandler(gzipHandler);
        return ctx;
    }

    @Override
    public void close() throws Exception {
        jettyServer.setStopTimeout(1000L);
        jettyServer.stop();
    }
}
