package com.danielflower.crank4j.connector;

import com.danielflower.crank4j.sharedstuff.HealthServiceHandler;
import com.danielflower.crank4j.sharedstuff.RestfulServer;
import com.danielflower.crank4j.utils.Action;
import com.danielflower.crank4j.utils.ConnectionMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

public class ConnectorApp {
	private static final Logger log = LoggerFactory.getLogger(ConnectorApp.class);
	private final RestfulServer server;
	public final URI healthUri;
	private final Connector connector;
	private final ConnectorHealthService connectorHealthService;


	public ConnectorApp(URI[] routerURIs, URI targetURI, String targetServiceName, int healthPort, int webSocketPoolSize,
	                    ConnectionMonitor.DataPublishHandler[] dataPublishHandlers) {
		this.connector = Crank4jConnectorFactory.createAndStartConnector(targetURI, targetServiceName, routerURIs, webSocketPoolSize, dataPublishHandlers);
		connectorHealthService = new ConnectorHealthService(connector.getConnectionMonitor());
		this.server = new RestfulServer(healthPort, new HealthServiceHandler(connectorHealthService));
		this.healthUri = URI.create("http://localhost:" + healthPort + "/health");
		try {
			ConnectorHealthService.registerMBean(connectorHealthService);
		} catch (Exception e) {
			log.warn("Cannot create Health Bean " + e.toString());
		}
	}

	public void start() throws Exception {
		server.start();
		connectorHealthService.scheduleHealthCheck();
	}

	public void shutdown() {
		Action.swallowException(server::stop);
		connector.shutdown();
	}
}
