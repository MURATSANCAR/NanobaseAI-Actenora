package com.nanobaseai.actenora.security.messaging;

import com.nanobaseai.actenora.sharedkernel.messaging.port.DeadLetterStore;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public final class MessagingDlqMetrics implements MeterBinder {

    private final DeadLetterStore deadLetterStore;

    public MessagingDlqMetrics(DeadLetterStore deadLetterStore) {
        this.deadLetterStore = Objects.requireNonNull(deadLetterStore);
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder(
                        "actenora.messaging.dlq.depth",
                        deadLetterStore,
                        store -> store.listOpen(10_000).size())
                .description("Open application dead-letter events")
                .register(registry);
    }
}
