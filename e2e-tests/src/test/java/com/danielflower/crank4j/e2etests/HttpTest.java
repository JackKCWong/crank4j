package com.danielflower.crank4j.e2etests;

import com.danielflower.crank4j.connector.ClientFactory;
import com.danielflower.crank4j.connector.ConnectorApp;
import com.danielflower.crank4j.router.RouterApp;
import com.danielflower.crank4j.sharedstuff.CertificatesChecker;
import com.danielflower.crank4j.sharedstuff.Porter;
import com.danielflower.crank4j.utils.ConnectionMonitor;
import com.timgroup.statsd.NonBlockingStatsDClient;
import com.timgroup.statsd.StatsDClient;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.api.Result;
import org.eclipse.jetty.client.util.DeferredContentProvider;
import org.eclipse.jetty.client.util.FormContentProvider;
import org.eclipse.jetty.client.util.FutureResponseListener;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.Fields;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.*;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scaffolding.*;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;

import static com.danielflower.crank4j.sharedstuff.Dirs.dirPath;
import static com.danielflower.crank4j.utils.Action.swallowException;
import static javax.servlet.http.HttpServletResponse.*;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.core.IsEqual.equalTo;

public class HttpTest {
    private static final Logger log = LoggerFactory.getLogger(HttpTest.class);

    private static final HttpClient client = ClientFactory.startedHttpClient();
    private static final TestWebServer targetServer = new TestWebServer(Porter.getAFreePort());
    private static final SslContextFactory sslContextFactory = ManualTest.testSslContextFactory();
    private static StatsDClient statsDClient = new NonBlockingStatsDClient("", "", 5125);
    private static RouterApp router = new RouterApp(new Integer[]{Porter.getAFreePort()},
        Porter.getAFreePort(), Porter.getAFreePort(), sslContextFactory, "localhost", "localhost",
        new ConnectionMonitor(new ConnectionMonitor.DataPublishHandler[]{(a, b) -> statsDClient.gauge(a, b)}), SSLUtilities.testCertificationChecker);
    private static ConnectorApp connector = new ConnectorApp(new URI[]{router.registerUri}, targetServer.uri, "", Porter.getAFreePort(), 1, (new ConnectionMonitor.DataPublishHandler[]{(a, b) -> statsDClient.gauge(a, b)}));

    @BeforeClass
    public static void start() throws Exception {
        router.start();
        targetServer.start();
        connector.start();
    }

    @AfterClass
    public static void stop() throws Exception {
        swallowException(client::stop);
        swallowException(targetServer::close);
        swallowException(router::shutdown);
        swallowException(connector::shutdown);
    }

    @Before
    public void setup() throws InterruptedException, ExecutionException, TimeoutException {
        healthCheck();
    }

    @After
    public void teardown() throws InterruptedException, ExecutionException, TimeoutException {
        healthCheck();
    }

    private static final String ERROR_MSG_405 = "Method Not Allowed";
    private static final String ERROR_MSG_403 = "Forbidden";

    @Test
    public void canMakeGETRequests() throws Exception {
        ContentResponse response = client.GET(router.uri.get(0).resolve("/static/hello.html"));
        assertThat(response.getStatus(), is(SC_OK));
        Assert.assertEquals(FileFinder.helloHtmlContents(), response.getContentAsString());
    }

    @Test
    public void cantMakeOptionRequests() throws Exception {
        ContentResponse response = client.newRequest(router.uri.get(0).resolve("/static/hello.html")).method("OPTIONS").send();
        assertThat(response.getStatus(), is(SC_METHOD_NOT_ALLOWED));
        Assert.assertEquals(response.getContentAsString().contains(ERROR_MSG_405), true);
    }

    @Test
    public void cantMakeTrackRequests() throws Exception {
        ContentResponse response = client.newRequest(router.uri.get(0).resolve("/static/hello.html")).method("TRACE").send();
        assertThat(response.getStatus(), is(SC_METHOD_NOT_ALLOWED));
        Assert.assertEquals(response.getContentAsString().contains(ERROR_MSG_405), true);
    }

    @Test
    public void cantMakeTrackRequestsOnWebSocketPort() throws Exception {
        ContentResponse response = client.newRequest(new URI("https:" + router.registerUri.getSchemeSpecificPart()).resolve("/")).method("TRACE").send();
        assertThat(response.getStatus(), is(SC_FORBIDDEN));
        Assert.assertEquals(response.getContentAsString().contains(ERROR_MSG_403), true);
    }

