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

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

public class ForgejoUserTest {

  @Test
  public void shouldHoldUserData() {
    ForgejoUser user = new ForgejoUser();
    user.setId("1");
    user.setName("John Doe");
    user.setEmail("jdoe@example.com");

    assertEquals(user.getId(), "1");
    assertEquals(user.getName(), "John Doe");
    assertEquals(user.getEmail(), "jdoe@example.com");
    assertEquals(user.toString(), "ForgejoUser{id='1', name='John Doe', email='jdoe@example.com'}");
  }
}
