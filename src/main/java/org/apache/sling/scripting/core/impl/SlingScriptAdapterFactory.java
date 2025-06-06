/*
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
 */
package org.apache.sling.scripting.core.impl;

import javax.script.ScriptEngine;

import java.util.Collection;
import java.util.List;

import org.apache.sling.api.adapter.AdapterFactory;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.wrappers.JakartaToJavaxServletWrapper;
import org.apache.sling.commons.mime.MimeTypeProvider;
import org.apache.sling.scripting.api.BindingsValuesProvider;
import org.apache.sling.scripting.api.BindingsValuesProvidersByContext;
import org.apache.sling.scripting.api.ScriptCache;
import org.apache.sling.scripting.core.impl.jsr223.SlingScriptEngineManager;
import org.jetbrains.annotations.NotNull;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

/**
 * AdapterFactory that adapts Resources to the DefaultSlingScript servlet, which
 * executes the Resources as scripts.
 */
@Component(
        service = {AdapterFactory.class, MimeTypeProvider.class},
        property = {
            Constants.SERVICE_VENDOR + "=The Apache Software Foundation",
            Constants.SERVICE_DESCRIPTION + "=Default SlingScriptResolver",
            "adaptables=org.apache.sling.api.resource.Resource",
            "adapters=org.apache.sling.api.scripting.SlingJakartaScript",
            "adapters=jakarta.servlet.Servlet",
            "adapters=org.apache.sling.api.scripting.SlingScript",
            "adapters=javax.servlet.Servlet",
            "adapter.condition=If the resource's path ends in an extension registered by a script engine."
        })
public class SlingScriptAdapterFactory implements AdapterFactory, MimeTypeProvider {

    private BundleContext bundleContext;

    /** The context string to use to select BindingsValuesProviders */
    public static final String BINDINGS_CONTEXT = BindingsValuesProvider.DEFAULT_CONTEXT;

    /**
     * The service cache for script execution.
     */
    private ServiceCache serviceCache;

    /**
     * The script engine manager.
     */
    @Reference
    private SlingScriptEngineManager scriptEngineManager;

    @Reference
    private volatile SlingScriptEnginePicker scriptEnginePicker;

    /**
     * The BindingsValuesProviderTracker
     */
    @Reference
    private BindingsValuesProvidersByContext bindingsValuesProviderTracker;

    @Reference
    private ScriptCache scriptCache;

    // ---------- AdapterFactory -----------------------------------------------

    @Override
    @SuppressWarnings("unchecked")
    public <A> A getAdapter(@NotNull final Object adaptable, @NotNull final Class<A> type) {

        final Resource resource = (Resource) adaptable;
        final String path = resource.getPath();
        final String extension = path.substring(path.lastIndexOf('.') + 1);

        final List<ScriptEngine> engines = scriptEngineManager.getEnginesByExtension(extension);
        final ScriptEngine engine;
        if (engines.isEmpty()) {
            return null;
        } else if (engines.size() == 1) {
            engine = engines.get(0);
        } else {
            engine = scriptEnginePicker.pickScriptEngine(engines, resource, extension);
        }
        if (engine != null) {
            final Collection<BindingsValuesProvider> bindingsValuesProviders =
                    bindingsValuesProviderTracker.getBindingsValuesProviders(engine.getFactory(), BINDINGS_CONTEXT);
            // unchecked cast
            final DefaultSlingScript script = new DefaultSlingScript(
                    this.bundleContext, resource, engine, bindingsValuesProviders, this.serviceCache, scriptCache);
            if (type == javax.servlet.Servlet.class) {
                return (A) JakartaToJavaxServletWrapper.toJavaxServlet(script);
            }
            return (A) script;
        }

        return null;
    }

    // ---------- MimeTypeProvider

    /**
     * Returns the first MIME type entry of the supported MIME types of a
     * ScriptEngineFactory which is registered for the extension of the given
     * name. If no ScriptEngineFactory is registered for the given extension or
     * the registered ScriptEngineFactory is not registered for a MIME type,
     * this method returns <code>null</code>.
     *
     * @param name The name whose extension is to be mapped to a MIME type. The
     *            extension is the string after the last dot in the name. If the
     *            name contains no dot, the entire name is considered the
     *            extension.
     */
    @Override
    public String getMimeType(String name) {
        name = name.substring(name.lastIndexOf('.') + 1);
        ScriptEngine se = scriptEngineManager.getEngineByExtension(name);
        if (se != null) {
            List<?> mimeTypes = se.getFactory().getMimeTypes();
            if (mimeTypes != null && !mimeTypes.isEmpty()) {
                return String.valueOf(mimeTypes.get(0));
            }
        }

        return null;
    }

    /**
     * Returns the first extension entry of the supported extensions of a
     * ScriptEngineFactory which is registered for the given MIME type. If no
     * ScriptEngineFactory is registered for the given MIME type or the
     * registered ScriptEngineFactory is not registered for an extensions, this
     * method returns <code>null</code>.
     *
     * @param mimeType The MIME type to be mapped to an extension.
     */
    @Override
    public String getExtension(String mimeType) {
        ScriptEngine se = scriptEngineManager.getEngineByMimeType(mimeType);
        if (se != null) {
            List<?> extensions = se.getFactory().getExtensions();
            if (extensions != null && !extensions.isEmpty()) {
                return String.valueOf(extensions.get(0));
            }
        }

        return null;
    }

    // ---------- SCR integration ----------------------------------------------

    @Activate
    protected void activate(BundleContext bc) {
        bundleContext = bc;
        this.serviceCache = new ServiceCache(this.bundleContext);
    }

    @Deactivate
    protected void deactivate() {
        this.serviceCache.dispose();
        this.serviceCache = null;
        this.bundleContext = null;
    }
}
