package com.olaaref.learninghelper.gateway;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Tells the home screen which projects are available right now. */
@RestController
public class ServicesController {
    public record ServiceStatus(String id, String name, boolean up) {}

    private final ServicesProperties services;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();

    public ServicesController(ServicesProperties services) {
        this.services = services;
    }

    @GetMapping("/api/services")
    public List<ServiceStatus> services() {
        return List.of(
                new ServiceStatus("notion", "Notion Sync", isUp(services.notion().url())),
                new ServiceStatus("db", "DB Practice Lab", isUp(services.dbLab().url())));
    }

    private boolean isUp(String baseUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/actuator/health"))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
