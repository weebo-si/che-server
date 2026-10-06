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
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.factory.server.urlfactory.DevfileFilenamesProvider;
import org.eclipse.che.commons.annotation.Nullable;

/** Parser of String Forgejo URLs and provide {@link ForgejoUrl} objects. */
public class ForgejoUrlParser extends AbstractForgejoUrlParser {

  private static final String OAUTH_PROVIDER_NAME = "forgejo";

  @Inject
  public ForgejoUrlParser(
      @Nullable @Named("che.integration.forgejo.oauth_endpoint") String serverUrl,
      DevfileFilenamesProvider devfileFilenamesProvider,
      PersonalAccessTokenManager personalAccessTokenManager) {
    super(serverUrl, devfileFilenamesProvider, personalAccessTokenManager, OAUTH_PROVIDER_NAME);
  }
}
