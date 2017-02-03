package com.danielflower.crank4j.sharedstuff;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HealthServiceHandlerTest {
    private HealthService healthService;
    private HealthServiceHandler healthServiceHandler;
    private JSONObject healthReport;

    @Before
    public void setup() {

        healthReport = new JSONObject()
            .put("Hello", "World");

        healthService = mock(HealthService.class);
        healthServiceHandler = new HealthServiceHandler(healthService);
        when(healthService.createHealthReport()).thenReturn(healthReport);
    }

    @Test
    public void testGetHealthInfo() throws Exception {
        JSONAssert.assertEquals("{ 'Hello': 'World' }", healthServiceHandler.getHealthInfo(), JSONCompareMode.STRICT);
    }
}