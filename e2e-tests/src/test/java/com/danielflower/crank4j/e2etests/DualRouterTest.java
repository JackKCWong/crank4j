package com.danielflower.crank4j.e2etests;

import com.danielflower.crank4j.connector.ClientFactory;
import com.danielflower.crank4j.connector.ConnectorApp;
import com.danielflower.crank4j.router.RouterApp;
import com.danielflower.crank4j.sharedstuff.CertificatesChecker;
import com.danielflower.crank4j.sharedstuff.Porter;
import com.danielflower.crank4j.utils.ConnectionMonitor;
import com.timgroup.statsd.NonBlockingStatsDClient;
import com.timgroup.statsd.StatsDClient;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scaffolding.FileFinder;
import scaffolding.SSLUtilities;
import scaffolding.TestWebServer;

import java.net.URI;
import java.util.Collections;

import static com.danielflower.crank4j.utils.Action.swallowException;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class DualRouterTest {

    private static final HttpClient client = ClientFactory.startedHttpClient();
    private static final ContextualizedWebServer targetServer1 = new ContextualizedWebServer(Porter.getAFreePort(), "/a");
    private static final ContextualizedWebServer targetServer2 = new ContextualizedWebServer(Porter.getAFreePort(), "/b");
    private static final ContextualizedWebServer targetServer3= new ContextualizedWebServer(Porter.getAFreePort(), "/a");
    private static final ContextualizedWebServer targetServer4= new ContextualizedWebServer(Porter.getAFreePort(), "");
    private static final SslContextFactory sslContextFactory = ManualTest.testSslContextFactory();

    private static StatsDClient statsDClient = new NonBlockingStatsDClient("", "", 8125);
    private static RouterApp router1 = new RouterApp(new Integer[] {Porter.getAFreePort()},
            Porter.getAFreePort(), Porter.getAFreePort(), sslContextFactory, "localhost", "localhost",
            new ConnectionMonitor(new ConnectionMonitor.DataPublishHandler[]{(a,b) -> statsDClient.gauge(a, b)}), SSLUtilities.testCertificationChecker);
    
    private static RouterApp router2 = new RouterApp(new Integer[] {Porter.getAFreePort()},
            Porter.getAFreePort(), Porter.getAFreePort(), sslContextFactory, "localhost", "localhost",
            new ConnectionMonitor(new ConnectionMonitor.DataPublishHandler[]{(a,b) -> statsDClient.gauge(a, b)}), SSLUtilities.testCertificationChecker);
    
    
    private static ConnectorApp connector1 = new ConnectorApp(new URI[]{router1.registerUri, router2.registerUri}, targetServer1.uri, "a", Porter.getAFreePort(), 3, new ConnectionMonitor.DataPublishHandler[]{(a,b) -> statsDClient.gauge(a, b)});
    
    private static ConnectorApp connector2 = new ConnectorApp(new URI[]{router1.registerUri, router2.registerUri}, targetServer2.uri, "b", Porter.getAFreePort(), 3, new ConnectionMonitor.DataPublishHandler[]{(a,b) -> statsDClient.gauge(a, b)});
    
    private static ConnectorApp connector3 = new ConnectorApp(new URI[]{router1.registerUri, router2.registerUri}, targetServer3.uri, "a", Porter.getAFreePort(), 3, new ConnectionMonitor.DataPublishHandler[]{(a,b) -> statsDClient.gauge(a, b)});
    
    private static ConnectorApp connector4 = new ConnectorApp(new URI[]{router1.registerUri, router2.registerUri}, targetServer4.uri, "", Porter.getAFreePort(), 3, new ConnectionMonitor.DataPublishHandler[]{(a,b) -> statsDClient.gauge(a, b)});

    @BeforeClass
    public static void start() throws Exception {
        targetServer1.start();
        targetServer2.start();
        targetServer3.start();
        targetServer4.start();
        router1.start();
        router2.start();        
        connector1.start();
        connector2.start();
        connector3.start();
        connector4.start();
    }

    @AfterClass
    public static void stop() throws Exception {
        swallowException(client::stop);
        swallowException(targetServer1::close);
        swallowException(router1::shutdown);
        swallowException(connector1::shutdown);
        swallowException(targetServer2::close);
        swallowException(router2::shutdown);
        swallowException(connector2::shutdown);
        swallowException(targetServer3::close);
        swallowException(connector3::shutdown);
        swallowException(targetServer4::close);
        swallowException(connector4::shutdown);
    }
    
    @Test
    public void canMakeGETRequestsToBothConnectorApp() throws Exception {
        ContentResponse response1 = client.GET(router1.uri.get(0).resolve("/a/static/hello.html"));
        assertThat(response1.getStatus(), is(SC_OK));
        Assert.assertEquals(FileFinder.helloHtmlContents(), response1.getContentAsString());
        ContentResponse response2 = client.GET(router1.uri.get(0).resolve("/b/static/hello.html"));
        assertThat(response2.getStatus(), is(SC_OK));
        Assert.assertEquals(FileFinder.helloHtmlContents(), response2.getContentAsString());

        ContentResponse response3 = client.GET(router1.uri.get(0).resolve("/a/static/hello.html"));
        assertThat(response3.getStatus(), is(SC_OK));
        Assert.assertEquals(FileFinder.helloHtmlContents(), response3.getContentAsString());

        ContentResponse response4 = client.GET(router2.uri.get(0).resolve("/b/static/hello.html"));
        assertThat(response4.getStatus(), is(SC_OK));
        Assert.assertEquals(FileFinder.helloHtmlContents(), response4.getContentAsString());
    }
    
    @Test
    public void canMakeRequestWhenOneConnectorAppIsDown() throws Exception{
    	swallowException(connector1::shutdown);
        ContentResponse response1 = client.GET(router1.uri.get(0).resolve("/a/static/hello.html"));
        assertThat(response1.getStatus(), is(SC_OK));
        Assert.assertEquals(FileFinder.helloHtmlContents(), response1.getContentAsString());
        swallowException(connector1::start);
    }
   
    @Test
    public void canMakeRequestToCatchAllService() throws Exception{
        ContentResponse response1 = client.GET(router1.uri.get(0).resolve("/static/hello.html"));
        assertThat(response1.getStatus(), is(SC_OK));
        Assert.assertEquals(FileFinder.helloHtmlContents(), response1.getContentAsString());
    }
}

