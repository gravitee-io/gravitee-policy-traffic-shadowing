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

import io.gravitee.el.TemplateEngine;
import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.reactive.api.ExecutionFailure;
import io.gravitee.gateway.reactive.api.context.TlsSession;
import io.gravitee.gateway.reactive.api.context.http.HttpExecutionContext;
import io.gravitee.gateway.reactive.api.context.http.HttpRequest;
import io.gravitee.gateway.reactive.api.context.http.HttpResponse;
import io.gravitee.gateway.reactive.api.message.Message;
import io.gravitee.gateway.reactive.api.tracing.Tracer;
import io.gravitee.reporter.api.v4.metric.Metrics;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ShadowHttpExecutionContext implements HttpExecutionContext {

    private final HttpExecutionContext incomingContext;
    private final ShadowResponse response;
    private final HttpRequest request;

    public ShadowHttpExecutionContext(HttpExecutionContext incomingContext, ShadowRequest request) {
        this.incomingContext = incomingContext;
        this.request = request;
        this.response = new ShadowResponse();
    }

    @Override
    public HttpRequest request() {
        return request;
    }

    @Override
    public HttpResponse response() {
        return response;
    }

    @Override
    public Completable interrupt() {
        return Completable.complete();
    }

    @Override
    public Completable interruptWith(ExecutionFailure executionFailure) {
        return Completable.complete();
    }

    @Override
    public Maybe<Buffer> interruptBody() {
        return Maybe.empty();
    }

    @Override
    public Maybe<Buffer> interruptBodyWith(ExecutionFailure executionFailure) {
        return Maybe.empty();
    }

    @Override
    public Flowable<Message> interruptMessagesWith(ExecutionFailure executionFailure) {
        return Flowable.empty();
    }

    @Override
    public Maybe<Message> interruptMessageWith(ExecutionFailure executionFailure) {
        return Maybe.empty();
    }

    @Override
    public Flowable<Message> interruptMessages() {
        return Flowable.empty();
    }

    @Override
    public Maybe<Message> interruptMessage() {
        return Maybe.empty();
    }

    @Override
    public TemplateEngine getTemplateEngine(Message message) {
        return incomingContext.getTemplateEngine();
    }

    @Override
    public Metrics metrics() {
        return Metrics.builder().build();
    }

    @Override
    public <T> T getComponent(Class<T> aClass) {
        return incomingContext.getComponent(aClass);
    }

    @Override
    public void setAttribute(String s, Object o) {}

    @Override
    public void putAttribute(String s, Object o) {}

    @Override
    public void removeAttribute(String s) {}

    @Override
    public <T> T getAttribute(String s) {
        return incomingContext.getAttribute(s);
    }

    @Override
    public <T> List<T> getAttributeAsList(String s) {
        return incomingContext.getAttributeAsList(s);
    }

    @Override
    public Set<String> getAttributeNames() {
        return incomingContext.getAttributeNames();
    }

    @Override
    public <T> Map<String, T> getAttributes() {
        return Collections.unmodifiableMap(incomingContext.getAttributes());
    }

    @Override
    public void setInternalAttribute(String s, Object o) {}

    @Override
    public void putInternalAttribute(String s, Object o) {}

    @Override
    public void removeInternalAttribute(String s) {}

    @Override
    public <T> T getInternalAttribute(String s) {
        return incomingContext.getInternalAttribute(s);
    }

    @Override
    public <T> Map<String, T> getInternalAttributes() {
        return Collections.unmodifiableMap(incomingContext.getInternalAttributes());
    }

    @Override
    public TemplateEngine getTemplateEngine() {
        return incomingContext.getTemplateEngine();
    }

    @Override
    public Tracer getTracer() {
        return incomingContext.getTracer();
    }

    @Override
    public long timestamp() {
        return incomingContext.timestamp();
    }

    @Override
    public String remoteAddress() {
        return incomingContext.remoteAddress();
    }

    @Override
    public String localAddress() {
        return incomingContext.localAddress();
    }

    @Override
    public TlsSession tlsSession() {
        return incomingContext.tlsSession();
    }
}
