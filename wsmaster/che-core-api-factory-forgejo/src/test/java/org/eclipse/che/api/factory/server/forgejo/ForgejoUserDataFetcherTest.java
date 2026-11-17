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
package org.eclipse.che.api.factory.server.forgejo;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.Slf4jNotifier;
import com.google.common.net.HttpHeaders;
import java.util.Optional;
import org.eclipse.che.api.factory.server.scm.GitUserData;
import org.eclipse.che.api.factory.server.scm.PersonalAccessToken;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.factory.server.scm.exception.ScmCommunicationException;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(MockitoTestNGListener.class)
public class ForgejoUserDataFetcherTest {

  @Mock private PersonalAccessTokenManager personalAccessTokenManager;

  private WireMockServer wireMockServer;
  private ForgejoUserDataFetcher userDataFetcher;

  @BeforeMethod
  public void start() {
    wireMockServer =
        new WireMockServer(wireMockConfig().notifier(new Slf4jNotifier(false)).dynamicPort());
    wireMockServer.start();
    WireMock.configureFor("localhost", wireMockServer.port());
    userDataFetcher =
        new ForgejoUserDataFetcher(
            wireMockServer.baseUrl(), "http://che.api", personalAccessTokenManager);
  }

  @AfterMethod
  public void stop() {
    wireMockServer.stop();
  }

  @Test
  public void shouldFetchGitUserDataWithOAuthToken() throws Exception {
    when(personalAccessTokenManager.get(any(), eq("forgejo"), isNull(), isNull()))
        .thenReturn(Optional.empty());
    when(personalAccessTokenManager.get(any(), isNull(), eq(wireMockServer.baseUrl()), isNull()))
        .thenReturn(Optional.of(token("oauth2-abcde", "oauthtoken")));
    stubFor(
        get(urlEqualTo("/api/v1/user"))
            .withHeader(HttpHeaders.AUTHORIZATION, equalTo("token oauthtoken"))
            .willReturn(aResponse().withBodyFile("forgejo/api/v1/user.json")));

    GitUserData gitUserData = userDataFetcher.fetchGitUserData(null);

    assertEquals(gitUserData.getScmUsername(), "John Doe");
    assertEquals(gitUserData.getScmUserEmail(), "jdoe@example.com");
  }

  @Test
  public void shouldUseLoginWhenFullNameIsEmpty() throws Exception {
    when(personalAccessTokenManager.get(any(), eq("forgejo"), isNull(), isNull()))
        .thenReturn(Optional.of(token("forgejo", "pat")));
    stubFor(
        get(urlEqualTo("/api/v1/user"))
            .withHeader(HttpHeaders.AUTHORIZATION, equalTo("token pat"))
            .willReturn(
                aResponse()
                    .withBody(
                        "{\"id\": 2, \"login\": \"jane\", \"full_name\": \"\", \"email\": \"jane@example.com\"}")));

    GitUserData gitUserData = userDataFetcher.fetchGitUserData(null);

    assertEquals(gitUserData.getScmUsername(), "jane");
    assertEquals(gitUserData.getScmUserEmail(), "jane@example.com");
  }

  @Test
  public void shouldFetchGitUserDataWithPersonalAccessTokenWhenNotConfigured() throws Exception {
    ForgejoUserDataFetcher fetcher =
        new ForgejoUserDataFetcher(null, "http://che.api", personalAccessTokenManager);
    when(personalAccessTokenManager.get(any(), eq("forgejo"), isNull(), isNull()))
        .thenReturn(Optional.of(token("forgejo", "pat")));
    stubFor(
        get(urlEqualTo("/api/v1/user"))
            .withHeader(HttpHeaders.AUTHORIZATION, equalTo("token pat"))
            .willReturn(aResponse().withBodyFile("forgejo/api/v1/user.json")));

    GitUserData gitUserData = fetcher.fetchGitUserData(null);

    assertEquals(gitUserData.getScmUsername(), "John Doe");
    assertEquals(gitUserData.getScmUserEmail(), "jdoe@example.com");
  }

  @Test(expectedExceptions = ScmCommunicationException.class)
  public void shouldNotLookUpOAuthTokensWhenNotConfigured() throws Exception {
    ForgejoUserDataFetcherSecond fetcher =
        new ForgejoUserDataFetcherSecond(null, "http://che.api", personalAccessTokenManager);
    when(personalAccessTokenManager.get(any(), eq("forgejo_2"), isNull(), isNull()))
        .thenReturn(Optional.empty());
    try {
      fetcher.fetchGitUserData(null);
    } finally {
      // the OAuth token lookup by server URL would match the token of any provider
      verify(personalAccessTokenManager, never()).get(any(), isNull(), any(), any());
    }
  }

  @Test
  public void shouldBuildLocalAuthenticateUrl() {
    assertEquals(
        userDataFetcher.getLocalAuthenticateUrl(),
        "http://che.api/oauth/authenticate?oauth_provider=forgejo&scope=read:user+write:repository"
            + "&request_method=POST&signature_method=rsa");
  }

  private PersonalAccessToken token(String tokenName, String token) {
    return new PersonalAccessToken(
        wireMockServer.baseUrl(), "forgejo", "id1", null, "jdoe", tokenName, "id", token, null, 0);
  }
}
