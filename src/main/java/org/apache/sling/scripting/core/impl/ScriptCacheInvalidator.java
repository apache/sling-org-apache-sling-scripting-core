/*******************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 ******************************************************************************/

package org.apache.sling.scripting.core.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.script.Compilable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;

import org.apache.sling.api.resource.observation.ExternalResourceChangeListener;
import org.apache.sling.api.resource.observation.ResourceChange;
import org.apache.sling.api.resource.observation.ResourceChangeListener;
import org.apache.sling.scripting.api.ScriptCache;
import org.apache.sling.scripting.core.impl.jsr223.SlingScriptEngineManager;
import org.jetbrains.annotations.NotNull;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(
    immediate = true, // event handler should be immediate
    service = {EventHandler.class},
    property = {
        EventConstants.EVENT_TOPIC + "=org/apache/sling/scripting/core/impl/jsr223/SlingScriptEngineManager/*"
    },
    configurationPid = "org.apache.sling.scripting.core.impl.ScriptCacheImpl"
)
public class ScriptCacheInvalidator implements ResourceChangeListener, ExternalResourceChangeListener, EventHandler {

    private final Logger logger = LoggerFactory.getLogger(ScriptCacheInvalidator.class);

    private final BundleContext bundleContext;
    private final Set<String> extensions = new TreeSet<>();
    private final String[] additionalExtensions;

    private volatile ServiceRegistration<ResourceChangeListener> resourceChangeListener;

    private final SlingScriptEngineManager slingScriptEngineManager;

    private final ExecutorService threadPool;

    private final ScriptCache scriptCache;

    @Activate
    public ScriptCacheInvalidator(@Reference final SlingScriptEngineManager slingScriptEngineManager,
        @Reference final ScriptCache scriptCache,
        final ScriptCacheImplConfiguration configuration, 
        final BundleContext bundleCtx) {

        this.slingScriptEngineManager = slingScriptEngineManager;
        this.scriptCache = scriptCache;
        this.threadPool = Executors.newSingleThreadExecutor();
        this.bundleContext = bundleCtx;
        this.additionalExtensions = configuration.org_apache_sling_scripting_cache_additional__extensions();
        this.handleEvent(null);
    }

    @Deactivate
    protected void deactivate() {
        if (resourceChangeListener != null) {
            resourceChangeListener.unregister();
            resourceChangeListener = null;
        }
        threadPool.shutdown();
        try {
            threadPool.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            logger.warn("Unable to shutdown script cache thread in time");
        }
    }

    @Override
    public void onChange(@NotNull List<ResourceChange> list) {
        for (final ResourceChange change : list) {
            Runnable eventTask = () -> {
                final String path = change.getPath();
                if (!this.scriptCache.removeScript(path)) {
                    this.scriptCache.removeScript(path.concat("/"));
                }
            };
            threadPool.execute(eventTask);
        }
    }

    private void configureListener() {
        this.scriptCache.clear();
        if (extensions.isEmpty()) {
            if (resourceChangeListener != null) {
                resourceChangeListener.unregister();
                resourceChangeListener = null;
            }
        } else {
            final List<String> globPatterns = new ArrayList<>(extensions.size());
            for (final String extension : extensions) {
                globPatterns.add("glob:**/*.".concat(extension));
            }
            final String[] paths = globPatterns.toArray(new String[globPatterns.size()]);
            if (resourceChangeListener != null) {
                final Dictionary<String, Object> resourceChangeListenerProperties = resourceChangeListener.getReference().getProperties();
                if ( !Arrays.equals(paths, (String[])resourceChangeListenerProperties.get(ResourceChangeListener.PATHS))) {
                    resourceChangeListenerProperties.put(ResourceChangeListener.PATHS, paths);
                    resourceChangeListener.setProperties(resourceChangeListenerProperties);
                }
            } else {
                final Dictionary<String, Object> resourceChangeListenerProperties = new Hashtable<>();
                resourceChangeListenerProperties.put(ResourceChangeListener.PATHS, paths);
                resourceChangeListenerProperties.put(ResourceChangeListener.CHANGES,
                    new String[]{ResourceChange.ChangeType.CHANGED.name(), ResourceChange.ChangeType.REMOVED.name()});
                resourceChangeListener =
                    bundleContext.registerService(
                            ResourceChangeListener.class,
                            this,
                            resourceChangeListenerProperties
                    );
            }
        }
    }

    private void initializeExtensions() {
        this.extensions.clear();
        for (final ScriptEngineFactory factory : this.slingScriptEngineManager.getEngineFactories()) {
            final ScriptEngine scriptEngine = factory.getScriptEngine();
            if (scriptEngine instanceof Compilable) {
                extensions.addAll(factory.getExtensions());
            }
        }
        if (this.additionalExtensions != null) {
            extensions.addAll(Arrays.asList(this.additionalExtensions));
        }
    }

    @Override
    public void handleEvent(final Event event) {
        synchronized ( this.extensions ) {
            this.initializeExtensions();
            this.configureListener();
        }
    }
}
