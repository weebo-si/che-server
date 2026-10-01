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
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.Slf4jNotifier;
import java.util.List;
import java.util.Optional;
import org.eclipse.che.api.factory.server.scm.PersonalAccessToken;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.factory.server.scm.exception.ScmConfigurationPersistenceException;
import org.eclipse.che.api.factory.server.urlfactory.DevfileFilenamesProvider;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(MockitoTestNGListener.class)
public class ForgejoUrlParserTest {

  private static final String SERVER = "https://forgejo.example.com";

  @Mock private DevfileFilenamesProvider devfileFilenamesProvider;
  @Mock private PersonalAccessTokenManager personalAccessTokenManager;

  private ForgejoUrlParser forgejoUrlParser;
  private WireMockServer wireMockServer;

  @BeforeMethod
  public void setUp() {
    wireMockServer =
        new WireMockServer(wireMockConfig().notifier(new Slf4jNotifier(false)).dynamicPort());
    wireMockServer.start();
    WireMock.configureFor("localhost", wireMockServer.port());
    lenient()
        .when(devfileFilenamesProvider.getConfiguredDevfileFilenames())
        .thenReturn(List.of("devfile.yaml", ".devfile.yaml"));
    forgejoUrlParser =
        new ForgejoUrlParser(SERVER, devfileFilenamesProvider, personalAccessTokenManager);
  }

  @AfterMethod
  public void tearDown() {
    wireMockServer.stop();
  }

  @DataProvider
  public Object[][] urls() {
    return new Object[][] {
      // url, owner, repo, branch, repository location
      {"https://forgejo.example.com/owner/repo", "owner", "repo", null, SERVER + "/owner/repo.git"},
      {
        "https://forgejo.example.com/owner/repo/", "owner", "repo", null, SERVER + "/owner/repo.git"
      },
      {
        "https://forgejo.example.com/owner/repo.git",
        "owner",
        "repo",
        null,
        SERVER + "/owner/repo.git"
      },
      {
        "https://forgejo.example.com/owner/my.repo",
        "owner",
        "my.repo",
        null,
        SERVER + "/owner/my.repo.git"
      },
      {
        "https://forgejo.example.com/owner/repo/src/branch/main",
        "owner",
        "repo",
        "main",
        SERVER + "/owner/repo.git"
      },
      {
        "https://forgejo.example.com/owner/repo/src/branch/feature/new-ui",
        "owner",
        "repo",
        "feature/new-ui",
        SERVER + "/owner/repo.git"
      },
      {
        "https://forgejo.example.com/owner/repo/src/tag/v1.0.0",
        "owner",
        "repo",
        "v1.0.0",
        SERVER + "/owner/repo.git"
      },
      {
        "https://forgejo.example.com/owner/repo/src/commit/0a1b2c3d4e5f",
        "owner",
        "repo",
        "0a1b2c3d4e5f",
        SERVER + "/owner/repo.git"
      },
      {
        "https://forgejo.example.com/owner/repo/src/commit/0a1b2c3d4e5f/docs/README.md",
        "owner",
        "repo",
        "0a1b2c3d4e5f",
        SERVER + "/owner/repo.git"
      },
      {
        "https://forgejo.example.com/owner/repo/src/branch/main?display=source",
        "owner",
        "repo",
        "main",
        SERVER + "/owner/repo.git"
      },
      {
        "https://forgejo.example.com/owner/repo/issues/1",
        "owner",
        "repo",
        null,
        SERVER + "/owner/repo.git"
      },
      {
        "git@forgejo.example.com:owner/repo.git",
        "owner",
        "repo",
        null,
        "git@forgejo.example.com:owner/repo.git"
      },
      {
        "ssh://git@forgejo.example.com/owner/repo.git",
        "owner",
        "repo",
        null,
        "ssh://git@forgejo.example.com/owner/repo.git"
      },
      {
        "ssh://git@forgejo.example.com:2222/owner/repo.git",
        "owner",
        "repo",
        null,
        "ssh://git@forgejo.example.com:2222/owner/repo.git"
      },
    };
  }

  @Test(dataProvider = "urls")
  public void shouldParseUrl(
      String url, String owner, String repository, String branch, String repositoryLocation) {
    assertTrue(forgejoUrlParser.isValid(url), url);

    ForgejoUrl forgejoUrl = forgejoUrlParser.parse(url, null);

    assertEquals(forgejoUrl.getProviderName(), "forgejo");
    assertEquals(forgejoUrl.getProviderUrl(), SERVER);
    assertEquals(forgejoUrl.getHostName(), "forgejo.example.com");
    assertEquals(forgejoUrl.getOwner(), owner);
    assertEquals(forgejoUrl.getRepository(), repository);
    assertEquals(forgejoUrl.getBranch(), branch);
    assertEquals(forgejoUrl.repositoryLocation(), repositoryLocation);
  }

