package edu.cit.aquino.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class TianggeLifecycleManager {
    private static final Logger log = LoggerFactory.getLogger(TianggeLifecycleManager.class);

    private final TianggeOrderRepository repository;
    private final TianggeHeartbeatJob heartbeatJob;
    private final ChannelService channelService;
    private volatile boolean initialized;

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
        initializeShop();
    }

    @Scheduled(fixedDelay = 30000, initialDelay = 30000)
    public void retryInitialization() {
        if (!initialized) {
            initializeShop();
        }
    }

    private synchronized void initializeShop() {
        if (initialized) {
            return;
        }

        log.info("Starting Tiangge Lifecycle Initialization...");
        try {
            repository.initSchema();
            heartbeatJob.sendHeartbeatNow();
            channelService.publishListings();
            channelService.syncStock();
            initialized = true;
            log.info("Tiangge shop is now LIVE and online!");
        } catch (Exception e) {
            log.error("Tiangge lifecycle initialization failed; it will be retried: {}", e.getMessage(), e);
        }
    }
}
