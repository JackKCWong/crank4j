package com.danielflower.crank4j.connector;

import com.danielflower.crank4j.protocol.CrankerProtocol;
import com.danielflower.crank4j.utils.Action;
import com.danielflower.crank4j.utils.ConnectionMonitor;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.api.UpgradeRequest;
import org.eclipse.jetty.websocket.api.UpgradeResponse;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.eclipse.jetty.websocket.client.io.UpgradeListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;

public class Connector {
    private static final Logger log = LoggerFactory.getLogger(Connector.class);
    private final int webSocketPoolSize;

    private final URI[] routerURIs;
    private final URI targetURI;
    private final WebSocketClient webSocketClient = new WebSocketClient(new SslContextFactory(true));
    private final ConnectionMonitor connectionMonitor;
    private final String targetServiceName;
    

    public Connector(URI[] routerURIs, URI targetURI, String targetServiceName, int webSocketPoolSize, ConnectionMonitor connectionMonitor) {
        this.routerURIs = routerURIs;
        this.targetURI = targetURI;
        this.targetServiceName = targetServiceName;
        this.webSocketPoolSize = webSocketPoolSize;
        this.connectionMonitor = connectionMonitor;
    }

    public ConnectionMonitor getConnectionMonitor() {
        return this.connectionMonitor;
    }

    public void start() throws Exception {
        webSocketClient.setMaxBinaryMessageBufferSize(16384);
        webSocketClient.setMaxIdleTimeout(Constants.MAX_TOTAL_TIME);
        webSocketClient.start();
        for(URI routerURI : routerURIs){
            URI registerURI = routerURI.resolve("/register");
            log.info("Connecting to " + registerURI);
            for (int i = 0; i < webSocketPoolSize; i++) {
                connectToRouter(registerURI);
            }
        }

    }

    private void connectToRouter(URI registerURI) throws IOException {
        ConnectorSocket socket = new ConnectorSocket(registerURI, targetURI, connectionMonitor);
        socket.whenConsumed(() -> {
            try {
                log.info("Adding another socket");
                connectToRouter(socket.getSourceURI());
            } catch (Exception e) {
                log.error("Could not replace socket to " + registerURI);
            }
        });
        ClientUpgradeRequest cur = new ClientUpgradeRequest();
        UpgradeListener upgradeListener = new UpgradeListener() {
            @Override
            public void onHandshakeRequest(UpgradeRequest upgradeRequest) {

            }

            @Override
            public void onHandshakeResponse(UpgradeResponse upgradeResponse) {
                String crankerProtocolVersion = upgradeResponse.getHeader("CrankerProtocol");
                log.debug("Hi Connector, Can you talk to me with Cranker Protocol " + crankerProtocolVersion + "?");
                CrankerProtocol.validateCrankerProtocolVersion(crankerProtocolVersion, log);
            }
        };
        cur.setHeader("CrankerProtocol", CrankerProtocol.CRANKER_PROTOCOL_VERSION_1_0);
        cur.setHeader("Route", targetServiceName);
        webSocketClient.connect(socket, registerURI, cur, upgradeListener);
    }

    public void shutdown() {
        Action.swallowException(webSocketClient::stop);
    }
}
