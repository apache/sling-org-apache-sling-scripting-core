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

import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.util.Arrays;
import java.util.Collection;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.apache.sling.api.SlingException;
import org.apache.sling.api.SlingJakartaHttpServletRequest;
import org.apache.sling.api.SlingJakartaHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceMetadata;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceWrapper;
import org.apache.sling.api.resource.SyntheticResource;
import org.apache.sling.api.scripting.LazyBindings;
import org.apache.sling.api.scripting.ScriptEvaluationException;
import org.apache.sling.api.scripting.SlingBindings;
import org.apache.sling.api.scripting.SlingScript;
import org.apache.sling.api.scripting.SlingScriptConstants;
import org.apache.sling.api.scripting.SlingScriptHelper;
import org.apache.sling.scripting.api.BindingsValuesProvider;
import org.apache.sling.scripting.api.CachedScript;
import org.apache.sling.scripting.api.ScriptCache;
import org.apache.sling.scripting.core.ScriptNameAwareReader;
import org.apache.sling.scripting.core.impl.helper.CachedScriptImpl;
import org.apache.sling.scripting.core.impl.helper.ProtectedBindings;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.sling.api.scripting.SlingBindings.FLUSH;
import static org.apache.sling.api.scripting.SlingBindings.JAKARTA_REQUEST;
import static org.apache.sling.api.scripting.SlingBindings.JAKARTA_RESPONSE;
import static org.apache.sling.api.scripting.SlingBindings.LOG;
import static org.apache.sling.api.scripting.SlingBindings.OUT;
import static org.apache.sling.api.scripting.SlingBindings.READER;
import static org.apache.sling.api.scripting.SlingBindings.REQUEST;
import static org.apache.sling.api.scripting.SlingBindings.RESOLVER;
import static org.apache.sling.api.scripting.SlingBindings.RESOURCE;
import static org.apache.sling.api.scripting.SlingBindings.RESPONSE;
import static org.apache.sling.api.scripting.SlingBindings.SLING;

class DefaultSlingScript implements SlingScript, Servlet, ServletConfig {

