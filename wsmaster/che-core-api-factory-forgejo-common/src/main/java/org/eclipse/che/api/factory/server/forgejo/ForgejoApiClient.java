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

import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_UNAUTHORIZED;
import static java.time.Duration.ofSeconds;
import static org.eclipse.che.commons.lang.StringUtils.trimEnd;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.function.Function;
import org.eclipse.che.api.factory.server.scm.exception.ScmBadRequestException;
import org.eclipse.che.api.factory.server.scm.exception.ScmCommunicationException;
import org.eclipse.che.api.factory.server.scm.exception.ScmItemNotFoundException;
import org.eclipse.che.api.factory.server.scm.exception.ScmUnauthorizedException;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.commons.lang.concurrent.LoggingUncaughtExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Forgejo API operations helper. Also compatible with Gitea, which exposes the same {@code /api/v1}
 * API.
 *
 * <p>Uses the default JVM truststore. Tokens are never logged.
 */
public class ForgejoApiClient {

  private static final Logger LOG = LoggerFactory.getLogger(ForgejoApiClient.class);

  static final String PROVIDER_NAME = "forgejo";

  private final HttpClient httpClient;
  private final String serverUrl;

  private static final Duration DEFAULT_HTTP_TIMEOUT = ofSeconds(10);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  public ForgejoApiClient(String serverUrl) {
    this.serverUrl = trimEnd(serverUrl, '/');
    this.httpClient =
        HttpClient.newBuilder()
            .executor(
                Executors.newCachedThreadPool(
                    new ThreadFactoryBuilder()
                        .setUncaughtExceptionHandler(LoggingUncaughtExceptionHandler.getInstance())
                        .setNameFormat(ForgejoApiClient.class.getName() + "-%d")
                        .setDaemon(true)
                        .build()))
            .connectTimeout(DEFAULT_HTTP_TIMEOUT)
            .version(HttpClient.Version.HTTP_1_1)
            .build();
  }

  /** Returns the user that owns the given token: {@code GET /api/v1/user}. */
  public ForgejoUser getUser(String authenticationToken)
      throws ScmItemNotFoundException,
          ScmCommunicationException,
          ScmBadRequestException,
          ScmUnauthorizedException {
    HttpRequest request = newRequest("/api/v1/user", authenticationToken);
    return executeRequest(
        request, inputStream -> readValue(inputStream, ForgejoUser.class, OBJECT_MAPPER));
  }

