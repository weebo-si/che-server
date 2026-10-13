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
package org.eclipse.che.api.deploy;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import com.google.inject.spi.Elements;
import com.google.inject.spi.LinkedKeyBinding;
import java.util.List;
import java.util.stream.Collectors;
import org.eclipse.che.api.factory.server.FactoryParametersResolver;
import org.eclipse.che.api.factory.server.ScmFileResolver;
import org.eclipse.che.api.factory.server.forgejo.ForgejoFactoryParametersResolver;
import org.eclipse.che.api.factory.server.forgejo.ForgejoFactoryParametersResolverSecond;
import org.eclipse.che.api.factory.server.forgejo.ForgejoScmFileResolver;
import org.eclipse.che.api.factory.server.forgejo.ForgejoScmFileResolverSecond;
import org.eclipse.che.api.factory.server.github.GithubFactoryParametersResolver;
import org.eclipse.che.api.factory.server.github.GithubScmFileResolver;
import org.testng.annotations.Test;

/**
 * The GitHub resolvers also accept Forgejo servers (Gitea-compatible API detection), and on equal
 * priority the first bound resolver wins: the Forgejo resolvers must be bound first.
 */
public class WsMasterModuleResolversOrderTest {

  private static List<Class<?>> boundResolvers(Class<?> resolverType) {
    return Elements.getElements(new WsMasterModule()).stream()
        .filter(e -> e instanceof LinkedKeyBinding)
        .map(e -> (LinkedKeyBinding<?>) e)
        .filter(b -> b.getKey().getTypeLiteral().getRawType() == resolverType)
        .map(b -> b.getLinkedKey().getTypeLiteral().getRawType())
        .collect(Collectors.toList());
  }

  @Test
  public void shouldBindForgejoFactoryParametersResolversFirst() {
    List<Class<?>> resolvers = boundResolvers(FactoryParametersResolver.class);

    assertEquals(
        resolvers.subList(0, 2),
        List.of(
            ForgejoFactoryParametersResolver.class, ForgejoFactoryParametersResolverSecond.class));
    assertTrue(resolvers.contains(GithubFactoryParametersResolver.class));
  }

  @Test
  public void shouldBindForgejoScmFileResolversFirst() {
    List<Class<?>> resolvers = boundResolvers(ScmFileResolver.class);

    assertEquals(
        resolvers.subList(0, 2),
        List.of(ForgejoScmFileResolver.class, ForgejoScmFileResolverSecond.class));
    assertTrue(resolvers.contains(GithubScmFileResolver.class));
  }
}
