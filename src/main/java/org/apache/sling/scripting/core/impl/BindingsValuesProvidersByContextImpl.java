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

import javax.script.ScriptEngineFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import org.apache.sling.scripting.api.BindingsValuesProvider;
import org.apache.sling.scripting.api.BindingsValuesProvidersByContext;
import org.apache.sling.scripting.core.impl.jsr223.SlingScriptEngineManager;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.util.converter.Converters;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.sling.scripting.api.BindingsValuesProvider.CONTEXT;
import static org.apache.sling.scripting.api.BindingsValuesProvider.DEFAULT_CONTEXT;

/** Our default {@link BindingsValuesProvidersByContext} implementation */
@Component(
        service = BindingsValuesProvidersByContext.class,
        property = {Constants.SERVICE_VENDOR + "=The Apache Software Foundation"})
public class BindingsValuesProvidersByContextImpl implements BindingsValuesProvidersByContext {

    private final Map<String, ContextBvpCollector> customizers = new HashMap<>();
    public static final String[] DEFAULT_CONTEXT_ARRAY = new String[] {DEFAULT_CONTEXT};

    private static final String TOPIC_CREATED = "org/apache/sling/scripting/core/BindingsValuesProvider/CREATED";
    private static final String TOPIC_MODIFIED = "org/apache/sling/scripting/core/BindingsValuesProvider/MODIFIED";
    private static final String TOPIC_REMOVED = "org/apache/sling/scripting/core/BindingsValuesProvider/REMOVED";

    private ServiceTracker<BindingsValuesProvider, Object> bvpTracker;

    @SuppressWarnings("rawtypes")
    private ServiceTracker<Map, Object> mapsTracker;

    private BundleContext bundleContext;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final List<ServiceReference<?>> pendingRefs = new ArrayList<>();

    @Reference
    private SlingScriptEngineManager scriptEngineManager;

    @Reference(
            policy = ReferencePolicy.DYNAMIC,
            policyOption = ReferencePolicyOption.GREEDY,
            cardinality = ReferenceCardinality.OPTIONAL)
    private volatile EventAdmin eventAdmin;

    private abstract class ContextLoop {
        private String[] getContexts(ServiceReference<?> reference) {
            return Converters.standardConverter()
                    .convert(reference.getProperty(CONTEXT))
                    .defaultValue(DEFAULT_CONTEXT_ARRAY)
                    .to(String[].class);
        }

        Object apply(ServiceReference<?> ref) {
            final Object service = bundleContext.getService(ref);
            if (service != null) {
                for (String context : getContexts(ref)) {
                    ContextBvpCollector c = customizers.get(context);
                    if (c == null) {
                        synchronized (BindingsValuesProvidersByContextImpl.this) {
                            c = new ContextBvpCollector(bundleContext);
                            customizers.put(context, c);
                        }
                    }
                    applyInContext(c);
                }
            }
            return service;
        }

        protected abstract void applyInContext(ContextBvpCollector c);
    }

    @Activate
    public void activate(ComponentContext ctx) {
        bundleContext = ctx.getBundleContext();

        synchronized (pendingRefs) {
            for (ServiceReference<?> ref : pendingRefs) {
                addingService(ref);
            }
            pendingRefs.clear();
        }

        bvpTracker = new ServiceTracker<>(
                bundleContext, BindingsValuesProvider.class, new ProvidersServiceTrackerCustomizer<>());
        bvpTracker.open();

        // Map services can also be registered to provide bindings
        mapsTracker = new ServiceTracker<>(bundleContext, Map.class, new ProvidersServiceTrackerCustomizer<>());
        mapsTracker.open();
    }

    @Deactivate
    public void deactivate(ComponentContext ctx) {
        bvpTracker.close();
        mapsTracker.close();
        bundleContext = null;
    }

    @Override
    public Collection<BindingsValuesProvider> getBindingsValuesProviders(
            ScriptEngineFactory scriptEngineFactory, String context) {
        final List<BindingsValuesProvider> results = new ArrayList<>();
        if (context == null) {
            context = DEFAULT_CONTEXT;
        }
        final ContextBvpCollector bvpc = customizers.get(context);
        if (bvpc == null) {
            logger.debug("no BindingsValuesProviderCustomizer available for context '{}'", context);
            return results;
        }

        results.addAll(bvpc.getGenericBindingsValuesProviders().values());
        logger.debug(
                "Generic BindingsValuesProviders added for engine {}: {}", scriptEngineFactory.getNames(), results);

        // we load the compatible language ones first so that the most specific
        // overrides these
        final Map<String, Object> factoryProperties = scriptEngineManager.getServiceProperties(scriptEngineFactory);
        if (factoryProperties != null) {
            String[] compatibleLangs = Converters.standardConverter()
                    .convert(factoryProperties.get("compatible.javax.script.name"))
                    .to(String[].class);
            for (final String name : compatibleLangs) {
                final Map<ServiceReference<?>, BindingsValuesProvider> langProviders =
                        bvpc.getLangBindingsValuesProviders().get(name);
                if (langProviders != null) {
                    results.addAll(langProviders.values());
                }
            }
            logger.debug(
                    "Compatible BindingsValuesProviders added for engine {}: {}",
                    scriptEngineFactory.getNames(),
                    results);
        }

        for (final String name : scriptEngineFactory.getNames()) {
            final Map<ServiceReference<?>, BindingsValuesProvider> langProviders =
                    bvpc.getLangBindingsValuesProviders().get(name);
            if (langProviders != null) {
                results.addAll(langProviders.values());
            }
        }
        logger.debug("All BindingsValuesProviders added for engine {}: {}", scriptEngineFactory.getNames(), results);

        return results;
    }

    private Event newEvent(final String topic, final ServiceReference<?> reference) {
        Dictionary<String, Object> props = new Hashtable<>(); // NOSONAR
        props.put("service.id", reference.getProperty(Constants.SERVICE_ID));
        return new Event(topic, props);
    }

    private Object addingService(final ServiceReference<?> reference) {
        if (bundleContext == null) {
            synchronized (pendingRefs) {
                pendingRefs.add(reference);
            }
            return null;
        }
        return new ContextLoop() {
            @Override
            protected void applyInContext(ContextBvpCollector c) {
                c.addingService(reference);
                if (eventAdmin != null) {
                    eventAdmin.postEvent(newEvent(TOPIC_CREATED, reference));
                }
            }
        }.apply(reference);
    }

    private class ProvidersServiceTrackerCustomizer<S, T> implements ServiceTrackerCustomizer<S, T> {

        @SuppressWarnings("unchecked")
        @Override
        public T addingService(ServiceReference<S> reference) {
            return (T) BindingsValuesProvidersByContextImpl.this.addingService(reference);
        }

        @Override
        public void modifiedService(ServiceReference<S> reference, T service) {
            new ContextLoop() {
                @Override
                protected void applyInContext(ContextBvpCollector c) {
                    c.modifiedService(reference);
                    if (eventAdmin != null) {
                        eventAdmin.postEvent(newEvent(TOPIC_MODIFIED, reference));
                    }
                }
            }.apply(reference);
        }

        @Override
        public void removedService(ServiceReference<S> reference, T service) {
            if (bundleContext == null) {
                synchronized (pendingRefs) {
                    pendingRefs.remove(reference);
                }
                return;
            }
            new ContextLoop() {
                @Override
                protected void applyInContext(ContextBvpCollector c) {
                    c.removedService(reference);
                    if (eventAdmin != null) {
                        eventAdmin.postEvent(newEvent(TOPIC_REMOVED, reference));
                    }
                }
            }.apply(reference);
        }
    }
}
