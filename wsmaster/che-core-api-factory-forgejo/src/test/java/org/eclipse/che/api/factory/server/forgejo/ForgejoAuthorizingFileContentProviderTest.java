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
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.Slf4jNotifier;
import com.github.tomakehurst.wiremock.http.Fault;
import java.io.FileNotFoundException;
import org.eclipse.che.api.factory.server.scm.PersonalAccessToken;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.factory.server.scm.exception.UnknownScmProviderException;
import org.eclipse.che.api.workspace.server.devfile.FileContentProvider;
import org.eclipse.che.api.workspace.server.devfile.URLFetcher;
import org.eclipse.che.api.workspace.server.devfile.exception.DevfileException;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(MockitoTestNGListener.class)
public class ForgejoAuthorizingFileContentProviderTest {

  @Mock private PersonalAccessTokenManager personalAccessTokenManager;
  @Mock private URLFetcher urlFetcher;

  private WireMockServer wireMockServer;

  @BeforeMethod
  public void start() {
    wireMockServer =
        new WireMockServer(wireMockConfig().notifier(new Slf4jNotifier(false)).dynamicPort());
    wireMockServer.start();
    WireMock.configureFor("localhost", wireMockServer.port());
  }

  @AfterMethod
  public void stop() {
    wireMockServer.stop();
  }

  private ForgejoUrl forgejoUrl(String providerUrl) {
    return new ForgejoUrl()
        .withProviderUrl(providerUrl)
        .withHostName("forgejo.example.com")
        .withOwner("owner")
        .withRepository("repo")
        .withBranch("main");
  }

  @Test
  public void shouldFetchRelativePathWithToken() throws Exception {
    FileContentProvider fileContentProvider =
        new ForgejoAuthorizingFileContentProvider(
            forgejoUrl("https://forgejo.example.com"), urlFetcher, personalAccessTokenManager);
    when(personalAccessTokenManager.getAndStore("https://forgejo.example.com"))
        .thenReturn(
            new PersonalAccessToken("https://forgejo.example.com", "forgejo", "jdoe", "my-token"));

    fileContentProvider.fetchContent("devfile.yaml");

    verify(urlFetcher)
        .fetch(
            eq("https://forgejo.example.com/api/v1/repos/owner/repo/raw/devfile.yaml?ref=main"),
            eq("token my-token"));
  }

  @Test(expectedExceptions = FileNotFoundException.class)
  public void shouldThrowFileNotFoundExceptionForPublicRepository() throws Exception {
    String devfileUrl = wireMockServer.url("/api/v1/repos/owner/repo/raw/devfile.yaml?ref=main");
    when(personalAccessTokenManager.getAndStore(anyString()))
        .thenThrow(new UnknownScmProviderException("", ""));
    when(urlFetcher.fetch(devfileUrl)).thenThrow(new FileNotFoundException());
    stubFor(
        get(urlEqualTo("/api/v1/repos/owner/repo")).willReturn(aResponse().withStatus(HTTP_OK)));

    new ForgejoAuthorizingFileContentProvider(
            forgejoUrl(wireMockServer.baseUrl()), urlFetcher, personalAccessTokenManager)
        .fetchContent("devfile.yaml");
  }

  @Test(expectedExceptions = DevfileException.class)
  public void shouldThrowDevfileExceptionWhenServerIsUnreachable() throws Exception {
    String devfileUrl = wireMockServer.url("/api/v1/repos/owner/repo/raw/devfile.yaml?ref=main");
    when(personalAccessTokenManager.getAndStore(anyString()))
        .thenThrow(new UnknownScmProviderException("", ""));
    when(urlFetcher.fetch(devfileUrl)).thenThrow(new FileNotFoundException());
    stubFor(
        get(urlEqualTo("/api/v1/repos/owner/repo"))
            .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

    new ForgejoAuthorizingFileContentProvider(
            forgejoUrl(wireMockServer.baseUrl()), urlFetcher, personalAccessTokenManager)
        .fetchContent("devfile.yaml");
  }

  @Test(expectedExceptions = DevfileException.class)
  public void shouldThrowDevfileExceptionForPrivateRepository() throws Exception {
    String devfileUrl = wireMockServer.url("/api/v1/repos/owner/repo/raw/devfile.yaml?ref=main");
    when(personalAccessTokenManager.getAndStore(anyString()))
        .thenThrow(new UnknownScmProviderException("", ""));
    when(urlFetcher.fetch(devfileUrl)).thenThrow(new FileNotFoundException());
    stubFor(
        get(urlEqualTo("/api/v1/repos/owner/repo"))
            .willReturn(aResponse().withStatus(HTTP_NOT_FOUND)));

    new ForgejoAuthorizingFileContentProvider(
            forgejoUrl(wireMockServer.baseUrl()), urlFetcher, personalAccessTokenManager)
        .fetchContent("devfile.yaml");
  }
}
