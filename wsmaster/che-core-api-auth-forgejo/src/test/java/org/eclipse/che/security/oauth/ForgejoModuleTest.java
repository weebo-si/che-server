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

import com.google.inject.spi.Elements;
import com.google.inject.spi.ProviderKeyBinding;
import java.util.Set;
import java.util.stream.Collectors;
import org.testng.annotations.Test;

public class ForgejoModuleTest {

  @Test
  public void shouldBindAuthenticatorProviders() {
    Set<Class<?>> providers =
        Elements.getElements(new ForgejoModule()).stream()
            .filter(e -> e instanceof ProviderKeyBinding)
            .map(e -> ((ProviderKeyBinding<?>) e).getProviderKey().getTypeLiteral().getRawType())
            .collect(Collectors.toSet());

    assertEquals(
        providers,
        Set.of(
            ForgejoOAuthAuthenticatorProvider.class,
            ForgejoOAuthAuthenticatorProviderSecond.class));
  }
}
