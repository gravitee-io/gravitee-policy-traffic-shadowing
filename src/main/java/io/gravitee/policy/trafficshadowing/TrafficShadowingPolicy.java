/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.policy.trafficshadowing;

import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.api.endpoint.resolver.EndpointResolver;
import io.gravitee.gateway.api.endpoint.resolver.ProxyEndpoint;
import io.gravitee.gateway.api.http.HttpHeaders;
import io.gravitee.gateway.api.proxy.ProxyConnection;
import io.gravitee.gateway.api.proxy.ProxyRequest;
import io.gravitee.gateway.api.stream.BufferedReadWriteStream;
import io.gravitee.gateway.api.stream.ReadWriteStream;
import io.gravitee.gateway.api.stream.SimpleReadWriteStream;
import io.gravitee.policy.api.annotations.OnRequestContent;
import io.gravitee.policy.trafficshadowing.configuration.TrafficShadowingPolicyConfiguration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class TrafficShadowingPolicy {

    private static final Logger LOGGER = LoggerFactory.getLogger(TrafficShadowingPolicy.class);

    private final TrafficShadowingPolicyConfiguration configuration;

    public TrafficShadowingPolicy(final TrafficShadowingPolicyConfiguration configuration) {
        this.configuration = configuration;
    }

    @OnRequestContent
    public ReadWriteStream<Buffer> onRequestContent(ExecutionContext context) {
        final EndpointResolver endpointResolver = context.getComponent(EndpointResolver.class);

        String configurationTarget = configuration.getTarget();
        String target = context.getTemplateEngine().eval(configurationTarget, String.class).blockingGet();
        ProxyEndpoint endpoint = endpointResolver.resolve(target);

        if (endpoint != null) {
            HttpHeaders shadowingHeaders = addShadowingHeaders(context.request().headers(), context);
            ProxyRequest proxyRequest = endpoint.createProxyRequest(
                context.request(),
                proxyRequestBuilder -> proxyRequestBuilder.headers(shadowingHeaders)
            );

            return new ShadowingReadWriteStream(proxyRequest, context, endpoint);
        }

        return null;
    }

    private HttpHeaders addShadowingHeaders(HttpHeaders headers, ExecutionContext context) {
        HttpHeaders httpHeaders = HttpHeaders.create(headers);
        if (configuration.getHeaders() != null) {
            configuration
                .getHeaders()
                .forEach(header -> {
                    if (header.getName() != null && !header.getName().trim().isEmpty()) {
                        try {
                            String extValue = (header.getValue() != null)
                                ? context.getTemplateEngine().eval(header.getValue(), String.class).blockingGet()
                                : null;
                            if (extValue != null) {
                                httpHeaders.set(header.getName(), extValue);
                            }
                        } catch (Exception ex) {
                            // Do nothing
                            ex.printStackTrace();
                        }
                    }
                });
        }
        return httpHeaders;
    }

    private static class ShadowingReadWriteStream extends BufferedReadWriteStream {

        ProxyConnection connection;

        List<Buffer> chunkBuffers = new ArrayList<>();

        public ShadowingReadWriteStream(ProxyRequest proxyRequest, ExecutionContext context, ProxyEndpoint endpoint) {
            endpoint
                .connector()
                .request(
                    proxyRequest,
                    context,
                    connection -> {
                        this.connection = connection;

                        connection.responseHandler(response -> {
                            LOGGER.debug("Traffic shadowing status is: {}", response.status());
                        });
                        connection.exceptionHandler(throwable -> {
                            LOGGER.error("An error occurs while sending traffic shadowing request", throwable);
                        });
                    }
                );
        }

        @Override
        public SimpleReadWriteStream<Buffer> write(Buffer chunk) {
            if (connection == null) {
                chunkBuffers.add(chunk);
            } else {
                sendPendingChunkBuffers();
                writeChunk(chunk);
            }

            return super.write(chunk);
        }

        private void sendPendingChunkBuffers() {
            if (!chunkBuffers.isEmpty()) {
                chunkBuffers.forEach(this::writeChunk);
                chunkBuffers.clear();
            }
        }

        private void writeChunk(Buffer buffer) {
            connection.write(buffer);
            if (connection.writeQueueFull()) {
                pause();
                connection.drainHandler(aVoid -> resume());
            }
        }

        @Override
        public void end() {
            if (connection == null) {
                LOGGER.warn("No connection available to send traffic shadowing request");
            } else {
                sendPendingChunkBuffers();
                connection.end();
            }
            super.end();
        }
    }
}
