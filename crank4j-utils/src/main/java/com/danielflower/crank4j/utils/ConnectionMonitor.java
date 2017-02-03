package com.danielflower.crank4j.utils;

import com.sun.management.UnixOperatingSystemMXBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.concurrent.atomic.AtomicInteger;

public class ConnectionMonitor {
	private static final Logger log = LoggerFactory.getLogger(ConnectionMonitor.class);

	private final AtomicInteger requestNum;
	private long openFiles;
	private DataPublishHandler[] dataPublishHandlers;

	public ConnectionMonitor(DataPublishHandler[] dataPublishHandlers) {
		this.requestNum = new AtomicInteger(0);
		this.dataPublishHandlers = dataPublishHandlers;
	}

	public void onConnectionStarted() {
		requestNum.incrementAndGet();

		reportConnectionCount();
		reportOpenFilesCount();

		log.info("activeConnections=" + requestNum.get() + ", openFiles=" + openFiles);
	}

	public void onConnectionEnded() {
		requestNum.decrementAndGet();

		reportConnectionCount();
		reportOpenFilesCount();

		log.info("activeConnections=" + requestNum.get() + ", openFiles=" + openFiles);
	}

    public void reportWebsocketPoolSize(int size){
//        this.statsd.gauge("websocket.pool.size", size);
	    for (DataPublishHandler dataPublishHandler : dataPublishHandlers) {
		    dataPublishHandler.publishData("websocket.pool.size", size);
	    }
    }

    private void  reportConnectionCount() {
//        this.statsd.gauge("connections", requestNum.get());
	    for (DataPublishHandler dataPublishHandler : dataPublishHandlers) {
		    dataPublishHandler.publishData("connections", requestNum.get());
	    }
    }

	private void reportOpenFilesCount() {
		OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
		if (os instanceof UnixOperatingSystemMXBean) {
			openFiles = ((UnixOperatingSystemMXBean) os).getOpenFileDescriptorCount();
//			this.statsd.gauge("openfiles", openFiles);
			for (DataPublishHandler dataPublishHandler : dataPublishHandlers) {
				dataPublishHandler.publishData("openfiles", (int) openFiles);
			}
		}
	}

	public int getConnectionCount() {
		return requestNum.get();
	}

	public long getOpenFiles() {
		return openFiles;
	}

	public interface DataPublishHandler<T> {
		void publishData(String key, Integer value);
	}

}
