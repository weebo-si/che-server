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

import static java.net.HttpURLConnection.HTTP_OK;
import static java.time.Duration.ofSeconds;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Executors;
import org.eclipse.che.api.factory.server.scm.AuthorizingFileContentProvider;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.workspace.server.devfile.URLFetcher;
import org.eclipse.che.commons.lang.concurrent.LoggingUncaughtExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Forgejo specific authorizing file content provider. */
class ForgejoAuthorizingFileContentProvider extends AuthorizingFileContentProvider<ForgejoUrl> {

  private static final Logger LOG =
      LoggerFactory.getLogger(ForgejoAuthorizingFileContentProvider.class);

  private final HttpClient httpClient;

  private static final Duration DEFAULT_HTTP_TIMEOUT = ofSeconds(10);

  ForgejoAuthorizingFileContentProvider(
      ForgejoUrl forgejoUrl,
      URLFetcher urlFetcher,
      PersonalAccessTokenManager personalAccessTokenManager) {
    super(forgejoUrl, urlFetcher, personalAccessTokenManager);
    this.httpClient =
        HttpClient.newBuilder()
            .executor(
                Executors.newCachedThreadPool(
                    new ThreadFactoryBuilder()
                        .setUncaughtExceptionHandler(LoggingUncaughtExceptionHandler.getInstance())
                        .setNameFormat(
                            ForgejoAuthorizingFileContentProvider.class.getName() + "-%d")
                        .setDaemon(true)
                        .build()))
            .connectTimeout(DEFAULT_HTTP_TIMEOUT)
            .version(HttpClient.Version.HTTP_1_1)
            .build();
  }

  @Override
  protected String formatAuthorization(String token, boolean isPAT) {
    return "token " + token;
  }

  /** A repository is public when its metadata can be read anonymously. */
  @Override
  protected boolean isPublicRepository(ForgejoUrl remoteFactoryUrl) {
    try {
      HttpRequest request =
          HttpRequest.newBuilder(
                  URI.create(
                      remoteFactoryUrl.getProviderUrl()
                          + "/api/v1/repos/"
                          + ForgejoUrl.encode(remoteFactoryUrl.getOwner())
                          + "/"
                          + ForgejoUrl.encode(remoteFactoryUrl.getRepository())))
              .timeout(DEFAULT_HTTP_TIMEOUT)
              .build();
      HttpResponse<InputStream> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
      return response.statusCode() == HTTP_OK;
    } catch (IllegalArgumentException e) {
      LOG.debug("Invalid Forgejo repository URL {}", remoteFactoryUrl.getProviderUrl(), e);
      return false;
    } catch (IOException | InterruptedException e) {
      return false;
    }
  }
}