    @Test
    public void invalidRequestsWithBadQueryAreRejected() throws Exception {
        String request = "/sw000.asp?|-|0|404_Object_Not_Found";
        int responseCode = BadRequestRunner.newRequest(router.uri.get(0) + request);
        assertThat(responseCode, is(400));
    }

    @Test
    public void invalidRequestsWithBadPathAreRejected() throws Exception {
        String request = "/ca/..\\\\..\\\\..\\\\..\\\\..\\\\..\\\\..\\\\..\\\\winnt/\\\\win.ini";
        int responseCode = BadRequestRunner.newRequest(router.uri.get(0) + request);
        assertThat(responseCode, is(400));
    }

    @Test
    @Ignore("Too slow to run frequently")
    public void slowConsumersAreOkay() throws Exception {
        StringBuffer received = new StringBuffer();
        AtomicInteger count = new AtomicInteger(0);
        ContentResponse response = client.newRequest(router.uri.get(0).resolve("/static/hello.html"))
            .onResponseContent((response1, content) -> {
                int num = count.incrementAndGet();
                System.out.println("Receiving " + num);
                received.append(bufferToString(content));
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.out.println("Done " + num);
            })
            .send();
        assertThat(response.getStatus(), is(SC_OK));
        Assert.assertEquals(FileFinder.helloHtmlContents(), received.toString());
    }

    @Test
    public void ifTheTargetGZipsThenItComesBackGZipped() throws Exception {
        ContentResponse response = client.newRequest(router.uri.get(0).resolve("/gzipped-static/hello.html"))
            .header("Accept-Encoding", "gzip")
            .send();
        assertThat(response.getStatus(), is(SC_OK));
        String respText = new String(decompress(response.getContent()), "UTF-8");
        Assert.assertEquals(FileFinder.helloHtmlContents(), respText);
    }

