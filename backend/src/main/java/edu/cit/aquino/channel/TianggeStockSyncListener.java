package edu.cit.aquino.channel;

import edu.cit.aquino.shop.OrderCancelledEvent;
import edu.cit.aquino.shop.OrderPlacedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
class TianggeStockSyncListener {
    private static final Logger log = LoggerFactory.getLogger(TianggeStockSyncListener.class);

    private final ChannelService channelService;

    TianggeStockSyncListener(ChannelService channelService) {
        this.channelService = channelService;
    }

    @EventListener
    public void onOrderPlaced(OrderPlacedEvent event) {
        if (TianggeContext.isTiangge()) {
            // Tiangge feed processing publishes stock synchronously AFTER recording the decision
            return;
        }
        log.info("Non-Tiangge order placed (Order #{}), syncing stock to Tiangge", event.orderId());
        channelService.syncStock();
    }

    @EventListener
    public void onOrderCancelled(OrderCancelledEvent event) {
        if (TianggeContext.isTiangge()) {
            // Tiangge cancellation processing publishes stock synchronously AFTER confirming cancellation
            return;
        }
        log.info("Non-Tiangge order cancelled (Order #{}), syncing stock to Tiangge", event.orderId());
        channelService.syncStock();
    }
}
