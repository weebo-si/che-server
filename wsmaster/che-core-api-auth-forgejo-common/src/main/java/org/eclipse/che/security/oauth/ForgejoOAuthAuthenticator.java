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
package org.eclipse.che.security.oauth;

import static com.google.common.base.Strings.isNullOrEmpty;
import static org.eclipse.che.commons.lang.StringUtils.trimEnd;

import com.google.api.client.auth.oauth2.StoredCredential;
import com.google.api.client.util.store.DataStore;
import com.google.api.client.util.store.MemoryDataStoreFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Singleton;
import org.eclipse.che.api.auth.shared.dto.OAuthToken;
import org.eclipse.che.commons.json.JsonHelper;
import org.eclipse.che.commons.json.JsonNameConventions;
import org.eclipse.che.commons.json.JsonParseException;

/**
 * OAuth2 authenticator for Forgejo account.
 *
 * <p>Forgejo exposes the standard authorization code flow with refresh tokens. It has no token
 * revocation endpoint, so {@link #invalidateToken(String)} only forgets the token on the Che side.
 */
@Singleton
public class ForgejoOAuthAuthenticator extends OAuthAuthenticator {

  /**
   * Scopes requested to Forgejo: read the user, clone and push repositories. Requested when the
   * caller does not ask for any scope. Keep in sync with {@code
   * AbstractForgejoOAuthTokenFetcher.DEFAULT_TOKEN_SCOPES} of the factory module, which has no
   * dependency on this module.
   */
  public static final List<String> DEFAULT_SCOPES = List.of("read:user", "write:repository");

  private final String forgejoUserEndpoint;
  private final String cheApiEndpoint;
  private final String forgejoEndpoint;
  private final String providerName;

  public ForgejoOAuthAuthenticator(
      String clientId,
      String clientSecret,
      String forgejoEndpoint,
      String cheApiEndpoint,
      String providerName)
      throws IOException {
    this.forgejoEndpoint = trimEnd(forgejoEndpoint, '/');
    this.providerName = providerName;
    this.forgejoUserEndpoint = this.forgejoEndpoint + "/api/v1/user";
    this.cheApiEndpoint = cheApiEndpoint;
    configure(
        clientId,
        clientSecret,
        new String[] {},
        this.forgejoEndpoint + "/login/oauth/authorize",
        this.forgejoEndpoint + "/login/oauth/access_token",
        new MemoryDataStoreFactory(),
        DEFAULT_SCOPES);
  }

  @Override
  public String getOAuthProvider() {
    return providerName;
  }

  /**
   * Builds the authentication URL, requesting {@link #DEFAULT_SCOPES} when no scope is given: the
   * scopes of the request override the default scopes of the authorization flow, even when empty.
   */
  @Override
  public String getAuthenticateUrl(URL requestUrl, List<String> scopes)
      throws OAuthAuthenticationException {
    return super.getAuthenticateUrl(
        requestUrl, scopes == null || scopes.isEmpty() ? DEFAULT_SCOPES : scopes);
  }

  @Override
  protected String findRedirectUrl(URL requestUrl) {
    return cheApiEndpoint + "/oauth/callback";
  }

  @Override
  protected <O> O getJson(String getUserUrl, String accessToken, Class<O> userClass)
      throws OAuthAuthenticationException {
    HttpClient client = HttpClient.newHttpClient();
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(getUserUrl))
            .header("Authorization", "token " + accessToken)
            .header("Accept", "application/json")
            .build();
    try {
      HttpResponse<InputStream> response =
          client.send(request, HttpResponse.BodyHandlers.ofInputStream());
      if (response.statusCode() != 200) {
        throw new OAuthAuthenticationException(
            "Unexpected status code " + response.statusCode() + " from " + getUserUrl);
      }
      // Forgejo names JSON fields in snake case, e.g. full_name
      return JsonHelper.fromJson(
          response.body(), userClass, null, JsonNameConventions.CAMEL_UNDERSCORE);
    } catch (IOException | InterruptedException | JsonParseException e) {
      throw new OAuthAuthenticationException(e.getMessage(), e);
    }
  }

  /** Returns the token only if it can still read the Forgejo user. */
  @Override
  public OAuthToken getOrRefreshToken(String userId) throws IOException {
    final OAuthToken token = super.getOrRefreshToken(userId);
    try {
      if (token == null || isNullOrEmpty(token.getToken())) {
        return null;
      }
      ForgejoUser user = getJson(forgejoUserEndpoint, token.getToken(), ForgejoUser.class);
      if (user == null || isNullOrEmpty(user.getId())) {
        return null;
      }
    } catch (OAuthAuthenticationException e) {
      return null;
    }
    return token;
  }

  /**
   * Forgets the given token.
   *
   * <p>Forgejo has no OAuth token revocation endpoint: the token stays valid on the Forgejo side
   * until it expires or the user revokes the application in the Forgejo settings. Only the
   * credential stored by Che is dropped, so that the token is no longer used and the next request
   * goes through the authorization flow again.
   *
   * @return {@code true} if a stored credential held the token, {@code false} otherwise
   */
  @Override
  public boolean invalidateToken(String token) throws IOException {
    if (!isConfigured() || isNullOrEmpty(token)) {
      return false;
    }
    DataStore<StoredCredential> credentialDataStore = flow.getCredentialDataStore();
    boolean invalidated = false;
    for (String userId : new ArrayList<>(credentialDataStore.keySet())) {
      StoredCredential credential = credentialDataStore.get(userId);
      if (credential != null && token.equals(credential.getAccessToken())) {
        credentialDataStore.delete(userId);
        invalidated = true;
      }
    }
    return invalidated;
  }

  @Override
  public String getEndpointUrl() {
    return forgejoEndpoint;
  }
}
