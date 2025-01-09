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

import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.api.handler.Handler;
import io.gravitee.gateway.api.http2.HttpFrame;
import io.gravitee.gateway.api.proxy.ProxyConnection;
import io.gravitee.gateway.api.proxy.ProxyResponse;
import io.gravitee.gateway.api.stream.WriteStream;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ShadowProxyConnection implements ProxyConnection {

    private final ProxyConnection incomingProxyConnection;
    private final ProxyConnection shadowConnection;

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
