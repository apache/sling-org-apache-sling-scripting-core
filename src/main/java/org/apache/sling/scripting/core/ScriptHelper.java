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
package org.apache.sling.scripting.core;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.SlingIOException;
import org.apache.sling.api.SlingJakartaHttpServletRequest;
import org.apache.sling.api.SlingJakartaHttpServletResponse;
import org.apache.sling.api.SlingServletException;
import org.apache.sling.api.request.RequestDispatcherOptions;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.scripting.InvalidServiceFilterSyntaxException;
import org.apache.sling.api.scripting.SlingScript;
import org.apache.sling.api.scripting.SlingScriptHelper;
import org.apache.sling.api.wrappers.JakartaToJavaxRequestWrapper;
import org.apache.sling.api.wrappers.JakartaToJavaxResponseWrapper;
import org.apache.sling.api.wrappers.JavaxToJakartaRequestWrapper;
import org.apache.sling.api.wrappers.JavaxToJakartaResponseWrapper;
import org.apache.sling.api.wrappers.SlingHttpServletRequestWrapper;
import org.apache.sling.api.wrappers.SlingHttpServletResponseWrapper;
import org.apache.sling.api.wrappers.SlingJakartaHttpServletRequestWrapper;
import org.apache.sling.api.wrappers.SlingJakartaHttpServletResponseWrapper;
import org.apache.sling.scripting.core.impl.helper.OnDemandReaderJakartaRequest;
import org.apache.sling.scripting.core.impl.helper.OnDemandReaderRequest;
import org.apache.sling.scripting.core.impl.helper.OnDemandWriterJakartaResponse;
import org.apache.sling.scripting.core.impl.helper.OnDemandWriterResponse;
import org.jetbrains.annotations.NotNull;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.slf4j.LoggerFactory;

/**
 * Simple script helper providing access to the (wrapped) response, the
 * on-demand writer and a simple API for request inclusion. Instances of this
 * class are made available to the scripts as the global <code>sling</code>
 * variable.
 *
 * Client code using this object should take care to call {@link #cleanup()}
 * when the object is not used anymore!
 */
public class ScriptHelper implements SlingScriptHelper {

    /** The corresponding script. */
    private final SlingScript script;

    /** The current request or <code>null</code>. */
    @SuppressWarnings("deprecation")
    private SlingHttpServletRequest request;

    /** The current response or <code>null</code>. */
    @SuppressWarnings("deprecation")
    private SlingHttpServletResponse response;

    /** The current request or <code>null</code>. */
    private final SlingJakartaHttpServletRequest jakartaRequest;

    /** The current response or <code>null</code>. */
    private final SlingJakartaHttpServletResponse jakartaResponse;

    /** The bundle context. */
    protected final BundleContext bundleContext;

    /**
     * The list of references - we don't need to synchronize this as we are
     * running in one single request.
     */
    protected List<ServiceReference<?>> references;

    /** A map of found services. */
    protected Map<String, Object> services;

    public ScriptHelper(final BundleContext ctx, final SlingScript script) {
        if (ctx == null) {
            throw new IllegalArgumentException("Bundle context must not be null.");
        }
        this.jakartaRequest = null;
        this.jakartaResponse = null;
        this.script = script;
        this.bundleContext = ctx;
    }

    /**
     * Creates a new script helper instance.
     *
     * @param ctx The bundle context, must not be <code>null</code>.
     * @param script The script, must not be <code>null</code>.
     * @param request The request, may be <code>null</code>.
     * @param response The response, may be <code>null</code>.
     * @since 2.2.0
     */
    public ScriptHelper(
            final BundleContext ctx,
            final SlingScript script,
            final SlingJakartaHttpServletRequest request,
            final SlingJakartaHttpServletResponse response) {
        if (ctx == null) {
            throw new IllegalArgumentException("Bundle context must not be null.");
        }
        this.script = script;
        this.jakartaRequest = wrapIfNeeded(request);
        this.jakartaResponse = wrapIfNeeded(response);
        this.bundleContext = ctx;
    }

