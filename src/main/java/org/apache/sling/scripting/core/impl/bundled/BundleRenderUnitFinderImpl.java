/*******************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 ******************************************************************************/
package org.apache.sling.scripting.core.impl.bundled;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.type.ResourceType;
import org.apache.sling.commons.compiler.source.JavaEscapeHelper;
import org.apache.sling.scripting.spi.bundle.BundledRenderUnit;
import org.apache.sling.scripting.spi.bundle.BundledRenderUnitCapability;
import org.apache.sling.scripting.spi.bundle.BundledRenderUnitFinder;
import org.apache.sling.scripting.spi.bundle.TypeProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component
public class BundleRenderUnitFinderImpl implements BundledRenderUnitFinder {
    @Reference
    ScriptContextProvider scriptContextProvider;

    private static final String NS_JAVAX_SCRIPT_CAPABILITY = "javax.script";
    private static final String SLASH = "/";
    private static final String DOT = ".";

    @Override
    @Nullable
    public BundledRenderUnit findUnit(@NotNull BundleContext context, @NotNull Set<TypeProvider> providers, @NotNull Set<TypeProvider> allProviders) {
        for (TypeProvider provider : providers) {
            BundledRenderUnitCapability capability = provider.getBundledRenderUnitCapability();
            for (String match : buildScriptMatches(capability.getResourceTypes(),
                capability.getSelectors().toArray(new String[0]), capability.getMethod(), capability.getExtension())) {
                String scriptExtension = capability.getScriptExtension();
                String scriptEngineName = capability.getScriptEngineName();
                if (StringUtils.isNotEmpty(scriptExtension) && StringUtils.isNotEmpty(scriptEngineName)) {
                    BundledRenderUnit executable = getExecutable(context, provider.getBundle(), match, scriptEngineName, scriptExtension, allProviders);
                    if (executable != null) {
                        return executable;
                    }
                }
            }
        }
        return null;
    }

    @Override
    @Nullable
    public BundledRenderUnit findUnit(@NotNull BundleContext context, @NotNull TypeProvider provider, @NotNull Set<TypeProvider> providers) {
        BundledRenderUnitCapability capability = provider.getBundledRenderUnitCapability();
        String path = capability.getPath();
        String scriptEngineName = capability.getScriptEngineName();
        String scriptExtension = capability.getScriptExtension();
        if (StringUtils.isNotEmpty(path) && StringUtils.isNotEmpty(scriptEngineName) && StringUtils.isNotEmpty(scriptExtension)) {
            return findUnit(context, provider.getBundle(), path, scriptEngineName, scriptExtension, providers);
        }
        return null;
    }

    @Nullable
    private BundledRenderUnit getExecutable(@NotNull BundleContext context, @NotNull Bundle bundle, @NotNull String match, @NotNull String scriptEngineName,
        @NotNull String scriptExtension, @NotNull Set<TypeProvider> providers) {
        String path = match + DOT + scriptExtension;
        return findUnit(context, bundle, path, scriptEngineName, scriptExtension, providers);
    }

    @Nullable
    private BundledRenderUnit findUnit(@NotNull BundleContext context, @NotNull Bundle bundle, @NotNull String path, String scriptEngineName,
                                       @NotNull String scriptExtension, @NotNull Set<TypeProvider> providers) {
        String className = JavaEscapeHelper.makeJavaPackage(path);
        try {
            Class<?> clazz = bundle.loadClass(className);
            return new PrecompiledScript(providers, context, bundle, path, clazz, scriptEngineName, scriptExtension, scriptContextProvider);
        } catch (ClassNotFoundException ignored) {
            URL bundledScriptURL = bundle.getEntry(NS_JAVAX_SCRIPT_CAPABILITY + (path.startsWith("/") ? "" : SLASH) + path);
            if (bundledScriptURL != null) {
                return new Script(providers, context, bundle, path, bundledScriptURL, scriptEngineName, scriptExtension, scriptContextProvider);
            }
        }
        return null;
    }

    @NotNull
    private List<String> buildScriptMatches(@NotNull Set<ResourceType> resourceTypes, @NotNull String[] selectors, @Nullable String method,
                                            @Nullable String extension) {
        List<String> matches = new ArrayList<>();
        for (ResourceType resourceType : resourceTypes) {
            if (selectors.length > 0) {
                for (int i = selectors.length - 1; i >= 0; i--) {
                    String base =
                        resourceType.getType() +
                            (resourceType.getVersion() != null ? SLASH + resourceType.getVersion() + SLASH :
                                SLASH) +
                            String.join(SLASH, Arrays.copyOf(selectors, i + 1));
                    if (StringUtils.isNotEmpty(extension)) {
                        if (StringUtils.isNotEmpty(method)) {
                            matches.add(base + DOT + extension + DOT + method);
                        }
                        matches.add(base + DOT + extension);
                    }
                    if (StringUtils.isNotEmpty(method)) {
                        matches.add(base + DOT + method);
                        matches.add(base + SLASH + method);
                    }
                    matches.add(base);
                }
            }
            String base = resourceType.getType() +
                (resourceType.getVersion() != null ? SLASH + resourceType.getVersion() : StringUtils.EMPTY);

            if (StringUtils.isNotEmpty(extension)) {
                if (StringUtils.isNotEmpty(method)) {
                    matches.add(base + SLASH + resourceType.getResourceLabel() + DOT + extension + DOT + method);
                }
                matches.add(base + SLASH + resourceType.getResourceLabel() + DOT + extension);
            }
            if (StringUtils.isNotEmpty(method)) {
                matches.add(base + SLASH + method);
                matches.add(base + SLASH + resourceType.getResourceLabel() + DOT + method);
            }
            if (StringUtils.isNotEmpty(extension)) {
                matches.add(base + SLASH + extension);
            }
            matches.add(base + SLASH + resourceType.getResourceLabel());
        }
        return Collections.unmodifiableList(matches);
    }
}
