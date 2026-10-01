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
import static java.util.regex.Pattern.compile;
import static org.eclipse.che.commons.lang.StringUtils.trimEnd;

import com.google.common.base.Charsets;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.che.api.factory.server.scm.PersonalAccessToken;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.factory.server.scm.exception.ScmBadRequestException;
import org.eclipse.che.api.factory.server.scm.exception.ScmCommunicationException;
import org.eclipse.che.api.factory.server.scm.exception.ScmConfigurationPersistenceException;
import org.eclipse.che.api.factory.server.scm.exception.ScmUnauthorizedException;
import org.eclipse.che.api.factory.server.urlfactory.DevfileFilenamesProvider;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parser of Forgejo repository URLs into {@link ForgejoUrl} objects.
 *
 * <p>Supported forms:
 *
 * <ul>
 *   <li>{@code https://<host>/<owner>/<repo>[.git]}
 *   <li>{@code https://<host>/<owner>/<repo>/src/branch/<branch>[/<path>]}
 *   <li>{@code https://<host>/<owner>/<repo>/src/tag/<tag>[/<path>]}
 *   <li>{@code https://<host>/<owner>/<repo>/src/commit/<sha>[/<path>]}
 *   <li>{@code git@<host>:<owner>/<repo>.git}
 *   <li>{@code ssh://git@<host>[:<port>]/<owner>/<repo>.git}
 * </ul>
 *
 * <p>Branch and tag names may contain slashes, so the part after {@code /src/branch/} or {@code
 * /src/tag/} can be a reference followed by a file path, e.g. {@code src/branch/main/devfile.yaml}
 * as copied from the Forgejo UI. When it has several segments, the parser resolves it as Forgejo
 * does: it asks the Forgejo API for the shortest leading segments that name an existing branch or
 * tag, the remaining path being ignored as for the {@code /src/commit/} form. When no candidate
 * matches or the API cannot be reached, the whole part is taken as the reference. At most five
 * candidates are requested.
 *
 * <p>A URL matches when its host is the configured Forgejo endpoint. Other hosts are accepted only
 * when the user has a Forgejo personal access token for that server and the server answers the
 * Forgejo version API: Che does not probe arbitrary hosts.
 */
public class AbstractForgejoUrlParser {

  /** {@code /<owner>/<repo>[.git][/<rest>]}, relative to the server URL */
  private static final Pattern REPOSITORY_PATH_PATTERN =
      compile("^/(?<owner>[^/]++)/(?<repo>[^/]+?)(\\.git)?(/(?<rest>.*))?$");

  private static final Pattern BRANCH_OR_TAG_PATTERN =
      compile("^src/(?<type>branch|tag)/(?<ref>.+?)/?$");
  private static final Pattern COMMIT_PATTERN =
      compile("^src/commit/(?<ref>[0-9a-fA-F]{4,64})(/.*)?$");

  /** {@code git@<host>:<owner>/<repo>[.git]} */
  private static final Pattern SCP_PATTERN =
      compile("^[^@/\\s]+@(?<host>[^:/\\s]+):/?(?<owner>[^/]+)/(?<repo>[^/]+?)(\\.git)?/?$");

  /** {@code ssh://git@<host>[:<port>]/<owner>/<repo>[.git]} */
  private static final Pattern SSH_URI_PATTERN =
      compile(
          "^ssh://([^@/\\s]+@)?(?<host>[^:/\\s]+)(:\\d+)?/(?<owner>[^/]+)/(?<repo>[^/]+?)(\\.git)?/?$");

  /** Maximum number of Forgejo API calls made to resolve a branch or tag that has a path */
  private static final int MAX_REF_CANDIDATES = 5;

  private static final Logger LOG = LoggerFactory.getLogger(AbstractForgejoUrlParser.class);

  private final DevfileFilenamesProvider devfileFilenamesProvider;
  private final PersonalAccessTokenManager personalAccessTokenManager;
  private final String providerName;

  /** Configured server URL without trailing slash, {@code null} when not configured */
  @Nullable private final String serverUrl;

  /** Hostname of the configured server URL, {@code null} when not configured */
  @Nullable private final String serverHost;

  public AbstractForgejoUrlParser(
      @Nullable String serverUrl,
      DevfileFilenamesProvider devfileFilenamesProvider,
      PersonalAccessTokenManager personalAccessTokenManager,
      String providerName) {
    this.devfileFilenamesProvider = devfileFilenamesProvider;
    this.personalAccessTokenManager = personalAccessTokenManager;
    this.providerName = providerName;
    String trimmedServerUrl = isNullOrEmpty(serverUrl) ? null : trimEnd(serverUrl, '/');
    String host = trimmedServerUrl == null ? null : getHost(trimmedServerUrl);
    if (trimmedServerUrl != null && host == null) {
      LOG.warn(
          "Invalid {} Forgejo endpoint '{}': an absolute URL like https://forgejo.example.com is"
              + " expected. The endpoint is ignored.",
          providerName,
          serverUrl);
    }
    this.serverUrl = host == null ? null : trimmedServerUrl;
    this.serverHost = host;
  }

  /** Host of an absolute URL, {@code null} when the URL is not absolute or is malformed. */
  @Nullable
  private static String getHost(String url) {
    try {
      URI uri = URI.create(url);
      return uri.getScheme() == null ? null : uri.getHost();
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  public boolean isValid(@NotNull String url) {
    return parseConfigured(url).isPresent()
        // Unknown host: only if the user has a Forgejo token for it, and it is a Forgejo server
        || (isUserTokenPresent(url) && isForgejoServer(url));
  }

  /**
   * Parses a Forgejo repository URL.
   *
   * @param revision branch, tag or commit used when the URL does not contain one
   */
  public ForgejoUrl parse(String url, @Nullable String revision) {
    String trimmedUrl = trimEnd(url.trim(), '/');
    ForgejoUrl forgejoUrl =
        parseConfigured(trimmedUrl)
            .or(() -> parseAnyHost(trimmedUrl))
            .orElseThrow(
                () ->
                    new UnsupportedOperationException(
                        "The Forgejo integration is not configured properly and cannot be used at"
                            + " this moment. Please refer to docs to check the Forgejo integration"
                            + " instructions"));
    resolveBranchOrTag(forgejoUrl, trimmedUrl);
    if (forgejoUrl.getBranch() == null) {
      forgejoUrl.withBranch(revision);
    }
    forgejoUrl.withDevfileFilenames(devfileFilenamesProvider.getConfiguredDevfileFilenames());
    forgejoUrl.withUrl(trimmedUrl);
    return forgejoUrl;
  }

  /** Parses the URL if it belongs to the configured server. */
  private Optional<ForgejoUrl> parseConfigured(String url) {
    if (serverUrl == null) {
      return Optional.empty();
    }
    String trimmedUrl = trimEnd(url.trim(), '/');
    Optional<ForgejoUrl> ssh = parseSsh(trimmedUrl);
    if (ssh.isPresent()) {
      return ssh.get().getHostName().equalsIgnoreCase(serverHost)
          ? Optional.of(ssh.get().withProviderUrl(serverUrl))
          : Optional.empty();
    }
    if (!trimmedUrl.regionMatches(true, 0, serverUrl + "/", 0, serverUrl.length() + 1)) {
      return Optional.empty();
    }
    return parseRepositoryPath(trimmedUrl.substring(serverUrl.length()))
        .map(forgejoUrl -> forgejoUrl.withProviderUrl(serverUrl).withHostName(serverHost));
  }

  /** Parses the URL of any host, the server URL being the scheme and authority of the URL. */
  private Optional<ForgejoUrl> parseAnyHost(String url) {
    Optional<ForgejoUrl> ssh = parseSsh(url);
    if (ssh.isPresent()) {
      return Optional.of(ssh.get().withProviderUrl("https://" + ssh.get().getHostName()));
    }
    try {
      URI uri = URI.create(url);
      if (uri.getScheme() == null || uri.getRawAuthority() == null || uri.getHost() == null) {
        return Optional.empty();
      }
      String providerUrl = uri.getScheme() + "://" + uri.getRawAuthority();
      return parseRepositoryPath(url.substring(providerUrl.length()))
          .map(forgejoUrl -> forgejoUrl.withProviderUrl(providerUrl).withHostName(uri.getHost()));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  private static Optional<ForgejoUrl> parseSsh(String url) {
    Matcher matcher = SCP_PATTERN.matcher(url);
    if (!matcher.matches()) {
      matcher = SSH_URI_PATTERN.matcher(url);
      if (!matcher.matches()) {
        return Optional.empty();
      }
    }
    return Optional.of(
        new ForgejoUrl()
            .withHostName(matcher.group("host"))
            .withOwner(matcher.group("owner"))
            .withRepository(matcher.group("repo"))
            .withSshLocation(url));
  }

  /** Parses {@code /<owner>/<repo>[...]}, the path relative to the server URL. */
  private static Optional<ForgejoUrl> parseRepositoryPath(String path) {
    // drop query and fragment
    String pathOnly = path.replaceAll("[?#].*$", "");
    Matcher matcher = REPOSITORY_PATH_PATTERN.matcher(pathOnly);
    if (!matcher.matches()) {
      return Optional.empty();
    }
    ForgejoUrl forgejoUrl =
        new ForgejoUrl().withOwner(matcher.group("owner")).withRepository(matcher.group("repo"));
    String rest = matcher.group("rest");
    if (!isNullOrEmpty(rest)) {
      Matcher refMatcher = BRANCH_OR_TAG_PATTERN.matcher(rest);
      if (!refMatcher.matches()) {
        refMatcher = COMMIT_PATTERN.matcher(rest);
      }
      if (refMatcher.matches()) {
        forgejoUrl.withBranch(decode(refMatcher.group("ref")));
      }
    }
    return Optional.of(forgejoUrl);
  }

  /**
   * Resolves the reference of a {@code /src/branch/<ref>[/<path>]} or {@code
   * /src/tag/<ref>[/<path>]} URL, the reference and the path being both slash separated. The
   * leading segments are tried from the shortest, as Forgejo does, and Git does not allow a branch
   * (or a tag) to be the prefix of another one, so at most one candidate exists. The whole part is
   * the fallback, already set as the branch by {@link #parseRepositoryPath(String)}, so it is not
   * requested.
   */
  private void resolveBranchOrTag(ForgejoUrl forgejoUrl, String url) {
    String providerUrl = forgejoUrl.getProviderUrl();
    if (forgejoUrl.getBranch() == null
        || !url.regionMatches(true, 0, providerUrl + "/", 0, providerUrl.length() + 1)) {
      return;
    }
    Matcher matcher =
        REPOSITORY_PATH_PATTERN.matcher(
            url.substring(providerUrl.length()).replaceAll("[?#].*$", ""));
    if (!matcher.matches() || isNullOrEmpty(matcher.group("rest"))) {
      return;
    }
    Matcher refMatcher = BRANCH_OR_TAG_PATTERN.matcher(matcher.group("rest"));
    if (!refMatcher.matches()) {
      return;
    }
    String[] segments = refMatcher.group("ref").split("/");
    if (segments.length < 2) {
      return;
    }
    boolean isTag = "tag".equals(refMatcher.group("type"));
    String token = getToken(providerUrl);
    ForgejoApiClient apiClient = new ForgejoApiClient(providerUrl);
    for (int i = 1; i <= Math.min(segments.length - 1, MAX_REF_CANDIDATES); i++) {
      String candidate = decode(String.join("/", Arrays.copyOfRange(segments, 0, i)));
      try {
        if (isTag
            ? apiClient.isTagPresent(
                forgejoUrl.getOwner(), forgejoUrl.getRepository(), candidate, token)
            : apiClient.isBranchPresent(
                forgejoUrl.getOwner(), forgejoUrl.getRepository(), candidate, token)) {
          forgejoUrl.withBranch(candidate);
          return;
        }
      } catch (ScmCommunicationException | ScmBadRequestException | ScmUnauthorizedException e) {
        LOG.debug("Failed to resolve the reference of {}", url, e);
        return;
      }
    }
  }

  /** Returns the token of the user for the Forgejo server, {@code null} when there is none. */
  @Nullable
  private String getToken(String providerUrl) {
    try {
      return personalAccessTokenManager
          .get(EnvironmentContext.getCurrent().getSubject(), null, providerUrl, null)
          .map(PersonalAccessToken::getToken)
          .orElse(null);
    } catch (ScmConfigurationPersistenceException | ScmCommunicationException e) {
      return null;
    }
  }

  private static String decode(String value) {
    return URLDecoder.decode(value.replace("+", "%2B"), Charsets.UTF_8);
  }

  private boolean isUserTokenPresent(String repositoryUrl) {
    Optional<String> serverUrlOptional = getServerUrl(repositoryUrl);
    if (serverUrlOptional.isPresent()) {
      try {
        // The token is only read, it is neither validated against nor refreshed by its provider:
        // the URL may belong to another provider, e.g. https://github.com, whose resolvers come
        // after the Forgejo ones.
        Optional<PersonalAccessToken> token =
            personalAccessTokenManager.getStored(
                EnvironmentContext.getCurrent().getSubject(), null, serverUrlOptional.get(), null);
        return token.isPresent()
            && (providerName.equals(token.get().getScmTokenName())
                || providerName.equals(token.get().getScmProviderName()));
      } catch (ScmConfigurationPersistenceException | ScmCommunicationException exception) {
        return false;
      }
    }
    return false;
  }

  private boolean isForgejoServer(String repositoryUrl) {
    return getServerUrl(repositoryUrl)
        .map(url -> new ForgejoApiClient(url).isForgejoServer())
        .orElse(false);
  }

  private Optional<String> getServerUrl(String repositoryUrl) {
    return parseAnyHost(trimEnd(repositoryUrl.trim(), '/')).map(ForgejoUrl::getProviderUrl);
  }
}
