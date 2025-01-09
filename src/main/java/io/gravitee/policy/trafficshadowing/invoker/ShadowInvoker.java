/*
 * Copyright © 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.policy.trafficshadowing.invoker;

import static org.springframework.util.StringUtils.hasText;

import com.google.api.Http;
import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.api.http.HttpHeaders;
import io.gravitee.gateway.reactive.api.connector.endpoint.HttpEndpointConnector;
import io.gravitee.gateway.reactive.api.context.http.HttpExecutionContext;
import io.gravitee.gateway.reactive.api.invoker.HttpInvoker;
import io.gravitee.gateway.reactive.core.v4.endpoint.EndpointCriteria;
import io.gravitee.gateway.reactive.core.v4.endpoint.EndpointManager;
import io.gravitee.gateway.reactive.core.v4.endpoint.ManagedEndpoint;
import io.gravitee.policy.trafficshadowing.configuration.HttpHeader;
import io.gravitee.policy.trafficshadowing.configuration.TrafficShadowingPolicyConfiguration;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.processors.UnicastProcessor;
import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ShadowInvoker implements HttpInvoker {

    private final HttpInvoker defaultInvoker;
    private final TrafficShadowingPolicyConfiguration configuration;

    public ShadowInvoker(HttpInvoker defaultInvoker, TrafficShadowingPolicyConfiguration configuration) {
        this.defaultInvoker = defaultInvoker;
        this.configuration = configuration;
    }

    @Override
    public String getId() {
        return "shadow-invoker";
    }

    @Override
    public Completable invoke(HttpExecutionContext ctx) {
        var endpoint = getShadowEndpoint(ctx);
        if (endpoint == null) {
            log.info("No shadow endpoint found for target: {}", configuration.getTarget());
            return defaultInvoker.invoke(ctx);
        }
        log.debug("Shadowing request to endpoint: {}", endpoint.getDefinition().getName());

        var requestChunkProcessor = UnicastProcessor.<Buffer>create(
            UnicastProcessor.bufferSize(),
            () -> log.debug("Shadow request chunk processor terminated")
        );

        ctx
            .request()
            .chunks(
                ctx
                    .request()
                    .chunks()
                    .doOnEach(event -> {
                        try {
                            if (event.isOnNext()) {
                                requestChunkProcessor.onNext(event.getValue());
                            } else if (event.isOnError()) {
                                requestChunkProcessor.onError(event.getError());
                            } else if (event.isOnComplete()) {
                                requestChunkProcessor.onComplete();
                            }
                        } catch (Exception e) {
                            // Should not happen but just in case
                            log.error("Forwarding request chunk to shadow failed", e);
                        }
                    })
            );
        ShadowHttpExecutionContext shadowCtx = new ShadowHttpExecutionContext(ctx, prepareShadowRequest(ctx, requestChunkProcessor));

        HttpEndpointConnector connector = endpoint.getConnector();
        return Completable
            .mergeArray(
                defaultInvoker.invoke(ctx),
                connector
                    .connect(shadowCtx)
                    .andThen(
                        Completable.defer(() ->
                            shadowCtx
                                .response()
                                .end(shadowCtx)
                                .doOnComplete(() -> {
                                    log.debug("Traffic shadowing status is: {}", shadowCtx.response().status());
                                })
                        )
                    )
                    .doOnError(throwable -> log.error("An error occurred while connecting to shadow endpoint", throwable))
                    .doOnDispose(() -> log.debug("Shadow connection disposed"))
                    .onErrorComplete()
            )
            .doFinally(() -> {
                // UniCasProcessor should complete when request chunks flowable complete, the following line is a safety net
                if (!requestChunkProcessor.hasComplete()) {
                    requestChunkProcessor.onComplete();
                }
            });
    }

    private ManagedEndpoint getShadowEndpoint(HttpExecutionContext ctx) {
        var target = ctx.getTemplateEngine().evalNow(configuration.getTarget(), String.class);

        if (target.endsWith(":")) {
            target = target.substring(0, target.length() - 1);
        }

        var endpointManager = ctx.getComponent(EndpointManager.class);
        var endpointCriteria = new EndpointCriteria();
        endpointCriteria.setName(target);

        return endpointManager.next(endpointCriteria);
    }

    private ShadowRequest prepareShadowRequest(HttpExecutionContext ctx, UnicastProcessor<Buffer> requestChunkProcessor) {
        var shadowRequest = new ShadowRequest(ctx.request(), requestChunkProcessor);
        shadowRequest.headers(handleConfigHeader(ctx));
        return shadowRequest;
    }

    private HttpHeaders handleConfigHeader(HttpExecutionContext ctx) {
        var shadowHeaders = HttpHeaders.create(ctx.request().headers());
        if (configuration.getHeaders() != null) {
            configuration.getHeaders().forEach(configHeader -> addConfigHeader(ctx, shadowHeaders, configHeader));
        }
        return shadowHeaders;
    }

    private void addConfigHeader(HttpExecutionContext ctx, HttpHeaders headers, HttpHeader header) {
        try {
            if (!hasText(header.getName())) {
                log.debug("Shadowed request header name is empty. The header will not be added to the request");
                return;
            }
            String value = ctx.getTemplateEngine().evalNow(header.getValue(), String.class);
            if (!hasText(value)) {
                log.debug("Shadowed request header {} value is empty. The header will not be added to the request", header.getName());
                return;
            }
            headers.set(header.getName(), value);
        } catch (Exception e) {
            log.debug("Shadowed request header raised an error. The header will not be added to the request", e);
        }
    }
}
