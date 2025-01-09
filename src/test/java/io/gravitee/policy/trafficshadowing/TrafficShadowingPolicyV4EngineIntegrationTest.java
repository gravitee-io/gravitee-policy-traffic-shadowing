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
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import io.gravitee.apim.gateway.tests.sdk.annotations.DeployApi;
import io.vertx.core.http.HttpMethod;
import io.vertx.rxjava3.core.http.HttpClient;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Slf4j
class TrafficShadowingPolicyV4EngineIntegrationTest extends V4EngineTest {

    @AfterEach
    void tearDown() {
        log.debug("Teardown");
        wiremock.getAllServeEvents().forEach(x -> log.debug("serverenvet: {}", x.getRequest()));
    }

    @DeployApi({ "/apis/v4/traffic-shadowing.json", "/apis/v4/traffic-shadowing-el.json", "/apis/v2/traffic-shadowing.json" })
    @ParameterizedTest
    @ValueSource(strings = { "/v2", "/v4", "/v4-el" })
    void should_call_shadow_endpoint_with_new_headers(String requestUri, HttpClient httpClient) {
        wiremock.stubFor(post("/endpoint").willReturn(ok()));
        wiremock.stubFor(post("/shadow-endpoint").willReturn(ok()));

        httpClient
            .rxRequest(HttpMethod.POST, requestUri)
            .flatMap(httpClientRequest -> httpClientRequest.rxSend("A request"))
            .test()
            .awaitDone(10, TimeUnit.SECONDS)
            .assertComplete()
            .assertValue(response -> {
                assertThat(response.statusCode()).isEqualTo(200);
                return true;
            });

        wiremock.verify(postRequestedFor(urlPathEqualTo("/endpoint")).withRequestBody(equalTo("A request")));
        wiremock.verify(
            postRequestedFor(urlPathEqualTo("/shadow-endpoint"))
                .withHeader("Custom-Request-Id-Header", containing("id-"))
                .withRequestBody(equalTo("A request"))
        );
    }

    @DeployApi({ "/apis/v4/traffic-shadowing.json", "/apis/v2/traffic-shadowing.json" })
    @ParameterizedTest
    @ValueSource(strings = { "/v2", "/v4" })
    void should_ignore_error_response_from_shadow(String requestUri, HttpClient httpClient) {
        wiremock.stubFor(get("/endpoint").willReturn(ok()));
        wiremock.stubFor(get("/shadow-endpoint").willReturn(serverError()));

        httpClient
            .rxRequest(HttpMethod.GET, requestUri)
            .flatMap(httpClientRequest -> httpClientRequest.rxSend())
            .test()
            .awaitDone(10, TimeUnit.SECONDS)
            .assertComplete()
            .assertValue(response -> {
                assertThat(response.statusCode()).isEqualTo(200);
                return true;
            });

        wiremock.verify(getRequestedFor(urlPathEqualTo("/endpoint")));
        wiremock.verify(getRequestedFor(urlPathEqualTo("/shadow-endpoint")).withHeader("Custom-Request-Id-Header", containing("id-")));
    }

    @DeployApi({ "/apis/v4/traffic-shadowing-bad-endpoint.json", "/apis/v2/traffic-shadowing-bad-endpoint.json" })
    @ParameterizedTest
    @ValueSource(strings = { "/v2-shadow-ko", "/v4-shadow-ko" })
    void should_ignore_invalid_shadowing_endpoint_and_call_standard_endpoint(String requestUri, HttpClient httpClient)
        throws InterruptedException {
        wiremock.stubFor(get("/endpoint").willReturn(ok()));
        wiremock.stubFor(get("/shadow-endpoint").willReturn(ok()));

        httpClient
            .rxRequest(HttpMethod.GET, requestUri)
            .flatMap(httpClientRequest -> httpClientRequest.rxSend("A request"))
            .test()
            .await()
            .assertComplete()
            .assertValue(response -> {
                assertThat(response.statusCode()).isEqualTo(200);
                return true;
            })
            .assertNoErrors();

        wiremock.verify(getRequestedFor(urlPathEqualTo("/endpoint")).withRequestBody(equalTo("A request")));
        wiremock.verify(0, getRequestedFor(urlPathEqualTo("/shadow-endpoint")).withRequestBody(equalTo("A request")));
    }
}
