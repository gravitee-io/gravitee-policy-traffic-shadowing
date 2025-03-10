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
package io.gravitee.policy.trafficshadowing.invoker;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.*;

import io.gravitee.el.TemplateEngine;
import io.gravitee.gateway.api.Connector;
import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.gateway.api.Invoker;
import io.gravitee.gateway.api.Request;
import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.api.endpoint.resolver.EndpointResolver;
import io.gravitee.gateway.api.handler.Handler;
import io.gravitee.gateway.api.http.HttpHeaders;
import io.gravitee.gateway.api.proxy.ProxyConnection;
import io.gravitee.gateway.api.proxy.ProxyRequest;
import io.gravitee.gateway.api.stream.ReadStream;
import io.gravitee.policy.trafficshadowing.configuration.HttpHeader;
import io.gravitee.policy.trafficshadowing.configuration.TrafficShadowingPolicyConfiguration;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ShadowInvokerTest {

    @Mock
    private Invoker invoker;

    @Mock
    private MockProxyEndpoint shadowEndpoint;

    @Mock
    private TrafficShadowingPolicyConfiguration configuration;

    @Mock
    private TemplateEngine templateEngine;

    @Mock
    private EndpointResolver endpointResolver;

    @Mock
    private Request request;

    @Mock
    private ProxyRequest proxyRequest;

    @Mock
    private Connector connector;

    @Mock
    private ExecutionContext executionContext;

    @Mock
    private ReadStream<Buffer> stream;

    @Mock
    private Handler<ProxyConnection> connectionHandler;

    private ShadowInvoker shadowInvoker;

    private HttpHeaders headers;

    @Before
    @SuppressWarnings("removal")
    public void setUp() {
        openMocks(this);

        headers = HttpHeaders.create();
        shadowInvoker = new ShadowInvoker(invoker, configuration);

        when(executionContext.getTemplateEngine()).thenReturn(templateEngine);
        when(executionContext.getComponent(EndpointResolver.class)).thenReturn(endpointResolver);
        when(executionContext.request()).thenReturn(request);
        when(request.headers()).thenReturn(headers);
        when(templateEngine.convert(any(String.class))).thenAnswer(returnsFirstArg());
        when(shadowEndpoint.createProxyRequest(any(), any())).thenReturn(proxyRequest);
        when(shadowEndpoint.connector()).thenReturn(connector);
    }

    @Test
    public void shouldInvokeBackendWhenEndpointIsNotResolved() {
        when(endpointResolver.resolve(any())).thenReturn(null);
        shadowInvoker.invoke(executionContext, stream, connectionHandler);
        verify(invoker, times(1)).invoke(executionContext, stream, connectionHandler);
    }

    @Test
    public void shouldNotAddHeadersToOriginalRequest() {
        headers.add("X-Gravitee-Policy", "value");
        when(endpointResolver.resolve(any())).thenReturn(shadowEndpoint);
        when(configuration.getHeaders()).thenReturn(List.of(new HttpHeader("Custom-Header", "custom")));

        shadowInvoker.invoke(executionContext, stream, connectionHandler);

        assertThat(headers).hasSize(1);
    }

    @Test
    public void shouldAddHeadersToShadowRequest() {
        headers.add("X-Gravitee-Policy", "value");
        when(configuration.getHeaders()).thenReturn(List.of(new HttpHeader("Custom-Header", "custom")));
        when(endpointResolver.resolve(any())).thenReturn(shadowEndpoint);
        when(shadowEndpoint.createProxyRequest(any(), any())).thenCallRealMethod();

        ArgumentCaptor<ProxyRequest> proxyRequestArgumentCaptor = ArgumentCaptor.forClass(ProxyRequest.class);

        shadowInvoker.invoke(executionContext, stream, connectionHandler);

        verify(connector).request(proxyRequestArgumentCaptor.capture(), any(), any());

        HttpHeaders shadowHeaders = proxyRequestArgumentCaptor.getValue().headers();

        assertThat(shadowHeaders).usingRecursiveComparison().isEqualTo(HttpHeaders.create(headers).add("Custom-Header", "custom"));
    }

    @Test
    public void shouldOverrideOriginalHeadersInShadowRequest() {
        headers.add("X-Gravitee-Policy", "value");
        when(configuration.getHeaders()).thenReturn(List.of(new HttpHeader("X-Gravitee-Policy", "custom")));
        when(endpointResolver.resolve(any())).thenReturn(shadowEndpoint);
        when(shadowEndpoint.createProxyRequest(any(), any())).thenCallRealMethod();

        ArgumentCaptor<ProxyRequest> proxyRequestArgumentCaptor = ArgumentCaptor.forClass(ProxyRequest.class);

        shadowInvoker.invoke(executionContext, stream, connectionHandler);

        verify(connector).request(proxyRequestArgumentCaptor.capture(), any(), any());

        HttpHeaders shadowHeaders = proxyRequestArgumentCaptor.getValue().headers();

        assertThat(shadowHeaders).usingRecursiveComparison().isEqualTo(HttpHeaders.create(this.headers).set("X-Gravitee-Policy", "custom"));
    }
}
