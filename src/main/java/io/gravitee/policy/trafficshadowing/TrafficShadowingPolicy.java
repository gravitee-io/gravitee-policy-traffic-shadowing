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
import io.gravitee.gateway.api.Invoker;
import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.api.endpoint.resolver.EndpointResolver;
import io.gravitee.gateway.api.endpoint.resolver.ProxyEndpoint;
import io.gravitee.gateway.api.handler.Handler;
import io.gravitee.gateway.api.http.HttpHeaders;
import io.gravitee.gateway.api.http2.HttpFrame;
import io.gravitee.gateway.api.proxy.ProxyConnection;
import io.gravitee.gateway.api.proxy.ProxyRequest;
import io.gravitee.gateway.api.proxy.ProxyResponse;
import io.gravitee.gateway.api.stream.ReadStream;
import io.gravitee.gateway.api.stream.WriteStream;
import io.gravitee.policy.api.PolicyChain;
import io.gravitee.policy.api.annotations.OnRequest;
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

    @OnRequest
    public void onRequest(ExecutionContext context, PolicyChain chain) {
        // Override the invoker
        Invoker defaultInvoker = (Invoker) context.getAttribute(ExecutionContext.ATTR_INVOKER);
        context.setAttribute(ExecutionContext.ATTR_INVOKER, new ShadowInvoker(defaultInvoker));

        chain.doNext(context.request(), context.response());
    }

    private class ShadowInvoker implements Invoker {

        private final Invoker invoker;

        public ShadowInvoker(Invoker invoker) {
            this.invoker = invoker;
        }

        @Override
        public void invoke(ExecutionContext context, ReadStream<Buffer> stream, Handler<ProxyConnection> connectionHandler) {
            final EndpointResolver endpointResolver = context.getComponent(EndpointResolver.class);

            String configurationTarget = configuration.getTarget();
            String target = context.getTemplateEngine().eval(configurationTarget, String.class).blockingGet();
            ProxyEndpoint endpoint = endpointResolver.resolve(target);

            if (endpoint == null) {
                // No shadow endpoint resolved, keep continuing with standard endpoint.
                invoker.invoke(
                        context,
                        stream,
                        connectionHandler
                );
            } else {
                final HttpHeaders shadowingHeaders = addShadowingHeaders(context.request().headers(), context);
                final ProxyRequest proxyRequest = endpoint.createProxyRequest(
                        context.request(),
                        proxyRequestBuilder -> proxyRequestBuilder.headers(shadowingHeaders)
                );

                endpoint
                        .connector()
                        .request(
                                proxyRequest,
                                context,
                                shadowConnection -> {
                                    shadowConnection.responseHandler(response -> {
                                        LOGGER.debug("Traffic shadowing status is: {}", response.status());

                                        response.bodyHandler(__ -> {})
                                                .endHandler(__ -> {});

                                        // Resume the shadow response to read the stream and mark as ended
                                        response.resume();
                                    });

                                    shadowConnection.exceptionHandler(throwable -> {
                                        LOGGER.error("An error occurs while sending traffic shadowing request", throwable);
                                    });

                                    invoker.invoke(
                                            context,
                                            stream,
                                            backendConnection -> {
                                                final ShadowProxyConnection shadowProxyConnection = new ShadowProxyConnection(backendConnection, shadowConnection);

                                                // Plug underlying stream to connection stream
                                                stream.bodyHandler(shadowProxyConnection::write).endHandler(aVoid -> shadowProxyConnection.end());

                                                connectionHandler.handle(shadowProxyConnection);
                                            });
                                }
                        );
            }
        }
    }

    private class ShadowProxyConnection implements ProxyConnection {
        private final ProxyConnection incomingProxyConnection, shadowConnection;

        private ShadowProxyConnection(ProxyConnection incomingProxyConnection, ProxyConnection shadowConnection) {
            this.incomingProxyConnection = incomingProxyConnection;
            this.shadowConnection = shadowConnection;
        }

        @Override
        public ProxyConnection writeCustomFrame(HttpFrame frame) {
            incomingProxyConnection.writeCustomFrame(frame);
            shadowConnection.writeCustomFrame(frame);
            return this;
        }

        @Override
        public ProxyConnection cancel() {
            incomingProxyConnection.cancel();
            shadowConnection.cancel();
            return this;
        }

        @Override
        public ProxyConnection cancelHandler(Handler<Void> cancelHandler) {
            incomingProxyConnection.cancelHandler(cancelHandler);
            return this;
        }

        @Override
        public ProxyConnection exceptionHandler(Handler<Throwable> exceptionHandler) {
            incomingProxyConnection.exceptionHandler(exceptionHandler);
            return this;
        }

        @Override
        public ProxyConnection responseHandler(Handler<ProxyResponse> responseHandler) {
            incomingProxyConnection.responseHandler(responseHandler);
            return this;
        }

        @Override
        public WriteStream<Buffer> write(Buffer buffer) {
            incomingProxyConnection.write(buffer);
            shadowConnection.write(buffer);

            return this;
        }

        @Override
        public void end() {
            incomingProxyConnection.end();
            shadowConnection.end();
        }

        @Override
        public void end(Buffer buffer) {
            incomingProxyConnection.end(buffer);
            shadowConnection.end(buffer);
        }

        @Override
        public WriteStream<Buffer> drainHandler(Handler<Void> drainHandler) {
            incomingProxyConnection.drainHandler(drainHandler);
            return this;
        }

        @Override
        public boolean writeQueueFull() {
            return incomingProxyConnection.writeQueueFull();
        }
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
}
