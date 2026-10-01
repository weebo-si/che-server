/*
 * Copyright (c) 2012-2026 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.security.oauth;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static java.lang.Long.MAX_VALUE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.Slf4jNotifier;
import com.github.tomakehurst.wiremock.http.Fault;
import com.google.api.client.auth.oauth2.StoredCredential;
import com.google.api.client.util.store.MemoryDataStoreFactory;
import com.google.common.net.HttpHeaders;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.List;
import org.eclipse.che.api.auth.shared.dto.OAuthToken;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class ForgejoAuthenticatorTest {

  private WireMockServer wireMockServer;
  private ForgejoOAuthAuthenticator authenticator;

  @BeforeMethod
  public void setup() throws Exception {
    wireMockServer =
        new WireMockServer(wireMockConfig().notifier(new Slf4jNotifier(false)).dynamicPort());
    wireMockServer.start();
    WireMock.configureFor("localhost", wireMockServer.port());
    authenticator =
        new ForgejoOAuthAuthenticator(
            "id", "secret", wireMockServer.url("/"), "https://che.api.com", "forgejo");
    storeCredential(authenticator, "token");
  }

  @AfterMethod
  public void stop() {
    wireMockServer.stop();
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static void storeCredential(OAuthAuthenticator authenticator, String accessToken)
      throws Exception {
    Field flowField = OAuthAuthenticator.class.getDeclaredField("flow");
    Field credentialDataStoreField =
        ((Class) flowField.getGenericType()).getDeclaredField("credentialDataStore");
    credentialDataStoreField.setAccessible(true);
    credentialDataStoreField.set(
        flowField.get(authenticator),
        new MemoryDataStoreFactory()
            .getDataStore("test")
            .set(
                "userId",
                new StoredCredential()
                    .setAccessToken(accessToken)
                    .setRefreshToken("refreshToken")
                    .setExpirationTimeMilliseconds(MAX_VALUE)));
  }

  @Test
  public void shouldGetToken() throws Exception {
    // Forgejo returns a numeric user id
    stubFor(
        get(urlEqualTo("/api/v1/user"))
            .withHeader(HttpHeaders.AUTHORIZATION, equalTo("token token"))
            .willReturn(
                aResponse()
                    .withBody(
                        "{\"id\": 1, \"login\": \"jdoe\", \"full_name\": \"John Doe\", \"email\": \"jdoe@example.com\"}")));

    OAuthToken token = authenticator.getOrRefreshToken("userId");

    assertEquals(token.getToken(), "token");
    assertEquals(token.getRefreshToken(), "refreshToken");
  }

  @Test
  public void shouldNotReturnRejectedToken() throws Exception {
    stubFor(get(urlEqualTo("/api/v1/user")).willReturn(aResponse().withStatus(401)));

    assertNull(authenticator.getOrRefreshToken("userId"));
  }

  @Test
  public void shouldNotReturnTokenWithoutUser() throws Exception {
    stubFor(get(urlEqualTo("/api/v1/user")).willReturn(aResponse().withBody("{}")));

    assertNull(authenticator.getOrRefreshToken("userId"));
  }

  @Test
  public void shouldNotReturnTokenOfUnknownUser() throws Exception {
    assertNull(authenticator.getOrRefreshToken("unknownUserId"));
  }

  @Test
  public void shouldNotReturnEmptyToken() throws Exception {
    storeCredential(authenticator, "");

    assertNull(authenticator.getOrRefreshToken("userId"));
  }

  @Test
  public void shouldNotReturnTokenWhenUserBodyIsInvalid() throws Exception {
    stubFor(get(urlEqualTo("/api/v1/user")).willReturn(aResponse().withBody("null")));

    assertNull(authenticator.getOrRefreshToken("userId"));
  }

  @Test
  public void shouldNotReturnTokenWhenNoUserIsReturned() throws Exception {
    ForgejoOAuthAuthenticator noUserAuthenticator =
        new ForgejoOAuthAuthenticator(
            "id", "secret", wireMockServer.url("/"), "https://che.api.com", "forgejo") {
          @Override
          protected <O> O getJson(String getUserUrl, String accessToken, Class<O> userClass) {
            return null;
          }
        };
    storeCredential(noUserAuthenticator, "token");

    assertNull(noUserAuthenticator.getOrRefreshToken("userId"));
  }

  @Test
  public void shouldNotReturnTokenWhenServerIsUnreachable() throws Exception {
    stubFor(
        get(urlEqualTo("/api/v1/user"))
            .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

    assertNull(authenticator.getOrRefreshToken("userId"));
  }

  @Test
  public void shouldBuildAuthenticateUrl() throws Exception {
    String url =
        authenticator.getAuthenticateUrl(
            new URL("https://che.api.com/oauth/authenticate"),
            List.of("read:user", "write:repository"));

    assertTrue(url.startsWith(wireMockServer.url("/login/oauth/authorize?")), url);
    assertTrue(url.contains("client_id=id"), url);
    assertTrue(url.contains("redirect_uri=https://che.api.com/oauth/callback"), url);
    assertTrue(url.contains("scope=read:user%20write:repository"), url);
  }

  @Test
  public void shouldExposeProviderAndEndpoint() {
    assertEquals(authenticator.getOAuthProvider(), "forgejo");
    assertEquals(authenticator.getEndpointUrl(), wireMockServer.baseUrl());
  }
}
