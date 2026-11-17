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

import javax.inject.Inject;
import javax.inject.Named;
import org.eclipse.che.api.factory.server.scm.*;
import org.eclipse.che.commons.annotation.Nullable;

/** Forgejo git user data retriever. */
public class ForgejoUserDataFetcher extends AbstractForgejoUserDataFetcher {

  /** Name of this OAuth provider as found in OAuthAPI. */
  private static final String OAUTH_PROVIDER_NAME = "forgejo";

  @Inject
  public ForgejoUserDataFetcher(
      @Nullable @Named("che.integration.forgejo.oauth_endpoint") String serverUrl,
      @Named("che.api") String apiEndpoint,
      PersonalAccessTokenManager personalAccessTokenManager) {
    super(serverUrl, apiEndpoint, personalAccessTokenManager, OAUTH_PROVIDER_NAME);
  }
}
