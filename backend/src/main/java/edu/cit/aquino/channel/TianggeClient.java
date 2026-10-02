package edu.cit.aquino.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.cit.aquino.AppInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

@Component
class TianggeClient {
    private static final Logger log = LoggerFactory.getLogger(TianggeClient.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(6);
    private static final int MAX_RETRIES = 3;

    private final String baseUrl;
    private final String clientId;
    private final String apiKey;
    private final AppInstance appInstance;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    TianggeClient(
            @Value("${tiangge.base-url:https://legacysupply.onrender.com/tiangge/v1}") String baseUrl,
            @Value("${legacysupply.client-id:22-2068-823}") String clientId,
            @Value("${legacysupply.api-key:}") String apiKey,
            AppInstance appInstance
    ) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.clientId = (clientId != null && !clientId.isBlank()) ? clientId : System.getenv().getOrDefault("LS_CLIENT_ID", "22-2068-823");
        String key = (apiKey != null && !apiKey.isBlank()) ? apiKey : System.getenv("LS_API_KEY");
        this.apiKey = key != null ? key : "";
        this.appInstance = appInstance;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    private HttpRequest.Builder baseRequestBuilder(String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("X-Client-Id", clientId)
                .header("X-Client-Instance", appInstance.getInstanceId());

        if (apiKey != null && !apiKey.isBlank()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
        return builder;
    }

    TianggeHeartbeatResponse sendHeartbeat(TianggeHeartbeatRequest request) {
        return executeWithRetry("heartbeat", () -> {
            String json = objectMapper.writeValueAsString(request);
            HttpRequest httpRequest = baseRequestBuilder("/instances/heartbeat")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 || response.statusCode() == 201) {
                return objectMapper.readValue(response.body(), TianggeHeartbeatResponse.class);
            }
            throw new RuntimeException("Heartbeat failed with HTTP " + response.statusCode() + ": " + response.body());
        });
    }

    void publishListings(List<TianggeListingItem> listings) {
        executeWithRetry("publishListings", () -> {
            String json = objectMapper.writeValueAsString(listings);
            HttpRequest httpRequest = baseRequestBuilder("/listings")
                    .PUT(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 || response.statusCode() == 204) {
                log.info("Successfully published {} listings to Tiangge", listings.size());
                return null;
            }
            throw new RuntimeException("Publish listings failed with HTTP " + response.statusCode() + ": " + response.body());
        });
    }

    void publishStock(List<TianggeStockItem> stockItems) {
        executeWithRetry("publishStock", () -> {
            String json = objectMapper.writeValueAsString(stockItems);
            HttpRequest httpRequest = baseRequestBuilder("/stock")
                    .PUT(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 || response.statusCode() == 204) {
                log.info("Successfully synced stock for {} items to Tiangge", stockItems.size());
                return null;
            }
            throw new RuntimeException("Publish stock failed with HTTP " + response.statusCode() + ": " + response.body());
        });
    }

    TianggeFeedResponse getFeed(long afterCursor, int limit) {
        return executeWithRetry("feed", () -> {
            HttpRequest httpRequest = baseRequestBuilder("/feed?after=" + afterCursor + "&limit=" + limit)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return objectMapper.readValue(response.body(), TianggeFeedResponse.class);
            }
            throw new RuntimeException("GET /feed failed with HTTP " + response.statusCode() + ": " + response.body());
        });
    }

    void sendDecision(String orderId, TianggeDecisionRequest decision) {
        executeWithRetry("decision for " + orderId, () -> {
            String json = objectMapper.writeValueAsString(decision);
            HttpRequest httpRequest = baseRequestBuilder("/orders/" + orderId + "/decision")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 || response.statusCode() == 201) {
                log.info("Tiangge order {} decision recorded: {}", orderId, decision.decision());
                return null;
            }

            throw new RuntimeException("Order decision failed with HTTP " + response.statusCode() + ": " + response.body());
        });
    }

    void sendResolution(String orderId, TianggeResolutionRequest resolution) {
        executeWithRetry("resolution for " + orderId, () -> {
            String json = objectMapper.writeValueAsString(resolution);
            HttpRequest httpRequest = baseRequestBuilder("/orders/" + orderId + "/resolution")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 || response.statusCode() == 204) {
                log.info("Tiangge order {} backorder resolution recorded: {}", orderId, resolution.status());
                return null;
            }
            throw new RuntimeException("Order resolution failed with HTTP " + response.statusCode() + ": " + response.body());
        });
    }

    void sendCancellation(String orderId, TianggeCancellationRequest cancellation) {
        executeWithRetry("cancellation for " + orderId, () -> {
            String json = objectMapper.writeValueAsString(cancellation);
            HttpRequest httpRequest = baseRequestBuilder("/orders/" + orderId + "/cancellation")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 || response.statusCode() == 204) {
                log.info("Tiangge order {} cancellation confirmed: restocked={}", orderId, cancellation.restocked());
                return null;
            }
            throw new RuntimeException("Cancellation confirm failed with HTTP " + response.statusCode() + ": " + response.body());
        });
    }

    private <T> T executeWithRetry(String operation, ApiCallable<T> callable) {
        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return callable.call();
            } catch (Exception e) {
                lastException = e;
                log.warn("Tiangge {} attempt {}/{} failed: {}", operation, attempt, MAX_RETRIES, e.getMessage());
                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(attempt * 400L);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted during retry backoff", ie);
                    }
                }
            }
        }
        log.error("Tiangge {} failed after {} attempts: {}", operation, MAX_RETRIES, lastException.getMessage());
        throw new IllegalStateException("Tiangge " + operation + " failed after " + MAX_RETRIES + " attempts", lastException);
    }

    @FunctionalInterface
    private interface ApiCallable<T> {
        T call() throws Exception;
    }
}
