/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sling.scripting.core.impl.bundled;

import java.io.IOException;
import java.util.Set;

import javax.script.ScriptException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.scripting.core.impl.ServiceCache;
import org.apache.sling.scripting.spi.bundle.TypeProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.wiring.BundleWiring;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class AbstractBundledRenderUnit implements ExecutableUnit {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractBundledRenderUnit.class.getName());

    private final Set<TypeProvider> providers;
    private final Bundle bundle;
    private final BundleContext bundleContext;
    private final String path;
    private final String scriptEngineName;
    private final String scriptExtension;
    private final ScriptContextProvider scriptContextProvider;
    private final ServiceCache serviceCache;

    AbstractBundledRenderUnit(@NotNull Set<TypeProvider> providers, @NotNull BundleContext context, @NotNull Bundle bundle, @NotNull String path,
                              @NotNull String scriptEngineName, @NotNull String scriptExtension,
                              @NotNull ScriptContextProvider scriptContextProvider, @NotNull ServiceCache serviceCache) {
        this.providers = providers;
        this.bundle = bundle;
        this.path = path;
        this.scriptEngineName = scriptEngineName;
        this.scriptExtension = scriptExtension;
        this.scriptContextProvider = scriptContextProvider;
        this.bundleContext = context;
        this.serviceCache = serviceCache;
    }

    @Override
    @NotNull
    public Bundle getBundle() {
        return bundle;
    }

    @Override
    @NotNull
    public BundleContext getBundleContext() {
        return bundleContext;
    }

    @Override
    public @NotNull Set<TypeProvider> getTypeProviders() {
        return providers;
    }

    @Override
    public @NotNull String getPath() {
        return path;
    }

    @Override
    public @NotNull String getScriptEngineName() {
        return scriptEngineName;
    }

    @Override
    public @NotNull String getScriptExtension() {
        return scriptExtension;
    }

    @Override
    public @NotNull ServiceCache getServiceCache() {
        return serviceCache;
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public <T> T getService(@NotNull String className) {
        try {
            ClassLoader bundleClassloader = getBundle().adapt(BundleWiring.class).getClassLoader();
            return (T) serviceCache.getService(bundleClassloader.loadClass(className));
        } catch (ClassNotFoundException e) {
            LOG.error("Unable to retrieve a service of type " + className + " for bundled script " + path, e);
        }
        return null;
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public <T> T[] getServices(@NotNull String className, @Nullable String filter) {
        try {
            ClassLoader bundleClassloader = getBundle().adapt(BundleWiring.class).getClassLoader();
            return (T[]) serviceCache.getServices(bundleClassloader.loadClass(className), filter);
        } catch (ClassNotFoundException e) {
            LOG.error("Unable to retrieve a service of type " + className + " for bundled script " + path, e);
        }
        return null;
    }

    @Override
    public void eval(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response) throws ScriptException {
        try {
            ScriptContextProvider.ExecutableContext executableContext = scriptContextProvider
                .prepareScriptContext((SlingHttpServletRequest) request, (SlingHttpServletResponse) response, this);
            try {
                executableContext.eval();
            }
            finally {
                executableContext.clean();
            }
        } catch (IOException ex) {
            throw new ScriptException(ex);
        }
    }
}
