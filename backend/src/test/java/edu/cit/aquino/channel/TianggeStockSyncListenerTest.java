package edu.cit.aquino.channel;

import edu.cit.aquino.shop.OrderCancelledEvent;
import edu.cit.aquino.shop.OrderPlacedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.*;

class TianggeStockSyncListenerTest {
    private ChannelService channelService;
    private TianggeStockSyncListener listener;

    @BeforeEach
    void setUp() {
        channelService = mock(ChannelService.class);
        listener = new TianggeStockSyncListener(channelService);
    }

    @Test
    void syncsStockOnNonTianggeOrderPlaced() {
        TianggeContext.clear();
        listener.onOrderPlaced(new OrderPlacedEvent(1L, List.of()));
        verify(channelService).syncStock();
    }

    @Test
    void skipsStockSyncOnTianggeOrderPlaced() {
        try {
            TianggeContext.set(true);
            listener.onOrderPlaced(new OrderPlacedEvent(1L, List.of()));
            verify(channelService, never()).syncStock();
        } finally {
            TianggeContext.clear();
        }
    }

    @Test
    void syncsStockOnNonTianggeOrderCancelled() {
        TianggeContext.clear();
        listener.onOrderCancelled(new OrderCancelledEvent(1L, List.of()));
        verify(channelService).syncStock();
    }

    @Test
    void skipsStockSyncOnTianggeOrderCancelled() {
        try {
            TianggeContext.set(true);
            listener.onOrderCancelled(new OrderCancelledEvent(1L, List.of()));
            verify(channelService, never()).syncStock();
        } finally {
            TianggeContext.clear();
        }
    }
}
