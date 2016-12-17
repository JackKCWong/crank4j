package com.danielflower.crank4j.connector;

import com.danielflower.crank4j.protocol.CrankerProtocol;
import com.danielflower.crank4j.sharedstuff.HealthService;
import com.danielflower.crank4j.utils.ConnectionMonitor;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import javax.management.StandardMBean;
import java.lang.management.ManagementFactory;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ConnectorHealthService implements HealthService {
    private static final Logger log = LoggerFactory.getLogger(ConnectorHealthService.class);

    private final String version;
    private final ScheduledExecutorService healthCheckScheduler = Executors.newScheduledThreadPool(1);
    private final ConnectionMonitor connectorToRouterConnectionMonitor;
    private volatile boolean isAvailable;

    public ConnectorHealthService(ConnectionMonitor connectorToRouterConnectionMonitor) {
        String version = Connector.class.getPackage().getImplementationVersion();
        this.version = version != null ? version : "N/A";
        this.connectorToRouterConnectionMonitor = connectorToRouterConnectionMonitor;
        this.isAvailable = false;
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public synchronized boolean getAvailable() {
        return isAvailable;
    }

    @Override
    public JSONObject createHealthReport() {
        JSONObject health = new JSONObject()
            .put("component", "crank4j-connector")
            .put("version", version)
            .put("isAvailable", isAvailable)
            .put("CrankerProtocol", CrankerProtocol.CRANKER_PROTOCOL_VERSION_1_0)
            .put("activeConnections", connectorToRouterConnectionMonitor.getConnectionCount())
            .put("openFiles", connectorToRouterConnectionMonitor.getOpenFiles());
        return health;
    }

    public void scheduleHealthCheck() {
        healthCheckScheduler.scheduleAtFixedRate(() -> {
            updateHealth();
        }, 10, 60, TimeUnit.SECONDS);
        log.info("Health check scheduler started with 1 minute period");
    }

    private void updateHealth() {
        isAvailable = true;
    }

    public static void registerMBean(ConnectorHealthService healthService) throws MalformedObjectNameException, NotCompliantMBeanException, MBeanRegistrationException {
        final MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        StandardMBean mb = new StandardMBean(healthService, HealthService.class);
        try {
            mbs.registerMBean(mb, new ObjectName("crank4j-connector:type=Health"));
        } catch (InstanceAlreadyExistsException e) {
            log.info("Health MBean already exists. Will not re-register it.");
        }
    }
}
