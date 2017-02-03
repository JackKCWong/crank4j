package com.danielflower.crank4j.connector;


import com.danielflower.crank4j.utils.ConnectionMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

public class Crank4jConnectorFactory {
	private static final Logger log = LoggerFactory.getLogger(Crank4jConnectorFactory.class);

	/**
	 *
	 * @param targetURI e.g. https://localhost:port
	 * @param targetServiceName the path name when routing
	 * @param routerURIs the crank4j routers it is going to connect, at least one
	 * @param connectorPoolSize default to 10
	 * @param dataPublishHandlers default to empty array
	 * @return crank4j connector singleton instance
	 */
	public static Connector createAndStartConnector(URI targetURI, String targetServiceName, URI[] routerURIs, int connectorPoolSize, ConnectionMonitor.DataPublishHandler[] dataPublishHandlers) {
		int socketSize = connectorPoolSize != 0 ? connectorPoolSize : 10; /* default to 10 */
		dataPublishHandlers = dataPublishHandlers==null ? new ConnectionMonitor.DataPublishHandler[]{} : dataPublishHandlers;
		try {
			ConnectionMonitor connectionMonitor = new ConnectionMonitor(dataPublishHandlers);
			Connector connector = new Connector(routerURIs, targetURI, targetServiceName, socketSize, connectionMonitor);
			connector.start();
			Runtime.getRuntime().addShutdownHook(new Thread(connector::shutdown));
			return connector;
		} catch (Throwable t) {
			log.error("Error during startup embedded crank4j-connector", t);
			System.exit(1);
		}
		return null;
	}

}
