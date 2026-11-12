package com.dbtool.core.events;

import com.dbtool.core.model.DatabaseType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class WorkspaceEventBusTest {

    @Test
    void testSubscribePublishAndUnsubscribe() throws Exception {
        WorkspaceEventBus bus = new WorkspaceEventBus();
        List<String> received = new ArrayList<>();

        try (AutoCloseable sub = bus.subscribe(ConnectionChangedEvent.class,
                ev -> received.add(ev.getConnectionId() + ":" + ev.isConnected()))) {
            assertEquals(1, bus.subscriberCount(ConnectionChangedEvent.class));
            bus.publish(new ConnectionChangedEvent("source", true, "prod", DatabaseType.POSTGRESQL));
            assertEquals(List.of("source:true"), received);
        }

        assertEquals(0, bus.subscriberCount(ConnectionChangedEvent.class));
        bus.publish(new ConnectionChangedEvent("source", false, "prod", DatabaseType.POSTGRESQL));
        assertEquals(1, received.size(), "Unsubscribed listener must not receive subsequent events");
    }

    @Test
    void testBrokenListenerDoesNotBlockOtherSubscribers() {
        WorkspaceEventBus bus = new WorkspaceEventBus();
        List<Integer> rows = new ArrayList<>();

        bus.subscribe(QueryExecutedEvent.class, ev -> {
            throw new IllegalStateException("Simulated UI listener failure");
        });
        bus.subscribe(QueryExecutedEvent.class, ev -> rows.add(ev.getRowCount()));

        bus.publish(new QueryExecutedEvent("SELECT 1", 12, 1, true, null));
        assertEquals(List.of(1), rows);
    }
}
