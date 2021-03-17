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
import io.gravitee.gateway.api.handler.Handler;
import io.gravitee.gateway.api.proxy.ProxyConnection;
import io.gravitee.gateway.api.proxy.ProxyResponse;
import io.gravitee.gateway.api.stream.ReadWriteStream;
import io.gravitee.gateway.api.stream.SimpleReadWriteStream;
import io.gravitee.policy.api.annotations.OnRequestContent;
import io.gravitee.policy.trafficshadowing.configuration.TrafficShadowingPolicyConfiguration;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class TrafficShadowingPolicy {

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
            ProxyConnection connection = endpoint.connector().request(
                    endpoint.createProxyRequest(context.request()));

            if (connection != null) {

                connection.responseHandler(new Handler<ProxyResponse>() {
                    @Override
                    public void handle(ProxyResponse response) {
                        System.out.println("Traffic shadowing status is: " + response.status());

                        response.bodyHandler(new Handler<Buffer>() {
                            @Override
                            public void handle(Buffer result) {
                                System.out.println(result.toString());
                            }
                        });
                    }
                });

                return new SimpleReadWriteStream<Buffer>() {
                    @Override
                    public SimpleReadWriteStream<Buffer> write(Buffer chunk) {
                        connection.write(chunk);

                        if (connection.writeQueueFull()) {
                            pause();
                            connection.drainHandler(aVoid -> resume());
                        }

                        return super.write(chunk);
                    }

                    @Override
                    public void end() {
                        connection.end();
                        super.end();
                    }
                };
            }
        }

        return null;
    }
}