class ContextualizedWebServer implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(TestWebServer.class);

    public final URI uri;
    private final Server jettyServer;
    
    private final String context;

    public ContextualizedWebServer(int port, String context) {
        this.uri = URI.create("http://localhost:" + port);
        this.context = context;
        jettyServer = new Server();
        HttpConfiguration config = new HttpConfiguration();
        ServerConnector connector = new ServerConnector(jettyServer);
        connector.setConnectionFactories(Collections.singletonList(new HttpConnectionFactory(config)));
        connector.setPort(uri.getPort());
        connector.setHost(uri.getHost());
        jettyServer.setConnectors(new org.eclipse.jetty.server.Connector[]{connector});
        jettyServer.setStopAtShutdown(true);
    }

    public void start() throws Exception {
        HandlerList handlers = new HandlerList();
        handlers.addHandler(noCompressionResourceHandler());
        jettyServer.setHandler(handlers);
        jettyServer.start();
        log.info("Started at " + uri);
    }

    private Handler noCompressionResourceHandler() {
        ResourceHandler resourceHandler = new ResourceHandler();
        resourceHandler.setBaseResource(Resource.newClassPathResource("/web", true, false));
        ContextHandler ctx = new ContextHandler(context+"/static");
        ctx.setHandler(resourceHandler);
        return ctx;
    }

    @Override
    public void close() throws Exception {
        jettyServer.setStopTimeout(1000L);
        jettyServer.stop();
    }
}
