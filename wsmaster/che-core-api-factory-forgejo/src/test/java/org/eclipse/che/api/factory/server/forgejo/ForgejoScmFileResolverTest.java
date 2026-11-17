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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.io.FileNotFoundException;
import java.io.IOException;
import org.eclipse.che.api.core.BadRequestException;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.factory.server.scm.PersonalAccessToken;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.factory.server.scm.exception.ScmUnauthorizedException;
import org.eclipse.che.api.factory.server.urlfactory.DevfileFilenamesProvider;
import org.eclipse.che.api.workspace.server.devfile.URLFetcher;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(MockitoTestNGListener.class)
public class ForgejoScmFileResolverTest {

  private static final String SERVER = "https://forgejo.example.com";
  private static final String DEVFILE_URL =
      SERVER + "/api/v1/repos/owner/repo/raw/devfile.yaml?ref=main";

  @Mock private URLFetcher urlFetcher;
  @Mock private DevfileFilenamesProvider devfileFilenamesProvider;
  @Mock private PersonalAccessTokenManager personalAccessTokenManager;

  private ForgejoScmFileResolver scmFileResolver;

  @BeforeMethod
  protected void init() {
    ForgejoUrlParser forgejoUrlParser =
        new ForgejoUrlParser(
            SERVER, devfileFilenamesProvider, mock(PersonalAccessTokenManager.class));
    scmFileResolver =
        new ForgejoScmFileResolver(forgejoUrlParser, urlFetcher, personalAccessTokenManager);
  }

  @Test
  public void shouldAcceptOnlyForgejoRepositories() {
    assertTrue(scmFileResolver.accept(SERVER + "/owner/repo.git"));
    assertFalse(scmFileResolver.accept("https://github.com/owner/repo"));
  }

  @Test
  public void shouldFetchContentWithToken() throws Exception {
    when(personalAccessTokenManager.getAndStore(SERVER))
        .thenReturn(new PersonalAccessToken(SERVER, "forgejo", "jdoe", "token123"));
    when(urlFetcher.fetch(DEVFILE_URL, "token token123")).thenReturn("schemaVersion: 2.2.0");

    String content =
        scmFileResolver.fileContent(SERVER + "/owner/repo/src/branch/main", "devfile.yaml");

    assertEquals(content, "schemaVersion: 2.2.0");
  }

  @Test
  public void shouldFetchContentWithoutAuthenticationWhenUnauthorized() throws Exception {
    when(personalAccessTokenManager.getAndStore(anyString()))
        .thenThrow(new ScmUnauthorizedException("message", "forgejo", "v2", "url"));
    when(urlFetcher.fetch(DEVFILE_URL)).thenReturn("schemaVersion: 2.2.0");

    String content =
        scmFileResolver.fileContent(SERVER + "/owner/repo/src/branch/main", "devfile.yaml");

    assertEquals(content, "schemaVersion: 2.2.0");
    verify(urlFetcher).fetch(DEVFILE_URL);
  }

  @Test(expectedExceptions = NotFoundException.class)
  public void shouldThrowNotFoundExceptionWhenAnonymousFetchFails() throws Exception {
    when(personalAccessTokenManager.getAndStore(anyString()))
        .thenThrow(new ScmUnauthorizedException("message", "forgejo", "v2", "url"));
    // private repository: the anonymous retry fails too
    when(urlFetcher.fetch(DEVFILE_URL)).thenThrow(new FileNotFoundException("devfile.yaml"));

    scmFileResolver.fileContent(SERVER + "/owner/repo/src/branch/main", "devfile.yaml");
  }

  @Test(expectedExceptions = BadRequestException.class)
  public void shouldThrowBadRequestExceptionOnInvalidPath() throws Exception {
    // the file path is not a valid URI: both the authenticated and the anonymous attempts fail
    scmFileResolver.fileContent(SERVER + "/owner/repo", "dir/my file.yaml");
  }

  @Test
  public void shouldCreateSecondResolver() {
    ForgejoScmFileResolverSecond second =
        new ForgejoScmFileResolverSecond(
            new ForgejoUrlParserSecond(
                null, devfileFilenamesProvider, mock(PersonalAccessTokenManager.class)),
            urlFetcher,
            personalAccessTokenManager);

    assertFalse(second.accept(SERVER + "/owner/repo"));
  }

  @Test(expectedExceptions = NotFoundException.class)
  public void shouldThrowNotFoundExceptionOnFetchError() throws Exception {
    when(personalAccessTokenManager.getAndStore(SERVER))
        .thenReturn(new PersonalAccessToken(SERVER, "forgejo", "jdoe", "token123"));
    when(urlFetcher.fetch(DEVFILE_URL, "token token123")).thenThrow(new IOException("not found"));

    scmFileResolver.fileContent(SERVER + "/owner/repo/src/branch/main", "devfile.yaml");
  }
}
