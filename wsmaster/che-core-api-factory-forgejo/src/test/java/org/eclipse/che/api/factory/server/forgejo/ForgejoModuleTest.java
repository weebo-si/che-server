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

import com.google.inject.spi.Element;
import com.google.inject.spi.Elements;
import com.google.inject.spi.LinkedKeyBinding;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.testng.annotations.Test;

public class ForgejoModuleTest {

  @Test
  public void shouldBindTokenAndUserDataFetchers() {
    List<Element> elements = Elements.getElements(new ForgejoModule());

    Set<Class<?>> boundTargets =
        elements.stream()
            .filter(e -> e instanceof LinkedKeyBinding)
            .map(e -> ((LinkedKeyBinding<?>) e).getLinkedKey().getTypeLiteral().getRawType())
            // ignore the multibinder internal bindings
            .filter(type -> type.getPackage() == ForgejoModule.class.getPackage())
            .collect(Collectors.toSet());

    assertEquals(
        boundTargets,
        Set.of(
            ForgejoOAuthTokenFetcher.class,
            ForgejoOAuthTokenFetcherSecond.class,
            ForgejoUserDataFetcher.class,
            ForgejoUserDataFetcherSecond.class));
  }
}
