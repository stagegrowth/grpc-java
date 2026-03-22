/*
 * Copyright 2019 The gRPC Authors
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

package io.grpc.internal;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import io.grpc.internal.RetriableStream.Throttle;
import io.grpc.internal.ServiceConfigUtil.LbConfig;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit test for {@link ServiceConfigUtil}.
 */
@RunWith(JUnit4.class)
public class ServiceConfigUtilTest {

  @Test
  public void unwrapLoadBalancingConfig() throws Exception {
    String lbConfig = "{\"xds_experimental\" : { "
        + "\"childPolicy\" : [{\"round_robin\" : {}}, {\"lbPolicy2\" : {\"key\" : \"val\"}}]"
        + "}}";

    LbConfig config =
        ServiceConfigUtil.unwrapLoadBalancingConfig(checkObject(JsonParser.parse(lbConfig)));
    assertThat(config.getPolicyName()).isEqualTo("xds_experimental");
    assertThat(config.getRawConfigValue()).isEqualTo(JsonParser.parse(
            "{\"childPolicy\" : [{\"round_robin\" : {}}, {\"lbPolicy2\" : {\"key\" : \"val\"}}]"
            + "}"));
  }

  @Test
  public void unwrapLoadBalancingConfig_failOnTooManyFields() throws Exception {
    // A LoadBalancingConfig should not have more than one field.
    String lbConfig = "{\"xds_experimental\" : { "
        + "\"childPolicy\" : [{\"round_robin\" : {}}, {\"lbPolicy2\" : {\"key\" : \"val\"}}]"
        + "},"
        + "\"grpclb\" : {} }";
    try {
      ServiceConfigUtil.unwrapLoadBalancingConfig(checkObject(JsonParser.parse(lbConfig)));
      fail("Should throw");
    } catch (Exception e) {
      assertThat(e).hasMessageThat().contains("There are 2 fields");
    }
  }

  @Test
  public void unwrapLoadBalancingConfig_failOnEmptyObject() throws Exception {
    // A LoadBalancingConfig should not exactly one field.
    String lbConfig = "{}";
    try {
      ServiceConfigUtil.unwrapLoadBalancingConfig(checkObject(JsonParser.parse(lbConfig)));
      fail("Should throw");
    } catch (Exception e) {
      assertThat(e).hasMessageThat().contains("There are 0 fields");
    }
  }

  @Test
  public void unwrapLoadBalancingConfig_failWhenConfigIsString() throws Exception {
    // The value of the config should be a JSON dictionary (map)
    String lbConfig = "{ \"xds\" : \"I thought I was a config.\" }";
    try {
      ServiceConfigUtil.unwrapLoadBalancingConfig(checkObject(JsonParser.parse(lbConfig)));
      fail("Should throw");
    } catch (Exception e) {
      assertThat(e).hasMessageThat().contains("is not object");
    }
  }

  @Test
  public void unwrapLoadBalancingConfigList() throws Exception {
    String lbConfig = "[ "
        + "{\"xds_experimental\" : {\"unknown_field\" : \"dns:///balancer.example.com:8080\"} },"
        + "{\"grpclb\" : {} } ]";
    List<LbConfig> configs =
        ServiceConfigUtil.unwrapLoadBalancingConfigList(
            checkObjectList(JsonParser.parse(lbConfig)));
    assertThat(configs).containsExactly(
        ServiceConfigUtil.unwrapLoadBalancingConfig(checkObject(JsonParser.parse(
                "{\"xds_experimental\" : "
                + "{\"unknown_field\" : \"dns:///balancer.example.com:8080\"} }"))),
        ServiceConfigUtil.unwrapLoadBalancingConfig(checkObject(JsonParser.parse(
                "{\"grpclb\" : {} }")))).inOrder();
  }

  @Test
  public void unwrapLoadBalancingConfigList_failOnMalformedConfig() throws Exception {
    String lbConfig = "[ "
        + "{\"xds_experimental\" : \"I thought I was a config\" },"
        + "{\"grpclb\" : {} } ]";
    try {
      ServiceConfigUtil.unwrapLoadBalancingConfigList(checkObjectList(JsonParser.parse(lbConfig)));
      fail("Should throw");
    } catch (Exception e) {
      assertThat(e).hasMessageThat().contains("is not object");
    }
  }

  @Test
  public void getThrottlePolicy_maxTokensMissing() throws Exception {
    Map<String, Object> throttling = new HashMap<>();
    throttling.put("tokenRatio", 0.1);
    Map<String, Object> serviceConfig = new HashMap<>();
    serviceConfig.put("retryThrottling", throttling);
    try {
      ServiceConfigUtil.getThrottlePolicy(serviceConfig);
      fail("Should throw");
    } catch (IllegalStateException e) {
      assertThat(e).hasMessageThat().contains("maxTokens");
    }
  }

  @Test
  public void getThrottlePolicy_tokenRatioMissing() throws Exception {
    Map<String, Object> throttling = new HashMap<>();
    throttling.put("maxTokens", 10.0);
    Map<String, Object> serviceConfig = new HashMap<>();
    serviceConfig.put("retryThrottling", throttling);
    try {
      ServiceConfigUtil.getThrottlePolicy(serviceConfig);
      fail("Should throw");
    } catch (IllegalStateException e) {
      assertThat(e).hasMessageThat().contains("tokenRatio");
    }
  }

  @Test
  public void getThrottlePolicy_bothFieldsMissing() throws Exception {
    Map<String, Object> serviceConfig = new HashMap<>();
    serviceConfig.put("retryThrottling", Collections.emptyMap());
    try {
      ServiceConfigUtil.getThrottlePolicy(serviceConfig);
      fail("Should throw");
    } catch (IllegalStateException e) {
      assertThat(e).hasMessageThat().contains("maxTokens");
    }
  }

  @Test
  public void getThrottlePolicy_validConfig() throws Exception {
    Map<String, Object> throttling = new HashMap<>();
    throttling.put("maxTokens", 10.0);
    throttling.put("tokenRatio", 0.1);
    Map<String, Object> serviceConfig = new HashMap<>();
    serviceConfig.put("retryThrottling", throttling);
    Throttle throttle = ServiceConfigUtil.getThrottlePolicy(serviceConfig);
    assertThat(throttle).isEqualTo(new Throttle(10f, 0.1f));
  }

  @Test
  public void getThrottlePolicy_nullServiceConfig() {
    assertThat(ServiceConfigUtil.getThrottlePolicy(null)).isNull();
  }

  @Test
  public void getThrottlePolicy_noRetryThrottling() throws Exception {
    Map<String, Object> serviceConfig = new HashMap<>();
    assertThat(ServiceConfigUtil.getThrottlePolicy(serviceConfig)).isNull();
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, ?>> checkObjectList(Object o) {
    return (List<Map<String, ?>>) o;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, ?> checkObject(Object o) {
    return (Map<String, ?>) o;
  }
}
