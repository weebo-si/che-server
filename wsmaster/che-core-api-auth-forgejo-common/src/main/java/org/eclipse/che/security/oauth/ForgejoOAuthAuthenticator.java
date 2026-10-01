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

import com.google.api.client.util.store.MemoryDataStoreFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import javax.inject.Singleton;
import org.eclipse.che.api.auth.shared.dto.OAuthToken;
import org.eclipse.che.commons.json.JsonHelper;
import org.eclipse.che.commons.json.JsonParseException;

/**
 * OAuth2 authenticator for Forgejo account.
 *
 * <p>Forgejo exposes the standard authorization code flow with refresh tokens. It has no token
 * revocation endpoint, so {@link #invalidateToken(String)} is not supported.
 */
@Singleton
public class ForgejoOAuthAuthenticator extends OAuthAuthenticator {

  /** Scopes requested to Forgejo: read the user, clone and push repositories. */
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
      return JsonHelper.fromJson(response.body(), userClass, null);
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

  @Override
  public String getEndpointUrl() {
    return forgejoEndpoint;
  }
}
