package com.danielflower.crank4j.e2etests;

import com.danielflower.crank4j.router.RouterApp;
import com.danielflower.crank4j.sharedstuff.Porter;
import org.eclipse.jetty.client.HttpClient;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import scaffolding.ContentResponseMatcher;
import scaffolding.TestWebServer;

import java.net.URI;

import static com.danielflower.crank4j.sharedstuff.Action.silently;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;

public class HttpTests {
    private static final HttpClient client = new HttpClient();
    private static final TestWebServer server = TestWebServer.startOnRandomPort();
    private static RouterApp router;

    @BeforeClass
    public static void start() throws Exception {
        router = startedRouter(server.uri);
        client.start();
    }
    @AfterClass
    public static void stop() throws Exception {
        silently(client::stop);
        silently(server::close);
        silently(router::shutdown);
    }

    @Test
    public void canMakeRequests() throws Exception {
        assertThat(client.GET(router.uri.resolve("/hello.txt")),
            ContentResponseMatcher.equalTo(200, equalTo("Hello there")));
    }

    private static RouterApp startedRouter(URI server) throws Exception {
        RouterApp routerApp = new RouterApp(Porter.getAFreePort(), server);
        routerApp.start();
        return routerApp;
    }

}
