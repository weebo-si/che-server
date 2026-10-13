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

import static org.eclipse.che.api.factory.shared.Constants.REVISION_PARAMETER_NAME;
import static org.eclipse.che.api.factory.shared.Constants.URL_PARAMETER_NAME;
import static org.eclipse.che.dto.server.DtoFactory.newDto;

import jakarta.validation.constraints.NotNull;
import java.util.Map;
import org.eclipse.che.api.core.ApiException;
import org.eclipse.che.api.core.BadRequestException;
import org.eclipse.che.api.factory.server.BaseFactoryParameterResolver;
import org.eclipse.che.api.factory.server.FactoryParametersResolver;
import org.eclipse.che.api.factory.server.scm.AuthorisationRequestManager;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.factory.server.urlfactory.RemoteFactoryUrl;
import org.eclipse.che.api.factory.server.urlfactory.URLFactoryBuilder;
import org.eclipse.che.api.factory.shared.dto.FactoryDevfileV2Dto;
import org.eclipse.che.api.factory.shared.dto.FactoryMetaDto;
import org.eclipse.che.api.factory.shared.dto.FactoryVisitor;
import org.eclipse.che.api.factory.shared.dto.ScmInfoDto;
import org.eclipse.che.api.workspace.server.devfile.URLFetcher;

/** Provides Factory Parameters resolver for Forgejo repositories. */
public class AbstractForgejoFactoryParametersResolver extends BaseFactoryParameterResolver
    implements FactoryParametersResolver {

  private final URLFetcher urlFetcher;
  private final AbstractForgejoUrlParser forgejoUrlParser;
  private final PersonalAccessTokenManager personalAccessTokenManager;
  private final String providerName;

  public AbstractForgejoFactoryParametersResolver(
      URLFactoryBuilder urlFactoryBuilder,
      URLFetcher urlFetcher,
      AbstractForgejoUrlParser forgejoUrlParser,
      PersonalAccessTokenManager personalAccessTokenManager,
      AuthorisationRequestManager authorisationRequestManager,
      String providerName) {
    super(authorisationRequestManager, urlFactoryBuilder, providerName);
    this.urlFetcher = urlFetcher;
    this.forgejoUrlParser = forgejoUrlParser;
    this.personalAccessTokenManager = personalAccessTokenManager;
    this.providerName = providerName;
  }

  /**
   * Check if this resolver can be used with the given parameters.
   *
   * @param factoryParameters map of parameters dedicated to factories
   * @return true if it will be accepted by the resolver implementation or false if it is not
   *     accepted
   */
  @Override
  public boolean accept(@NotNull final Map<String, String> factoryParameters) {
    return factoryParameters.containsKey(URL_PARAMETER_NAME)
        && forgejoUrlParser.isValid(factoryParameters.get(URL_PARAMETER_NAME));
  }

  @Override
  public String getProviderName() {
    return providerName;
  }

  /**
   * Create factory object based on provided parameters
   *
   * @param factoryParameters map containing factory data parameters provided through URL
   * @throws BadRequestException when data are invalid
   */
  @Override
  public FactoryMetaDto createFactory(@NotNull final Map<String, String> factoryParameters)
      throws ApiException {
    // no need to check null value of url parameter as accept() method has performed the check
    final ForgejoUrl forgejoUrl =
        forgejoUrlParser.parse(
            factoryParameters.get(URL_PARAMETER_NAME),
            factoryParameters.get(REVISION_PARAMETER_NAME));
    // create factory from the following location if location exists, else create default factory
    return createFactory(
        factoryParameters,
        forgejoUrl,
        new ForgejoFactoryVisitor(forgejoUrl),
        new ForgejoAuthorizingFileContentProvider(
            forgejoUrl, urlFetcher, personalAccessTokenManager));
  }

  /**
   * Visitor that puts the default devfile or updates devfile projects into the Forgejo Factory, if
   * needed.
   */
  private class ForgejoFactoryVisitor implements FactoryVisitor {

    private final ForgejoUrl forgejoUrl;

    private ForgejoFactoryVisitor(ForgejoUrl forgejoUrl) {
      this.forgejoUrl = forgejoUrl;
    }

    @Override
    public FactoryDevfileV2Dto visit(FactoryDevfileV2Dto factoryDto) {
      ScmInfoDto scmInfo =
          newDto(ScmInfoDto.class)
              .withScmProviderName(forgejoUrl.getProviderName())
              .withRepositoryUrl(forgejoUrl.repositoryLocation());
      if (forgejoUrl.getBranch() != null) {
        scmInfo.withBranch(forgejoUrl.getBranch());
      }
      return factoryDto.withScmInfo(scmInfo);
    }
  }

  @Override
  public RemoteFactoryUrl parseFactoryUrl(String factoryUrl) throws ApiException {
    return forgejoUrlParser.parse(factoryUrl, null);
  }
}
