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
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.workspace.server.devfile.URLFetcher;

/** Forgejo specific SCM file resolver. */
public class ForgejoScmFileResolverSecond extends AbstractForgejoScmFileResolver {

  @Inject
  public ForgejoScmFileResolverSecond(
      ForgejoUrlParserSecond forgejoUrlParser,
      URLFetcher urlFetcher,
      PersonalAccessTokenManager personalAccessTokenManager) {
    super(forgejoUrlParser, urlFetcher, personalAccessTokenManager);
  }
}
