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

import static com.google.common.base.Strings.isNullOrEmpty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.inject.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides implementation of Forgejo {@link OAuthAuthenticator} based on available configuration.
 */
public class AbstractForgejoOAuthAuthenticatorProvider implements Provider<OAuthAuthenticator> {
  private static final Logger LOG =
      LoggerFactory.getLogger(AbstractForgejoOAuthAuthenticatorProvider.class);
  private final OAuthAuthenticator authenticator;
  private final String providerName;

  public AbstractForgejoOAuthAuthenticatorProvider(
      String clientIdPath,
      String clientSecretPath,
      String forgejoEndpoint,
      String cheApiEndpoint,
      String providerName)
      throws IOException {
    this.providerName = providerName;
    authenticator =
        getOAuthAuthenticator(clientIdPath, clientSecretPath, forgejoEndpoint, cheApiEndpoint);
    LOG.debug("{} Forgejo OAuth Authenticator is used.", authenticator);
  }

  @Override
  public OAuthAuthenticator get() {
    return authenticator;
  }

  private OAuthAuthenticator getOAuthAuthenticator(
      String clientIdPath, String clientSecretPath, String forgejoEndpoint, String cheApiEndpoint)
      throws IOException {
    if (!isNullOrEmpty(clientIdPath)
        && !isNullOrEmpty(clientSecretPath)
        && !isNullOrEmpty(forgejoEndpoint)) {
      String clientId = Files.readString(Path.of(clientIdPath)).trim();
      String clientSecret = Files.readString(Path.of(clientSecretPath)).trim();
      if (!isNullOrEmpty(clientId) && !isNullOrEmpty(clientSecret)) {
        return new ForgejoOAuthAuthenticator(
            clientId, clientSecret, forgejoEndpoint, cheApiEndpoint, providerName);
      }
    }
    return new NoopOAuthAuthenticator();
  }

  static class NoopOAuthAuthenticator extends OAuthAuthenticator {

    @Override
    public String getOAuthProvider() {
      return "Noop";
    }

    @Override
    public String getEndpointUrl() {
      return "Noop";
    }
  }
}
