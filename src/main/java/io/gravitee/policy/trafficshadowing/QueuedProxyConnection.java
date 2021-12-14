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

import io.gravitee.gateway.api.Connector;
import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.api.handler.Handler;
import io.gravitee.gateway.api.proxy.ProxyConnection;
import io.gravitee.gateway.api.proxy.ProxyRequest;
import io.gravitee.gateway.api.proxy.ProxyResponse;
import io.gravitee.gateway.api.stream.WriteStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A ProxyConnection handling connection asynchronously.
 * You can write in before being connected ;
 * Written chunks will be enqueued, and sent to the backend after successful connection.
 *
 * @author GraviteeSource Team
 */
public class QueuedProxyConnection implements ProxyConnection {

    private static final Logger LOGGER = LoggerFactory.getLogger(QueuedProxyConnection.class);

    private ProxyConnection proxyConnection;

    private Queue<Buffer> queue = new ConcurrentLinkedQueue<>();

    public QueuedProxyConnection(Connector connector, ProxyRequest request, Handler<ProxyResponse> responseHandler) {
        connector.request(
            request,
            connection -> {
                if (connection != null) {
                    LOGGER.debug("*** Connection result"); // TODO: remove log

                    // TODO : we don't really succeed to connect when here ; this will be triggered also on connection fail

                    proxyConnection = connection;
                    proxyConnection.responseHandler(responseHandler);
                    proxyConnection.cancelHandler(v -> handleConnectionCancel(request));
                    proxyConnection.exceptionHandler(e -> handleConnectionException(e, request));

                    // We succeeded to do the connection to underlying backend, so push back remaining buffer
                    Buffer buffer = null;
                    PressureSafeWriteStream writeStream = new PressureSafeWriteStream(proxyConnection);
                    while ((buffer = queue.poll()) != null) {
                        LOGGER.debug("****** Writing polled buffer to connection"); // TODO: remove log
                        writeStream.write(buffer);
                    }
                } else {
                    LOGGER.error("Null connection retrieved");
                    queue.clear();
                }
            }
        );
    }

    @Override
    public WriteStream<Buffer> write(Buffer buffer) {
        // write directly to connection if connected and there is no pending queue
        if (proxyConnection != null && queue.isEmpty()) {
            LOGGER.debug("****** Writing to connection [{}] (queue size [{}]", proxyConnection == null ? "null" : "not null", queue.size()); // TODO: remove log
            proxyConnection.write(buffer);
        }
        // or write to the queue that will be consumed when connected
        else {
            // TODO: remove log
            LOGGER.debug(
                "****** Writing to queue (connection [{}] queue size [{}])",
                proxyConnection == null ? "null" : "not null",
                queue.size()
            );
            queue.add(buffer);
        }
        return this;
    }

    @Override
    public void end() {
        // TODO : how to end properly if ended before connected ?
        if (proxyConnection != null) {
            // End must be called only when we are sure all the chunk have been written back to the underlying proxy connection
            if (queue.isEmpty()) {
                proxyConnection.end();
            }
        }
    }

    private void handleConnectionException(Throwable throwable, ProxyRequest request) {
        LOGGER.error("Error connecting to {}", request.uri(), throwable);
        queue.clear();
    }

    private void handleConnectionCancel(ProxyRequest request) {
        LOGGER.error("Connection to {} cancelled", request.uri());
        queue.clear();
    }
}
