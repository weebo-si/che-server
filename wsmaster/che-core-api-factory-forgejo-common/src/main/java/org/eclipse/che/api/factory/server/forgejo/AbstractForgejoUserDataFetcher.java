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

import static com.google.common.base.Strings.isNullOrEmpty;

import com.google.common.base.Joiner;
import java.util.Optional;
import org.eclipse.che.api.factory.server.scm.AbstractGitUserDataFetcher;
import org.eclipse.che.api.factory.server.scm.GitUserData;
import org.eclipse.che.api.factory.server.scm.PersonalAccessToken;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.factory.server.scm.exception.ScmBadRequestException;
import org.eclipse.che.api.factory.server.scm.exception.ScmCommunicationException;
import org.eclipse.che.api.factory.server.scm.exception.ScmConfigurationPersistenceException;
import org.eclipse.che.api.factory.server.scm.exception.ScmItemNotFoundException;
import org.eclipse.che.api.factory.server.scm.exception.ScmUnauthorizedException;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.commons.subject.Subject;

/** Forgejo git user data retriever. */
public class AbstractForgejoUserDataFetcher extends AbstractGitUserDataFetcher {

  private final String apiEndpoint;
  private final String providerName;

  public AbstractForgejoUserDataFetcher(
      @Nullable String serverUrl,
      String apiEndpoint,
      PersonalAccessTokenManager personalAccessTokenManager,
      String providerName) {
    super(providerName, serverUrl, personalAccessTokenManager);
    this.apiEndpoint = apiEndpoint;
    this.providerName = providerName;
  }

  @Override
  public GitUserData fetchGitUserData(@Nullable String namespaceName)
      throws ScmUnauthorizedException,
          ScmCommunicationException,
          ScmConfigurationPersistenceException,
          ScmItemNotFoundException,
          ScmBadRequestException {
    if (!isNullOrEmpty(oAuthProviderUrl)) {
      return super.fetchGitUserData(namespaceName);
    }
    // Forgejo has no default public instance: without a configured server, the OAuth token
    // lookup by server URL would match any provider. Only personal access tokens of this
    // provider are looked up.
    Subject cheSubject = EnvironmentContext.getCurrent().getSubject();
    Optional<PersonalAccessToken> tokenOptional =
        personalAccessTokenManager.get(cheSubject, providerName, null, namespaceName);
    if (tokenOptional.isPresent()) {
      return fetchGitUserDataWithPersonalAccessToken(tokenOptional.get());
    }
    throw new ScmCommunicationException(
        "There are no tokens for the user " + cheSubject.getUserId());
  }

  @Override
  protected GitUserData fetchGitUserDataWithOAuthToken(String token)
      throws ScmItemNotFoundException,
          ScmCommunicationException,
          ScmBadRequestException,
          ScmUnauthorizedException {
    return toGitUserData(new ForgejoApiClient(oAuthProviderUrl).getUser(token));
  }

  @Override
  protected GitUserData fetchGitUserDataWithPersonalAccessToken(
      PersonalAccessToken personalAccessToken)
      throws ScmItemNotFoundException,
          ScmCommunicationException,
          ScmBadRequestException,
          ScmUnauthorizedException {
    return toGitUserData(
        new ForgejoApiClient(personalAccessToken.getScmProviderUrl())
            .getUser(personalAccessToken.getToken()));
  }

  private static GitUserData toGitUserData(ForgejoUser user) {
    // full_name is optional in Forgejo
    String name = isNullOrEmpty(user.getFullName()) ? user.getLogin() : user.getFullName();
    return new GitUserData(name, user.getEmail());
  }

  @Override
  protected String getLocalAuthenticateUrl() {
    return apiEndpoint
        + "/oauth/authenticate?oauth_provider="
        + providerName
        + "&scope="
        + Joiner.on('+').join(AbstractForgejoOAuthTokenFetcher.DEFAULT_TOKEN_SCOPES)
        + "&request_method=POST&signature_method=rsa";
  }
}
