package edu.cit.aquino.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
class TianggeLifecycleManager {
    private static final Logger log = LoggerFactory.getLogger(TianggeLifecycleManager.class);

    private final TianggeOrderRepository repository;
    private final TianggeHeartbeatJob heartbeatJob;
    private final ChannelService channelService;

    TianggeLifecycleManager(
            TianggeOrderRepository repository,
            TianggeHeartbeatJob heartbeatJob,
            ChannelService channelService
    ) {
        this.repository = repository;
        this.heartbeatJob = heartbeatJob;
        this.channelService = channelService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Starting Tiangge Lifecycle Initialization...");
        try {
            // 1. Initialize DB schema
            repository.initSchema();

            // 2. Send initial heartbeat to register instance as online
            heartbeatJob.sendHeartbeatNow();

            // 3. Publish listings
            channelService.publishListings();

            // 4. Publish initial stock
            channelService.syncStock();

            log.info("Tiangge shop is now LIVE and online!");
        } catch (Exception e) {
            log.error("Failed during Tiangge lifecycle startup: {}", e.getMessage(), e);
        }
    }
}
