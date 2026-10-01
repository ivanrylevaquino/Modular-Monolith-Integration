package edu.cit.aquino.channel;

import java.util.List;
import java.util.Optional;

public interface ChannelService {
    void publishListings();
    void syncStock();
    Optional<ChannelOrderInfo> getOrderInfo(String orderId);
    long getLastFeedCursor();
    List<ChannelListing> getActiveListings();
}
