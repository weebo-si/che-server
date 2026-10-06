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

import static org.eclipse.che.api.factory.server.ApiExceptionMapper.toApiException;

import java.io.IOException;
import org.eclipse.che.api.core.ApiException;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.factory.server.ScmFileResolver;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.workspace.server.devfile.URLFetcher;
import org.eclipse.che.api.workspace.server.devfile.exception.DevfileException;

/** Forgejo specific SCM file resolver. */
public class AbstractForgejoScmFileResolver implements ScmFileResolver {

  private final AbstractForgejoUrlParser forgejoUrlParser;
  private final URLFetcher urlFetcher;
  private final PersonalAccessTokenManager personalAccessTokenManager;

  public AbstractForgejoScmFileResolver(
      AbstractForgejoUrlParser forgejoUrlParser,
      URLFetcher urlFetcher,
      PersonalAccessTokenManager personalAccessTokenManager) {
    this.forgejoUrlParser = forgejoUrlParser;
    this.urlFetcher = urlFetcher;
    this.personalAccessTokenManager = personalAccessTokenManager;
  }

  @Override
  public boolean accept(String repository) {
    return forgejoUrlParser.isValid(repository);
  }

  @Override
  public String fileContent(String repository, String filePath) throws ApiException {
    ForgejoUrl forgejoUrl = forgejoUrlParser.parse(repository, null);

    try {
      return fetchContent(forgejoUrl, filePath, false);
    } catch (DevfileException exception) {
      // This catch might mean that the authentication was rejected by user, try to repeat the fetch
      // without authentication flow.
      try {
        return fetchContent(forgejoUrl, filePath, true);
      } catch (DevfileException devfileException) {
        throw toApiException(devfileException);
      }
    }
  }

  private String fetchContent(ForgejoUrl forgejoUrl, String filePath, boolean skipAuthentication)
      throws DevfileException, NotFoundException {
    try {
      ForgejoAuthorizingFileContentProvider contentProvider =
          new ForgejoAuthorizingFileContentProvider(
              forgejoUrl, urlFetcher, personalAccessTokenManager);
      return skipAuthentication
          ? contentProvider.fetchContentWithoutAuthentication(filePath)
          : contentProvider.fetchContent(filePath);
    } catch (IOException e) {
      throw new NotFoundException(e.getMessage());
    }
  }
}