    /**
     * Creates a new script helper instance.
     *
     * @param ctx The bundle context, must not be <code>null</code>.
     * @param script The script, must not be <code>null</code>.
     * @param request The request, may be <code>null</code>.
     * @param response The response, may be <code>null</code>.
     * @deprecated Use {@link #ScriptHelper(BundleContext, SlingScript, SlingJakartaHttpServletRequest, SlingJakartaHttpServletResponse)}
     */
    @Deprecated
    public ScriptHelper(
            final BundleContext ctx,
            final SlingScript script,
            final SlingHttpServletRequest request,
            final SlingHttpServletResponse response) {
        if (ctx == null) {
            throw new IllegalArgumentException("Bundle context must not be null.");
        }
        this.script = script;
        this.request = wrapIfNeeded(request);
        this.response = wrapIfNeeded(response);
        this.jakartaRequest = JavaxToJakartaRequestWrapper.toJakartaRequest(this.request);
        this.jakartaResponse = JavaxToJakartaResponseWrapper.toJakartaResponse(this.response);
        this.bundleContext = ctx;
    }

    /**
     * @see org.apache.sling.api.scripting.SlingScriptHelper#getScript()
     */
    public SlingScript getScript() {
        return script;
    }

    /**
     * @see org.apache.sling.api.scripting.SlingScriptHelper#getJakartaRequest()
     */
    public SlingJakartaHttpServletRequest getJakartaRequest() {
        return jakartaRequest;
    }

    /**
     * @see org.apache.sling.api.scripting.SlingScriptHelper#getJakartaResponse()
     */
    public SlingJakartaHttpServletResponse getJakartaResponse() {
        return jakartaResponse;
    }

    /**
     * @see org.apache.sling.api.scripting.SlingScriptHelper#getRequest()
     */
    @SuppressWarnings("deprecation")
    public SlingHttpServletRequest getRequest() {
        if (this.request == null && this.jakartaRequest != null) {
            this.request = JakartaToJavaxRequestWrapper.toJavaxRequest(this.jakartaRequest);
        }
        return request;
    }

    /**
     * @see org.apache.sling.api.scripting.SlingScriptHelper#getResponse()
     */
    @SuppressWarnings("deprecation")
    public SlingHttpServletResponse getResponse() {
        if (this.response == null && this.jakartaResponse != null) {
            this.response = JakartaToJavaxResponseWrapper.toJavaxResponse(this.jakartaResponse);
        }
        return response;
    }

    /**
     * @see org.apache.sling.api.scripting.SlingScriptHelper#include(java.lang.String)
     */
    public void include(String path) {
        include(path, (RequestDispatcherOptions) null);
    }

    /**
     * @see org.apache.sling.api.scripting.SlingScriptHelper#include(java.lang.String, java.lang.String)
     */
    public void include(String path, String options) {
        include(path, new RequestDispatcherOptions(options));
    }

    /**
     * @see org.apache.sling.api.scripting.SlingScriptHelper#include(java.lang.String, org.apache.sling.api.request.RequestDispatcherOptions)
     */
    public void include(String path, RequestDispatcherOptions options) {
        final RequestDispatcher dispatcher = getJakartaRequest().getRequestDispatcher(path, options);

        if (dispatcher != null) {
            try {
                dispatcher.include(getJakartaRequest(), getJakartaResponse());
            } catch (IOException ioe) {
                throw new SlingIOException(ioe);
            } catch (ServletException se) {
                throw new SlingServletException(se);
            }
        }
    }

    /**
     * @see org.apache.sling.api.scripting.SlingScriptHelper#forward(java.lang.String)
     */
    public void forward(String path) {
        forward(path, (RequestDispatcherOptions) null);
    }

    /**
     * @see org.apache.sling.api.scripting.SlingScriptHelper#forward(java.lang.String, java.lang.String)
     */
    public void forward(String path, String options) {
        forward(path, new RequestDispatcherOptions(options));
    }

