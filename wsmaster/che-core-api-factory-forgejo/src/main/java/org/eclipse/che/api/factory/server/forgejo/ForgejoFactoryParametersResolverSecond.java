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
import javax.inject.Singleton;
import org.eclipse.che.api.factory.server.FactoryParametersResolver;
import org.eclipse.che.api.factory.server.scm.AuthorisationRequestManager;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.factory.server.urlfactory.URLFactoryBuilder;
import org.eclipse.che.api.workspace.server.devfile.URLFetcher;

/** Provides Factory Parameters resolver for Forgejo repositories. */
@Singleton
public class ForgejoFactoryParametersResolverSecond extends AbstractForgejoFactoryParametersResolver
    implements FactoryParametersResolver {

  private static final String PROVIDER_NAME = "forgejo_2";

  @Inject
  public ForgejoFactoryParametersResolverSecond(
      URLFactoryBuilder urlFactoryBuilder,
      URLFetcher urlFetcher,
      ForgejoUrlParserSecond forgejoUrlParser,
      PersonalAccessTokenManager personalAccessTokenManager,
      AuthorisationRequestManager authorisationRequestManager) {
    super(
        urlFactoryBuilder,
        urlFetcher,
        forgejoUrlParser,
        personalAccessTokenManager,
        authorisationRequestManager,
        PROVIDER_NAME);
  }
}
