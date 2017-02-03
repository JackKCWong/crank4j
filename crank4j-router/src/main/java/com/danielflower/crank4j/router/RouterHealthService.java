package com.danielflower.crank4j.router;

import com.danielflower.crank4j.protocol.CrankerProtocol;
import com.danielflower.crank4j.sharedstuff.CertificatesChecker;
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
import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class RouterHealthService implements HealthService {
	private static final Logger log = LoggerFactory.getLogger(RouterHealthService.class);

	private final String version;
	private final ScheduledExecutorService healthCheckScheduler = Executors.newScheduledThreadPool(1);
	private final ConnectionMonitor connectionMonitor;
	private final CertificatesChecker certificatesChecker;
	private final WebSocketFarm webSocketFarm;
	private volatile boolean isAvailable;

	public RouterHealthService(ConnectionMonitor connectionMonitor, CertificatesChecker certificatesChecker,
	                           WebSocketFarm webSocketFarm) {
		String version = RouterApp.class.getPackage().getImplementationVersion();
		this.version = version != null ? version : "N/A";
		this.connectionMonitor = connectionMonitor;
		this.certificatesChecker = certificatesChecker;
		this.isAvailable = false;
		this.webSocketFarm = webSocketFarm;
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
		JSONObject servicesRegisterMapping = new JSONObject();
		webSocketFarm.getSockets().forEach((a, b) -> {
			HashMap<String, Integer> remoteAddr = new HashMap<>();
			b.forEach(e -> getRemoteAddrMapping(remoteAddr, e));
			servicesRegisterMapping.put(a, remoteAddr.toString());
		});
		HashMap<String, Integer> catchAllremoteAddr = new HashMap<>();
		webSocketFarm.getCatchAll().forEach(a -> getRemoteAddrMapping(catchAllremoteAddr, a));
		servicesRegisterMapping.put("default", catchAllremoteAddr.toString());
		JSONObject health = new JSONObject()
				.put("component", "crank4j-router")
				.put("version", version)
				.put("isAvailable", isAvailable)
				.put("Cert Status", certificatesChecker.getAvailabilityDetails())
				.put("CrankerProtocol", CrankerProtocol.CRANKER_PROTOCOL_VERSION_1_0)
				.put("activeConnections", connectionMonitor.getConnectionCount())
				.put("openFiles", connectionMonitor.getOpenFiles())
				.put("Services Register Map", servicesRegisterMapping);
		return health;
	}

	private void getRemoteAddrMapping(HashMap<String, Integer> remoteAddr, RouterSocket e) {
		String currRemoteAddr = e.remoteAddress().split(":")[0];
		if (remoteAddr.containsKey(currRemoteAddr)) {
			remoteAddr.replace(currRemoteAddr, remoteAddr.get(currRemoteAddr) +1);
		} else {
			remoteAddr.put(currRemoteAddr, 1);
		}
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

	public static void registerMBean(RouterHealthService healthService) throws MalformedObjectNameException, NotCompliantMBeanException, MBeanRegistrationException {
		final MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
		StandardMBean mb = new StandardMBean(healthService, HealthService.class);
		try {
			mbs.registerMBean(mb, new ObjectName("crank4j-router:type=Health"));
		} catch (InstanceAlreadyExistsException e) {
			log.info("Health MBean already exists. Will not re-register it.");
		}
	}
}
