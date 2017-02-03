package com.danielflower.crank4j.connector;


import com.danielflower.crank4j.utils.Crank4jException;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.util.HttpCookieStore;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import java.util.concurrent.TimeUnit;

public class ClientFactory {

    public static HttpClient startedHttpClient() {
        SslContextFactory sslContextFactory = new SslContextFactory(true);
        sslContextFactory.setTrustAll(true);
        HttpClient client = new HttpClient(sslContextFactory);
        client.setFollowRedirects(false); // redirects should be proxied
        client.setRequestBufferSize(Constants.MAX_REQUEST_HEADERS_SIZE);
        client.setIdleTimeout(TimeUnit.HOURS.toMillis(2));
        client.setMaxConnectionsPerDestination(32768); // copied Jetty's ReverseProxy default value
        // Must not store cookies, otherwise cookies of different clients will mix.
        client.setCookieStore(new HttpCookieStore.Empty());

        try {
            client.start();
        } catch (Exception e) {
            throw new Crank4jException("Error while starting HttpClient", e);
        }
        client.getContentDecoderFactories().clear(); // don't decode things like gzip - must be after start() is called
        client.getProtocolHandlers().clear(); // No protocol handlers, pass everything to the client.
        return client;
    }

}
