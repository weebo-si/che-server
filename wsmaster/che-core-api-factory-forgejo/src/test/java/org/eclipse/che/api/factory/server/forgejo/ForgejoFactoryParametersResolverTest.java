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

import static java.util.Collections.singletonMap;
import static org.eclipse.che.api.factory.shared.Constants.CURRENT_VERSION;
import static org.eclipse.che.api.factory.shared.Constants.REVISION_PARAMETER_NAME;
import static org.eclipse.che.api.factory.shared.Constants.URL_PARAMETER_NAME;
import static org.eclipse.che.dto.server.DtoFactory.newDto;
import static org.eclipse.che.security.oauth1.OAuthAuthenticationService.ERROR_QUERY_NAME;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.eclipse.che.api.core.ApiException;
import org.eclipse.che.api.core.model.factory.ScmInfo;
import org.eclipse.che.api.factory.server.FactoryResolverPriority;
import org.eclipse.che.api.factory.server.scm.AuthorisationRequestManager;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.factory.server.urlfactory.DevfileFilenamesProvider;
import org.eclipse.che.api.factory.server.urlfactory.RemoteFactoryUrl;
import org.eclipse.che.api.factory.server.urlfactory.RemoteFactoryUrl.DevfileLocation;
import org.eclipse.che.api.factory.server.urlfactory.URLFactoryBuilder;
import org.eclipse.che.api.factory.shared.dto.FactoryDevfileV2Dto;
import org.eclipse.che.api.factory.shared.dto.ScmInfoDto;
import org.eclipse.che.api.workspace.server.devfile.URLFetcher;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(MockitoTestNGListener.class)
public class ForgejoFactoryParametersResolverTest {

  private static final String SERVER = "https://forgejo.example.com";

  @Mock private URLFactoryBuilder urlFactoryBuilder;
  @Mock private URLFetcher urlFetcher;
  @Mock private DevfileFilenamesProvider devfileFilenamesProvider;
  @Mock private PersonalAccessTokenManager personalAccessTokenManager;
  @Mock private AuthorisationRequestManager authorisationRequestManager;

  private ForgejoFactoryParametersResolver resolver;

  @BeforeMethod
  protected void init() {
    lenient()
        .when(devfileFilenamesProvider.getConfiguredDevfileFilenames())
        .thenReturn(List.of("devfile.yaml", ".devfile.yaml"));
    ForgejoUrlParser forgejoUrlParser =
        new ForgejoUrlParser(
            SERVER, devfileFilenamesProvider, mock(PersonalAccessTokenManager.class));
    resolver =
        new ForgejoFactoryParametersResolver(
            urlFactoryBuilder,
            urlFetcher,
            forgejoUrlParser,
            personalAccessTokenManager,
            authorisationRequestManager);
  }

  @Test
  public void shouldNotAcceptOtherUrl() {
    assertFalse(resolver.accept(singletonMap(URL_PARAMETER_NAME, "https://github.com/user/repo")));
    assertFalse(resolver.accept(singletonMap("other", SERVER + "/owner/repo")));
  }

  @Test
  public void shouldAcceptForgejoUrl() {
    assertTrue(resolver.accept(singletonMap(URL_PARAMETER_NAME, SERVER + "/owner/repo.git")));
    assertTrue(
        resolver.accept(
            singletonMap(URL_PARAMETER_NAME, "git@forgejo.example.com:owner/repo.git")));
  }

  @Test
  public void shouldExposeProviderNameAndPriority() {
    assertEquals(resolver.getProviderName(), "forgejo");
    assertEquals(resolver.priority(), FactoryResolverPriority.DEFAULT);
  }

  @Test
  public void shouldGenerateDefaultFactoryWhenNoDevfile() throws Exception {
    when(urlFactoryBuilder.createFactoryFromDevfile(
            any(RemoteFactoryUrl.class), any(), anyMap(), anyBoolean()))
        .thenReturn(Optional.empty());

    FactoryDevfileV2Dto factory =
        (FactoryDevfileV2Dto)
            resolver.createFactory(ImmutableMap.of(URL_PARAMETER_NAME, SERVER + "/owner/repo"));

    ScmInfoDto scmInfo = factory.getScmInfo();
    assertEquals(scmInfo.getScmProviderName(), "forgejo");
    assertEquals(scmInfo.getRepositoryUrl(), SERVER + "/owner/repo.git");
    assertNull(scmInfo.getBranch());
  }

  @Test
  public void shouldSetScmInfoFromBranchUrl() throws Exception {
    when(urlFactoryBuilder.createFactoryFromDevfile(
            any(RemoteFactoryUrl.class), any(), anyMap(), anyBoolean()))
        .thenReturn(Optional.of(generateDevfileV2Factory()));

    FactoryDevfileV2Dto factory =
        (FactoryDevfileV2Dto)
            resolver.createFactory(
                ImmutableMap.of(URL_PARAMETER_NAME, SERVER + "/owner/repo/src/branch/feature/x"));

    assertNotNull(factory.getDevfile());
    ScmInfo scmInfo = factory.getScmInfo();
    assertEquals(scmInfo.getScmProviderName(), "forgejo");
    assertEquals(scmInfo.getRepositoryUrl(), SERVER + "/owner/repo.git");
    assertEquals(scmInfo.getBranch(), "feature/x");
  }

