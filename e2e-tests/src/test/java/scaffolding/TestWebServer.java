package scaffolding;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;
import org.eclipse.jetty.util.resource.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.URI;

public class TestWebServer implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(TestWebServer.class);

    public final URI uri;
    private final Server jettyServer;

    public TestWebServer(int port) {
        this.uri = URI.create("http://localhost:" + port);
        jettyServer = new Server(new InetSocketAddress(uri.getHost(), uri.getPort()));
        jettyServer.setStopAtShutdown(true);
    }

    public void start() throws Exception {
        HandlerList handlers = new HandlerList();
        handlers.addHandler(noCompressionResourceHandler());
        handlers.addHandler(gzippedResourceHandler());
        jettyServer.setHandler(handlers);
        jettyServer.start();
        log.info("Started at " + uri);
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
        jettyServer.stop();
    }
}
