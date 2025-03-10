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
package io.gravitee.policy.trafficshadowing.configuration;

import static org.junit.Assert.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URL;
import org.junit.Test;

/**
 * @author Yann TAVERNIER (yann.tavernier at graviteesource.com)
 * @author GraviteeSource Team
 */
public class TrafficShadowingPolicyConfigurationTest {

    @Test
    public void test_configuration() throws IOException {
        TrafficShadowingPolicyConfiguration configuration = load(
            "/io/gravitee/policy/trafficshadowing/configuration/configuration.json",
            TrafficShadowingPolicyConfiguration.class
        );

        assertNotNull(configuration.getHeaders());
        assertNotNull(configuration.getTarget());

        assertEquals(2, configuration.getHeaders().size());
        assertEquals("target_endpoint", configuration.getTarget());
    }

    private <T> T load(String resource, Class<T> type) throws IOException {
        URL jsonFile = this.getClass().getResource(resource);
        return new ObjectMapper().readValue(jsonFile, type);
    }
}
