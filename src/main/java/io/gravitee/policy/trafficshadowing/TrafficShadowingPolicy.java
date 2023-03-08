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
import io.gravitee.policy.trafficshadowing.invoker.ShadowInvoker;
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
        context.setAttribute(ExecutionContext.ATTR_INVOKER, new ShadowInvoker(defaultInvoker, configuration));
        chain.doNext(context.request(), context.response());
    }
}
