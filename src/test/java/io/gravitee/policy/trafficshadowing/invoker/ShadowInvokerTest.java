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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

import io.gravitee.definition.model.v4.endpointgroup.Endpoint;
import io.gravitee.el.TemplateEngine;
import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.api.http.HttpHeaders;
import io.gravitee.gateway.reactive.api.connector.endpoint.HttpEndpointConnector;
import io.gravitee.gateway.reactive.api.context.http.HttpExecutionContext;
import io.gravitee.gateway.reactive.api.context.http.HttpRequest;
import io.gravitee.gateway.reactive.api.invoker.HttpInvoker;
import io.gravitee.gateway.reactive.core.v4.endpoint.EndpointManager;
import io.gravitee.gateway.reactive.core.v4.endpoint.ManagedEndpoint;
import io.gravitee.policy.trafficshadowing.configuration.HttpHeader;
import io.gravitee.policy.trafficshadowing.configuration.TrafficShadowingPolicyConfiguration;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

class ShadowInvokerTest {

    @Mock
    private HttpInvoker defaultInvoker;

    @Mock
    private TrafficShadowingPolicyConfiguration configuration;

    @Mock
    private HttpExecutionContext ctx;

    @Mock
    private HttpRequest request;

    @Mock
    private TemplateEngine templateEngine;

    @Mock
    private EndpointManager endpointManager;

    @Mock
    private ManagedEndpoint endpoint;

    @Mock
    private HttpEndpointConnector connector;

    @Mock
    private Endpoint endpointDefinition;

    private ShadowInvoker shadowInvoker;

    @BeforeEach
    void setUp() {
        openMocks(this);
        shadowInvoker = new ShadowInvoker(defaultInvoker, configuration);

        when(ctx.request()).thenReturn(request);
        when(ctx.getTemplateEngine()).thenReturn(templateEngine);
        when(ctx.getComponent(EndpointManager.class)).thenReturn(endpointManager);
        when(templateEngine.evalNow(anyString(), any())).thenAnswer(returnsFirstArg());
        when(configuration.getTarget()).thenReturn("shadow-endpoint");
        when(endpointManager.next(any())).thenReturn(endpoint);
        when(endpoint.getDefinition()).thenReturn(endpointDefinition);
        when(endpointDefinition.getName()).thenReturn("shadow");
        when(endpoint.getConnector()).thenReturn(connector);
        when(defaultInvoker.invoke(ctx)).thenReturn(Completable.complete());
        when(request.chunks()).thenReturn(Flowable.<Buffer>empty());
    }

    /**
     * Regression test for APIM-14291: the incoming client's Host header must not leak into the
     * shadow request. If it did, HttpConnector's setHost() call (added by APIM-13512) would change
     * the TLS SNI to the client's host, causing handshake failures against HTTPS shadow endpoints.
     */
    @Test
    void should_strip_client_host_header_from_shadow_request() {
        var incomingHeaders = HttpHeaders.create();
        incomingHeaders.set("Host", "api.example.com");
        incomingHeaders.set("X-Custom", "value");
        when(request.headers()).thenReturn(incomingHeaders);

        var shadowCtxCaptor = ArgumentCaptor.forClass(HttpExecutionContext.class);
        when(connector.connect(shadowCtxCaptor.capture())).thenReturn(Completable.complete());

        shadowInvoker.invoke(ctx).blockingAwait();

        var shadowHeaders = shadowCtxCaptor.getValue().request().headers();
        assertThat(shadowHeaders.get("Host")).as("client Host must not reach shadow connection").isNull();
        assertThat(shadowHeaders.get("X-Custom")).isEqualTo("value");
    }

    @Test
    void should_preserve_policy_configured_host_in_shadow_request() {
        var incomingHeaders = HttpHeaders.create();
        incomingHeaders.set("Host", "api.example.com");
        when(request.headers()).thenReturn(incomingHeaders);
        when(configuration.getHeaders()).thenReturn(List.of(new HttpHeader("Host", "shadow.example.com")));

        var shadowCtxCaptor = ArgumentCaptor.forClass(HttpExecutionContext.class);
        when(connector.connect(shadowCtxCaptor.capture())).thenReturn(Completable.complete());

        shadowInvoker.invoke(ctx).blockingAwait();

        assertThat(shadowCtxCaptor.getValue().request().headers().get("Host")).isEqualTo("shadow.example.com");
    }
}