    /**
     * @see org.apache.sling.api.scripting.SlingScriptHelper#forward(java.lang.String, org.apache.sling.api.request.RequestDispatcherOptions)
     */
    public void forward(String path, RequestDispatcherOptions options) {
        final RequestDispatcher dispatcher = getJakartaRequest().getRequestDispatcher(path, options);

        if (dispatcher != null) {
            try {
                dispatcher.forward(getJakartaRequest(), getJakartaResponse());
            } catch (IOException ioe) {
                throw new SlingIOException(ioe);
            } catch (ServletException se) {
                throw new SlingServletException(se);
            }
        }
    }

    /**
     * @see org.apache.sling.api.scripting.SlingScriptHelper#dispose()
     * @deprecated This method is deprecated and should never be called by clients!
     */
    @Deprecated
    public void dispose() {
        LoggerFactory.getLogger(this.getClass())
                .error(
                        "ScriptHelper#dispose has been called. This method is deprecated and should never be called by clients!");
    }

    /**
     * @see org.apache.sling.api.scripting.SlingScriptHelper#getService(java.lang.Class)
     */
    @SuppressWarnings("unchecked")
    public <T> T getService(Class<T> type) {
        T service = (this.services == null ? null : (T) this.services.get(type.getName()));
        if (service == null) {
            final ServiceReference<T> ref = this.bundleContext.getServiceReference(type);
            if (ref != null) {
                service = this.bundleContext.getService(ref);
                if (service != null) {
                    if (this.services == null) {
                        this.services = new HashMap<>();
                    }
                    if (this.references == null) {
                        this.references = new ArrayList<>();
                    }
                    this.references.add(ref);
                    this.services.put(type.getName(), service);
                }
            }
        }
        return service;
    }

    /**
     * @see org.apache.sling.api.scripting.SlingScriptHelper#getServices(java.lang.Class, java.lang.String)
     */
    @SuppressWarnings("unchecked")
    public <T> T[] getServices(Class<T> serviceType, String filter) throws InvalidServiceFilterSyntaxException {
        try {
            Collection<ServiceReference<T>> refsCollection =
                    this.bundleContext.getServiceReferences(serviceType, filter);
            T[] result = null;
            if (refsCollection != null) {
                // sort by service ranking (lowest first) (see ServiceReference#compareTo(Object))
                List<ServiceReference<T>> refsList = new ArrayList<>(refsCollection);
                Collections.sort(refsList);
                // get the highest ranking first
                Collections.reverse(refsList);

                final List<T> objects = new ArrayList<>();
                for (ServiceReference<T> reference : refsList) {
                    final T service = this.bundleContext.getService(reference);
                    if (service != null) {
                        if (this.references == null) {
                            this.references = new ArrayList<>();
                        }
                        this.references.add(reference);
                        objects.add(service);
                    }
                }
                if (!objects.isEmpty()) {
                    T[] srv = (T[]) Array.newInstance(serviceType, objects.size());
                    result = objects.toArray(srv);
                }
            }
            return result;
        } catch (InvalidSyntaxException ise) {
            throw new InvalidServiceFilterSyntaxException(filter, "Invalid filter syntax", ise);
        }
    }

    /**
     * Clean up this instance.
     */
    public void cleanup() {
        if (this.references != null) {
            final Iterator<ServiceReference<?>> i = this.references.iterator();
            while (i.hasNext()) {
                final ServiceReference<?> ref = i.next();
                this.bundleContext.ungetService(ref);
            }
            this.references.clear();
        }
        if (this.services != null) {
            this.services.clear();
        }
    }

    /**
     * @see org.apache.sling.api.scripting.SlingScriptHelper#forward(org.apache.sling.api.resource.Resource)
     */
    public void forward(Resource resource) {
        forward(resource, (RequestDispatcherOptions) null);
    }

