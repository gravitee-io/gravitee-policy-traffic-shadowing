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

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import io.gravitee.apim.gateway.tests.sdk.annotations.DeployApi;
import io.gravitee.apim.gateway.tests.sdk.annotations.GatewayTest;
import io.gravitee.definition.model.ExecutionMode;
import io.vertx.core.http.HttpMethod;
import io.vertx.rxjava3.core.http.HttpClient;
import org.junit.jupiter.api.Test;

@GatewayTest(v2ExecutionMode = ExecutionMode.V3)
class TrafficShadowingPolicyV3EngineIntegrationTest extends V3EngineTest {

    @Test
    @DeployApi("/apis/v2/traffic-shadowing.json")
    void should_transform_and_add_headers_on_request_content(HttpClient httpClient) throws InterruptedException {
        wiremock.stubFor(post("/endpoint").willReturn(ok()));
        wiremock.stubFor(post("/shadow-endpoint").willReturn(ok()));

        httpClient
            .rxRequest(HttpMethod.POST, "/v2")
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
    @DeployApi("/apis/v2/traffic-shadowing-bad-endpoint.json")
    void should_ignore_invalid_shadowing_endpoint_and_call_standard_endpoint(HttpClient httpClient) throws InterruptedException {
        wiremock.stubFor(post("/endpoint").willReturn(ok()));
        wiremock.stubFor(post("/shadow-endpoint").willReturn(ok()));

        httpClient
            .rxRequest(HttpMethod.POST, "/v2-shadow-ko")
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
        wiremock.verify(0, postRequestedFor(urlPathEqualTo("/shadow-endpoint")).withRequestBody(equalTo("A request")));
    }
}
