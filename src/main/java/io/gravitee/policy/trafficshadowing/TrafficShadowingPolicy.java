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
package io.gravitee.policy.trafficshadowing;

import io.gravitee.gateway.reactive.api.context.http.HttpPlainExecutionContext;
import io.gravitee.gateway.reactive.api.policy.http.HttpPolicy;
import io.gravitee.policy.trafficshadowing.configuration.TrafficShadowingPolicyConfiguration;
import io.gravitee.policy.trafficshadowing.v3.TrafficShadowingPolicyV3;
import io.reactivex.rxjava3.core.Completable;

public class TrafficShadowingPolicy extends TrafficShadowingPolicyV3 implements HttpPolicy {

    public TrafficShadowingPolicy(final TrafficShadowingPolicyConfiguration configuration) {
        super(configuration);
    }

    @Override
    public String id() {
        return "traffic-shadowing";
    }

    @Override
    public Completable onRequest(HttpPlainExecutionContext ctx) {
        return HttpPolicy.super.onRequest(ctx);
    }
}
