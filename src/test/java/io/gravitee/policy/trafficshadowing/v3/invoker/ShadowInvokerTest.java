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
package io.gravitee.policy.trafficshadowing.v3.invoker;

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
import io.gravitee.gateway.api.proxy.ProxyResponse;
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

    /**
     * Regression test for APIM-14211: when the inner invoker is FailoverInvoker, it consumes
     * the request body stream before calling the backendConnection callback (the callback fires
     * only after the circuit breaker onComplete, i.e. after a full round-trip). ShadowInvoker
     * must buffer the body and replay it to the shadow connection in this case.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void shouldReplayBufferedBodyToShadowWhenStreamConsumedByInnerInvokerBeforeCallback() {
        when(endpointResolver.resolve(any())).thenReturn(shadowEndpoint);

        ProxyConnection shadowConnection = mock(ProxyConnection.class);
        when(shadowConnection.responseHandler(any())).thenReturn(shadowConnection);
        when(shadowConnection.exceptionHandler(any())).thenReturn(shadowConnection);

        // Shadow connector fires its callback immediately (connection established)
        doAnswer(invocation -> {
                Handler<ProxyConnection> shadowConnHandler = invocation.getArgument(2);
                shadowConnHandler.handle(shadowConnection);
                return null;
            })
            .when(connector)
            .request(any(), any(), any());

        // Capture the intercepting handlers that recordingStream registers on the underlying stream
        ArgumentCaptor<Handler<Buffer>> streamBodyHandlerCaptor = ArgumentCaptor.forClass(Handler.class);
        ArgumentCaptor<Handler<Void>> streamEndHandlerCaptor = ArgumentCaptor.forClass(Handler.class);
        when(stream.bodyHandler(streamBodyHandlerCaptor.capture())).thenReturn(stream);
        when(stream.endHandler(streamEndHandlerCaptor.capture())).thenReturn(stream);

        ProxyConnection mainBackendConnection = mock(ProxyConnection.class);

        // Simulate FailoverInvoker: consume the stream entirely, then call backendConnection callback
        doAnswer(invocation -> {
                ReadStream<Buffer> recordingStream = invocation.getArgument(1);
                Handler<ProxyConnection> backendConnHandler = invocation.getArgument(2);

                // EndpointInvoker registers handlers on recordingStream (which delegates to stream)
                recordingStream.bodyHandler(chunk -> {});
                recordingStream.endHandler(v -> {});

                // Stream body flows (intercepting handlers on stream are now registered)
                streamBodyHandlerCaptor.getValue().handle(Buffer.buffer("hello"));
                streamBodyHandlerCaptor.getValue().handle(Buffer.buffer(" world"));
                streamEndHandlerCaptor.getValue().handle(null);

                // FailoverInvoker calls connectionHandler only after stream is consumed
                backendConnHandler.handle(mainBackendConnection);
                return null;
            })
            .when(invoker)
            .invoke(any(), any(), any());

        shadowInvoker.invoke(executionContext, stream, connectionHandler);

        // Shadow connection must have received the replayed body chunks
        ArgumentCaptor<Buffer> writtenBuffers = ArgumentCaptor.forClass(Buffer.class);
        verify(shadowConnection, times(2)).write(writtenBuffers.capture());
        assertThat(writtenBuffers.getAllValues()).extracting(Buffer::toString).containsExactly("hello", " world");
        verify(shadowConnection).end();

        verify(connectionHandler).handle(any(ShadowProxyConnection.class));
    }

    /**
     * Ensures the normal path (EndpointInvoker, no failover) still works after the fix:
     * when the backendConnection callback fires before the stream is consumed, body handlers
     * are wired through ShadowProxyConnection so both endpoints receive the data.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void shouldWireBodyHandlersViaShadowProxyConnectionWhenStreamNotYetConsumed() {
        when(endpointResolver.resolve(any())).thenReturn(shadowEndpoint);

        ProxyConnection shadowConnection = mock(ProxyConnection.class);
        when(shadowConnection.responseHandler(any())).thenReturn(shadowConnection);
        when(shadowConnection.exceptionHandler(any())).thenReturn(shadowConnection);

        doAnswer(invocation -> {
                Handler<ProxyConnection> shadowConnHandler = invocation.getArgument(2);
                shadowConnHandler.handle(shadowConnection);
                return null;
            })
            .when(connector)
            .request(any(), any(), any());

        ProxyConnection mainBackendConnection = mock(ProxyConnection.class);

        // Simulate EndpointInvoker: call backendConnection callback BEFORE stream flows
        doAnswer(invocation -> {
                ReadStream<Buffer> recordingStream = invocation.getArgument(1);
                Handler<ProxyConnection> backendConnHandler = invocation.getArgument(2);

                // EndpointInvoker registers handlers on recordingStream, then calls the callback
                recordingStream.bodyHandler(chunk -> {});
                recordingStream.endHandler(v -> {});

                // Callback fires before resume() — stream has NOT been consumed yet
                backendConnHandler.handle(mainBackendConnection);
                return null;
            })
            .when(invoker)
            .invoke(any(), any(), any());

        // Capture the bodyHandler eventually registered on the original stream by the else branch
        ArgumentCaptor<Handler<Buffer>> streamBodyHandlerCaptor = ArgumentCaptor.forClass(Handler.class);
        when(stream.bodyHandler(streamBodyHandlerCaptor.capture())).thenReturn(stream);
        when(stream.endHandler(any())).thenReturn(stream);

        shadowInvoker.invoke(executionContext, stream, connectionHandler);

        // The else branch must have overridden stream.bodyHandler with shadowProxyConnection::write,
        // so sending a chunk through it writes to BOTH connections.
        streamBodyHandlerCaptor.getValue().handle(Buffer.buffer("test"));

        verify(mainBackendConnection).write(any(Buffer.class));
        verify(shadowConnection).write(any(Buffer.class));
        verify(shadowConnection, never()).end(); // end not called yet (endHandler not triggered)

        verify(connectionHandler).handle(any(ShadowProxyConnection.class));
    }
}
