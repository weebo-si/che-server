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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class ForgejoOAuthAuthenticatorProviderTest {

  private static final String TEST_URI = "https://forgejo.example.com";

  private File clientIdFile;
  private File clientSecretFile;
  private File emptyFile;

  @BeforeClass
  public void setup() throws IOException {
    clientIdFile = File.createTempFile("ForgejoOAuthAuthenticatorProviderTest-", "-id");
    // secrets mounted from Kubernetes often end with a newline
    Files.asCharSink(clientIdFile, StandardCharsets.UTF_8).write("client-id\n");
    clientIdFile.deleteOnExit();
    clientSecretFile = File.createTempFile("ForgejoOAuthAuthenticatorProviderTest-", "-secret");
    Files.asCharSink(clientSecretFile, StandardCharsets.UTF_8).write("client-secret");
    clientSecretFile.deleteOnExit();
    emptyFile = File.createTempFile("ForgejoOAuthAuthenticatorProviderTest-", "-empty");
    emptyFile.deleteOnExit();
  }

  @DataProvider
  public Object[][] noopConfig() {
    return new Object[][] {
      {null, null, null},
      {clientIdFile.getPath(), clientSecretFile.getPath(), null},
      {null, clientSecretFile.getPath(), TEST_URI},
      {clientIdFile.getPath(), null, TEST_URI},
      {emptyFile.getPath(), emptyFile.getPath(), TEST_URI},
      {clientIdFile.getPath(), emptyFile.getPath(), TEST_URI},
      // invalid endpoints: no scheme, malformed URI
      {clientIdFile.getPath(), clientSecretFile.getPath(), "forgejo.example.com"},
      {clientIdFile.getPath(), clientSecretFile.getPath(), "https://forgejo example.com"},
    };
  }

  @Test(dataProvider = "noopConfig")
  public void shouldProvideNoopAuthenticatorWhenNotConfigured(
      String clientIdPath, String clientSecretPath, String endpoint) throws IOException {
    OAuthAuthenticator authenticator =
        new ForgejoOAuthAuthenticatorProvider(clientIdPath, clientSecretPath, endpoint, "che.api")
            .get();

    assertTrue(
        authenticator instanceof AbstractForgejoOAuthAuthenticatorProvider.NoopOAuthAuthenticator);
  }

  @Test
  public void shouldDescribeNoopAuthenticator() throws IOException {
    OAuthAuthenticator authenticator =
        new ForgejoOAuthAuthenticatorProvider(null, null, null, "che.api").get();

    assertEquals(authenticator.getOAuthProvider(), "Noop");
    assertEquals(authenticator.getEndpointUrl(), "Noop");
  }

  @Test
  public void shouldProvideForgejoAuthenticator() throws IOException {
    OAuthAuthenticator authenticator =
        new ForgejoOAuthAuthenticatorProvider(
                clientIdFile.getPath(), clientSecretFile.getPath(), TEST_URI + "/", "che.api")
            .get();

    assertTrue(authenticator instanceof ForgejoOAuthAuthenticator);
    assertEquals(authenticator.getOAuthProvider(), "forgejo");
    assertEquals(authenticator.getEndpointUrl(), TEST_URI);
    assertEquals(authenticator.getClientId(), "client-id");
  }

  @Test
  public void shouldProvideSecondForgejoAuthenticator() throws IOException {
    OAuthAuthenticator authenticator =
        new ForgejoOAuthAuthenticatorProviderSecond(
                clientIdFile.getPath(), clientSecretFile.getPath(), TEST_URI, "che.api")
            .get();

    assertEquals(authenticator.getOAuthProvider(), "forgejo_2");
  }
}
