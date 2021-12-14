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

import io.gravitee.common.http.HttpHeaders;
import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.api.endpoint.resolver.EndpointResolver;
import io.gravitee.gateway.api.endpoint.resolver.ProxyEndpoint;
import io.gravitee.gateway.api.proxy.ProxyRequest;
import io.gravitee.gateway.api.stream.ReadWriteStream;
import io.gravitee.policy.api.annotations.OnRequestContent;
import io.gravitee.policy.trafficshadowing.configuration.TrafficShadowingPolicyConfiguration;
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

        final String target = context.getTemplateEngine().getValue(configuration.getTarget(), String.class);
        ProxyEndpoint endpoint = endpointResolver.resolve(target);

        if (endpoint != null) {
            HttpHeaders shadowingHeaders = addShadowingHeaders(context.request().headers(), context);
            ProxyRequest proxyRequest = endpoint.createProxyRequest(
                context.request(),
                proxyRequestBuilder -> proxyRequestBuilder.headers(shadowingHeaders)
            );

            final QueuedProxyConnection queuedProxyConnection = new QueuedProxyConnection(
                endpoint.connector(),
                proxyRequest,
                proxyResponse -> {
                    LOGGER.debug("Traffic shadowing status is: {}", proxyResponse.status());
                    proxyResponse.bodyHandler(buffer -> {}).endHandler(handler -> {});
                }
            );

            return new PressureSafeWriteStream(queuedProxyConnection);
        }

        return null;
    }

    private HttpHeaders addShadowingHeaders(HttpHeaders headers, ExecutionContext context) {
        HttpHeaders httpHeaders = new HttpHeaders(headers);
        if (configuration.getHeaders() != null) {
            configuration
                .getHeaders()
                .forEach(
                    header -> {
                        if (header.getName() != null && !header.getName().trim().isEmpty()) {
                            try {
                                String extValue = (header.getValue() != null)
                                    ? context.getTemplateEngine().convert(header.getValue())
                                    : null;
                                if (extValue != null) {
                                    httpHeaders.set(header.getName(), extValue);
                                }
                            } catch (Exception ex) {
                                LOGGER.error("Error adding header {}", header.getName(), ex);
                            }
                        }
                    }
                );
        }
        return httpHeaders;
    }
}
