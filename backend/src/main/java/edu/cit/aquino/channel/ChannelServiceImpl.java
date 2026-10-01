package edu.cit.aquino.channel;

import edu.cit.aquino.inventory.InventoryItem;
import edu.cit.aquino.inventory.InventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
class ChannelServiceImpl implements ChannelService {
    private static final Logger log = LoggerFactory.getLogger(ChannelServiceImpl.class);

    private static final List<ChannelListing> DEFAULT_LISTINGS = List.of(
            new ChannelListing("P100", "Wireless Mouse", "KTB-7686"),
            new ChannelListing("P200", "Mechanical Keyboard", "KTB-7976"),
            new ChannelListing("P300", "USB-C Hub", "KTB-1734")
    );

    private final TianggeClient client;
    private final TianggeOrderRepository repository;
    private final InventoryService inventoryService;

    ChannelServiceImpl(
            TianggeClient client,
            TianggeOrderRepository repository,
            InventoryService inventoryService
    ) {
        this.client = client;
        this.repository = repository;
        this.inventoryService = inventoryService;
    }

    @Override
    public void publishListings() {
        log.info("Publishing {} default listings to Tiangge", DEFAULT_LISTINGS.size());
        List<TianggeListingItem> items = DEFAULT_LISTINGS.stream()
                .map(l -> new TianggeListingItem(l.sellerSku(), l.title(), l.supplierSku()))
                .toList();
        client.publishListings(items);
    }

    @Override
    public void syncStock() {
        List<InventoryItem> items = inventoryService.getAllItems();
        if (items != null && !items.isEmpty()) {
            List<TianggeStockItem> stockItems = items.stream()
                    .map(i -> new TianggeStockItem(i.productId(), Math.max(0, i.stock())))
                    .toList();
            log.info("Syncing inventory stock to Tiangge for {} items", stockItems.size());
            client.publishStock(stockItems);
        }
    }

    @Override
    public Optional<ChannelOrderInfo> getOrderInfo(String orderId) {
        return repository.findOrder(orderId).map(r -> new ChannelOrderInfo(
                r.orderId(),
                r.eventId(),
                r.decision(),
                r.shopOrderId(),
                r.status()
        ));
    }

    @Override
    public long getLastFeedCursor() {
        return repository.getLastCursor();
    }

    @Override
    public List<ChannelListing> getActiveListings() {
        return DEFAULT_LISTINGS;
    }
}
