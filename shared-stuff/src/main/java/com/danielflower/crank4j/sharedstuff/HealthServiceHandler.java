package com.danielflower.crank4j.sharedstuff;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/")
public class HealthServiceHandler {
    public static final String healthPath = "/health";
    private final HealthService healthService;

    public HealthServiceHandler(HealthService healthService) {
        this.healthService = healthService;
    }

    @GET
    @Path(healthPath)
    @Produces(MediaType.APPLICATION_JSON)
    public String getHealthInfo() throws Exception {
        return healthService.createHealthReport().toString(2);
    }
}
