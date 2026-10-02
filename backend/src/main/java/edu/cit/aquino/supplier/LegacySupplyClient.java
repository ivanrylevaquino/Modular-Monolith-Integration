package edu.cit.aquino.supplier;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

@Component
class LegacySupplyClient {
    private static final Logger log = LoggerFactory.getLogger(LegacySupplyClient.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofMillis(2500);
    private static final Duration REQUEST_TIMEOUT = Duration.ofMillis(3000);
    private static final long SESSION_TTL_SECONDS = 150; // proactive refresh before 180s expiry

    private final String baseUrl;
    private final String clientId;
    private final String apiKey;
    private final HttpClient httpClient;
    private final XmlMapper xmlMapper;

    private final AtomicReference<String> sessionToken = new AtomicReference<>(null);
    private final AtomicReference<Instant> tokenExpiresAt = new AtomicReference<>(Instant.MIN);
    private final edu.cit.aquino.AppInstance appInstance;

    LegacySupplyClient(
            @Value("${legacysupply.base-url:https://legacysupply.onrender.com/api/v1}") String baseUrl,
            @Value("${legacysupply.client-id:22-2068-823}") String clientId,
            @Value("${legacysupply.api-key:}") String apiKey,
            @org.springframework.beans.factory.annotation.Autowired(required = false) edu.cit.aquino.AppInstance appInstance
    ) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.clientId = (clientId != null && !clientId.isBlank()) ? clientId : System.getenv().getOrDefault("LS_CLIENT_ID", "22-2068-823");
        String key = (apiKey != null && !apiKey.isBlank()) ? apiKey : System.getenv("LS_API_KEY");
        this.apiKey = key != null ? key : "";
        this.appInstance = appInstance;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        this.xmlMapper = new XmlMapper();
    }

    private HttpRequest.Builder createRequestBuilder(String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(REQUEST_TIMEOUT);
        if (appInstance != null && appInstance.getInstanceId() != null) {
            builder.header("X-Client-Instance", appInstance.getInstanceId());
        }
        return builder;
    }