  @Test
  public void shouldUseRevisionParameter() throws Exception {
    when(urlFactoryBuilder.createFactoryFromDevfile(
            any(RemoteFactoryUrl.class), any(), anyMap(), anyBoolean()))
        .thenReturn(Optional.of(generateDevfileV2Factory()));

    FactoryDevfileV2Dto factory =
        (FactoryDevfileV2Dto)
            resolver.createFactory(
                ImmutableMap.of(
                    URL_PARAMETER_NAME, SERVER + "/owner/repo", REVISION_PARAMETER_NAME, "v1.0"));

    assertEquals(factory.getScmInfo().getBranch(), "v1.0");
  }

  @Test
  public void shouldKeepSshRepositoryLocation() throws Exception {
    when(urlFactoryBuilder.createFactoryFromDevfile(
            any(RemoteFactoryUrl.class), any(), anyMap(), anyBoolean()))
        .thenReturn(Optional.of(generateDevfileV2Factory()));

    FactoryDevfileV2Dto factory =
        (FactoryDevfileV2Dto)
            resolver.createFactory(
                ImmutableMap.of(
                    URL_PARAMETER_NAME, "ssh://git@forgejo.example.com:2222/owner/repo.git"));

    assertEquals(
        factory.getScmInfo().getRepositoryUrl(),
        "ssh://git@forgejo.example.com:2222/owner/repo.git");
  }

  @Test
  public void shouldLookUpDevfilesThroughTheForgejoApi() throws Exception {
    ArgumentCaptor<RemoteFactoryUrl> remoteFactoryUrl =
        ArgumentCaptor.forClass(RemoteFactoryUrl.class);
    when(urlFactoryBuilder.createFactoryFromDevfile(
            remoteFactoryUrl.capture(), any(), anyMap(), anyBoolean()))
        .thenReturn(Optional.of(generateDevfileV2Factory()));

    resolver.createFactory(
        ImmutableMap.of(URL_PARAMETER_NAME, SERVER + "/owner/repo/src/branch/main"));

    assertEquals(
        remoteFactoryUrl.getValue().devfileFileLocations().stream()
            .map(DevfileLocation::location)
            .collect(Collectors.toList()),
        List.of(
            SERVER + "/api/v1/repos/owner/repo/raw/devfile.yaml?ref=main",
            SERVER + "/api/v1/repos/owner/repo/raw/.devfile.yaml?ref=main"));
  }

  @Test
  public void shouldCreateFactoryWithoutAuthenticationWhenAccessDenied() throws ApiException {
    when(urlFactoryBuilder.createFactoryFromDevfile(
            any(RemoteFactoryUrl.class), any(), anyMap(), anyBoolean()))
        .thenReturn(Optional.of(generateDevfileV2Factory()));

    resolver.createFactory(
        ImmutableMap.of(
            URL_PARAMETER_NAME, SERVER + "/owner/repo", ERROR_QUERY_NAME, "access_denied"));

    verify(urlFactoryBuilder)
        .createFactoryFromDevfile(
            any(ForgejoUrl.class),
            any(ForgejoAuthorizingFileContentProvider.class),
            anyMap(),
            eq(true));
  }

  @Test
  public void shouldParseFactoryUrl() throws ApiException {
    ForgejoUrl forgejoUrl = (ForgejoUrl) resolver.parseFactoryUrl(SERVER + "/owner/repo");

    assertEquals(forgejoUrl.getProviderUrl(), SERVER);
    assertEquals(forgejoUrl.getOwner(), "owner");
    assertEquals(forgejoUrl.getRepository(), "repo");
  }

  @Test
  public void shouldCreateSecondResolver() {
    ForgejoFactoryParametersResolverSecond second =
        new ForgejoFactoryParametersResolverSecond(
            urlFactoryBuilder,
            urlFetcher,
            new ForgejoUrlParserSecond(
                null, devfileFilenamesProvider, mock(PersonalAccessTokenManager.class)),
            personalAccessTokenManager,
            authorisationRequestManager);

    assertEquals(second.getProviderName(), "forgejo_2");
    assertFalse(second.accept(singletonMap(URL_PARAMETER_NAME, SERVER + "/owner/repo")));
  }

  private FactoryDevfileV2Dto generateDevfileV2Factory() {
    return newDto(FactoryDevfileV2Dto.class)
        .withV(CURRENT_VERSION)
        .withSource("repo")
        .withDevfile(Map.of("schemaVersion", "2.0.0"));
  }
}
