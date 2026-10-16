package io.quarkus.opentelemetry.runtime.tracing.cdi;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Internal interceptor binding carrying the pre-computed {@code code.function.name} of a {@code @WithSpan} method.
 * <p>
 * It is added at build time to methods for which the intercepted method does not belong to the class the user
 * wrote, e.g. the generated {@code $$CDIWrapper} of a REST client interface. The {@link WithSpanInterceptor} uses
 * the value when present instead of deriving the name from the intercepted method.
 * <p>
 * The annotation is package-private on purpose: it is only ever added by the build-time annotation transformation of
 * the OpenTelemetry extension and must not be used by applications.
 */
@Target(METHOD)
@Retention(RUNTIME)
@interface SpanCodeFunction {

    String value();
}
