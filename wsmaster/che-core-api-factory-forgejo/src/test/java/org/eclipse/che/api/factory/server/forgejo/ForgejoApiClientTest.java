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
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.Slf4jNotifier;
import com.github.tomakehurst.wiremock.http.Fault;
import com.google.common.net.HttpHeaders;
import org.eclipse.che.api.factory.server.scm.exception.ScmBadRequestException;
import org.eclipse.che.api.factory.server.scm.exception.ScmCommunicationException;
import org.eclipse.che.api.factory.server.scm.exception.ScmItemNotFoundException;
import org.eclipse.che.api.factory.server.scm.exception.ScmUnauthorizedException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class ForgejoApiClientTest {

  private WireMockServer wireMockServer;
  private ForgejoApiClient client;

  @BeforeMethod
  public void start() {
    wireMockServer =
        new WireMockServer(wireMockConfig().notifier(new Slf4jNotifier(false)).dynamicPort());
    wireMockServer.start();
    WireMock.configureFor("localhost", wireMockServer.port());
    client = new ForgejoApiClient(wireMockServer.url("/"));
  }

  @AfterMethod
  public void stop() {
    wireMockServer.stop();
  }

  @Test
  public void shouldGetUser() throws Exception {
    stubFor(
        get(urlEqualTo("/api/v1/user"))
            .withHeader(HttpHeaders.AUTHORIZATION, equalTo("token my-token"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withBodyFile("forgejo/api/v1/user.json")));

    ForgejoUser user = client.getUser("my-token");

    assertEquals(user.getId(), 1);
    assertEquals(user.getLogin(), "jdoe");
    assertEquals(user.getFullName(), "John Doe");
    assertEquals(user.getEmail(), "jdoe@example.com");
  }

  @Test(expectedExceptions = ScmUnauthorizedException.class)
  public void shouldThrowUnauthorizedException() throws Exception {
    stubFor(
        get(urlEqualTo("/api/v1/user"))
            .willReturn(aResponse().withStatus(401).withBody("{\"message\": \"invalid token\"}")));

    client.getUser("bad-token");
  }

  @Test
  public void shouldGetFileContent() throws Exception {
    stubFor(
        get(urlEqualTo("/api/v1/repos/owner/repo/raw/.devfile.yaml?ref=feature%2Fx"))
            .withHeader(HttpHeaders.AUTHORIZATION, equalTo("token my-token"))
            .willReturn(aResponse().withBody("schemaVersion: 2.2.0")));

    assertEquals(
        client.getFileContent("owner", "repo", ".devfile.yaml", "feature/x", "my-token"),
        "schemaVersion: 2.2.0");
  }

  @Test
  public void shouldGetFileContentAnonymously() throws Exception {
    stubFor(
        get(urlEqualTo("/api/v1/repos/owner/repo/raw/devfile.yaml"))
            .willReturn(aResponse().withBody("schemaVersion: 2.2.0")));

    assertEquals(
        client.getFileContent("owner", "repo", "devfile.yaml", null, null), "schemaVersion: 2.2.0");
    wireMockServer.verify(
        getRequestedFor(urlEqualTo("/api/v1/repos/owner/repo/raw/devfile.yaml"))
            .withHeader(HttpHeaders.AUTHORIZATION, absent()));
  }

  @Test(expectedExceptions = ScmItemNotFoundException.class)
  public void shouldThrowNotFoundException() throws Exception {
    stubFor(
        get(urlEqualTo("/api/v1/repos/owner/repo/raw/devfile.yaml"))
            .willReturn(aResponse().withStatus(404)));

    client.getFileContent("owner", "repo", "devfile.yaml", null, null);
  }

  @Test
  public void shouldDetectForgejoServer() {
    stubFor(
        get(urlEqualTo("/api/forgejo/v1/version"))
            .willReturn(aResponse().withBody("{\"version\": \"11.0.0\"}")));

    assertTrue(client.isForgejoServer());
  }

  @Test
  public void shouldDetectGiteaServer() {
    stubFor(get(urlEqualTo("/api/forgejo/v1/version")).willReturn(aResponse().withStatus(404)));
    stubFor(
        get(urlEqualTo("/api/v1/version"))
            .willReturn(aResponse().withBody("{\"version\": \"1.22.0\"}")));

    assertTrue(client.isForgejoServer());
  }

  @Test
  public void shouldNotDetectOtherServer() {
    stubFor(get(urlEqualTo("/api/forgejo/v1/version")).willReturn(aResponse().withStatus(404)));
    stubFor(
        get(urlEqualTo("/api/v1/version")).willReturn(aResponse().withBody("<html>Hello</html>")));

    assertFalse(client.isForgejoServer());
  }

  @Test
  public void shouldCheckConnectedServer() {
    assertTrue(client.isConnected(wireMockServer.url("/")));
    assertTrue(client.isConnected(wireMockServer.baseUrl()));
    assertFalse(client.isConnected("https://other.example.com"));
  }

  @Test
  public void shouldNotDetectServerWithoutVersion() {
    // 204: no body
    stubFor(get(urlEqualTo("/api/forgejo/v1/version")).willReturn(aResponse().withStatus(204)));
    // JSON without version
    stubFor(get(urlEqualTo("/api/v1/version")).willReturn(aResponse().withBody("{}")));

    assertFalse(client.isForgejoServer());
  }

  @Test(expectedExceptions = ScmBadRequestException.class)
  public void shouldThrowBadRequestException() throws Exception {
    stubFor(get(urlEqualTo("/api/v1/user")).willReturn(aResponse().withStatus(400)));

    client.getUser("token");
  }

  @Test(
      expectedExceptions = ScmCommunicationException.class,
      expectedExceptionsMessageRegExp = "Unexpected status code 500 .*")
  public void shouldThrowCommunicationExceptionOnServerError() throws Exception {
    stubFor(get(urlEqualTo("/api/v1/user")).willReturn(aResponse().withStatus(500)));

    client.getUser("token");
  }

  @Test(expectedExceptions = ScmCommunicationException.class)
  public void shouldThrowCommunicationExceptionWhenBodyCannotBeRead() throws Exception {
    stubFor(
        get(urlEqualTo("/api/v1/repos/owner/repo/raw/devfile.yaml"))
            .willReturn(aResponse().withFault(Fault.MALFORMED_RESPONSE_CHUNK)));

    client.getFileContent("owner", "repo", "devfile.yaml", null, "token");
  }
}