    /**
     * @see org.apache.sling.api.scripting.SlingScriptHelper#forward(org.apache.sling.api.resource.Resource, java.lang.String)
     */
    public void forward(Resource resource, String options) {
        forward(resource, new RequestDispatcherOptions(options));
    }

    /**
     * @see org.apache.sling.api.scripting.SlingScriptHelper#forward(org.apache.sling.api.resource.Resource, org.apache.sling.api.request.RequestDispatcherOptions)
     */
    public void forward(Resource resource, RequestDispatcherOptions options) {
        final RequestDispatcher dispatcher = getJakartaRequest().getRequestDispatcher(resource, options);

        if (dispatcher != null) {
            try {
                dispatcher.forward(getJakartaRequest(), getJakartaResponse());
            } catch (IOException ioe) {
                throw new SlingIOException(ioe);
            } catch (ServletException se) {
                throw new SlingServletException(se);
            }
        }
    }

    /**
     * @see org.apache.sling.api.scripting.SlingScriptHelper#forward(org.apache.sling.api.resource.Resource)
     */
    public void include(Resource resource) {
        include(resource, (RequestDispatcherOptions) null);
    }

    /**
     * @see org.apache.sling.api.scripting.SlingScriptHelper#include(org.apache.sling.api.resource.Resource, java.lang.String)
     */
    public void include(Resource resource, String options) {
        include(resource, new RequestDispatcherOptions(options));
    }

    /**
     * @see org.apache.sling.api.scripting.SlingScriptHelper#include(org.apache.sling.api.resource.Resource, org.apache.sling.api.request.RequestDispatcherOptions)
     */
    public void include(Resource resource, RequestDispatcherOptions options) {
        final RequestDispatcher dispatcher = getJakartaRequest().getRequestDispatcher(resource, options);

        if (dispatcher != null) {
            try {
                dispatcher.include(getJakartaRequest(), getJakartaResponse());
            } catch (IOException ioe) {
                throw new SlingIOException(ioe);
            } catch (ServletException se) {
                throw new SlingServletException(se);
            }
        }
    }

    @SuppressWarnings("deprecation")
    private SlingHttpServletRequest findOnDemandReader(final @NotNull SlingHttpServletRequest initialRequest) {
        SlingHttpServletRequest request = initialRequest;
        while (request instanceof SlingHttpServletRequestWrapper) {
            if (request instanceof OnDemandReaderRequest) {
                return null;
            }
            request = ((SlingHttpServletRequestWrapper) request).getSlingRequest();
        }
        if (request instanceof JakartaToJavaxRequestWrapper) {
            final SlingJakartaHttpServletRequest initialJakartaRequest =
                    (SlingJakartaHttpServletRequest) ((JakartaToJavaxRequestWrapper) request).getRequest();
            final SlingJakartaHttpServletRequest entry = findOnDemandReader(initialJakartaRequest);
            if (entry == null) {
                return null;
            }
            if (entry instanceof JavaxToJakartaRequestWrapper) {
                request = (SlingHttpServletRequest) ((JavaxToJakartaRequestWrapper) entry).getRequest();
                return this.findOnDemandReader(request);
            }
        }
        return request;
    }

    private SlingJakartaHttpServletRequest findOnDemandReader(
            final @NotNull SlingJakartaHttpServletRequest initialRequest) {
        SlingJakartaHttpServletRequest request = initialRequest;
        while (request instanceof SlingJakartaHttpServletRequestWrapper) {
            if (request instanceof OnDemandReaderJakartaRequest) {
                return null;
            }
            request = ((SlingJakartaHttpServletRequestWrapper) request).getSlingRequest();
        }
        if (request instanceof JavaxToJakartaRequestWrapper) {
            @SuppressWarnings("deprecation")
            final SlingHttpServletRequest initialJavaxRequest =
                    (SlingHttpServletRequest) ((JavaxToJakartaRequestWrapper) request).getRequest();
            @SuppressWarnings("deprecation")
            final SlingHttpServletRequest entry = findOnDemandReader(initialJavaxRequest);
            if (entry == null) {
                return null;
            }
            if (entry instanceof JakartaToJavaxRequestWrapper) {
                request = (SlingJakartaHttpServletRequest) ((JakartaToJavaxRequestWrapper) entry).getRequest();
                return this.findOnDemandReader(request);
            }
        }
        return request;
    }

