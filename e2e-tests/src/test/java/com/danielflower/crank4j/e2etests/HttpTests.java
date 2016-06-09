package com.danielflower.crank4j.e2etests;

import com.danielflower.crank4j.connector.ConnectorApp;
import com.danielflower.crank4j.router.RouterApp;
import com.danielflower.crank4j.sharedstuff.Porter;
import org.eclipse.jetty.client.HttpClient;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import scaffolding.ContentResponseMatcher;
import scaffolding.TestWebServer;

import static com.danielflower.crank4j.sharedstuff.Action.silently;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class HttpTests {
    private static final HttpClient client = new HttpClient();
    private static final TestWebServer server = new TestWebServer(Porter.getAFreePort());
    private static RouterApp router = new RouterApp(Porter.getAFreePort(), Porter.getAFreePort());
    private static ConnectorApp target = new ConnectorApp(router.registerUri, server.uri);

    @BeforeClass
    public static void start() throws Exception {
        client.setFollowRedirects(false);
        router.start();
        server.start();
        client.start();
        target.start();
    }
    @AfterClass
    public static void stop() throws Exception {
        silently(client::stop);
        silently(server::close);
        silently(router::shutdown);
        silently(target::shutdown);
    }

    @Test
    public void canMakeRequests() throws Exception {
        for (int i = 0; i < 1000; i++) {
            assertThat(client.GET(router.uri.resolve("/hello.txt")),
                ContentResponseMatcher.equalTo(200, equalTo("Hello there")));
        }
    }

}
