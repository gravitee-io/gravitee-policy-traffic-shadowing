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

import io.gravitee.gateway.api.Connector;
import io.gravitee.gateway.api.Request;
import io.gravitee.gateway.api.endpoint.EndpointAvailabilityListener;
import io.gravitee.gateway.api.endpoint.resolver.ProxyEndpoint;
import io.gravitee.gateway.api.proxy.ProxyRequest;
import io.gravitee.gateway.api.proxy.builder.ProxyRequestBuilder;
import java.util.function.Function;

/**
 * @author GraviteeSource Team
 */
class MockProxyEndpoint implements ProxyEndpoint {

    @Override
    public String name() {
        return null;
    }

    @Override
    public String target() {
        return null;
    }

    @Override
    public int weight() {
        return 0;
    }

    @Override
    public Connector connector() {
        return null;
    }

    @Override
    public boolean available() {
        return false;
    }

    @Override
    public boolean primary() {
        return false;
    }

    @Override
    public void addEndpointAvailabilityListener(EndpointAvailabilityListener listener) {}

    @Override
    public void removeEndpointAvailabilityListener(EndpointAvailabilityListener listener) {}

    @Override
    public ProxyRequest createProxyRequest(Request request, Function<ProxyRequestBuilder, ProxyRequestBuilder> mapper) {
        ProxyRequestBuilder builder = ProxyRequestBuilder.from(request).uri("uri").parameters(null);
        return mapper != null ? mapper.apply(builder).build() : builder.build();
    }
}
