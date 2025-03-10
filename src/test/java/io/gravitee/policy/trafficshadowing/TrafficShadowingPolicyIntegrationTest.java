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

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.gravitee.apim.gateway.tests.sdk.AbstractPolicyTest;
import io.gravitee.apim.gateway.tests.sdk.annotations.DeployApi;
import io.gravitee.apim.gateway.tests.sdk.annotations.GatewayTest;
import io.gravitee.policy.trafficshadowing.configuration.TrafficShadowingPolicyConfiguration;
import io.vertx.core.http.HttpMethod;
import io.vertx.rxjava3.core.http.HttpClient;
import io.vertx.rxjava3.core.http.HttpClientRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@GatewayTest
class TrafficShadowingPolicyIntegrationTest extends AbstractPolicyTest<TrafficShadowingPolicy, TrafficShadowingPolicyConfiguration> {

    @Test
    @DisplayName("Should shadow request to another endpoint")
    @DeployApi("/apis/traffic-shadowing.json")
    void shouldTransformAndAddHeadersOnRequestContent(HttpClient httpClient) throws InterruptedException {
        wiremock.stubFor(post("/endpoint").willReturn(ok()));
        wiremock.stubFor(post("/shadow-endpoint").willReturn(ok()));

        httpClient
            .rxRequest(HttpMethod.POST, "/test")
            .flatMap(httpClientRequest -> httpClientRequest.rxSend("A request"))
            .test()
            .await()
            .assertComplete()
            .assertValue(response -> {
                assertThat(response.statusCode()).isEqualTo(200);
                return true;
            })
            .assertNoErrors();

        wiremock.verify(postRequestedFor(urlPathEqualTo("/endpoint")).withRequestBody(equalTo("A request")));
        wiremock.verify(
            postRequestedFor(urlPathEqualTo("/shadow-endpoint"))
                .withHeader("Custom-Request-Id-Header", containing("id-"))
                .withRequestBody(equalTo("A request"))
        );
    }

    @Test
    @DisplayName("Should not shadow request to an invalid endpoint")
    @DeployApi("/apis/traffic-shadowing-bad-endpoint.json")
    void shouldCallDefaultEndpoint(HttpClient httpClient) throws InterruptedException {
        wiremock.stubFor(post("/endpoint").willReturn(ok()));
        wiremock.stubFor(post("/shadow-endpoint").willReturn(ok()));

        httpClient
            .rxRequest(HttpMethod.POST, "/test")
            .flatMap(httpClientRequest -> httpClientRequest.rxSend("A request"))
            .test()
            .await()
            .assertComplete()
            .assertValue(response -> {
                assertThat(response.statusCode()).isEqualTo(200);
                return true;
            })
            .assertNoErrors();

        wiremock.verify(postRequestedFor(urlPathEqualTo("/endpoint")).withRequestBody(equalTo("A request")));
    }
}
