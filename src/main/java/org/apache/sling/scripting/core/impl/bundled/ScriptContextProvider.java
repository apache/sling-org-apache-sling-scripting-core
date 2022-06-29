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
package org.apache.sling.scripting.core.impl.bundled;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.scripting.LazyBindings;
import org.apache.sling.api.scripting.SlingBindings;
import org.apache.sling.api.scripting.SlingScriptConstants;
import org.apache.sling.scripting.api.BindingsValuesProvider;
import org.apache.sling.scripting.api.BindingsValuesProvidersByContext;
import org.apache.sling.scripting.api.resource.ScriptingResourceResolverProvider;
import org.apache.sling.scripting.core.ScriptHelper;
import org.apache.sling.scripting.core.impl.InternalScriptHelper;
import org.apache.sling.scripting.core.impl.helper.ProtectedBindings;
import org.apache.sling.scripting.spi.bundle.BundledRenderUnit;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(
        service = ScriptContextProvider.class
)
public class ScriptContextProvider {

    private static final Logger LOG = LoggerFactory.getLogger(ScriptContextProvider.class);

    /** is defined on multiple files in this bundle*/
    private static final long WARN_LIMIT_FOR_BVP_NANOS = (1000*1000); // 1 ms

    private static final String BINDINGS_THRESHOLD_MESSAGE = "Adding the bindings of %s took %s microseconds which is above the hardcoded" +
            " limit of %s microseconds; if this message appears often it indicates that this BindingsValuesProvider has an impact on " +
            "general page rendering performance.";