  @DataProvider
  public Object[][] invalidUrls() {
    return new Object[][] {
      {"https://github.com/owner/repo"},
      {"https://forgejo.example.com.evil.com/owner/repo"},
      {"https://forgejo.example.com/owner"},
      {"https://forgejo.example.com"},
      {"git@github.com:owner/repo.git"},
      {"ssh://git@gitlab.com:2222/owner/repo.git"},
    };
  }

  @Test(dataProvider = "invalidUrls")
  public void shouldNotAcceptUrl(String url) throws Exception {
    assertFalse(forgejoUrlParser.isValid(url), url);
  }

  @Test
  public void shouldUseRevisionWhenUrlHasNoBranch() {
    assertEquals(
        forgejoUrlParser.parse("https://forgejo.example.com/owner/repo", "dev").getBranch(), "dev");
    assertEquals(
        forgejoUrlParser
            .parse("https://forgejo.example.com/owner/repo/src/branch/main", "dev")
            .getBranch(),
        "main");
  }

  @Test
  public void shouldDecodeBranch() {
    assertEquals(
        forgejoUrlParser
            .parse("https://forgejo.example.com/owner/repo/src/branch/fix%23123", null)
            .getBranch(),
        "fix#123");
  }

  @Test
  public void shouldBuildRawFileLocations() {
    ForgejoUrl forgejoUrl =
        forgejoUrlParser.parse("https://forgejo.example.com/owner/repo/src/branch/feature/x", null);

    assertEquals(
        forgejoUrl.devfileFileLocations().get(0).location(),
        SERVER + "/api/v1/repos/owner/repo/raw/devfile.yaml?ref=feature%2Fx");
    assertEquals(
        forgejoUrl.rawFileLocation("dir/my file.yaml"),
        SERVER + "/api/v1/repos/owner/repo/raw/dir/my%20file.yaml?ref=feature%2Fx");
    assertEquals(
        forgejoUrlParser
            .parse("https://forgejo.example.com/owner/repo", null)
            .rawFileLocation("devfile.yaml"),
        SERVER + "/api/v1/repos/owner/repo/raw/devfile.yaml");
  }

  @Test
  public void shouldSupportEndpointWithPath() throws Exception {
    ForgejoUrlParser parser =
        new ForgejoUrlParser(
            "https://example.com/forgejo/", devfileFilenamesProvider, personalAccessTokenManager);

    assertTrue(parser.isValid("https://example.com/forgejo/owner/repo"));
    assertFalse(parser.isValid("https://example.com/owner/repo"));
    ForgejoUrl forgejoUrl = parser.parse("https://example.com/forgejo/owner/repo", null);
    assertEquals(forgejoUrl.getProviderUrl(), "https://example.com/forgejo");
    assertEquals(forgejoUrl.getOwner(), "owner");
    assertEquals(forgejoUrl.repositoryLocation(), "https://example.com/forgejo/owner/repo.git");
  }

  @Test
  public void shouldSupportEndpointWithPort() throws Exception {
    ForgejoUrlParser parser =
        new ForgejoUrlParser(
            "https://forgejo.example.com:3000",
            devfileFilenamesProvider,
            personalAccessTokenManager);

    assertTrue(parser.isValid("https://forgejo.example.com:3000/owner/repo"));
    assertFalse(parser.isValid("https://forgejo.example.com/owner/repo"));
    // SSH port is independent from the HTTP one
    assertTrue(parser.isValid("ssh://git@forgejo.example.com:2222/owner/repo.git"));
    assertEquals(
        parser.parse("git@forgejo.example.com:owner/repo.git", null).getProviderUrl(),
        "https://forgejo.example.com:3000");
  }

  @Test
  public void shouldNotProbeUnknownHostWithoutToken() throws Exception {
    when(personalAccessTokenManager.get(any(), isNull(), eq(wireMockServer.baseUrl()), isNull()))
        .thenReturn(Optional.empty());

    assertFalse(forgejoUrlParser.isValid(wireMockServer.url("/owner/repo")));
    wireMockServer.verify(0, anyRequestedFor(anyUrl()));
  }

  @Test
  public void shouldAcceptUnknownForgejoHostWithToken() throws Exception {
    when(personalAccessTokenManager.get(any(), isNull(), eq(wireMockServer.baseUrl()), isNull()))
        .thenReturn(Optional.of(forgejoPersonalAccessToken(wireMockServer.baseUrl())));
    stubFor(
        get(urlEqualTo("/api/forgejo/v1/version"))
            .willReturn(aResponse().withBody("{\"version\": \"11.0.0+gitea-1.22.0\"}")));

    String url = wireMockServer.url("/owner/repo/src/branch/main");
    assertTrue(forgejoUrlParser.isValid(url));

    ForgejoUrl forgejoUrl = forgejoUrlParser.parse(url, null);
    assertEquals(forgejoUrl.getProviderUrl(), wireMockServer.baseUrl());
    assertEquals(forgejoUrl.getOwner(), "owner");
    assertEquals(forgejoUrl.getBranch(), "main");
  }

