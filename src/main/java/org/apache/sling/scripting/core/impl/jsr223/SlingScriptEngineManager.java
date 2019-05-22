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
package org.apache.sling.scripting.core.impl.jsr223;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;

import org.apache.sling.api.scripting.SlingScriptConstants;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleListener;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.wiring.BundleWiring;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(
        service = {ScriptEngineManager.class, SlingScriptEngineManager.class },
        reference = @Reference(
                name = "ScriptEngineFactory",
                bind = "bindScriptEngineFactory",
                unbind = "unbindScriptEngineFactory",
                service = ScriptEngineFactory.class,
                cardinality = ReferenceCardinality.MULTIPLE,
                policy = ReferencePolicy.DYNAMIC
        )
)
public class SlingScriptEngineManager extends ScriptEngineManager implements BundleListener {

    public static final String EVENT_TOPIC_SCRIPT_MANAGER_UPDATED =
            "org/apache/sling/scripting/core/impl/jsr223/SlingScriptEngineManager/UPDATED";
    public static final String ENGINE_FACTORY_SERVICE = "META-INF/services/" + ScriptEngineFactory.class.getName();

    private static final Logger LOG = LoggerFactory.getLogger(SlingScriptEngineManager.class);

    private final Set<Bundle> engineSpiBundles = new HashSet<>();
    private final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
    private final Map<ScriptEngineFactory, Map<String, Object>> factoriesProperties = new HashMap<>();
    private final Set<ServiceReference<ScriptEngineFactory>> serviceReferences = new HashSet<>();

    private ScriptEngineManager internalManager;
    private SortedSet<SortableScriptEngineFactory> factories = new TreeSet<>();
    private ComponentContext componentContext;

    @Reference(policy = ReferencePolicy.DYNAMIC,
               policyOption = ReferencePolicyOption.GREEDY,
               cardinality = ReferenceCardinality.OPTIONAL)
    private volatile EventAdmin eventAdmin;

