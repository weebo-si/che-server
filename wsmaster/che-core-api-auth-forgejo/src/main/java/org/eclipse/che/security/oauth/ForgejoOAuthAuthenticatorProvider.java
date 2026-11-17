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

import java.io.IOException;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.eclipse.che.commons.annotation.Nullable;

/**
 * Provides implementation of Forgejo {@link OAuthAuthenticator} based on available configuration.
 */
@Singleton
public class ForgejoOAuthAuthenticatorProvider extends AbstractForgejoOAuthAuthenticatorProvider {
  private static final String PROVIDER_NAME = "forgejo";

  @Inject
  public ForgejoOAuthAuthenticatorProvider(
      @Nullable @Named("che.oauth2.forgejo.clientid_filepath") String clientIdPath,
      @Nullable @Named("che.oauth2.forgejo.clientsecret_filepath") String clientSecretPath,
      @Nullable @Named("che.integration.forgejo.oauth_endpoint") String forgejoEndpoint,
      @Named("che.api") String cheApiEndpoint)
      throws IOException {
    super(clientIdPath, clientSecretPath, forgejoEndpoint, cheApiEndpoint, PROVIDER_NAME);
  }
}
