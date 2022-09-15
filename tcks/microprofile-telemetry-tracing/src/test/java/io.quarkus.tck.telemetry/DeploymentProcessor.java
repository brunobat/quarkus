package io.quarkus.tck.telemetry;

import org.eclipse.microprofile.config.spi.ConfigSource;
import org.eclipse.microprofile.telemetry.tracing.tck.TestConfigSource;
import org.eclipse.microprofile.telemetry.tracing.tck.exporter.InMemorySpanExporter;
import org.jboss.arquillian.container.test.spi.client.deployment.ApplicationArchiveProcessor;
import org.jboss.arquillian.test.spi.TestClass;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ArchivePaths;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;

public class DeploymentProcessor implements ApplicationArchiveProcessor {
    @Override
    public void process(Archive<?> archive, TestClass testClass) {
        if (archive instanceof WebArchive) {
            WebArchive war = (WebArchive) archive;
            war.addAsWebInfResource(EmptyAsset.INSTANCE, ArchivePaths.create("beans.xml"));
            war.addAsServiceProvider(ConfigSource.class, TestConfigSource.class);
            war.addClass(InMemorySpanExporter.class);

            //            String[] deps = {
            //                    //                    "org.jboss.resteasy:resteasy-servlet-initializer",
            //                    // Required for web-fragment and init of the CdiInjectorFactory
            //                    //                    "org.jboss.resteasy:resteasy-cdi",
            //                    "io.quarkus:quarkus-rest-client",
            //                    "io.quarkus:quarkus-opentelemetry",
            //                    "io.quarkus:quarkus-opentelemetry-exporter-otlp",
            //            };
            //            File[] dependencies = Maven.configureResolver()
            //                    .workOffline()
            //                    .loadPomFromFile(new File("pom.xml"), "arquillian-jetty")
            //                    .resolve(deps)
            //                    .withoutTransitivity()
            //                    .asFile();
            //            war.addAsLibraries(dependencies);
        }
    }
}