  /**
   * Returns the raw content of a file: {@code GET /api/v1/repos/{owner}/{repo}/raw/{path}?ref=}.
   *
   * @param ref branch, tag or commit; the default branch is used when {@code null}
   * @param authenticationToken token to use, anonymous request when {@code null}
   */
  public String getFileContent(
      String owner,
      String repository,
      String path,
      @Nullable String ref,
      @Nullable String authenticationToken)
      throws ScmItemNotFoundException,
          ScmCommunicationException,
          ScmBadRequestException,
          ScmUnauthorizedException {
    HttpRequest request =
        newRequest(ForgejoUrl.rawFilePath(owner, repository, path, ref), authenticationToken);
    return executeRequest(
        request,
        inputStream -> {
          try {
            return CharStreams.toString(new InputStreamReader(inputStream, Charsets.UTF_8));
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
        });
  }

  /**
   * Checks whether a branch exists: {@code GET /api/v1/repos/{owner}/{repo}/branches/{branch}}.
   *
   * @param authenticationToken token to use, anonymous request when {@code null}
   * @return {@code false} when the server answers that the branch does not exist
   */
  public boolean isBranchPresent(
      String owner, String repository, String branch, @Nullable String authenticationToken)
      throws ScmCommunicationException, ScmBadRequestException, ScmUnauthorizedException {
    return isRefPresent(owner, repository, "branches", branch, authenticationToken);
  }

  /**
   * Checks whether a tag exists: {@code GET /api/v1/repos/{owner}/{repo}/tags/{tag}}.
   *
   * @param authenticationToken token to use, anonymous request when {@code null}
   * @return {@code false} when the server answers that the tag does not exist
   */
  public boolean isTagPresent(
      String owner, String repository, String tag, @Nullable String authenticationToken)
      throws ScmCommunicationException, ScmBadRequestException, ScmUnauthorizedException {
    return isRefPresent(owner, repository, "tags", tag, authenticationToken);
  }

  private boolean isRefPresent(
      String owner,
      String repository,
      String refType,
      String ref,
      @Nullable String authenticationToken)
      throws ScmCommunicationException, ScmBadRequestException, ScmUnauthorizedException {
    HttpRequest request =
        newRequest(
            "/api/v1/repos/"
                + ForgejoUrl.encode(owner)
                + "/"
                + ForgejoUrl.encode(repository)
                + "/"
                + refType
                + "/"
                + ForgejoUrl.encodePath(ref),
            authenticationToken);
    try {
      executeRequest(request, inputStream -> readValue(inputStream, JsonNode.class, OBJECT_MAPPER));
      return true;
    } catch (ScmItemNotFoundException e) {
      return false;
    }
  }

  /**
   * Checks whether the server is a Forgejo (or Gitea) instance. Calls {@code GET
   * /api/forgejo/v1/version} and falls back to {@code GET /api/v1/version}; both are anonymous.
   */
  public boolean isForgejoServer() {
    return hasVersion("/api/forgejo/v1/version") || hasVersion("/api/v1/version");
  }

  private boolean hasVersion(String path) {
    try {
      JsonNode version =
          executeRequest(
              newRequest(path, null),
              inputStream -> readValue(inputStream, JsonNode.class, OBJECT_MAPPER));
      return version != null && version.hasNonNull("version");
    } catch (ScmItemNotFoundException
        | ScmCommunicationException
        | ScmBadRequestException
        | ScmUnauthorizedException
        | IllegalArgumentException e) {
      return false;
    }
  }

  private HttpRequest newRequest(String path, @Nullable String authenticationToken) {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(URI.create(serverUrl + path))
            .header("Accept", "application/json")
            .timeout(DEFAULT_HTTP_TIMEOUT);
    if (authenticationToken != null) {
      builder.header("Authorization", "token " + authenticationToken);
    }
    return builder.build();
  }

  private static <T> T readValue(InputStream inputStream, Class<T> type, ObjectMapper mapper) {
    try {
      String result = CharStreams.toString(new InputStreamReader(inputStream, Charsets.UTF_8));
      return mapper.readValue(result, type);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private <T> T executeRequest(HttpRequest request, Function<InputStream, T> bodyConverter)
      throws ScmBadRequestException,
          ScmItemNotFoundException,
          ScmCommunicationException,
          ScmUnauthorizedException {
    try {
      HttpResponse<InputStream> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
      // the request is not logged as is: its headers contain the token
      LOG.trace(
          "executeRequest={} {} response {}",
          request.method(),
          request.uri(),
          response.statusCode());
      if (response.statusCode() == 200) {
        return bodyConverter.apply(response.body());
      } else if (response.statusCode() == 204) {
        return null;
      } else {
        String body = CharStreams.toString(new InputStreamReader(response.body(), Charsets.UTF_8));
        switch (response.statusCode()) {
          case HTTP_BAD_REQUEST:
            throw new ScmBadRequestException(body);
          case HTTP_NOT_FOUND:
            throw new ScmItemNotFoundException(body);
          case HTTP_UNAUTHORIZED:
            throw new ScmUnauthorizedException(body, PROVIDER_NAME, "v2", "");
          default:
            throw new ScmCommunicationException(
                "Unexpected status code " + response.statusCode() + " " + response,
                response.statusCode(),
                PROVIDER_NAME);
        }
      }
    } catch (IOException | InterruptedException | UncheckedIOException e) {
      throw new ScmCommunicationException(e.getMessage(), e, PROVIDER_NAME);
    }
  }

  public boolean isConnected(String scmServerUrl) {
    return serverUrl.equals(trimEnd(scmServerUrl, '/'));
  }
}
