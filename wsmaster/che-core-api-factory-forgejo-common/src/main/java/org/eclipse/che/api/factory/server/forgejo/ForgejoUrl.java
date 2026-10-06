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

import com.google.common.base.Charsets;
import java.net.URLEncoder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.eclipse.che.api.factory.server.urlfactory.DefaultFactoryUrl;
import org.eclipse.che.commons.annotation.Nullable;

/**
 * Representation of a Forgejo repository URL, like {@code https://forgejo.example.com/owner/repo},
 * {@code https://forgejo.example.com/owner/repo/src/branch/<branch>} or {@code
 * git@forgejo.example.com:owner/repo.git}.
 */
public class ForgejoUrl extends DefaultFactoryUrl {

  private static final String NAME = "forgejo";

  /** Server URL, e.g. {@code https://forgejo.example.com:3000} or {@code https://host/forgejo} */
  private String providerUrl;

  /** Hostname, without port */
  private String hostName;

  /** SSH clone URL, when the factory URL is an SSH one */
  private String sshLocation;

  private String owner;
  private String repository;

  /** Branch, tag or commit */
  private String branch;

  private final List<String> devfileFilenames = new ArrayList<>();

  /**
   * Creation of this instance is made by the parser so user may not need to create a new instance
   * directly
   */
  protected ForgejoUrl() {}

  @Override
  public String getProviderName() {
    return NAME;
  }

  @Override
  public String getProviderUrl() {
    return providerUrl;
  }

  protected ForgejoUrl withProviderUrl(String providerUrl) {
    this.providerUrl = providerUrl;
    return this;
  }

  public String getHostName() {
    return hostName;
  }

  protected ForgejoUrl withHostName(String hostName) {
    this.hostName = hostName;
    return this;
  }

  protected ForgejoUrl withSshLocation(String sshLocation) {
    this.sshLocation = sshLocation;
    return this;
  }

  public String getOwner() {
    return owner;
  }

  protected ForgejoUrl withOwner(String owner) {
    this.owner = owner;
    return this;
  }

  public String getRepository() {
    return repository;
  }

  protected ForgejoUrl withRepository(String repository) {
    this.repository = repository;
    return this;
  }

  /** Branch, tag or commit of this URL, {@code null} for the default branch. */
  public String getBranch() {
    return branch;
  }

  protected ForgejoUrl withBranch(@Nullable String branch) {
    if (!isNullOrEmpty(branch)) {
      this.branch = branch;
    }
    return this;
  }

  protected ForgejoUrl withDevfileFilenames(List<String> devfileFilenames) {
    this.devfileFilenames.addAll(devfileFilenames);
    return this;
  }

  @Override
  public void setDevfileFilename(String devfileName) {
    this.devfileFilenames.clear();
    this.devfileFilenames.add(devfileName);
  }

  @Override
  public List<DevfileLocation> devfileFileLocations() {
    return devfileFilenames.stream().map(this::createDevfileLocation).collect(Collectors.toList());
  }

  private DevfileLocation createDevfileLocation(String devfileFilename) {
    return new DevfileLocation() {
      @Override
      public Optional<String> filename() {
        return Optional.of(devfileFilename);
      }

      @Override
      public String location() {
        return rawFileLocation(devfileFilename);
      }
    };
  }

  /** Location of the raw content of a file, through the Forgejo API. */
  @Override
  public String rawFileLocation(String fileName) {
    return providerUrl + rawFilePath(owner, repository, fileName, branch);
  }

  /** Location of the repository to clone. */
  protected String repositoryLocation() {
    if (!isNullOrEmpty(sshLocation)) {
      return sshLocation;
    }
    return providerUrl + "/" + owner + "/" + repository + ".git";
  }

  /** API path of the raw content of a file, relative to the server URL. */
  static String rawFilePath(String owner, String repository, String path, @Nullable String ref) {
    return "/api/v1/repos/"
        + encode(owner)
        + "/"
        + encode(repository)
        + "/raw/"
        + encodePath(normalizePath(path))
        + (isNullOrEmpty(ref) ? "" : "?ref=" + encode(ref));
  }

  /**
   * Resolves the {@code .} and {@code ..} segments of a path relative to the repository root, as
   * RFC 3986 does for URL paths: a {@code ..} segment above the root is dropped, so that the path
   * never leaves the repository. Empty segments are dropped as well.
   */
  static String normalizePath(String path) {
    Deque<String> segments = new ArrayDeque<>();
    for (String segment : path.split("/")) {
      if (segment.equals("..")) {
        segments.pollLast();
      } else if (!segment.isEmpty() && !segment.equals(".")) {
        segments.addLast(segment);
      }
    }
    return String.join("/", segments);
  }

  /** Encodes each segment of a slash separated path, keeping the slashes. */
  static String encodePath(String path) {
    return Arrays.stream(path.replaceAll("^/+", "").split("/"))
        .map(ForgejoUrl::encode)
        .collect(Collectors.joining("/"));
  }

  static String encode(String value) {
    return URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20");
  }
}