    private static final Set<String> PROTECTED_BINDINGS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            SlingBindings.REQUEST,
            SlingBindings.RESPONSE,
            SlingBindings.READER,
            SlingBindings.RESOURCE,
            SlingBindings.RESOLVER,
            SlingBindings.OUT,
            SlingBindings.LOG,
            SlingBindings.SLING,
            ScriptEngine.FILENAME,
            BundledRenderUnit.VARIABLE
    )));

    @Reference
    private BindingsValuesProvidersByContext bvpTracker;

    @Reference
    private ScriptEngineManager scriptEngineManager;

    @Reference
    private ScriptingResourceResolverProvider scriptingResourceResolverProvider;


    public ExecutableContext prepareScriptContext(SlingHttpServletRequest request, SlingHttpServletResponse response,
                                                  ExecutableUnit executable)
            throws IOException {
        InternalScriptHelper scriptHelper = new InternalScriptHelper(executable.getBundleContext(), new SlingScriptAdapter(request.getResourceResolver(),
                executable.getPath(), "sling/bundle/resource"), request, response,
                executable.getServiceCache());
        ScriptEngine scriptEngine = scriptEngineManager.getEngineByName(executable.getScriptEngineName());
        if (scriptEngine == null) {
            scriptEngine = scriptEngineManager.getEngineByExtension(executable.getScriptExtension());
            if (scriptEngine == null) {
                throw new IllegalStateException(String.format("Cannot find a script engine with name %s and extension %s for executable %s.",
                    executable.getScriptEngineName(), executable.getScriptExtension(), executable.getPath()));
            }
        }
        // prepare the SlingBindings
        Bindings bindings = new LazyBindings();
        bindings.put("properties", (LazyBindings.Supplier) () -> scriptHelper.getRequest().getResource().getValueMap());
        bindings.put(SlingBindings.REQUEST, scriptHelper.getRequest());
        bindings.put(SlingBindings.RESPONSE, scriptHelper.getResponse());
        bindings.put(SlingBindings.READER, scriptHelper.getRequest().getReader());
        bindings.put(SlingBindings.OUT, scriptHelper.getResponse().getWriter());
        bindings.put(SlingBindings.RESOURCE, scriptHelper.getRequest().getResource());
        bindings.put(SlingBindings.RESOLVER, scriptHelper.getRequest().getResource().getResourceResolver());
        Logger scriptLogger = LoggerFactory.getLogger(executable.getName());
        bindings.put(SlingBindings.LOG, scriptLogger);
        bindings.put(SlingBindings.SLING, scriptHelper);
        bindings.put(BundledRenderUnit.VARIABLE, executable);
        bindings.put(ScriptEngine.FILENAME, executable.getPath());
        bindings.put(ScriptEngine.FILENAME.replace(".", "_"), executable.getPath());

        ProtectedBindings protectedBindings = new ProtectedBindings(bindings, PROTECTED_BINDINGS);
        long inclusionStart = System.nanoTime();
        for (BindingsValuesProvider bindingsValuesProvider : bvpTracker.getBindingsValuesProviders(scriptEngine.getFactory(),
                BindingsValuesProvider.DEFAULT_CONTEXT)) {
            long start = System.nanoTime();
            bindingsValuesProvider.addBindings(protectedBindings);
            long stop = System.nanoTime();
            LOG.trace("Invoking addBindings() of {} took {} nanoseconds",
                    bindingsValuesProvider.getClass().getName(), stop-start);
            if (stop-start > WARN_LIMIT_FOR_BVP_NANOS) {
                // SLING-11182 - make this work with older implementations of the Sling API
                if (request.getRequestProgressTracker() != null) {
                    request.getRequestProgressTracker().log(String.format(BINDINGS_THRESHOLD_MESSAGE, bindingsValuesProvider.getClass().getName(),
                            (stop-start)/1000, WARN_LIMIT_FOR_BVP_NANOS/1000));
                } else {
                    if (LOG.isInfoEnabled()) {
                        LOG.info(String.format(BINDINGS_THRESHOLD_MESSAGE, bindingsValuesProvider.getClass().getName(), (stop-start)/1000,
                                WARN_LIMIT_FOR_BVP_NANOS/1000));
                    }
                }
            }
        }
        // SLING-11182 - make this work with older implementations of the Sling API
        if (request.getRequestProgressTracker() != null) {
            long duration = (System.nanoTime() - inclusionStart) / 1000;
            request.getRequestProgressTracker().log("Adding bindings took " + duration + " microseconds");
        }

        ScriptContext scriptContext = new BundledScriptContext();
        Map<String, LazyBindings.Supplier> slingBindingsSuppliers = new HashMap<>();
        slingBindingsSuppliers.put(SlingScriptConstants.ATTR_SCRIPT_RESOURCE_RESOLVER,
                () -> scriptingResourceResolverProvider.getRequestScopedResourceResolver());
        LazyBindings slingScopeBindings = new LazyBindings(slingBindingsSuppliers);
        scriptContext.setBindings(slingScopeBindings, SlingScriptConstants.SLING_SCOPE);
        scriptContext.setBindings(bindings, ScriptContext.ENGINE_SCOPE);
        scriptContext.setErrorWriter(new LogWriter(scriptLogger));
        scriptContext.setWriter(scriptHelper.getResponse().getWriter());
        scriptContext.setReader(scriptHelper.getRequest().getReader());
        return new ExecutableContext(scriptContext, executable, scriptEngine);
    }

    static class ExecutableContext {
        private final ScriptContext scriptContext;
        private final ExecutableUnit executable;
        private final ScriptEngine scriptEngine;

        private ExecutableContext(ScriptContext scriptContext, ExecutableUnit executable, ScriptEngine scriptEngine) {
            this.scriptContext = scriptContext;
            this.executable = executable;
            this.scriptEngine = scriptEngine;
        }

        void eval() throws ScriptException {
            executable.eval(scriptEngine, scriptContext);
        }

        void clean() {
            Bindings engineBindings = scriptContext.getBindings(ScriptContext.ENGINE_SCOPE);
            if (engineBindings != null && engineBindings.containsKey(SlingBindings.SLING)) {
                Object scriptHelper = engineBindings.get(SlingBindings.SLING);
                if (scriptHelper instanceof ScriptHelper) {
                    ((ScriptHelper) scriptHelper).cleanup();
                }
            }
        }
    }

}
