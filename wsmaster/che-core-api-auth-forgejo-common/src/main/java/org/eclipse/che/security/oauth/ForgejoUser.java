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

import org.eclipse.che.security.oauth.shared.User;

/**
 * Represents Forgejo user, as returned by {@code GET /api/v1/user} and parsed with the {@code
 * CAMEL_UNDERSCORE} JSON name convention: Forgejo returns {@code login} and {@code full_name}, not
 * {@code name}.
 */
public class ForgejoUser implements User {
  private String id;
  private String login;
  private String fullName;
  private String email;

  @Override
  public String getId() {
    return id;
  }

  @Override
  public void setId(String id) {
    this.id = id;
  }

  public String getLogin() {
    return login;
  }

  public void setLogin(String login) {
    this.login = login;
  }

  public String getFullName() {
    return fullName;
  }

  public void setFullName(String fullName) {
    this.fullName = fullName;
  }

  /** Returns the full name, or the login when the user has no full name (optional in Forgejo). */
  @Override
  public String getName() {
    return isNullOrEmpty(fullName) ? login : fullName;
  }

  @Override
  public void setName(String name) {
    this.fullName = name;
  }

  @Override
  public String getEmail() {
    return email;
  }

  @Override
  public void setEmail(String email) {
    this.email = email;
  }

  @Override
  public String toString() {
    return "ForgejoUser{"
        + "id='"
        + id
        + '\''
        + ", login='"
        + login
        + '\''
        + ", fullName='"
        + fullName
        + '\''
        + ", email='"
        + email
        + '\''
        + '}';
  }
}
