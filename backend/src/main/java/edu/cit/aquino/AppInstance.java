package edu.cit.aquino;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Component
public class AppInstance {
    private static final Logger log = LoggerFactory.getLogger(AppInstance.class);

    private final String instanceId;
    private final Instant startedAt;

    public AppInstance() {
        this.instanceId = UUID.randomUUID().toString();
        this.startedAt = Instant.now();
        log.info("=================================================");
        log.info("App starting with Instance ID: {}", this.instanceId);
        log.info("Started at: {}", this.startedAt);
        log.info("=================================================");
    }

    public String getInstanceId() {
        return instanceId;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public long getUptimeSeconds() {
        return Duration.between(startedAt, Instant.now()).getSeconds();
    }
}
