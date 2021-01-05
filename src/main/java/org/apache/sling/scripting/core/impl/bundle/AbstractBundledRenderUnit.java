/*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 ~ Licensed to the Apache Software Foundation (ASF) under one
 ~ or more contributor license agreements.  See the NOTICE file
 ~ distributed with this work for additional information
 ~ regarding copyright ownership.  The ASF licenses this file
 ~ to you under the Apache License, Version 2.0 (the
 ~ "License"); you may not use this file except in compliance
 ~ with the License.  You may obtain a copy of the License at
 ~
 ~   http://www.apache.org/licenses/LICENSE-2.0
 ~
 ~ Unless required by applicable law or agreed to in writing,
 ~ software distributed under the License is distributed on an
 ~ "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 ~ KIND, either express or implied.  See the License for the
 ~ specific language governing permissions and limitations
 ~ under the License.
 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/
package org.apache.sling.scripting.core.impl.bundle;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.script.ScriptException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.scripting.api.bundle.TypeProvider;
import org.apache.sling.scripting.core.impl.helper.OnDemandReaderRequest;
import org.apache.sling.scripting.core.impl.helper.OnDemandWriterResponse;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
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
    private List<ServiceReference<?>> references;
    private Map<String, Object> services;


    AbstractBundledRenderUnit(@NotNull Set<TypeProvider> providers, @NotNull BundleContext context, @NotNull Bundle bundle, @NotNull String path,
                              @NotNull String scriptEngineName, @NotNull String scriptExtension, @NotNull ScriptContextProvider scriptContextProvider) {
        this.providers = providers;
        this.bundle = bundle;
        this.path = path;
        this.scriptEngineName = scriptEngineName;
        this.scriptExtension = scriptExtension;
        this.scriptContextProvider = scriptContextProvider;
        this.bundleContext = context;
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
    @Nullable
    @SuppressWarnings("unchecked")
    public <T> T getService(@NotNull String className) {
        LOG.debug("Attempting to load class {} as an OSGi service.", className);
        T result = (this.services == null ? null : (T) this.services.get(className));
        if (result == null) {
            final ServiceReference<?> ref = this.bundleContext.getServiceReference(className);
            if (ref != null) {
                result = (T) this.bundleContext.getService(ref);
                if (result != null) {
                    if (this.services == null) {
                        this.services = new HashMap<>();
                    }
                    if (this.references == null) {
                        this.references = new ArrayList<>();
                    }
                    this.references.add(ref);
                    this.services.put(className, result);
                    return result;
                }
            }
        }
        return null;
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public <T> T[] getServices(@NotNull String className, @Nullable String filter) {
        T[] result = null;
        try {
            final ServiceReference<?>[] refs = this.bundleContext.getServiceReferences(className, filter);

            if (refs != null) {
                // sort by service ranking (lowest first) (see ServiceReference#compareTo(Object))
                List<ServiceReference<?>> localReferences = Arrays.asList(refs);
                Collections.sort(localReferences);
                // get the highest ranking first
                Collections.reverse(localReferences);

                final List<T> objects = new ArrayList<>();
                for (ServiceReference<?> reference : localReferences) {
                    final T service = (T) this.bundleContext.getService(reference);
                    if (service != null) {
                        if (this.references == null) {
                            this.references = new ArrayList<>();
                        }
                        this.references.add(reference);
                        objects.add(service);
                    }
                }
                if (!objects.isEmpty()) {
                    T[] srv = (T[]) Array.newInstance(bundle.loadClass(className), objects.size());
                    result = objects.toArray(srv);
                }
            }
        } catch (Exception e) {
            LOG.error(String.format("Unable to retrieve the services of type %s.", className), e);
        }
        return result;
    }

    @Override
    public void releaseDependencies() {
        if (references != null) {
            for (ServiceReference<?> reference : this.references) {
                bundleContext.ungetService(reference);
            }
            references.clear();
        }
        if (services != null) {
            services.clear();
        }
    }


    @Override
    public void eval(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response) throws ScriptException {
        try {
            ScriptContextProvider.ExecutableContext executableContext = scriptContextProvider
                .prepareScriptContext(new OnDemandReaderRequest((SlingHttpServletRequest) request),
                    new OnDemandWriterResponse((SlingHttpServletResponse) response), this);
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
