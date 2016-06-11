package com.danielflower.crank4j.e2etests;

import com.danielflower.crank4j.connector.ClientFactory;
import com.danielflower.crank4j.connector.ConnectorApp;
import com.danielflower.crank4j.router.RouterApp;
import com.danielflower.crank4j.sharedstuff.Porter;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.api.Result;
import org.eclipse.jetty.client.util.DeferredContentProvider;
import org.eclipse.jetty.client.util.FormContentProvider;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.Fields;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import scaffolding.ContentResponseMatcher;
import scaffolding.FileFinder;
import scaffolding.TestWebServer;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

import static com.danielflower.crank4j.sharedstuff.Action.silently;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;

public class HttpTests {
    private static final HttpClient client = ClientFactory.startedHttpClient();
    private static final TestWebServer server = new TestWebServer(Porter.getAFreePort());
    private static final SslContextFactory sslContextFactory = ManualTest.testSslContextFactory();
    private static RouterApp router = new RouterApp(Porter.getAFreePort(), Porter.getAFreePort(), sslContextFactory);
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
        Assert.assertEquals(FileFinder.helloHtmlContents(), resp.getContentAsString());
    }

    @Test
    public void ifTheTargetGZipsThenItComesBackGZipped() throws Exception {
        ContentResponse response = client.newRequest(router.uri.resolve("/gzipped-static/hello.html"))
            .header("Accept-Encoding", "gzip")
            .send();
        assertThat(response.getStatus(), is(200));
        String respText = new String(decompress(response.getContent()), "UTF-8");
        Assert.assertEquals(FileFinder.helloHtmlContents(), respText);
    }

    private static byte[] decompress(byte[] compressedContent) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IOUtils.copy(new GZIPInputStream(new ByteArrayInputStream(compressedContent)), out);
        return out.toByteArray();
    }

    @Test
    public void canPostData() throws Exception {
        server.registerHandler(new AbstractHandler() {
            @Override
            public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
                if (target.equals("/post-test")) {
                    String sentValue = request.getParameter("value");
                    response.getWriter().append("Got value=").append(sentValue).close();
                    baseRequest.setHandled(true);
                }
            }
        });

        Fields fields = new Fields();
        fields.put("value", "some-value");
        ContentResponse resp = client.POST(router.uri.resolve("/post-test"))
            .content(new FormContentProvider(fields))
            .send();
        assertThat(resp, ContentResponseMatcher.equalTo(200, equalTo("Got value=some-value")));
    }

    @Test
    public void largeHeadersAreOkay() throws Exception {
        int allowableResponseHeaderSize = 7000;
        String requestHeader = RandomStringUtils.randomAlphanumeric(32000);
        String expectedResponseHeader = requestHeader.substring(0, allowableResponseHeaderSize);

        server.registerHandler(new AbstractHandler() {
            @Override
            public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
                if (target.equals("/big-headers")) {

                    String responseValue = request.getHeader("X-Big-One").substring(0, allowableResponseHeaderSize);
                    response.addHeader("X-Big-Response", responseValue);
                    response.getWriter().append("Hi there").close();
                    baseRequest.setHandled(true);
                }
            }
        });

        ContentResponse response = client.newRequest(router.uri.resolve("/big-headers"))
            .header("X-Big-One", requestHeader)
            .send();
        assertThat(response, ContentResponseMatcher.equalTo(200, equalTo("Hi there")));
        assertThat(response.getHeaders().get("X-Big-Response"), equalTo(expectedResponseHeader));
    }

    @Test
    public void queryStringsAreProxied() throws Exception {
        server.registerHandler(new AbstractHandler() {
            @Override
            public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
                if (target.equals("/query-string-test")) {
                    String val = "greeting is " + request.getParameter("greeting");
                    response.getWriter().append(val).close();
                    baseRequest.setHandled(true);
                }
            }
        });
        ContentResponse resp = client.GET(router.uri.resolve("/query-string-test?greeting=hello%20world"));
        assertThat(resp, ContentResponseMatcher.equalTo(200, equalTo("greeting is hello world")));
    }

    @Test
    public void pathParametersAreProxied() throws Exception {
        server.registerHandler(new AbstractHandler() {
            @Override
            public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
                if (target.equals("/path-param-test/a-path-param")) {
                    String val = "greeting is " + request.getParameter("greeting");
                    response.getWriter().append(val).close();
                    baseRequest.setHandled(true);
                }
            }
        });
        ContentResponse resp = client.GET(router.uri.resolve("/path-param-test;/a-path-param?greeting=hi%20there"));
        assertThat(resp, ContentResponseMatcher.equalTo(200, equalTo("greeting is hi there")));
    }

    @Test
    public void hundredsOfKBsOfPostDataCanBeStreamedThereAndBackAgain() throws Exception {
        String val = RandomStringUtils.randomAlphanumeric(500000);
        StringBuffer errors = new StringBuffer();
        server.registerHandler(new AbstractHandler() {
            @Override
            public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
                if (target.equals("/large-post")) {
                    AsyncContext asyncContext = request.startAsync();
                    baseRequest.setHandled(true);
                    ServletInputStream requestInputStream = request.getInputStream();
                    ServletOutputStream responseStream = response.getOutputStream();
                    requestInputStream.setReadListener(new ReadListener() {
                        byte[] buffer = new byte[8192];

                        @Override
                        public void onDataAvailable() throws IOException {
                            while (requestInputStream.isReady()) {
                                int read = requestInputStream.read(buffer);
                                if (read == -1) {
                                    return;
                                } else {
                                    responseStream.write(buffer, 0, read);
                                }
                            }
                        }

                        @Override
                        public void onAllDataRead() throws IOException {
                            responseStream.close();
                            requestInputStream.close();
                            asyncContext.complete();
                        }

                        @Override
                        public void onError(Throwable t) {
                            errors.append("Error on read: ").append(t);
                        }
                    });

                }
            }
        });

        CountDownLatch latch2 = new CountDownLatch(1);
        StringBuffer actualBody = new StringBuffer();

        DeferredContentProvider content = new DeferredContentProvider();
        client.POST(router.uri.resolve("/large-post"))
            .content(content)
            .send(new Response.Listener.Adapter() {

                @Override
                public void onContent(Response response, ByteBuffer content) {
                    ByteBuffer responseBytes = ByteBuffer.allocate(content.capacity());
                    responseBytes.put(content);
                    responseBytes.position(0);
                    String s = new String(responseBytes.array());
                    actualBody.append(s);
                }

                @Override
                public void onComplete(Result result) {
                    latch2.countDown();
                }
            });

        CountDownLatch latch = new CountDownLatch(1);
        content.offer(ByteBuffer.wrap(val.getBytes()), new Callback() {
            @Override
            public void succeeded() {
                latch.countDown();
            }
        });
        latch.await();
        content.close();
        latch2.await(1, TimeUnit.MINUTES);

        Assert.assertEquals("", errors.toString());
        Assert.assertEquals(val, actualBody.toString());

    }


}
