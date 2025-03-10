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

import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.gateway.api.Invoker;
import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.api.endpoint.resolver.EndpointResolver;
import io.gravitee.gateway.api.endpoint.resolver.ProxyEndpoint;
import io.gravitee.gateway.api.handler.Handler;
import io.gravitee.gateway.api.http.HttpHeaders;
import io.gravitee.gateway.api.proxy.ProxyConnection;
import io.gravitee.gateway.api.proxy.ProxyRequest;
import io.gravitee.gateway.api.stream.ReadStream;
import io.gravitee.policy.trafficshadowing.configuration.HttpHeader;
import io.gravitee.policy.trafficshadowing.configuration.TrafficShadowingPolicyConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author GraviteeSource Team
 */
public class ShadowInvoker implements Invoker {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShadowInvoker.class);

    private final Invoker invoker;
    private final TrafficShadowingPolicyConfiguration configuration;

    public ShadowInvoker(Invoker invoker, TrafficShadowingPolicyConfiguration configuration) {
        this.invoker = invoker;
        this.configuration = configuration;
    }

    @Override
    public void invoke(ExecutionContext context, ReadStream<Buffer> stream, Handler<ProxyConnection> connectionHandler) {
        final EndpointResolver endpointResolver = context.getComponent(EndpointResolver.class);

        String configurationTarget = configuration.getTarget();
        String target = context.getTemplateEngine().convert(configurationTarget);
        ProxyEndpoint endpoint = endpointResolver.resolve(target);

        if (endpoint == null) {
            // No shadow endpoint resolved, keep continuing with standard endpoint.
            invoker.invoke(context, stream, connectionHandler);
        } else {
            invoke(context, stream, connectionHandler, endpoint);
        }
    }

    private void invoke(
        ExecutionContext context,
        ReadStream<Buffer> stream,
        Handler<ProxyConnection> connectionHandler,
        ProxyEndpoint endpoint
    ) {
        final HttpHeaders shadowHeaders = addConfigHeaders(context.request().headers(), context);
        final ProxyRequest shadowRequest = endpoint.createProxyRequest(
            context.request(),
            proxyRequestBuilder -> proxyRequestBuilder.headers(shadowHeaders)
        );

        endpoint
            .connector()
            .request(
                shadowRequest,
                context,
                shadowConnection -> {
                    shadowConnection.responseHandler(response -> {
                        LOGGER.debug("Traffic shadowing status is: {}", response.status());

                        response.bodyHandler(noop -> {}).endHandler(noop -> {});

                        // Resume the shadow response to read the stream and mark as ended
                        response.resume();
                    });

                    shadowConnection.exceptionHandler(throwable ->
                        LOGGER.error("An error occurs while sending traffic shadowing request", throwable)
                    );

                    invoker.invoke(
                        context,
                        stream,
                        backendConnection -> {
                            final ShadowProxyConnection shadowProxyConnection = new ShadowProxyConnection(
                                backendConnection,
                                shadowConnection
                            );

                            // Plug underlying stream to connection stream
                            stream.bodyHandler(shadowProxyConnection::write);
                            stream.endHandler(aVoid -> shadowProxyConnection.end());

                            connectionHandler.handle(shadowProxyConnection);
                        }
                    );
                }
            );
    }

    private HttpHeaders addConfigHeaders(HttpHeaders headers, ExecutionContext context) {
        HttpHeaders shadowHeaders = HttpHeaders.create(headers);
        if (configuration.getHeaders() != null) {
            configuration.getHeaders().forEach(header -> addConfigHeader(context, shadowHeaders, header));
        }
        return shadowHeaders;
    }

    private void addConfigHeader(ExecutionContext context, HttpHeaders headers, HttpHeader header) {
        try {
            if (!hasText(header.getName())) {
                LOGGER.debug("Shadowing header name is empty. The header will not be added to the request");
                return;
            }
            String value = context.getTemplateEngine().convert(header.getValue());
            if (!hasText(value)) {
                LOGGER.debug("Shadowing header value is empty. The header will not be added to the request");
                return;
            }
            headers.set(header.getName(), value);
        } catch (Exception e) {
            LOGGER.debug("Shadowing header raised an error. The header will not be added to the request", e);
        }
    }
}