    private static byte[] decompress(byte[] compressedContent) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IOUtils.copy(new GZIPInputStream(new ByteArrayInputStream(compressedContent)), out);
        return out.toByteArray();
    }

    @Test
    public void headersAreCorrect() throws Exception {
        // based on stuff in https://www.mnot.net/blog/2011/07/11/what_proxies_must_do
        Map<String, String> requestHeaders = new HashMap<>();
        targetServer.registerHandler(new AbstractHandler() {
            @Override
            public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
                if (target.equals("/headers-test")) {
                    response.setHeader("Server", "Hello");
                    response.getWriter().append("Hi").close();
                    Enumeration<String> headerNames = request.getHeaderNames();
                    while (headerNames.hasMoreElements()) {
                        String name = headerNames.nextElement();
                        requestHeaders.put(name, request.getHeader(name));
                    }
                    baseRequest.setHandled(true);
                }
            }
        });

        ContentResponse response = client.newRequest(router.uri.get(0).resolve("/headers-test"))
            .header("Proxy-Authorization", "Blah")
            .header("Proxy-Authenticate", "Yeah")
            .header("Foo", "Yo man")
            .header("Connection", "Foo")
            .send();
        assertThat(requestHeaders.get("Host"), equalTo(router.uri.get(0).getAuthority()));
        assertThat(requestHeaders.containsKey("Proxy-Authorization"), is(false));
        assertThat(requestHeaders.containsKey("Proxy-Authenticate"), is(false));
        assertThat(requestHeaders.containsKey("Foo"), is(false));
        assertThat(requestHeaders.get("Forwarded"), equalTo("for=127.0.0.1;proto=https;host=" + router.uri.get(0).getAuthority() + ";by=127.0.0.1"));
        assertThat(requestHeaders.get("X-Forwarded-Proto"), equalTo("https"));
        assertThat(requestHeaders.get("X-Forwarded-For"), equalTo("127.0.0.1"));
        assertThat(requestHeaders.get("X-Forwarded-Server"), equalTo("127.0.0.1"));
        assertThat(requestHeaders.get("X-Forwarded-Host"), equalTo(router.uri.get(0).getAuthority()));
        assertThat(response.getHeaders().getValuesList("Via"), equalTo(Collections.singletonList("1.1 crnk")));
        assertThat(response.getHeaders().getValuesList("Date"), hasSize(1));
        assertThat(response.getHeaders().getValuesList("Server"), hasSize(0)); // Some say exposing info about the Server is a security risk
    }

    @Test
    public void canPostData() throws Exception {
        targetServer.registerHandler(new AbstractHandler() {
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
        ContentResponse response = client.POST(router.uri.get(0).resolve("/post-test"))
            .content(new FormContentProvider(fields))
            .send();
        assertThat(response, ContentResponseMatcher.equalTo(SC_OK, equalTo("Got value=some-value")));
    }

    @Test
    public void largeHeadersAreOkay() throws Exception {
        int allowableResponseHeaderSize = 7000;
        String requestHeader = RandomStringUtils.randomAlphanumeric(32000);
        String expectedResponseHeader = requestHeader.substring(0, allowableResponseHeaderSize);

        targetServer.registerHandler(new AbstractHandler() {
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

        ContentResponse response = client.newRequest(router.uri.get(0).resolve("/big-headers"))
            .header("X-Big-One", requestHeader)
            .send();
        assertThat(response, ContentResponseMatcher.equalTo(SC_OK, equalTo("Hi there")));
        assertThat(response.getHeaders().get("X-Big-Response"), equalTo(expectedResponseHeader));
    }

    @Test
    public void queryStringsAreProxied() throws Exception {
        String greeting = RandomStringUtils.randomAlphanumeric(8000);
        targetServer.registerHandler(new AbstractHandler() {
            @Override
            public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
                if (target.equals("/query-string-test")) {
                    String val = "greeting is " + request.getParameter("greeting");
                    response.getWriter().append(val).close();
                    baseRequest.setHandled(true);
                }
            }
        });
        ContentResponse response = client.GET(router.uri.get(0).resolve("/query-string-test?greeting=" + greeting));
        assertThat(response, ContentResponseMatcher.equalTo(SC_OK, equalTo("greeting is " + greeting)));
    }

    @Test
    public void pathParametersAreProxied() throws Exception {
        targetServer.registerHandler(new AbstractHandler() {
            @Override
            public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
                if (target.equals("/path-param-test/a-path-param")) {
                    String val = "greeting is " + request.getParameter("greeting");
                    response.getWriter().append(val).close();
                    baseRequest.setHandled(true);
                }
            }
        });
        ContentResponse response = client.GET(router.uri.get(0).resolve("/path-param-test;/a-path-param?greeting=hi%20there"));
        assertThat(response, ContentResponseMatcher.equalTo(SC_OK, equalTo("greeting is hi there")));
    }

    @Test
    public void havingLongPoolingClientAndShouldAlwaysRemainFixedNumberOfAvailbleConnections() throws Exception {
        targetServer.registerHandler(new AbstractHandler() {
            @Override
            public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
                if (target.equals("/long-pooling")) {
                    for (; ; ) {
                        response.getWriter().append(new Date().toString()).flush();
                    }
                }
            }
        });
        startLongPoolingClient();
        ContentResponse response = client.GET(router.uri.get(0).resolve("/static/hello.html"));
        assertThat(response.getStatus(), is(SC_OK));
        startLongPoolingClient();
        response = client.GET(router.uri.get(0).resolve("/static/hello.html"));
        assertThat(response.getStatus(), is(SC_OK));

        restartRouterOnSamePort(); // to kill the long pooling
    }

    private void startLongPoolingClient() {
        new Thread(() -> {
            try {
                client.GET(router.uri.get(0).resolve("/long-pooling"));
            } catch (Exception e) {
            }
        }).start();
    }

    @Test
    public void blankRequestIsProxied() throws Exception {
        targetServer.registerHandler(new AbstractHandler() {
            @Override
            public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
                if (target.equals("/") && (request.getQueryString() == null)) {
                    response.getWriter().append("Blank request is good!").close();
                    baseRequest.setHandled(true);
                }
            }
        });
        ContentResponse response = client.GET(router.uri.get(0).resolve(""));
        assertThat(response, ContentResponseMatcher.equalTo(SC_OK, equalTo("Blank request is good!")));
    }

    @Test
    public void closingTheBrowserWillResultInCurrentSocketsBeingClosed() throws Exception {

        CountDownLatch targetServerHasErroredLatch = new CountDownLatch(1);

        targetServer.registerHandler(new AbstractHandler() {
            @Override
            public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
                if (target.equals("/close-browser-test")) {
                    PrintWriter writer = response.getWriter();
                    for (int i = 0; i < Integer.MAX_VALUE; i++) {
                        log.info("i = " + i);
                        try {
                            writer.append("Hello " + i + "\r\n").flush();
                        } catch (Exception e) {
                            log.info("Got error in target: " + e.getMessage());
                            targetServerHasErroredLatch.countDown();
                            break;
                        }
                        try {
                            Thread.sleep(5);
                        } catch (InterruptedException e) {
                        }
                    }
                    writer.close();
                    baseRequest.setHandled(true);
                }
            }
        });

        CountDownLatch latch = new CountDownLatch(1);
        client.newRequest(router.uri.get(0).resolve("/close-browser-test"))
            .timeout(50, TimeUnit.MILLISECONDS)
            .send(result -> {
                log.info("result = " + result);
                latch.countDown();
            });

        assertThat("Timed out waiting for the client response", latch.await(1, TimeUnit.MINUTES), is(true));
        assertThat("Timed out waiting for the target error to get an exception", targetServerHasErroredLatch.await(1, TimeUnit.MINUTES), is(true));

        JSONAssert.assertEquals("{ 'activeConnections': 0 }",
            client.GET(connector.healthUri).getContentAsString(), JSONCompareMode.LENIENT);
        JSONAssert.assertEquals("{ 'activeConnections': 0 }",
            client.GET(router.healthUri).getContentAsString(), JSONCompareMode.LENIENT);

    }

    @Test
    public void closingTheTargetServerWillResultInCurrentSocketsBeingClosedAndBadGatewaySentToClient() throws Exception {

        targetServer.registerHandler(new AbstractHandler() {
            @Override
            public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
                if (target.equals("/close-target-test")) {
                    log.info("Closing targetServer...");
                    swallowException(targetServer::close);
                    baseRequest.setHandled(true);
                }
            }
        });

        ContentResponse response = client.POST(router.uri.get(0).resolve("/close-target-test")).send();
        Integer httpResp = response.getStatus();
        log.info("Client get the response " + httpResp + " " + response.getReason());

        // Ensure socket in connector and router are closed properly
        JSONAssert.assertEquals("{ 'activeConnections': 0 }",
            client.GET(connector.healthUri).getContentAsString(), JSONCompareMode.LENIENT);
        JSONAssert.assertEquals("{ 'activeConnections': 0 }",
            client.GET(router.healthUri).getContentAsString(), JSONCompareMode.LENIENT);

        targetServer.start();

        assertThat(httpResp, is(SC_BAD_GATEWAY));

    }

    @Test
    public void restartingRouterAndConnectorShouldAutoReconnect() throws Exception {
        restartRouterOnSamePort();
    }

    private void restartRouterOnSamePort() throws Exception {
        int registerPort = router.registerUri.getPort();
        int healthPort = router.healthUri.getPort();
        int httpsPort = router.uri.get(0).getPort();
        router.shutdown();
        router = new RouterApp(new Integer[]{httpsPort},
            registerPort, healthPort, sslContextFactory, "localhost", "localhost",
            new ConnectionMonitor(new ConnectionMonitor.DataPublishHandler[]{(a, b) -> statsDClient.gauge(a, b)}), SSLUtilities.testCertificationChecker);
        router.start();
    }

    private void healthCheck() throws InterruptedException, ExecutionException, TimeoutException {
        CountDownLatch countDownLatch = new CountDownLatch(3);
        org.eclipse.jetty.client.api.Request request = client.newRequest(router.healthUri).method(HttpMethod.GET);
        FutureResponseListener listener = new FutureResponseListener(request);
        countDownLatch.await(1, TimeUnit.SECONDS);
        request.send(listener); // Asynchronous send
        ContentResponse response = listener.get(5, TimeUnit.SECONDS);
        JSONAssert.assertEquals("{'Services Register Map': {'default': '{/127.0.0.1=1}'}}",
            response.getContentAsString(), JSONCompareMode.LENIENT);
    }

    @Test
    public void restartingConnectorAndRouterShouldBeAutoReconnected() throws Exception {
        int healthPort = connector.healthUri.getPort();
        connector.shutdown();
        connector = new ConnectorApp(new URI[]{router.registerUri}, targetServer.uri, "", healthPort, 1, (new ConnectionMonitor.DataPublishHandler[]{(a, b) -> statsDClient.gauge(a, b)}));
        connector.start();
    }

    @Test
    public void hundredsOfKBsOfPostDataCanBeStreamedThereAndBackAgain() throws Exception {
        String val = RandomStringUtils.randomAlphanumeric(1500000);
        StringBuffer errors = new StringBuffer();
        targetServer.registerHandler(new AbstractHandler() {
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
        client.POST(router.uri.get(0).resolve("/large-post"))
            .content(content)
            .send(new Response.Listener.Adapter() {

                @Override
                public void onContent(Response response, ByteBuffer content) {
                    actualBody.append(bufferToString(content));
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

    private static String bufferToString(ByteBuffer content) {
        ByteBuffer responseBytes = ByteBuffer.allocate(content.capacity());
        responseBytes.put(content);
        responseBytes.position(0);
        try {
            return new String(responseBytes.array(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }


}