  @Test
  public void shouldAcceptUnknownGiteaHostWithToken() throws Exception {
    when(personalAccessTokenManager.get(any(), isNull(), eq(wireMockServer.baseUrl()), isNull()))
        .thenReturn(Optional.of(forgejoPersonalAccessToken(wireMockServer.baseUrl())));
    stubFor(get(urlEqualTo("/api/forgejo/v1/version")).willReturn(aResponse().withStatus(404)));
    stubFor(
        get(urlEqualTo("/api/v1/version"))
            .willReturn(aResponse().withBody("{\"version\": \"1.22.0\"}")));

    assertTrue(forgejoUrlParser.isValid(wireMockServer.url("/owner/repo")));
  }

  @Test
  public void shouldNotAcceptUnknownHostWhenNotForgejo() throws Exception {
    when(personalAccessTokenManager.get(any(), isNull(), eq(wireMockServer.baseUrl()), isNull()))
        .thenReturn(Optional.of(forgejoPersonalAccessToken(wireMockServer.baseUrl())));
    stubFor(get(anyUrl()).willReturn(aResponse().withStatus(404)));

    assertFalse(forgejoUrlParser.isValid(wireMockServer.url("/owner/repo")));
  }

  @Test
  public void shouldNotAcceptUnknownHostWithTokenOfAnotherProvider() throws Exception {
    when(personalAccessTokenManager.get(any(), isNull(), eq(wireMockServer.baseUrl()), isNull()))
        .thenReturn(Optional.of(personalAccessToken(wireMockServer.baseUrl(), "gitlab")));

    assertFalse(forgejoUrlParser.isValid(wireMockServer.url("/owner/repo")));
    wireMockServer.verify(0, anyRequestedFor(anyUrl()));
  }

  @Test
  public void shouldNotMatchAnythingWhenNotConfigured() throws Exception {
    ForgejoUrlParserSecond parser =
        new ForgejoUrlParserSecond(null, devfileFilenamesProvider, personalAccessTokenManager);

    assertFalse(parser.isValid("https://forgejo.example.com/owner/repo"));
    assertFalse(parser.isValid("git@forgejo.example.com:owner/repo.git"));
  }

  @Test(expectedExceptions = UnsupportedOperationException.class)
  public void shouldFailToParseNonRepositoryUrl() {
    forgejoUrlParser.parse("https://forgejo.example.com", null);
  }

  @Test
  public void shouldNotSetBranchWhenNoneGiven() {
    assertNull(forgejoUrlParser.parse("https://forgejo.example.com/owner/repo", null).getBranch());
  }

  @DataProvider
  public Object[][] notUrls() {
    return new Object[][] {
      {"not-a-url"}, // no scheme
      {"mailto:owner@forgejo.example.com"}, // no authority
      {"https://exa_mple.com/owner/repo"}, // no host
      {"https://exa mple.com/owner/repo"}, // invalid URI
    };
  }

  @Test(dataProvider = "notUrls")
  public void shouldNotAcceptNonRepositoryUrl(String url) {
    assertFalse(forgejoUrlParser.isValid(url), url);
  }

  @Test(dataProvider = "notUrls", expectedExceptions = UnsupportedOperationException.class)
  public void shouldFailToParseNonRepositoryUrl(String url) {
    forgejoUrlParser.parse(url, null);
  }

  @Test
  public void shouldNotAcceptUnknownHostWhenTokensCannotBeRead() throws Exception {
    when(personalAccessTokenManager.get(any(), isNull(), eq(wireMockServer.baseUrl()), isNull()))
        .thenThrow(
            new ScmConfigurationPersistenceException("cannot read secrets", new Exception()));

    assertFalse(forgejoUrlParser.isValid(wireMockServer.url("/owner/repo")));
  }

  @Test
  public void shouldOverrideDevfileFilename() {
    ForgejoUrl forgejoUrl = forgejoUrlParser.parse("https://forgejo.example.com/owner/repo", null);

    forgejoUrl.setDevfileFilename("custom.yaml");

    assertEquals(forgejoUrl.devfileFileLocations().size(), 1);
    assertEquals(forgejoUrl.devfileFileLocations().get(0).filename(), Optional.of("custom.yaml"));
    assertEquals(
        forgejoUrl.devfileFileLocations().get(0).location(),
        SERVER + "/api/v1/repos/owner/repo/raw/custom.yaml");
  }

  private static PersonalAccessToken forgejoPersonalAccessToken(String serverUrl) {
    return personalAccessToken(serverUrl, "forgejo");
  }

  private static PersonalAccessToken personalAccessToken(String serverUrl, String provider) {
    return new PersonalAccessToken(
        serverUrl, provider, "che-user", null, "user", provider, "id", "token", null, 0);
  }
}
