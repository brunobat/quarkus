package io.quarkus.opentelemetry.deployment.logs;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.quarkus.opentelemetry.deployment.common.InMemoryLogRecordExporter;
import io.quarkus.opentelemetry.deployment.common.InMemoryLogRecordExporterProvider;
import io.quarkus.test.QuarkusUnitTest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class JBossLoggingTest {

    private static final String MESSAGE = "Hello from JBoss Logging";

    @RegisterExtension
    static final QuarkusUnitTest TEST = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class)
                            .addClass(HelloBean.class)
                            .addClasses(InMemoryLogRecordExporter.class, InMemoryLogRecordExporterProvider.class)
                            .addAsResource(new StringAsset(InMemoryLogRecordExporterProvider.class.getCanonicalName()),
                                    "META-INF/services/io.opentelemetry.sdk.autoconfigure.spi.logs.ConfigurableLogRecordExporterProvider")
                            .add(new StringAsset(
                                            "quarkus.otel.logs.enabled=true\n" +
                                                    "quarkus.otel.traces.exporter=none\n"),
                                    "application.properties"));

    @Inject
    InMemoryLogRecordExporter logRecordExporter;

    @Inject
    HelloBean helloBean;

    @BeforeEach
    void setup() {
        logRecordExporter.reset();
    }

    @Test
    public void testJBossLogging() {
        helloBean.hello();
        List<LogRecordData> finishedLogRecordItems = logRecordExporter.getFinishedLogRecordItemsAtLeast(4);
        assertThat(finishedLogRecordItems.getLast().getBody().asString()).isEqualTo(MESSAGE);
    }

    @ApplicationScoped
    public static class HelloBean {
        private static final Logger LOG = Logger.getLogger(HelloBean.class.getName());

        public String hello() {
            LOG.info(MESSAGE);
            return "hello";
        }
    }
}
