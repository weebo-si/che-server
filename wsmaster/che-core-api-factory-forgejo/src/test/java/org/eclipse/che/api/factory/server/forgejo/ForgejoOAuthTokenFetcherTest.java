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
import static org.eclipse.che.dto.server.DtoFactory.newDto;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.Slf4jNotifier;
import com.google.common.net.HttpHeaders;
import java.util.Collections;
import java.util.Optional;
import org.eclipse.che.api.auth.shared.dto.OAuthToken;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.UnauthorizedException;
import org.eclipse.che.api.factory.server.scm.PersonalAccessToken;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenParams;
import org.eclipse.che.api.factory.server.scm.exception.ScmCommunicationException;
import org.eclipse.che.api.factory.server.scm.exception.ScmUnauthorizedException;
import org.eclipse.che.api.factory.server.scm.exception.UnknownScmProviderException;
import org.eclipse.che.commons.lang.Pair;
import org.eclipse.che.commons.subject.Subject;
import org.eclipse.che.commons.subject.SubjectImpl;
import org.eclipse.che.security.oauth.OAuthAPI;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(MockitoTestNGListener.class)
public class ForgejoOAuthTokenFetcherTest {

  @Mock private OAuthAPI oAuthAPI;

  private ForgejoOAuthTokenFetcher oAuthTokenFetcher;
  private WireMockServer wireMockServer;
  private final Subject subject =
      new SubjectImpl("Username", Collections.emptyList(), "id1", "token", false);

  @BeforeMethod
  public void start() {
    wireMockServer =
        new WireMockServer(wireMockConfig().notifier(new Slf4jNotifier(false)).dynamicPort());
    wireMockServer.start();
    WireMock.configureFor("localhost", wireMockServer.port());
    oAuthTokenFetcher =
        new ForgejoOAuthTokenFetcher(wireMockServer.url("/"), "http://che.api", oAuthAPI);
  }

  @AfterMethod
  public void stop() {
    wireMockServer.stop();
  }

