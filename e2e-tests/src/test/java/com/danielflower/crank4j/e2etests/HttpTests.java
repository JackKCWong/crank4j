package com.danielflower.crank4j.e2etests;

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

    @BeforeClass
    public static void start() throws Exception {
        client.start();
    }
    @AfterClass
    public static void stop() throws Exception {
        silently(client::stop);
        silently(server::close);
    }

    @Test
    public void canMakeRequests() throws Exception {

        URI router = createRouter();
        URI target = createTarget(router, server.uri);
        client.start();

        assertThat(client.GET(server.uri.resolve("/hello.txt")),
            ContentResponseMatcher.equalTo(200, equalTo("Hello there")));
    }

    private URI createTarget(URI router, URI server) {
        return null;
    }

    private URI createRouter() {
        return null;
    }

}