    /** The logger. */
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultSlingScript.class);

    /** is defined on multiple files in this bundle*/
    private static final long WARN_LIMIT_FOR_BVP_NANOS = (1000 * 1000); // 1 ms

    private static final String BINDINGS_THRESHOLD_MESSAGE =
            "Adding the bindings of %s took %s microseconds which is above the hardcoded"
                    + " limit of %s microseconds; if this message appears often it indicates that this BindingsValuesProvider has an impact on "
                    + "general page rendering performance.";

    /** Thread local containing the resource resolver. */
    private static ThreadLocal<ResourceResolver> requestResourceResolver = new ThreadLocal<>();

    /** The set of protected keys. */
    @SuppressWarnings("deprecation")
    private static final Set<String> PROTECTED_KEYS = new HashSet<>(Arrays.asList(
            REQUEST, RESPONSE, JAKARTA_REQUEST, JAKARTA_RESPONSE, READER, SLING, RESOURCE, RESOLVER, OUT, LOG));

    private static final Integer[] SCOPES = {
        SlingScriptConstants.SLING_SCOPE, Integer.valueOf(100), Integer.valueOf(200)
    };

    /** The resource pointing to the script. */
    private final Resource scriptResource;

    /** The name of the script (the resource path) */
    private final String scriptName;

    /** The encoding of the script. */
    private final String scriptEncoding;

    /** The script engine for this script. */
    private final ScriptEngine scriptEngine;

    /** The servlet context. */
    private ServletContext servletContext;

    /** The init parameters for this servlet. */
    private Dictionary<String, String> initParameters;

    /** The current bundle context. */
    private final BundleContext bundleContext;

    /** The ScriptBindingsValuesProviders. */
    private final Collection<BindingsValuesProvider> bindingsValuesProviders;

    /** The cache for services. */
    private final ServiceCache cache;

    /* The cache for compiled scripts. */
    private final ScriptCache scriptCache;

    /**
     * Constructor
     * @param bundleContext The bundle context
     * @param scriptResource The script resource
     * @param scriptEngine The script engine
     * @param bindingsValuesProviders additional bindings values providers
     * @param cache serviceCache
     */
    DefaultSlingScript(
            final BundleContext bundleContext,
            final Resource scriptResource,
            final ScriptEngine scriptEngine,
            final Collection<BindingsValuesProvider> bindingsValuesProviders,
            final ServiceCache cache,
            final ScriptCache scriptCache) {
        this.scriptResource = scriptResource;
        this.scriptEngine = scriptEngine;
        this.bundleContext = bundleContext;
        this.bindingsValuesProviders = bindingsValuesProviders;
        this.cache = cache;
        this.scriptCache = scriptCache;
        this.scriptName = this.scriptResource.getPath();
        // Now know how to get the input stream, we still have to decide
        // on the encoding of the stream's data. Primarily we assume it is
        // UTF-8, which is a default in many places in JCR. Secondarily
        // we try to get a jcr:encoding property besides the data property
        // to provide a possible encoding
        final ResourceMetadata meta = this.scriptResource.getResourceMetadata();
        String encoding = meta.getCharacterEncoding();
        if (encoding == null) {
            encoding = "UTF-8";
        }
        this.scriptEncoding = encoding;
    }

    // ---------- SlingScript interface ----------------------------------------

    /**
     * @see org.apache.sling.api.scripting.SlingScript#getScriptResource()
     */
    public Resource getScriptResource() {
        final ResourceResolver resolver = requestResourceResolver.get();
        if (resolver == null) {
            // if we don't have a request resolver we directly return the script resource
            return scriptResource;
        }
        return new LazyScriptResource(this.scriptName, this.scriptResource.getResourceType(), resolver);
    }

    @Override
    public Object eval(final SlingBindings props) {
        return call(props, null);
    }

    @Override
    public Object call(final SlingBindings props, final String method, final Object... args) {
        Bindings bindings = null;
        Reader reader = null;
        boolean disposeScriptHelper = !props.containsKey(SLING);
        ResourceResolver oldResolver = null;
        try {
            bindings = verifySlingBindings(props);

            // use final variable for inner class!
            final Bindings b = bindings;
            // create script context
            final ScriptContext ctx = new ScriptContext() {

                private Bindings globalScope;
                private Bindings engineScope = b;
                private Writer writer = (Writer) b.get(OUT);
                private Writer errorWriter = new LogWriter((Logger) b.get(LOG));
                private Reader reader = (Reader) b.get(READER);
                private Bindings slingScope = new LazyBindings();

                @Override
                public void setBindings(final Bindings bindings, final int scope) {
                    switch (scope) {
                        case SlingScriptConstants.SLING_SCOPE:
                            this.slingScope = bindings;
                            break;
                        case 100:
                            if (bindings == null) throw new NullPointerException("Bindings for ENGINE scope is null");
                            this.engineScope = bindings;
                            break;
                        case 200:
                            this.globalScope = bindings;
                            break;
                        default:
                            throw new IllegalArgumentException("Invalid scope");
                    }
                }

                @Override
                public Bindings getBindings(final int scope) {
                    switch (scope) {
                        case SlingScriptConstants.SLING_SCOPE:
                            return slingScope;
                        case 100:
                            return this.engineScope;
                        case 200:
                            return this.globalScope;
                        default:
                            throw new IllegalArgumentException("Invalid scope");
                    }
                }

                @Override
                public void setAttribute(final String name, final Object value, final int scope) {
                    if (name == null) throw new IllegalArgumentException("Name is null");
                    final Bindings bindings = getBindings(scope);
                    if (bindings != null) {
                        bindings.put(name, value);
                    }
                }

                @Override
                public Object getAttribute(final String name, final int scope) {
                    if (name == null) throw new IllegalArgumentException("Name is null");
                    final Bindings bindings = getBindings(scope);
                    if (bindings != null) {
                        return bindings.get(name);
                    }
                    return null;
                }

                @Override
                public Object removeAttribute(final String name, final int scope) {
                    if (name == null) throw new IllegalArgumentException("Name is null");
                    final Bindings bindings = getBindings(scope);
                    if (bindings != null) {
                        return bindings.remove(name);
                    }
                    return null;
                }

                @Override
                public Object getAttribute(String name) {
                    if (name == null) throw new IllegalArgumentException("Name is null");
                    for (final int scope : SCOPES) {
                        final Bindings bindings = getBindings(scope);
                        if (bindings != null) {
                            final Object o = bindings.get(name);
                            if (o != null) {
                                return o;
                            }
                        }
                    }
                    return null;
                }

                @Override
                public int getAttributesScope(String name) {
                    if (name == null) throw new IllegalArgumentException("Name is null");
                    for (final int scope : SCOPES) {
                        if ((getBindings(scope) != null) && (getBindings(scope).containsKey(name))) {
                            return scope;
                        }
                    }
                    return -1;
                }

                @Override
                public List<Integer> getScopes() {
                    return Arrays.asList(SCOPES);
                }

                @Override
                public Writer getWriter() {
                    return this.writer;
                }

                @Override
                public Writer getErrorWriter() {
                    return this.errorWriter;
                }

                @Override
                public void setWriter(Writer writer) {
                    this.writer = writer;
                }

                @Override
                public void setErrorWriter(Writer writer) {
                    this.errorWriter = writer;
                }

                @Override
                public Reader getReader() {
                    return this.reader;
                }

                @Override
                public void setReader(Reader reader) {
                    this.reader = reader;
                }
            };

            // set the current resource resolver if a request is available from the bindings
            if (props.getJakartaRequest() != null) {
                oldResolver = requestResourceResolver.get();
                requestResourceResolver.set(props.getJakartaRequest().getResourceResolver());
            }

            // set the script resource resolver as an attribute
            ctx.setAttribute(
                    SlingScriptConstants.ATTR_SCRIPT_RESOURCE_RESOLVER,
                    this.scriptResource.getResourceResolver(),
                    SlingScriptConstants.SLING_SCOPE);

            reader = getScriptReader();
            if (method != null && !(this.scriptEngine instanceof Invocable)) {
                reader = getWrapperReader(reader, method, args);
            }

            // evaluate the script
            final Object result;
            if (method == null && this.scriptEngine instanceof Compilable) {
                CachedScript cachedScript = scriptCache.getScript(scriptName);
                if (cachedScript == null) {
                    ScriptNameAwareReader snReader = new ScriptNameAwareReader(reader, scriptName);
                    CompiledScript compiledScript = ((Compilable) scriptEngine).compile(snReader);
                    cachedScript = new CachedScriptImpl(scriptName, compiledScript);
                    scriptCache.putScript(cachedScript);
                    LOGGER.debug("Adding {} to the script cache.", scriptName);
                } else {
                    LOGGER.debug("Script {} was already cached.", scriptName);
                }
                result = cachedScript.getCompiledScript().eval(ctx);
            } else {
                result = scriptEngine.eval(reader, ctx);
            }

            // call method - if supplied and script engine supports direct invocation
            if (method != null && (this.scriptEngine instanceof Invocable)) {
                try {
                    ((Invocable) scriptEngine)
                            .invokeFunction(method, Arrays.asList(args).toArray());
                } catch (NoSuchMethodException e) {
                    throw new ScriptEvaluationException(
                            this.scriptName, "Method " + method + " not found in script.", e);
                }
            }
            // optional flush the output channel
            Object flushObject = bindings.get(FLUSH);
            if (Boolean.TRUE.equals(flushObject)) {
                ctx.getWriter().flush();
            }

            // allways flush the error channel
            ctx.getErrorWriter().flush();

            return result;

        } catch (IOException ioe) {
            throw new ScriptEvaluationException(this.scriptName, ioe.getMessage(), ioe);

        } catch (ScriptEvaluationException see) {
            throw see;
        } catch (ScriptException se) {
            Throwable cause = (se.getCause() == null) ? se : se.getCause();
            throw new ScriptEvaluationException(this.scriptName, se.getMessage(), cause);

        } finally {
            if (props.getJakartaRequest() != null) {
                requestResourceResolver.set(oldResolver);
            }

            // close the script reader (SLING-380)
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignore) {
                    // don't care
                }
            }

            // dispose of the SlingScriptHelper
            if (bindings != null && disposeScriptHelper) {
                final InternalScriptHelper helper = (InternalScriptHelper) bindings.get(SLING);
                if (helper != null) {
                    helper.cleanup();
                }
            }
        }
    }

    // ---------- Servlet interface --------------------------------------------

    @Override
    public void init(final ServletConfig servletConfig) {
        if (servletConfig != null) {
            final Dictionary<String, String> params = new Hashtable<>(); // NOSONAR
            for (Enumeration<?> ne = servletConfig.getInitParameterNames(); ne.hasMoreElements(); ) {
                String name = String.valueOf(ne.nextElement());
                String value = servletConfig.getInitParameter(name);
                params.put(name, value);
            }
            this.initParameters = params;
            this.servletContext = servletConfig.getServletContext();
        }
    }

    @Override
    public void service(ServletRequest req, ServletResponse res) {
        final SlingJakartaHttpServletRequest request = (SlingJakartaHttpServletRequest) req;

        try {
            // prepare the properties for the script
            final SlingBindings props = new SlingBindings();
            props.setJakartaRequest(request);
            props.setJakartaResponse((SlingJakartaHttpServletResponse) res);

            // try to set content type (unless included)
            if (request.getAttribute(RequestDispatcher.INCLUDE_SERVLET_PATH) == null) {
                final String contentType = request.getResponseContentType();
                if (contentType != null) {
                    res.setContentType(contentType);

                    // only set the character encoding for text/ content types
                    // see SLING-679
                    if (contentType.startsWith("text/")) {
                        res.setCharacterEncoding("UTF-8");
                    }
                } else {
                    LOGGER.debug("service: No response content type defined for request {}.", request.getRequestURI());
                }
            } else {
                LOGGER.debug("service: Included request, not setting content type and encoding");
            }

            // evaluate the script now using the ScriptEngine
            eval(props);

        } catch (RuntimeException see) {

            // log in the request progress tracker
            logScriptError(request, see);

            throw see;
        } catch (Exception e) {

            // log in the request progress tracker
            logScriptError(request, e);

            throw new SlingException("Cannot get DefaultSlingScript: " + e.getMessage(), e);
        }
    }

    @Override
    public ServletConfig getServletConfig() {
        return this;
    }

    @Override
    public String getServletInfo() {
        return "Script " + scriptName;
    }

    @Override
    public void destroy() {
        initParameters = null;
        servletContext = null;
    }

    // ---------- ServletConfig ------------------------------------------------

    @Override
    public String getInitParameter(String name) {
        final Dictionary<String, String> params = initParameters;
        return (params != null) ? params.get(name) : null;
    }

    @Override
    public Enumeration<String> getInitParameterNames() {
        final Dictionary<String, String> params = initParameters;
        return (params != null) ? params.keys() : null;
    }

    @Override
    public ServletContext getServletContext() {
        return servletContext;
    }

    @Override
    public String getServletName() {
        return this.scriptName;
    }

    // ---------- internal -----------------------------------------------------

    private Reader getScriptReader() throws IOException {
        // access the value as a stream and return a buffered reader
        // converting the stream data using UTF-8 encoding, which is
        // the default encoding used
        return new BufferedReader(new InputStreamReader(new LazyInputStream(this.scriptResource), this.scriptEncoding));
    }

    private Reader getWrapperReader(final Reader scriptReader, final String method, final Object... args) {
        final StringBuilder buffer = new StringBuilder(method);
        buffer.append('(');
        for (Object o : args) {
            buffer.append('"');
            buffer.append(o);
            buffer.append('"');
        }
        buffer.append(')');
        final String msg = buffer.toString();
        return new Reader() {

            protected boolean doAppend = false;

            protected StringReader methodReader = new StringReader(msg);
            /**
             * @see java.io.Reader#close()
             */
            @Override
            public void close() throws IOException {
                scriptReader.close();
            }

            @Override
            public int read(char[] cbuf, int start, int len) throws IOException {
                if (doAppend) {
                    return methodReader.read(cbuf, start, len);
                }
                int readLen = scriptReader.read(cbuf, start, len);
                if (readLen == -1) {
                    doAppend = true;
                    return this.read(cbuf, start, len);
                }
                return readLen;
            }

            @Override
            public int read() throws IOException {
                if (doAppend) {
                    return methodReader.read();
                }
                int value = scriptReader.read();
                if (value == -1) {
                    doAppend = true;
                    return methodReader.read();
                }
                return value;
            }

            @Override
            public int read(char[] cbuf) throws IOException {
                return this.read(cbuf, 0, cbuf.length);
            }

            @Override
            public boolean ready() throws IOException {
                return scriptReader.ready();
            }
        };
    }

    @SuppressWarnings("deprecation")
    Bindings verifySlingBindings(final SlingBindings slingBindings) throws IOException {

        final Bindings bindings = new LazyBindings();

        final SlingJakartaHttpServletRequest request = slingBindings.getJakartaRequest();

        // check sling object
        Object slingObject = slingBindings.get(SLING);
        if (slingObject == null) {

            if (request != null) {
                slingObject = new InternalScriptHelper(
                        this.bundleContext, this, request, slingBindings.getJakartaResponse(), this.cache);
            } else {
                slingObject = new InternalScriptHelper(this.bundleContext, this, this.cache);
            }
        } else if (!(slingObject instanceof SlingScriptHelper)) {
            throw fail(SLING, "Wrong type");
        }
        final SlingScriptHelper sling = (SlingScriptHelper) slingObject;
        bindings.put(SLING, sling);

        if (request != null) {
            final SlingJakartaHttpServletResponse response = slingBindings.getJakartaResponse();
            if (response == null) {
                throw fail(JAKARTA_RESPONSE, "Missing or wrong type");
            }

            Object resourceObject = slingBindings.get(RESOURCE);
            if (resourceObject != null && !(resourceObject instanceof Resource)) {
                throw fail(RESOURCE, "Wrong type");
            }

            Object resolverObject = slingBindings.get(RESOLVER);
            if (resolverObject != null && !(resolverObject instanceof ResourceResolver)) {
                throw fail(RESOLVER, "Wrong type");
            }

            Object writerObject = slingBindings.get(OUT);
            if (writerObject != null && !(writerObject instanceof PrintWriter)) {
                throw fail(OUT, "Wrong type");
            }

            // if there is a provided sling script helper, check arguments
            if (slingBindings.get(SLING) != null) {

                if (sling.getJakartaRequest() != request) {
                    throw fail(JAKARTA_REQUEST, "Not the same as request field of SlingScriptHelper");
                }

                if (sling.getJakartaResponse() != response) {
                    throw fail(JAKARTA_RESPONSE, "Not the same as response field of SlingScriptHelper");
                }

                if (resourceObject != null && sling.getJakartaRequest().getResource() != resourceObject) {
                    throw fail(RESOURCE, "Not the same as resource of the SlingScriptHelper request");
                }

                if (resolverObject != null && sling.getJakartaRequest().getResourceResolver() != resolverObject) {
                    throw fail(
                            RESOLVER,
                            "Not the same as the resource resolver of the SlingScriptHelper request's resolver");
                }

                if (writerObject != null && sling.getJakartaResponse().getWriter() != writerObject) {
                    throw fail(OUT, "Not the same as writer of the SlingScriptHelper response");
                }
            }

            // set base variables when executing inside a request
            bindings.put(JAKARTA_REQUEST, sling.getJakartaRequest());
            bindings.put(JAKARTA_RESPONSE, sling.getJakartaResponse());
            bindings.put(REQUEST, sling.getRequest());
            bindings.put(RESPONSE, sling.getResponse());
            bindings.put(READER, sling.getJakartaRequest().getReader());
            bindings.put(RESOURCE, sling.getJakartaRequest().getResource());
            bindings.put(RESOLVER, sling.getJakartaRequest().getResourceResolver());
            bindings.put(OUT, sling.getJakartaResponse().getWriter());
        }

        Object logObject = slingBindings.get(LOG);
        if (logObject == null) {
            logObject = LoggerFactory.getLogger(getLoggerName());
        } else if (!(logObject instanceof Logger)) {
            throw fail(LOG, "Wrong type");
        }
        bindings.put(LOG, logObject);

        // copy non-base variables
        for (Map.Entry<String, Object> entry : slingBindings.entrySet()) {
            if (!bindings.containsKey(entry.getKey())) {
                bindings.put(entry.getKey(), entry.getValue());
            }
        }

        if (!bindingsValuesProviders.isEmpty()) {
            Set<String> protectedKeys = new HashSet<>();
            protectedKeys.addAll(PROTECTED_KEYS);
            ProtectedBindings protectedBindings = new ProtectedBindings(bindings, protectedKeys);

            long inclusionStart = System.nanoTime();
            for (BindingsValuesProvider provider : bindingsValuesProviders) {
                long start = System.nanoTime();
                provider.addBindings(protectedBindings);
                long stop = System.nanoTime();
                LOGGER.trace(
                        "Invoking addBindings() of {} took {} nanoseconds",
                        provider.getClass().getName(),
                        stop - start);
                if (stop - start > WARN_LIMIT_FOR_BVP_NANOS) {
                    // SLING-11182 - make this work with older implementations of the Sling API
                    if (request != null && request.getRequestProgressTracker() != null) {
                        request.getRequestProgressTracker()
                                .log(String.format(
                                        BINDINGS_THRESHOLD_MESSAGE,
                                        provider.getClass().getName(),
                                        (stop - start) / 1000,
                                        WARN_LIMIT_FOR_BVP_NANOS / 1000));
                    } else {
                        if (LOGGER.isInfoEnabled()) {
                            LOGGER.info(String.format(
                                    BINDINGS_THRESHOLD_MESSAGE,
                                    provider.getClass().getName(),
                                    (stop - start) / 1000,
                                    WARN_LIMIT_FOR_BVP_NANOS / 1000));
                        }
                    }
                }
            }
            // SLING-11182 - make this work with older implementations of the Sling API
            if (request != null && request.getRequestProgressTracker() != null) {
                long duration = (System.nanoTime() - inclusionStart) / 1000;
                request.getRequestProgressTracker().log("Adding bindings took " + duration + " microseconds");
            }
        }

        return bindings;
    }

    private ScriptEvaluationException fail(String variableName, String message) {
        return new ScriptEvaluationException(this.scriptName, variableName + ": " + message);
    }

    private String getLoggerName() {
        String name = scriptName;
        name = name.substring(1); // cut-off leading slash
        name = name.replace('.', '$'); // extension separator as part of name
        name = name.replace('/', '.'); // hierarchy defined by dot
        return name;
    }

    /**
     * Logs the error caused by executing the script in the request progress
     * tracker.
     */
    private void logScriptError(SlingJakartaHttpServletRequest request, Throwable throwable) {
        String message = throwable.getMessage();
        if (message != null) {
            message = throwable.getMessage().replace('\n', '/');
        } else {
            message = throwable.toString();
        }
        request.getRequestProgressTracker().log("SCRIPT ERROR: {0}", message);
    }

    /**
     * Input stream wrapper which acquires the underlying input stream lazily.
     * This ensures that the input stream is only fetched from the repository
     * if it is really used by the script engines.
     */
    public static final class LazyInputStream extends InputStream {

        /** The script resource which is adapted to an inputm stream. */
        private final Resource resource;

        /** The input stream created on demand, null if not used */
        private InputStream delegatee;

        public LazyInputStream(final Resource resource) {
            this.resource = resource;
        }

        /**
         * Closes the input stream if acquired otherwise does nothing.
         */
        @Override
        public void close() throws IOException {
            if (delegatee != null) {
                delegatee.close();
            }
        }

        @Override
        public int available() throws IOException {
            return getStream().available();
        }

        @Override
        public int read() throws IOException {
            return getStream().read();
        }

        @Override
        public int read(byte[] b) throws IOException {
            return getStream().read(b);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return getStream().read(b, off, len);
        }

        @Override
        public long skip(long n) throws IOException {
            return getStream().skip(n);
        }

        @Override
        public boolean markSupported() {
            try {
                return getStream().markSupported();
            } catch (IOException ioe) {
                // ignore
            }
            return false;
        }

        @Override
        public synchronized void mark(int readlimit) {
            try {
                getStream().mark(readlimit);
            } catch (IOException ioe) {
                // ignore
            }
        }

        @Override
        public synchronized void reset() throws IOException {
            getStream().reset();
        }

        /** Actually retrieves the input stream from the underlying JCR Value */
        private InputStream getStream() throws IOException {
            if (delegatee == null) {
                delegatee = this.resource.adaptTo(InputStream.class);
                if (delegatee == null) {
                    throw new IOException("Cannot get a stream to the script resource " + this.resource.getPath());
                }
            }
            return delegatee;
        }
    }

    /**
     * This is a lazy implementation of the script resource which
     * just returns the path, resource type and resource resolver directly.
     */
    private static final class LazyScriptResource extends ResourceWrapper {

        private final String path;

        private final String resourceType;

        private final ResourceResolver resolver;

        private Resource delegatee;

        public LazyScriptResource(final String path, final String resourceType, final ResourceResolver resolver) {
            super(null); // NOSONAR
            this.path = path;
            this.resourceType = resourceType;
            this.resolver = resolver;
        }

        @Override
        public Resource getResource() {
            if (this.delegatee == null) {
                this.delegatee = this.resolver.getResource(this.path);
                if (this.delegatee == null) {
                    this.delegatee = new SyntheticResource(resolver, this.path, this.resourceType);
                }
            }
            return this.delegatee;
        }

        /**
         * @see org.apache.sling.api.resource.Resource#getPath()
         */
        @Override
        public String getPath() {
            return this.path;
        }

        /**
         * @see org.apache.sling.api.resource.Resource#getResourceType()
         */
        @Override
        public String getResourceType() {
            return this.resourceType;
        }

        /**
         * @see org.apache.sling.api.resource.Resource#getResourceResolver()
         */
        @Override
        public ResourceResolver getResourceResolver() {
            return this.resolver;
        }
    }
}
