package com.danielflower.crank4j.e2etests;

import com.danielflower.crank4j.connector.ConnectorApp;
import com.danielflower.crank4j.connector.HttpClientFactory;
import com.danielflower.crank4j.router.RouterApp;
import com.danielflower.crank4j.sharedstuff.Porter;
import org.apache.commons.io.FileUtils;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import scaffolding.FileFinder;
import scaffolding.TestWebServer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.zip.GZIPInputStream;

import static com.danielflower.crank4j.sharedstuff.Action.silently;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class HttpTests {
    private static final HttpClient client = HttpClientFactory.startedClient();
    private static final TestWebServer server = new TestWebServer(Porter.getAFreePort());
    private static RouterApp router = new RouterApp(Porter.getAFreePort(), Porter.getAFreePort());
    private static ConnectorApp target = new ConnectorApp(router.registerUri, server.uri);

    @BeforeClass
    public static void start() throws Exception {
        router.start();
        server.start();
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
    public void canMakeGETRequests() throws Exception {
        ContentResponse resp = client.GET(router.uri.resolve("/static/hello.html"));
        assertThat(resp.getStatus(), is(200));
        Assert.assertEquals(helloHtmlContents(), resp.getContentAsString());
    }

    private static String helloHtmlContents() throws IOException {
        return FileUtils.readFileToString(FileFinder.testFile("web/hello.html"));
    }

    @Test
    public void ifTheTargetGZipsThenItComesBackGZipped() throws InterruptedException, ExecutionException, TimeoutException, IOException {
        ContentResponse response = client.newRequest(router.uri.resolve("/gzipped-static/hello.html"))
            .header("Accept-Encoding", "gzip")
            .send();
        assertThat(response.getStatus(), is(200));

        ByteArrayInputStream bytein = new ByteArrayInputStream(response.getContent());
        GZIPInputStream gzin = new GZIPInputStream(bytein);
        ByteArrayOutputStream byteout = new ByteArrayOutputStream();

        int res = 0;
        byte buf[] = new byte[1024];
        while (res >= 0) {
            res = gzin.read(buf, 0, buf.length);
            if (res > 0) {
                byteout.write(buf, 0, res);
            }
        }
        byte uncompressed[] = byteout.toByteArray();
        String respText = new String(uncompressed, "UTF-8");
        Assert.assertEquals(helloHtmlContents(), respText);
    }

}
