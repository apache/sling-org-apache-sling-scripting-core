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

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServiceCache implements ServiceListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceCache.class);

    private final BundleContext bundleContext;

    private final ConcurrentHashMap<String, SortedSet<Reference>> cache = new ConcurrentHashMap<>();

    public ServiceCache(final BundleContext ctx) {
        this.bundleContext = ctx;
        this.bundleContext.addServiceListener(this);
    }

    public void dispose() {
        this.bundleContext.removeServiceListener(this);
        for (final SortedSet<Reference> references : cache.values()) {
            for (Reference reference : references) {
                this.bundleContext.ungetService(reference.getServiceReference());
            }
        }
        cache.clear();
    }

    /**
     * Return a service for the given service class.
     * @param <ServiceType> The service class / interface
     * @param type The requested service
     * @return The service or <code>null</code>
     */
    @SuppressWarnings("unchecked")
    @Nullable
    public <T> T getService(Class<T> type) {
        SortedSet<Reference> references = getCachedReferences(type);
        for (Reference reference : references) {
            T service = (T) reference.getService();
            if (service != null) {
                return service;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    public <T> T[] getServices(Class<T> type, String filter) {
        List<T> result = new ArrayList<>();
        try {
            SortedSet<Reference> cachedReferences = getCachedReferences(type);
            final Collection<ServiceReference<T>> filteredReferences = this.bundleContext.getServiceReferences(type, filter);
            if (!filteredReferences.isEmpty()) {
                List<ServiceReference<T>> localFilteredReferences = new ArrayList<>(filteredReferences);
                Collections.sort(localFilteredReferences);
                // get the highest ranking first
                Collections.reverse(localFilteredReferences);
                for (ServiceReference<T> serviceReference : localFilteredReferences) {
                    Reference lookup = new Reference(serviceReference);
                    if (cachedReferences.contains(lookup)) {
                        for (Reference reference : cachedReferences) {
                            if (serviceReference.equals(reference.getServiceReference())) {
                                T service = (T) reference.getService();
                                if (service != null) {
                                    result.add(service);
                                }
                                break;
                            }
                        }
                    } else {
                        // concurrent change; restart
                        return getServices(type, filter);
                    }
                }
            }
        } catch (InvalidSyntaxException e) {
            LOGGER.error(String.format("Unable to retrieve the services of type %s.", type.getName()), e);
        }
        if (!result.isEmpty()) {
            T[] srv = (T[]) Array.newInstance(type, result.size());
            return result.toArray(srv);
        }
        return null;
    }

    /**
     * @see org.osgi.framework.ServiceListener#serviceChanged(org.osgi.framework.ServiceEvent)
     */
    public void serviceChanged(ServiceEvent event) {
        ServiceReference<?> serviceReference = event.getServiceReference();
        final String[] objectClasses = (String[]) serviceReference.getProperty(Constants.OBJECTCLASS);
        if (objectClasses != null) {
            for (final String key : objectClasses) {
                SortedSet<Reference> references;
                synchronized (this) {
                    references = this.cache.remove(key);
                    if (references != null) {
                        for (Reference reference : references) {
                            bundleContext.ungetService(reference.getServiceReference());
                        }
                    }
                }
            }
        }
    }

    private <T> SortedSet<Reference> getCachedReferences(Class<T> type) {
        String key = type.getName();
        SortedSet<Reference> references = cache.get(key);
        if (references == null) {
            references = new ConcurrentSkipListSet<>(Comparator.reverseOrder());
            try {
                Collection<ServiceReference<T>> serviceReferences = this.bundleContext.getServiceReferences(type, null);
                if (!serviceReferences.isEmpty()) {
                    List<ServiceReference<T>> localReferences = new ArrayList<>(serviceReferences);
                    Collections.sort(localReferences);
                    Collections.reverse(localReferences);
                    for (ServiceReference<T> ref : localReferences) {
                        references.add(new Reference(ref));
                    }
                    synchronized (this) {
                        SortedSet<Reference> existing = this.cache.get(key);
                        if (existing != null) {
                            existing.addAll(references);
                            references = existing;
                        } else {
                            this.cache.put(key, references);
                        }
                    }
                }
            } catch (InvalidSyntaxException e) {
                LOGGER.error(String.format("Unable to retrieve the services of type %s.", type.getName()), e);
            }
        }
        return references;
    }


    private final class Reference implements Comparable<Reference> {
        private final ServiceReference<?> serviceReference;
        private Object service;

        Reference(ServiceReference<?> serviceReference) {
            this.serviceReference = serviceReference;
        }

        @Override
        public int compareTo(@NotNull ServiceCache.Reference o) {
            return this.serviceReference.compareTo(o.serviceReference);
        }

        synchronized Object getService() {
            if (service == null) {
                service = bundleContext.getService(serviceReference);
            }
            return service;
        }

        ServiceReference<?> getServiceReference() {
            return serviceReference;
        }

        @Override
        public int hashCode() {
            return Objects.hash(serviceReference);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof Reference) {
                Reference other = (Reference) obj;
                return Objects.equals(this.serviceReference, other.serviceReference);
            }
            return false;
        }
    }
}
