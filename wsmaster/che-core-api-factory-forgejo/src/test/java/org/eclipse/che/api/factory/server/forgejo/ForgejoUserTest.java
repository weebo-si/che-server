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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class ForgejoUserTest {

  private static ForgejoUser user(long id, String login, String fullName, String email) {
    ForgejoUser user = new ForgejoUser();
    user.setId(id);
    user.setLogin(login);
    user.setFullName(fullName);
    user.setEmail(email);
    return user;
  }

  @Test
  public void shouldBeEqualToItselfAndToSameValues() {
    ForgejoUser user = user(1, "jdoe", "John Doe", "jdoe@example.com");

    assertEquals(user, user);
    assertEquals(user, user(1, "jdoe", "John Doe", "jdoe@example.com"));
    assertEquals(user.hashCode(), user(1, "jdoe", "John Doe", "jdoe@example.com").hashCode());
  }

  @DataProvider
  public Object[][] differentUsers() {
    return new Object[][] {
      {user(2, "jdoe", "John Doe", "jdoe@example.com")},
      {user(1, "other", "John Doe", "jdoe@example.com")},
      {user(1, "jdoe", "Other", "jdoe@example.com")},
      {user(1, "jdoe", "John Doe", "other@example.com")},
    };
  }

  @Test(dataProvider = "differentUsers")
  public void shouldNotBeEqualToDifferentValues(ForgejoUser other) {
    assertNotEquals(user(1, "jdoe", "John Doe", "jdoe@example.com"), other);
  }

  @Test
  public void shouldNotBeEqualToNullOrOtherType() {
    ForgejoUser user = user(1, "jdoe", "John Doe", "jdoe@example.com");

    // call equals directly: TestNG handles null without calling it
    assertFalse(user.equals(null));
    assertFalse(user.equals("jdoe"));
  }

  @Test
  public void shouldDescribeUserWithoutSecrets() {
    String description = user(1, "jdoe", "John Doe", "jdoe@example.com").toString();

    assertTrue(description.startsWith("ForgejoUser{id=1, login='jdoe'"), description);
  }
}
