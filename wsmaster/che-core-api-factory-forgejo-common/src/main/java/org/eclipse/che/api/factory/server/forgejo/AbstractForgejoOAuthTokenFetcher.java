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

import static java.lang.String.format;
import static org.eclipse.che.commons.lang.StringUtils.trimEnd;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import java.util.Optional;
import java.util.Set;
import org.eclipse.che.api.auth.shared.dto.OAuthToken;
import org.eclipse.che.api.core.BadRequestException;
import org.eclipse.che.api.core.ConflictException;
import org.eclipse.che.api.core.ForbiddenException;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.UnauthorizedException;
import org.eclipse.che.api.factory.server.scm.PersonalAccessToken;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenFetcher;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenParams;
import org.eclipse.che.api.factory.server.scm.exception.ScmBadRequestException;
import org.eclipse.che.api.factory.server.scm.exception.ScmCommunicationException;
import org.eclipse.che.api.factory.server.scm.exception.ScmItemNotFoundException;
import org.eclipse.che.api.factory.server.scm.exception.ScmUnauthorizedException;
import org.eclipse.che.api.factory.server.scm.exception.UnknownScmProviderException;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.commons.lang.NameGenerator;
import org.eclipse.che.commons.lang.Pair;
import org.eclipse.che.commons.subject.Subject;
import org.eclipse.che.security.oauth.OAuthAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Forgejo OAuth token retriever.
 *
 * <p>Forgejo has no token introspection endpoint: a token is considered valid when it can read the
 * authenticated user ({@code GET /api/v1/user}).
 */
public class AbstractForgejoOAuthTokenFetcher implements PersonalAccessTokenFetcher {

  private static final Logger LOG = LoggerFactory.getLogger(AbstractForgejoOAuthTokenFetcher.class);

  public static final Set<String> DEFAULT_TOKEN_SCOPES =
      ImmutableSet.of("read:user", "write:repository");

  private final OAuthAPI oAuthAPI;
  @Nullable private final String serverUrl;
  private final String apiEndpoint;
  private final String providerName;

  public AbstractForgejoOAuthTokenFetcher(
      @Nullable String serverUrl, String apiEndpoint, OAuthAPI oAuthAPI, String providerName) {
    this.serverUrl = serverUrl == null ? null : trimEnd(serverUrl, '/');
    this.apiEndpoint = apiEndpoint;
    this.providerName = providerName;
    this.oAuthAPI = oAuthAPI;
  }

  @Override
  public PersonalAccessToken refreshPersonalAccessToken(Subject cheSubject, String scmServerUrl)
      throws ScmUnauthorizedException, ScmCommunicationException, UnknownScmProviderException {
    return fetchOrRefreshPersonalAccessToken(cheSubject, scmServerUrl, true);
  }

  @Override
  public PersonalAccessToken fetchPersonalAccessToken(Subject cheSubject, String scmServerUrl)
      throws ScmUnauthorizedException, ScmCommunicationException, UnknownScmProviderException {
    return fetchOrRefreshPersonalAccessToken(cheSubject, scmServerUrl, false);
  }

  private PersonalAccessToken fetchOrRefreshPersonalAccessToken(
      Subject cheSubject, String scmServerUrl, boolean forceRefreshToken)
      throws ScmUnauthorizedException, ScmCommunicationException, UnknownScmProviderException {
    scmServerUrl = trimEnd(scmServerUrl, '/');
    ForgejoApiClient forgejoApiClient = getApiClient(scmServerUrl);
    if (forgejoApiClient == null) {
      LOG.debug("not a valid url {} for current fetcher ", scmServerUrl);
      return null;
    }
    if (oAuthAPI == null) {
      throw new ScmCommunicationException(
          format(
              "OAuth 2 is not configured for SCM provider [%s]. For details, refer "
                  + "the documentation in section of SCM providers configuration.",
              providerName));
    }
    try {
      OAuthToken oAuthToken =
          forceRefreshToken
              ? oAuthAPI.refreshToken(providerName)
              : oAuthAPI.getOrRefreshToken(providerName);
      String tokenName = NameGenerator.generate(OAUTH_2_PREFIX, 5);
      String tokenId = NameGenerator.generate("id-", 5);
      Optional<Pair<Boolean, String>> valid =
          isValid(
              new PersonalAccessTokenParams(
                  scmServerUrl, providerName, tokenName, tokenId, oAuthToken.getToken(), null));
      // isValid(params) only returns valid tokens: an empty result means the token is rejected
      if (valid.isEmpty()) {
        throw buildScmUnauthorizedException(cheSubject);
      }
      return new PersonalAccessToken(
          scmServerUrl,
          providerName,
          cheSubject.getUserId(),
          null,
          valid.get().second,
          tokenName,
          tokenId,
          oAuthToken.getToken(),
          oAuthToken.getRefreshToken(),
          oAuthToken.getExpiresIn());
    } catch (UnauthorizedException e) {
      throw buildScmUnauthorizedException(cheSubject);
    } catch (NotFoundException nfe) {
      throw new UnknownScmProviderException(nfe.getMessage(), scmServerUrl);
    } catch (ServerException | ForbiddenException | BadRequestException | ConflictException e) {
      LOG.warn(e.getMessage());
      throw new ScmCommunicationException(e.getMessage(), e);
    }
  }