  private void stubUser(String token) {
    stubFor(
        get(urlEqualTo("/api/v1/user"))
            .withHeader(HttpHeaders.AUTHORIZATION, equalTo("token " + token))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withBodyFile("forgejo/api/v1/user.json")));
  }

  @Test
  public void shouldReturnToken() throws Exception {
    OAuthToken oAuthToken =
        newDto(OAuthToken.class)
            .withToken("oauthtoken")
            .withRefreshToken("refresh")
            .withExpiresIn(3600);
    when(oAuthAPI.getOrRefreshToken("forgejo")).thenReturn(oAuthToken);
    stubUser("oauthtoken");

    PersonalAccessToken token =
        oAuthTokenFetcher.fetchPersonalAccessToken(subject, wireMockServer.url("/"));

    assertEquals(token.getToken(), "oauthtoken");
    assertEquals(token.getRefreshToken(), "refresh");
    assertEquals(token.getExpiresIn(), 3600);
    assertEquals(token.getScmProviderName(), "forgejo");
    assertEquals(token.getScmUserName(), "jdoe");
    assertEquals(token.getScmProviderUrl(), wireMockServer.baseUrl());
    assertTrue(token.getScmTokenName().startsWith("oauth2-"));
  }

  @Test
  public void shouldRefreshToken() throws Exception {
    when(oAuthAPI.refreshToken("forgejo"))
        .thenReturn(newDto(OAuthToken.class).withToken("refreshed"));
    stubUser("refreshed");

    PersonalAccessToken token =
        oAuthTokenFetcher.refreshPersonalAccessToken(subject, wireMockServer.url("/"));

    assertEquals(token.getToken(), "refreshed");
    verify(oAuthAPI, never()).getOrRefreshToken("forgejo");
  }

  @Test(
      expectedExceptions = ScmUnauthorizedException.class,
      expectedExceptionsMessageRegExp = "Username is not authorized in forgejo OAuth provider.")
  public void shouldThrowUnauthorizedExceptionWhenUserNotLoggedIn() throws Exception {
    when(oAuthAPI.getOrRefreshToken("forgejo")).thenThrow(UnauthorizedException.class);

    oAuthTokenFetcher.fetchPersonalAccessToken(subject, wireMockServer.url("/"));
  }

  @Test(expectedExceptions = ScmUnauthorizedException.class)
  public void shouldThrowUnauthorizedExceptionWhenTokenRejected() throws Exception {
    when(oAuthAPI.getOrRefreshToken("forgejo"))
        .thenReturn(newDto(OAuthToken.class).withToken("revoked"));
    stubFor(get(urlEqualTo("/api/v1/user")).willReturn(aResponse().withStatus(401)));

    oAuthTokenFetcher.fetchPersonalAccessToken(subject, wireMockServer.url("/"));
  }

  @Test
  public void shouldIgnoreOtherServers() throws Exception {
    assertNull(oAuthTokenFetcher.fetchPersonalAccessToken(subject, "https://other.example.com"));
  }

  @Test
  public void shouldIgnoreAllServersWhenNotConfigured() throws Exception {
    ForgejoOAuthTokenFetcherSecond fetcher =
        new ForgejoOAuthTokenFetcherSecond(null, "http://che.api", oAuthAPI);

    assertNull(fetcher.fetchPersonalAccessToken(subject, wireMockServer.url("/")));
  }

  @Test
  public void shouldValidatePersonalAccessToken() {
    stubUser("pat");

    assertEquals(
        oAuthTokenFetcher.isValid(personalAccessToken(wireMockServer.baseUrl(), "jdoe", "pat")),
        Optional.of(Boolean.TRUE));
    assertEquals(
        oAuthTokenFetcher.isValid(personalAccessToken(wireMockServer.baseUrl(), "other", "pat")),
        Optional.of(Boolean.FALSE));
  }

  @Test
  public void shouldValidatePersonalAccessTokenOfUnconfiguredServer() {
    ForgejoOAuthTokenFetcher fetcher =
        new ForgejoOAuthTokenFetcher(null, "http://che.api", oAuthAPI);
    ForgejoOAuthTokenFetcherSecond fetcherSecond =
        new ForgejoOAuthTokenFetcherSecond(null, "http://che.api", oAuthAPI);
    stubUser("pat");

    // personal access tokens are named after the provider: "forgejo"
    assertEquals(
        fetcher.isValid(personalAccessToken(wireMockServer.baseUrl(), "jdoe", "pat")),
        Optional.of(Boolean.TRUE));
    assertEquals(
        fetcherSecond.isValid(personalAccessToken(wireMockServer.baseUrl(), "jdoe", "pat")),
        Optional.empty());
  }

  @Test
  public void shouldInvalidateRejectedOAuthToken() {
    stubFor(get(urlEqualTo("/api/v1/user")).willReturn(aResponse().withStatus(401)));
    PersonalAccessToken token =
        new PersonalAccessToken(
            wireMockServer.baseUrl(),
            "forgejo",
            "id1",
            null,
            "jdoe",
            "oauth2-abcde",
            "id",
            "expired",
            null,
            0);

    assertEquals(oAuthTokenFetcher.isValid(token), Optional.of(Boolean.FALSE));
  }

  @Test
  public void shouldReturnUsernameOnParamsValidation() throws Exception {
    stubUser("pat");

    Optional<Pair<Boolean, String>> valid =
        oAuthTokenFetcher.isValid(
            new PersonalAccessTokenParams(
                wireMockServer.baseUrl(), "forgejo", "forgejo", "id", "pat", null));

    assertTrue(valid.isPresent());
    assertTrue(valid.get().first);
    assertEquals(valid.get().second, "jdoe");
  }

  @Test
  public void shouldNotValidateParamsOfOtherServer() throws Exception {
    assertFalse(
        oAuthTokenFetcher
            .isValid(
                new PersonalAccessTokenParams(
                    "https://other.example.com", "gitlab", "gitlab", "id", "pat", null))
            .isPresent());
  }

  @Test(
      expectedExceptions = ScmCommunicationException.class,
      expectedExceptionsMessageRegExp =
          "OAuth 2 is not configured for SCM provider \\[forgejo\\].*")
  public void shouldThrowWhenOAuthApiIsNotAvailable() throws Exception {
    new ForgejoOAuthTokenFetcher(wireMockServer.url("/"), "http://che.api", null)
        .fetchPersonalAccessToken(subject, wireMockServer.url("/"));
  }

  @Test(expectedExceptions = UnknownScmProviderException.class)
  public void shouldThrowUnknownScmProviderWhenProviderNotFound() throws Exception {
    when(oAuthAPI.getOrRefreshToken("forgejo")).thenThrow(new NotFoundException("not found"));

    oAuthTokenFetcher.fetchPersonalAccessToken(subject, wireMockServer.url("/"));
  }

  @Test(expectedExceptions = ScmCommunicationException.class)
  public void shouldThrowCommunicationExceptionOnServerError() throws Exception {
    when(oAuthAPI.getOrRefreshToken("forgejo")).thenThrow(new ServerException("error"));

    oAuthTokenFetcher.fetchPersonalAccessToken(subject, wireMockServer.url("/"));
  }

  @Test
  public void shouldValidateOAuthToken() {
    stubUser("oauthtoken");
    PersonalAccessToken token =
        new PersonalAccessToken(
            wireMockServer.baseUrl(),
            "forgejo",
            "id1",
            null,
            "jdoe",
            "oauth2-abcde",
            "id",
            "oauthtoken",
            null,
            0);

    assertEquals(oAuthTokenFetcher.isValid(token), Optional.of(Boolean.TRUE));
  }

  @Test
  public void shouldValidateTokenWithoutName() {
    stubUser("pat");
    PersonalAccessToken token =
        new PersonalAccessToken(
            wireMockServer.baseUrl(), "forgejo", "id1", null, "jdoe", null, "id", "pat", null, 0);

    assertEquals(oAuthTokenFetcher.isValid(token), Optional.of(Boolean.TRUE));
  }

  private static PersonalAccessToken personalAccessToken(
      String serverUrl, String userName, String token) {
    return new PersonalAccessToken(
        serverUrl, "forgejo", "id1", null, userName, "forgejo", "id", token, null, 0);
  }
}