    @SuppressWarnings("deprecation")
    private SlingHttpServletResponse findOnDemandWriter(final @NotNull SlingHttpServletResponse initialResponse) {
        SlingHttpServletResponse response = initialResponse;
        while (response instanceof SlingHttpServletResponseWrapper) {
            if (response instanceof OnDemandWriterResponse) {
                return null;
            }
            response = ((SlingHttpServletResponseWrapper) response).getSlingResponse();
        }
        if (response instanceof JakartaToJavaxResponseWrapper) {
            final SlingJakartaHttpServletResponse initialJakartaResponse =
                    (SlingJakartaHttpServletResponse) ((JakartaToJavaxResponseWrapper) response).getResponse();
            final SlingJakartaHttpServletResponse entry = findOnDemandWriter(initialJakartaResponse);
            if (entry == null) {
                return null;
            }
            if (entry instanceof JakartaToJavaxResponseWrapper) {
                response = (SlingHttpServletResponse) ((JakartaToJavaxResponseWrapper) entry).getResponse();
                return this.findOnDemandWriter(response);
            }
        }
        return response;
    }

    private SlingJakartaHttpServletResponse findOnDemandWriter(
            final @NotNull SlingJakartaHttpServletResponse initialResponse) {
        SlingJakartaHttpServletResponse response = initialResponse;
        while (response instanceof SlingJakartaHttpServletResponseWrapper) {
            if (response instanceof OnDemandWriterJakartaResponse) {
                return null;
            }
            response = ((SlingJakartaHttpServletResponseWrapper) response).getSlingResponse();
        }
        if (response instanceof JavaxToJakartaResponseWrapper) {
            @SuppressWarnings("deprecation")
            final SlingHttpServletResponse initialJavaxResponse =
                    (SlingHttpServletResponse) ((JavaxToJakartaResponseWrapper) response).getResponse();
            @SuppressWarnings("deprecation")
            final SlingHttpServletResponse entry = findOnDemandWriter(initialJavaxResponse);
            if (entry == null) {
                return null;
            }
            if (entry instanceof JakartaToJavaxResponseWrapper) {
                response = (SlingJakartaHttpServletResponse) ((JakartaToJavaxResponseWrapper) entry).getResponse();
                return this.findOnDemandWriter(response);
            }
        }
        return response;
    }

    private SlingJakartaHttpServletRequest wrapIfNeeded(final @NotNull SlingJakartaHttpServletRequest initialRequest) {
        if (this.findOnDemandReader(initialRequest) == null) {
            return initialRequest;
        }
        return new OnDemandReaderJakartaRequest(initialRequest);
    }

    private SlingJakartaHttpServletResponse wrapIfNeeded(
            final @NotNull SlingJakartaHttpServletResponse initialResponse) {
        if (this.findOnDemandWriter(initialResponse) == null) {
            return initialResponse;
        }
        return new OnDemandWriterJakartaResponse(initialResponse);
    }

    @SuppressWarnings("deprecation")
    private SlingHttpServletRequest wrapIfNeeded(final @NotNull SlingHttpServletRequest initialRequest) {
        if (this.findOnDemandReader(initialRequest) == null) {
            return initialRequest;
        }
        return new OnDemandReaderRequest(initialRequest);
    }

    @SuppressWarnings("deprecation")
    private SlingHttpServletResponse wrapIfNeeded(final @NotNull SlingHttpServletResponse initialResponse) {
        if (this.findOnDemandWriter(initialResponse) == null) {
            return initialResponse;
        }
        return new OnDemandWriterResponse(initialResponse);
    }
}