  private ScmUnauthorizedException buildScmUnauthorizedException(Subject cheSubject) {
    return new ScmUnauthorizedException(
        cheSubject.getUserName() + " is not authorized in " + providerName + " OAuth provider.",
        providerName,
        "2.0",
        getLocalAuthenticateUrl());
  }

  @Override
  public Optional<Boolean> isValid(PersonalAccessToken personalAccessToken) {
    ForgejoApiClient forgejoApiClient =
        getApiClient(
            personalAccessToken.getScmProviderUrl(), personalAccessToken.getScmTokenName());
    if (forgejoApiClient == null) {
      LOG.debug("not a valid url {} for current fetcher ", personalAccessToken.getScmProviderUrl());
      return Optional.empty();
    }
    try {
      ForgejoUser user = forgejoApiClient.getUser(personalAccessToken.getToken());
      boolean isOAuthToken =
          personalAccessToken.getScmTokenName() != null
              && personalAccessToken.getScmTokenName().startsWith(OAUTH_2_PREFIX);
      // OAuth tokens: reading the user is the only available check.
      // Personal access tokens: the token must belong to the user it was saved for.
      return Optional.of(
          isOAuthToken || user.getLogin().equals(personalAccessToken.getScmUserName()));
    } catch (ScmItemNotFoundException
        | ScmCommunicationException
        | ScmBadRequestException
        | ScmUnauthorizedException e) {
      return Optional.of(Boolean.FALSE);
    }
  }

  @Override
  public Optional<Pair<Boolean, String>> isValid(PersonalAccessTokenParams params)
      throws ScmCommunicationException {
    ForgejoApiClient forgejoApiClient =
        getApiClient(params.getScmProviderUrl(), params.getScmTokenName());
    if (forgejoApiClient == null) {
      LOG.debug("not a valid url {} for current fetcher ", params.getScmProviderUrl());
      return Optional.empty();
    }
    try {
      ForgejoUser user = forgejoApiClient.getUser(params.getToken());
      return Optional.of(Pair.of(Boolean.TRUE, user.getLogin()));
    } catch (ScmItemNotFoundException | ScmBadRequestException | ScmUnauthorizedException e) {
      return Optional.empty();
    }
  }

  private String getLocalAuthenticateUrl() {
    return apiEndpoint
        + "/oauth/authenticate?oauth_provider="
        + providerName
        + "&scope="
        + Joiner.on('+').join(DEFAULT_TOKEN_SCOPES)
        + "&request_method=POST&signature_method=rsa";
  }

  /** API client for the configured server, {@code null} for any other server. */
  private ForgejoApiClient getApiClient(String scmServerUrl) {
    return serverUrl != null && serverUrl.equals(trimEnd(scmServerUrl, '/'))
        ? new ForgejoApiClient(serverUrl)
        : null;
  }

  /**
   * API client for the configured server, or for any server when the token is a Forgejo personal
   * access token (self-hosted server without OAuth configuration).
   */
  private ForgejoApiClient getApiClient(String scmServerUrl, @Nullable String tokenName) {
    ForgejoApiClient forgejoApiClient = getApiClient(scmServerUrl);
    if (forgejoApiClient == null && providerName.equals(tokenName)) {
      return new ForgejoApiClient(scmServerUrl);
    }
    return forgejoApiClient;
  }
}
