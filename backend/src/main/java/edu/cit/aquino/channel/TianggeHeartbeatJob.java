package edu.cit.aquino.channel;

import edu.cit.aquino.AppInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

@Component
class TianggeHeartbeatJob {
    private static final Logger log = LoggerFactory.getLogger(TianggeHeartbeatJob.class);

    private final TianggeClient client;
    private final AppInstance appInstance;
    private final String appName;

    TianggeHeartbeatJob(
            TianggeClient client,
            AppInstance appInstance,
            @Value("${spring.application.name:shop-modular-monolith}") String appName
    ) {
        this.client = client;
        this.appInstance = appInstance;
        this.appName = appName;
    }

    @Scheduled(fixedDelay = 30000, initialDelay = 15000)
    public void sendHeartbeat() {
        try {
            sendHeartbeatNow();
        } catch (Exception e) {
            log.warn("Failed to send Tiangge heartbeat: {}", e.getMessage(), e);
        }
    }

    void sendHeartbeatNow() {
        String startedAtIso = DateTimeFormatter.ISO_INSTANT.format(appInstance.getStartedAt());
        TianggeHeartbeatRequest req = new TianggeHeartbeatRequest(
                appName,
                startedAtIso,
                appInstance.getUptimeSeconds()
        );

        TianggeHeartbeatResponse resp = client.sendHeartbeat(req);
        if (resp != null) {
            log.info("Tiangge heartbeat acknowledged at serverTime: {}, next in: {}s",
                    resp.serverTime(), resp.nextHeartbeatSeconds());
        }
    }
}
