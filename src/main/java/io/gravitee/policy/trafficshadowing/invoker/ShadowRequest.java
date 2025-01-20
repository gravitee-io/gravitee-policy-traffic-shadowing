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

import io.gravitee.common.http.HttpMethod;
import io.gravitee.common.http.HttpVersion;
import io.gravitee.common.util.LinkedMultiValueMap;
import io.gravitee.common.util.MultiValueMap;
import io.gravitee.common.util.URIUtils;
import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.api.http.HttpHeaders;
import io.gravitee.gateway.reactive.api.context.TlsSession;
import io.gravitee.gateway.reactive.api.context.http.HttpRequest;
import io.gravitee.gateway.reactive.api.message.Message;
import io.gravitee.gateway.reactive.api.ws.WebSocket;
import io.gravitee.gateway.reactive.core.context.AbstractRequest;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.FlowableTransformer;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.MaybeTransformer;
import io.reactivex.rxjava3.core.Single;

public class ShadowRequest extends AbstractRequest {

    private final HttpRequest incomingRequest;
    private final Flowable<Buffer> chunks;

    private HttpHeaders headers;
    private MultiValueMap<String, String> parameters;
    private MultiValueMap<String, String> pathParameters;

    public ShadowRequest(HttpRequest incomingRequest, Flowable<Buffer> requestChunks) {
        this.incomingRequest = incomingRequest;
        this.chunks = requestChunks;
    }

    @Override
    public Flowable<Message> messages() {
        return incomingRequest.messages();
    }

    @Override
    public void messages(Flowable<Message> flowable) {}

    @Override
    public Completable onMessages(FlowableTransformer<Message, Message> flowableTransformer) {
        return Completable.complete();
    }

    @Override
    public boolean isWebSocket() {
        return incomingRequest.isWebSocket();
    }

    @Override
    public WebSocket webSocket() {
        return incomingRequest.webSocket();
    }

    @Override
    public Maybe<Buffer> body() {
        return incomingRequest.body();
    }

    @Override
    public Single<Buffer> bodyOrEmpty() {
        return incomingRequest.bodyOrEmpty();
    }

    @Override
    public void body(Buffer buffer) {
        incomingRequest.body(buffer);
    }

    @Override
    public Completable onBody(MaybeTransformer<Buffer, Buffer> maybeTransformer) {
        return incomingRequest.onBody(maybeTransformer);
    }

    @Override
    public Flowable<Buffer> chunks() {
        return chunks;
    }

    @Override
    public void chunks(Flowable<Buffer> flowable) {}

    @Override
    public Completable onChunks(FlowableTransformer<Buffer, Buffer> flowableTransformer) {
        return Completable.complete();
    }

    @Override
    public void contentLength(long l) {
        incomingRequest.contentLength(l);
    }

    @Override
    public void method(HttpMethod httpMethod) {
        incomingRequest.method(httpMethod);
    }

    @Override
    public String id() {
        return incomingRequest.id();
    }

    @Override
    public String transactionId() {
        return incomingRequest.transactionId();
    }

    @Override
    public String clientIdentifier() {
        return incomingRequest.clientIdentifier();
    }

    @Override
    public String uri() {
        return incomingRequest.uri();
    }

    @Override
    public String host() {
        return incomingRequest.host();
    }

    @Override
    public String originalHost() {
        return incomingRequest.originalHost();
    }

    @Override
    public String path() {
        return incomingRequest.path();
    }

    @Override
    public String pathInfo() {
        return incomingRequest.pathInfo();
    }

    @Override
    public String contextPath() {
        return incomingRequest.contextPath();
    }

    @Override
    public MultiValueMap<String, String> parameters() {
        if (parameters == null) {
            parameters = URIUtils.parameters(incomingRequest.uri());
        }

        return parameters;
    }

    @Override
    public MultiValueMap<String, String> pathParameters() {
        if (pathParameters == null) {
            pathParameters = new LinkedMultiValueMap<>();
        }

        return pathParameters;
    }

    public void headers(HttpHeaders headers) {
        this.headers = headers;
    }

    @Override
    public HttpHeaders headers() {
        return headers;
    }

    @Override
    public HttpMethod method() {
        return incomingRequest.method();
    }

    @Override
    public String scheme() {
        return incomingRequest.scheme();
    }

    @Override
    public HttpVersion version() {
        return incomingRequest.version();
    }

    @Override
    public long timestamp() {
        return incomingRequest.timestamp();
    }

    @Override
    public boolean ended() {
        return incomingRequest.ended();
    }

    @Override
    public String remoteAddress() {
        return incomingRequest.remoteAddress();
    }

    @Override
    public String localAddress() {
        return incomingRequest.localAddress();
    }

    @Override
    public TlsSession tlsSession() {
        return incomingRequest.tlsSession();
    }
}