    synchronized String getOrRenewSession(boolean forceRefresh) {
        Instant now = Instant.now();
        String current = sessionToken.get();
        if (!forceRefresh && current != null && now.isBefore(tokenExpiresAt.get())) {
            return current;
        }

        try {
            log.info("Requesting new LegacySupply session token for client {}", clientId);
            AuthRequestXml authReq = new AuthRequestXml(clientId, apiKey);
            String requestXml = xmlMapper.writeValueAsString(authReq);

            HttpRequest request = createRequestBuilder("/auth/token")
                    .header("Content-Type", "application/xml")
                    .header("Accept", "application/xml")
                    .POST(HttpRequest.BodyPublishers.ofString(requestXml))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                LSErrorXml err = parseErrorXml(response.body());
                String code = err != null ? err.getCode() : "HTTP_" + response.statusCode();
                String msg = err != null ? err.getMessage() : response.body();
                throw new LegacySupplyException("Failed to authenticate with LegacySupply: " + msg, code, response.statusCode());
            }

            AuthResponseXml authResp = xmlMapper.readValue(response.body(), AuthResponseXml.class);
            String token = authResp.getSessionToken();
            sessionToken.set(token);
            tokenExpiresAt.set(now.plusSeconds(SESSION_TTL_SECONDS));
            log.info("Acquired LegacySupply session token successfully");
            return token;
        } catch (LegacySupplyException e) {
            throw e;
        } catch (Exception e) {
            throw new LegacySupplyException("Error during LegacySupply authentication: " + e.getMessage(), e);
        }
    }

    PurchaseOrderAckXml placePurchaseOrder(PurchaseOrderXml order, String requestId) {
        return executeWithSessionHandling((token) -> {
            String xmlBody = xmlMapper.writeValueAsString(order);
            HttpRequest.Builder reqBuilder = createRequestBuilder("/purchase-orders")
                    .header("Content-Type", "application/xml")
                    .header("Accept", "application/xml")
                    .header("X-LS-Session", token)
                    .POST(HttpRequest.BodyPublishers.ofString(xmlBody));

            if (requestId != null && !requestId.isBlank()) {
                reqBuilder.header("X-Request-Id", requestId);
            }

            HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 201 || response.statusCode() == 200) {
                return xmlMapper.readValue(response.body(), PurchaseOrderAckXml.class);
            }

            LSErrorXml err = parseErrorXml(response.body());
            String code = err != null ? err.getCode() : "HTTP_" + response.statusCode();
            String msg = err != null ? err.getMessage() : response.body();
            throw new LegacySupplyException("Failed to place purchase order: " + msg, code, response.statusCode());
        });
    }

    PurchaseOrderStatusXml getOrderStatus(String poNumber) {
        return executeWithSessionHandling((token) -> {
            HttpRequest request = createRequestBuilder("/purchase-orders/" + poNumber)
                    .header("Accept", "application/xml")
                    .header("X-LS-Session", token)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return xmlMapper.readValue(response.body(), PurchaseOrderStatusXml.class);
            }

            LSErrorXml err = parseErrorXml(response.body());
            String code = err != null ? err.getCode() : "HTTP_" + response.statusCode();
            String msg = err != null ? err.getMessage() : response.body();
            throw new LegacySupplyException("Failed to get order status: " + msg, code, response.statusCode());
        });
    }

    PurchaseOrderListXml getOrdersByBuyerRef(String buyerRef) {
        return executeWithSessionHandling((token) -> {
            HttpRequest request = createRequestBuilder("/purchase-orders?buyerRef=" + buyerRef)
                    .header("Accept", "application/xml")
                    .header("X-LS-Session", token)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                PurchaseOrderListXml list;
                try {
                    list = xmlMapper.readValue(response.body(), PurchaseOrderListXml.class);
                } catch (Exception e) {
                    list = new PurchaseOrderListXml();
                }
                if (list.getOrders() == null || list.getOrders().isEmpty()) {
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("<PoNumber>([^<]+)</PoNumber>").matcher(response.body());
                    if (m.find()) {
                        String po = m.group(1);
                        PurchaseOrderAckXml fallbackAck = new PurchaseOrderAckXml();
                        fallbackAck.setPoNumber(po);
                        fallbackAck.setBuyerRef(buyerRef);
                        list.setOrders(java.util.List.of(fallbackAck));
                    }
                }
                return list;
            }

            LSErrorXml err = parseErrorXml(response.body());
            String code = err != null ? err.getCode() : "HTTP_" + response.statusCode();
            String msg = err != null ? err.getMessage() : response.body();
            throw new LegacySupplyException("Failed to query order by buyerRef: " + msg, code, response.statusCode());
        });
    }

    private <T> T executeWithSessionHandling(ApiCall<T> call) {
        String token = getOrRenewSession(false);
        try {
            return call.execute(token);
        } catch (LegacySupplyException e) {
            if (isSessionError(e)) {
                log.warn("Session rejected by LegacySupply (code={}, status={}), renewing and retrying once",
                        e.getErrorCode(), e.getHttpStatus());
                String freshToken = getOrRenewSession(true);
                try {
                    return call.execute(freshToken);
                } catch (Exception retryEx) {
                    if (retryEx instanceof LegacySupplyException lse) {
                        throw lse;
                    }
                    throw new LegacySupplyException("Retry after session renewal failed: " + retryEx.getMessage(), retryEx);
                }
            }
            throw e;
        } catch (Exception e) {
            throw new LegacySupplyException("Network error contacting LegacySupply: " + e.getMessage(), e);
        }
    }

    private boolean isSessionError(LegacySupplyException e) {
        if (e.getHttpStatus() == 401) {
            return true;
        }
        String code = e.getErrorCode();
        return "E-AUTH-02".equals(code) || "E-AUTH-03".equals(code) || "E-AUTH-07".equals(code);
    }

    private LSErrorXml parseErrorXml(String xml) {
        if (xml == null || xml.isBlank()) return null;
        try {
            return xmlMapper.readValue(xml, LSErrorXml.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    @FunctionalInterface
    private interface ApiCall<T> {
        T execute(String token) throws Exception;
    }
}
