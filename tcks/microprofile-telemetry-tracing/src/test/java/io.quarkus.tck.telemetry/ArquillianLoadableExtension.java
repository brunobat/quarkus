package io.quarkus.tck.telemetry;

import org.jboss.arquillian.container.test.spi.client.deployment.ApplicationArchiveProcessor;
import org.jboss.arquillian.core.spi.LoadableExtension;

public class ArquillianLoadableExtension implements LoadableExtension {
    @Override
    public void register(ExtensionBuilder extensionBuilder) {
        extensionBuilder.service(ApplicationArchiveProcessor.class, DeploymentProcessor.class);
        //        extensionBuilder.observer(ArquillianLifecycle.class);
    }
}