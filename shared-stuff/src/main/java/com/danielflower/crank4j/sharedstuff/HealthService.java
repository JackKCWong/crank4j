package com.danielflower.crank4j.sharedstuff;

import org.json.JSONObject;

public interface HealthService {
    JSONObject createHealthReport();

    String getVersion();

    boolean getAvailable();
}
