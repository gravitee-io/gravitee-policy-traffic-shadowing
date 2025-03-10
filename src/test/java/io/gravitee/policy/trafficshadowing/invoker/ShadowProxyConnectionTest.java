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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.api.handler.Handler;
import io.gravitee.gateway.api.http2.HttpFrame;
import io.gravitee.gateway.api.proxy.ProxyConnection;
import io.gravitee.gateway.api.proxy.ProxyResponse;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class ShadowProxyConnectionTest {

    @Mock
    private ProxyConnection incomingProxyConnection;

    @Mock
    private ProxyConnection shadowConnection;

    @Test
    public void shouldSetHandlersOnShadowConnection() {
        new ShadowProxyConnection(incomingProxyConnection, shadowConnection);
        verify(shadowConnection, times(1)).responseHandler(any());
        verify(shadowConnection, times(1)).exceptionHandler(any());
    }

    @Test
    public void shouldWriteCustomFrameOnBothConnections() {
        HttpFrame frame = mock(HttpFrame.class);
        new ShadowProxyConnection(incomingProxyConnection, shadowConnection).writeCustomFrame(frame);
        verify(shadowConnection, times(1)).writeCustomFrame(frame);
        verify(incomingProxyConnection, times(1)).writeCustomFrame(frame);
    }

    @Test
    public void shouldNotSetCancelHandlerOnShadowConnection() {
        Handler<Void> handler = noop -> {};
        new ShadowProxyConnection(incomingProxyConnection, shadowConnection).cancelHandler(handler);
        verify(shadowConnection, never()).cancelHandler(handler);
        verify(incomingProxyConnection, times(1)).cancelHandler(handler);
    }

    @Test
    public void shouldNotSetExceptionHandlerOnShadowConnection() {
        Handler<Throwable> handler = noop -> {};
        new ShadowProxyConnection(incomingProxyConnection, shadowConnection).exceptionHandler(handler);
        verify(shadowConnection, never()).exceptionHandler(handler);
        verify(incomingProxyConnection, times(1)).exceptionHandler(handler);
    }

    @Test
    public void shouldNotSetResponseHandlerOnShadowConnection() {
        Handler<ProxyResponse> handler = noop -> {};
        new ShadowProxyConnection(incomingProxyConnection, shadowConnection).responseHandler(handler);
        verify(shadowConnection, never()).responseHandler(handler);
        verify(incomingProxyConnection, times(1)).responseHandler(handler);
    }

    @Test
    public void shouldWritOnBothConnections() {
        Buffer buffer = mock(Buffer.class);
        new ShadowProxyConnection(incomingProxyConnection, shadowConnection).write(buffer);
        verify(shadowConnection, times(1)).write(buffer);
        verify(incomingProxyConnection, times(1)).write(buffer);
    }

    @Test
    public void shouldEndBothConnections() {
        new ShadowProxyConnection(incomingProxyConnection, shadowConnection).end();
        verify(shadowConnection, times(1)).end();
        verify(incomingProxyConnection, times(1)).end();
    }

    @Test
    public void shouldEndBothConnectionsWithBuffer() {
        Buffer buffer = mock(Buffer.class);
        new ShadowProxyConnection(incomingProxyConnection, shadowConnection).end(buffer);
        verify(shadowConnection, times(1)).end(buffer);
        verify(incomingProxyConnection, times(1)).end(buffer);
    }

    @Test
    public void shouldNotSetDrainHandlerOnShadowConnection() {
        Handler<Void> handler = noop -> {};
        new ShadowProxyConnection(incomingProxyConnection, shadowConnection).drainHandler(handler);
        verify(shadowConnection, never()).drainHandler(handler);
        verify(incomingProxyConnection, times(1)).drainHandler(handler);
    }

    @Test
    public void shouldCallWriteQueueFullOnIncomingConnection() {
        when(incomingProxyConnection.writeQueueFull()).thenReturn(true);
        assertThat(new ShadowProxyConnection(incomingProxyConnection, shadowConnection).writeQueueFull()).isTrue();
        verify(shadowConnection, never()).writeQueueFull();
        verify(incomingProxyConnection, times(1)).writeQueueFull();
    }
}
