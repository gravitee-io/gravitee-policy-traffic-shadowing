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

import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.gravitee.el.TemplateEngine;
import io.gravitee.gateway.api.Connector;
import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.gateway.api.Request;
import io.gravitee.gateway.api.endpoint.EndpointAvailabilityListener;
import io.gravitee.gateway.api.endpoint.resolver.EndpointResolver;
import io.gravitee.gateway.api.endpoint.resolver.ProxyEndpoint;
import io.gravitee.gateway.api.http.HttpHeaders;
import io.gravitee.gateway.api.proxy.ProxyRequest;
import io.gravitee.gateway.api.proxy.builder.ProxyRequestBuilder;
import io.gravitee.policy.trafficshadowing.configuration.HttpHeader;
import io.gravitee.policy.trafficshadowing.configuration.TrafficShadowingPolicyConfiguration;
import java.util.function.Function;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * @author Yann TAVERNIER (yann.tavernier at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class TrafficShadowingPolicyTest {
/*
    private TrafficShadowingPolicy trafficShadowingPolicy;

    @Mock
    private TrafficShadowingPolicyConfiguration trafficShadowingPolicyConfiguration;

    @Mock
    private ExecutionContext executionContext;

    @Mock
    private TemplateEngine templateEngine;

    @Mock
    private EndpointResolver endpointResolver;

    @Mock
    private MockProxyEndpoint proxyEndpoint;

    @Mock
    private Request request;

    @Mock
    private ProxyRequest proxyRequest;

    @Mock
    private Connector connector;

    private io.gravitee.gateway.api.http.HttpHeaders requestHttpHeaders = HttpHeaders.create();

    @Before
    public void init() {
        initMocks(this);

        requestHttpHeaders = HttpHeaders.create();
        trafficShadowingPolicy = new TrafficShadowingPolicy(trafficShadowingPolicyConfiguration);
        when(executionContext.getTemplateEngine()).thenReturn(templateEngine);
        when(executionContext.getComponent(EndpointResolver.class)).thenReturn(endpointResolver);
        when(executionContext.request()).thenReturn(request);
        when(request.headers()).thenReturn(requestHttpHeaders);
        when(templateEngine.convert(any(String.class))).thenAnswer(returnsFirstArg());

        when(proxyEndpoint.createProxyRequest(any(), any())).thenReturn(proxyRequest);
        when(proxyEndpoint.connector()).thenReturn(connector);
    }

    @Test
    public void shouldDoNothingWhenNoEndpoint() {
        when(endpointResolver.resolve(any())).thenReturn(null);

        assertNull(trafficShadowingPolicy.onRequestContent(executionContext));
    }

    @Test
    public void shouldNotAddHeadersToOriginalRequest() {
        requestHttpHeaders.add("X-Gravitee-Policy", "value");
        when(endpointResolver.resolve(any())).thenReturn(proxyEndpoint);
        when(trafficShadowingPolicyConfiguration.getHeaders()).thenReturn(singletonList(new HttpHeader("Custom-Header", "custom")));

        trafficShadowingPolicy.onRequestContent(executionContext);
        assertEquals(1, request.headers().size());
    }

    @Test
    public void shouldAddHeadersToShadowingRequest() {
        requestHttpHeaders.add("X-Gravitee-Policy", "value");
        when(endpointResolver.resolve(any())).thenReturn(proxyEndpoint);
        when(trafficShadowingPolicyConfiguration.getHeaders()).thenReturn(singletonList(new HttpHeader("Custom-Header", "custom")));
        when(proxyEndpoint.createProxyRequest(any(), any())).thenCallRealMethod();

        ArgumentCaptor<ProxyRequest> proxyRequestArgumentCaptor = ArgumentCaptor.forClass(ProxyRequest.class);

        trafficShadowingPolicy.onRequestContent(executionContext);

        verify(connector).request(proxyRequestArgumentCaptor.capture(), any(), any());

        assertEquals(2, proxyRequestArgumentCaptor.getValue().headers().size());
        assertTrue(proxyRequestArgumentCaptor.getValue().headers().containsKey("X-Gravitee-Policy"));
        assertTrue(proxyRequestArgumentCaptor.getValue().headers().containsKey("Custom-Header"));
    }

    @Test
    public void shouldUpdateHeadersToOriginalRequest() {
        requestHttpHeaders.add("X-Gravitee-Policy", "value");
        when(endpointResolver.resolve(any())).thenReturn(proxyEndpoint);
        when(trafficShadowingPolicyConfiguration.getHeaders()).thenReturn(singletonList(new HttpHeader("X-Gravitee-Policy", "custom")));
        when(proxyEndpoint.createProxyRequest(any(), any())).thenCallRealMethod();

        ArgumentCaptor<ProxyRequest> proxyRequestArgumentCaptor = ArgumentCaptor.forClass(ProxyRequest.class);

        trafficShadowingPolicy.onRequestContent(executionContext);

        verify(connector).request(proxyRequestArgumentCaptor.capture(), any(), any());

        assertEquals(1, proxyRequestArgumentCaptor.getValue().headers().size());
        assertTrue(proxyRequestArgumentCaptor.getValue().headers().containsKey("X-Gravitee-Policy"));
        assertEquals("custom", proxyRequestArgumentCaptor.getValue().headers().get("X-Gravitee-Policy"));
    }

    public static class MockProxyEndpoint implements ProxyEndpoint {

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

            if (mapper != null) {
                builder = mapper.apply(builder);
            }

            return builder.build();
        }
    }
 */
}
