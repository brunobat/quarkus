package io.quarkus.opentelemetry.deployment.common;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import org.awaitility.Awaitility;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

@Unremovable
@ApplicationScoped
public class InMemoryLogRecordExporter implements LogRecordExporter {
    final Queue<LogRecordData> finishedLogItems = new ConcurrentLinkedQueue();
    boolean isStopped = false;

    private InMemoryLogRecordExporter() {
    }

    public static InMemoryLogRecordExporter create() {
        return new InMemoryLogRecordExporter();
    }

    public List<LogRecordData> getFinishedLogRecordItemsAtLeast(final int count) {
        Awaitility.await().atMost(5, SECONDS)
                .untilAsserted(() -> assertThat(getFinishedLogRecordItems().size()).isGreaterThanOrEqualTo(count));
        return getFinishedLogRecordItems();
    }

    private List<LogRecordData> getFinishedLogRecordItems() {
        return Collections.unmodifiableList(new ArrayList(this.finishedLogItems));
    }


    public void reset() {
        this.finishedLogItems.clear();
    }

    public CompletableResultCode export(Collection<LogRecordData> logs) {
        if (this.isStopped) {
            return CompletableResultCode.ofFailure();
        } else {
            this.finishedLogItems.addAll(logs);
            return CompletableResultCode.ofSuccess();
        }
    }

    public CompletableResultCode flush() {
        return CompletableResultCode.ofSuccess();
    }

    public CompletableResultCode shutdown() {
        this.isStopped = true;
        this.finishedLogItems.clear();
        return CompletableResultCode.ofSuccess();
    }
}