    @Override
    public ScriptEngine getEngineByName(String shortName) {
        readWriteLock.readLock().lock();
        try {
            return internalManager.getEngineByName(shortName);
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    @Override
    public ScriptEngine getEngineByExtension(String extension) {
        readWriteLock.readLock().lock();
        try {
            return internalManager.getEngineByExtension(extension);
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    @Override
    public ScriptEngine getEngineByMimeType(String mimeType) {
        readWriteLock.readLock().lock();
        try {
            return internalManager.getEngineByMimeType(mimeType);
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    @Override
    public List<ScriptEngineFactory> getEngineFactories() {
        readWriteLock.readLock().lock();
        try {
            ArrayList<ScriptEngineFactory> list = new ArrayList<>(factories.size());
            list.addAll(factories);
            return Collections.unmodifiableList(list);
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    @Override
    public void registerEngineName(String name, ScriptEngineFactory factory) {
        readWriteLock.writeLock().lock();
        try {
            internalManager.registerEngineName(name, factory);
        } finally {
            readWriteLock.writeLock().unlock();
        }
    }

    @Override
    public void registerEngineMimeType(String type, ScriptEngineFactory factory) {
        readWriteLock.writeLock().lock();
        try {
            internalManager.registerEngineMimeType(type, factory);
        } finally {
            readWriteLock.writeLock().unlock();
        }
    }

    @Override
    public void registerEngineExtension(String extension, ScriptEngineFactory factory) {
        readWriteLock.writeLock().lock();
        try {
            internalManager.registerEngineExtension(extension, factory);
        } finally {
            readWriteLock.writeLock().unlock();
        }
    }

    @Override
    public void bundleChanged(BundleEvent event) {
        if (event.getType() == BundleEvent.STARTED
                && event.getBundle().getBundleId() > 0
                && event.getBundle().getEntry(ENGINE_FACTORY_SERVICE) != null) {
            synchronized (this.engineSpiBundles) {
                this.engineSpiBundles.add(event.getBundle());
            }
            updateFactories();
        } else if (event.getType() == BundleEvent.STOPPED) {
            boolean refresh;
            synchronized (this.engineSpiBundles) {
                refresh = this.engineSpiBundles.remove(event.getBundle());
            }
            if (refresh) {
                updateFactories();
            }
        }
    }

    public Map<String, Object> getProperties(ScriptEngineFactory factory) {
        readWriteLock.readLock().lock();
        try {
            return factoriesProperties.get(factory);
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    @Activate
    private void activate(ComponentContext componentContext) {
        this.componentContext = componentContext;
        componentContext.getBundleContext().addBundleListener(this);
        updateFactories();
    }

    @Deactivate
    private void deactivate(ComponentContext componentContext) {
        componentContext.getBundleContext().removeBundleListener(this);
    }

    private void bindScriptEngineFactory(final ServiceReference<ScriptEngineFactory> serviceReference,
                                           final ScriptEngineFactory factory) {
        synchronized (this.serviceReferences) {
            serviceReferences.add(serviceReference);
        }
        updateFactories();
        postEvent(SlingScriptConstants.TOPIC_SCRIPT_ENGINE_FACTORY_ADDED, factory);
    }

    private void unbindScriptEngineFactory(final ServiceReference<ScriptEngineFactory> serviceReference, final ScriptEngineFactory
            factory) {
        synchronized (this.serviceReferences) {
            serviceReferences.remove(serviceReference);
            if (componentContext != null) {
                componentContext.getBundleContext().ungetService(serviceReference);
            }
        }
        updateFactories();
        postEvent(SlingScriptConstants.TOPIC_SCRIPT_ENGINE_FACTORY_REMOVED, factory);
    }

    private void updateFactories() {
        readWriteLock.writeLock().lock();
        try {
            internalManager = getInternalScriptEngineManager();
            factories = new TreeSet<>();
            long fakeBundleIdCounter = Long.MIN_VALUE;
            // first add the platform factories
            for (ScriptEngineFactory factory : internalManager.getEngineFactories()) {
                SortableScriptEngineFactory ssef = new SortableScriptEngineFactory(factory, fakeBundleIdCounter++, 0);
                factories.add(ssef);
            }
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            try {
                Thread.currentThread().setContextClassLoader(null);
                // then factories from SPI Bundles
                for (Bundle bundle : engineSpiBundles) {
                    try {
                        ScriptEngineManager manager = new ScriptEngineManager(bundle.adapt(BundleWiring.class).getClassLoader());
                        for (ScriptEngineFactory factory : manager.getEngineFactories()) {
                            factories.add(new SortableScriptEngineFactory(factory, bundle.getBundleId(), 0));
                        }
                    } catch (Exception ex) {
                        LOG.error("Unable to process bundle " + bundle.getSymbolicName(), ex);
                    }
                }
            }
            finally {
                Thread.currentThread().setContextClassLoader(loader);
            }
            // and finally factories registered as OSGi services
            if (componentContext != null) {
                factoriesProperties.clear();
                for (ServiceReference<ScriptEngineFactory> serviceReference : serviceReferences) {
                    ScriptEngineFactory scriptEngineFactory = componentContext.getBundleContext().getService(serviceReference);
                    SortableScriptEngineFactory sortableScriptEngineFactory =
                            new SortableScriptEngineFactory(scriptEngineFactory, serviceReference.getBundle().getBundleId(),
                                    PropertiesUtil.toInteger(serviceReference.getProperty(Constants.SERVICE_RANKING), 0));
                    factories.add(sortableScriptEngineFactory);
                    Map<String, Object> factoryProperties = new HashMap<>(serviceReference.getPropertyKeys().length);
                    for (String key : serviceReference.getPropertyKeys()) {
                        factoryProperties.put(key, serviceReference.getProperty(key));
                    }
                    factoriesProperties.put(scriptEngineFactory, factoryProperties);
                }
            }
            // register the associations at the end, so that the priority sorting is taken into consideration
            for (ScriptEngineFactory factory : factories) {
                registerAssociations(factory);
            }
            if (eventAdmin != null) {
                eventAdmin.postEvent(new Event(EVENT_TOPIC_SCRIPT_MANAGER_UPDATED, Collections.EMPTY_MAP));
            }
        } finally {
            readWriteLock.writeLock().unlock();
        }
    }

    private ScriptEngineManager getInternalScriptEngineManager() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(null);
            return new ScriptEngineManager(ClassLoader.getSystemClassLoader());
        }
        finally {
            Thread.currentThread().setContextClassLoader(loader);
        }
    }

    private void registerAssociations(ScriptEngineFactory factory) {
        for (String extension : factory.getExtensions()) {
            if (extension != null && !extension.isEmpty()) {
                internalManager.registerEngineExtension(extension, factory);
            } else {
                LOG.warn("Could not register an empty or null extension for script engine factory {}.", factory.getEngineName());
            }
        }
        for (String mimeType : factory.getMimeTypes()) {
            if (mimeType != null && !mimeType.isEmpty()) {
                internalManager.registerEngineMimeType(mimeType, factory);
            } else {
                LOG.warn("Could not register an empty or null mime type for script engine factory {}.", factory.getEngineName());
            }
        }
        for (String name : factory.getNames()) {
            if (name != null && !name.isEmpty()) {
                internalManager.registerEngineName(name, factory);
            } else {
                LOG.warn("Could not register an empty or null engine name for script engine factory {}.", factory.getEngineName());
            }
        }
    }

    private void postEvent(final String topic, final ScriptEngineFactory scriptEngineFactory) {
        if (eventAdmin != null) {
            final Dictionary<String, Object> props = new Hashtable<>();
            props.put(SlingScriptConstants.PROPERTY_SCRIPT_ENGINE_FACTORY_NAME, scriptEngineFactory.getEngineName());
            props.put(SlingScriptConstants.PROPERTY_SCRIPT_ENGINE_FACTORY_VERSION, scriptEngineFactory.getEngineVersion());
            props.put(SlingScriptConstants.PROPERTY_SCRIPT_ENGINE_FACTORY_EXTENSIONS, scriptEngineFactory.getExtensions().toArray(new
                    String[0]));
            props.put(SlingScriptConstants.PROPERTY_SCRIPT_ENGINE_FACTORY_LANGUAGE_NAME, scriptEngineFactory.getLanguageName());
            props.put(SlingScriptConstants.PROPERTY_SCRIPT_ENGINE_FACTORY_LANGUAGE_VERSION, scriptEngineFactory.getLanguageVersion());
            props.put(SlingScriptConstants.PROPERTY_SCRIPT_ENGINE_FACTORY_MIME_TYPES, scriptEngineFactory.getMimeTypes().toArray(new
                    String[0]));
            eventAdmin.postEvent(new Event(topic, props));
        }
    }
}
