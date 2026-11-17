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
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.security.oauth.OAuthAPI;

/** Forgejo OAuth token retriever. */
public class ForgejoOAuthTokenFetcherSecond extends AbstractForgejoOAuthTokenFetcher {

  private static final String OAUTH_PROVIDER_NAME = "forgejo_2";

  @Inject
  public ForgejoOAuthTokenFetcherSecond(
      @Nullable @Named("che.integration.forgejo.oauth_endpoint_2") String serverUrl,
      @Named("che.api") String apiEndpoint,
      OAuthAPI oAuthAPI) {
    super(serverUrl, apiEndpoint, oAuthAPI, OAUTH_PROVIDER_NAME);
  }
}
