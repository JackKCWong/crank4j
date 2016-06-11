package com.danielflower.crank4j.connector;

import com.danielflower.crank4j.sharedstuff.Crank4jException;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import static com.danielflower.crank4j.sharedstuff.Constants.MAX_REQUEST_HEADERS_SIZE;

public class ClientFactory {

    public static HttpClient startedHttpClient() {
        HttpClient client = new HttpClient(new SslContextFactory(true));
        client.setFollowRedirects(false); // redirects should be proxied
        client.setRequestBufferSize(MAX_REQUEST_HEADERS_SIZE);

        try {
            client.start();
        } catch (Exception e) {
            throw new Crank4jException("Error while starting HttpClient", e);
        }

        client.getContentDecoderFactories().clear(); // don't decode things like gzip - must be after start() is called

        return client;
    }

}
