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
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.util.Collection;
import java.util.Map;

import org.apache.felix.utils.json.JSONWriter;
import org.apache.felix.webconsole.AbstractWebConsolePlugin;
import org.apache.felix.webconsole.WebConsoleConstants;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.request.builder.Builders;
import org.apache.sling.api.resource.NonExistingResource;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.scripting.SlingBindings;
import org.apache.sling.api.scripting.SlingScriptConstants;
import org.apache.sling.scripting.api.BindingsValuesProvider;
import org.apache.sling.scripting.api.BindingsValuesProvidersByContext;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Web Console Plugin exposing all binding provider values.
 */
@Component(
        property = {
            Constants.SERVICE_VENDOR + "=The Apache Software Foundation",
            WebConsoleConstants.PLUGIN_LABEL + "=" + ScriptingVariablesConsolePlugin.LABEL,
            WebConsoleConstants.PLUGIN_TITLE + "=" + ScriptingVariablesConsolePlugin.TITLE,
            "felix.webconsole.category=Sling"
        },
        service = {Servlet.class})
public class ScriptingVariablesConsolePlugin extends AbstractWebConsolePlugin {

    protected static final String LABEL = "scriptingvariables";
    protected static final String TITLE = "Scripting Variables";
    protected static final String FORWARD_PATH = "/" + LABEL + "/show";

    private static final String PARAMETER_EXTENSION = "extension";
    private static final String PARAMETER_PATH = "path";

    protected static final String REQUEST_ATTR = ScriptingVariablesConsolePlugin.class.getName() + ".auth";

    /**
     *
     */
    private static final long serialVersionUID = 261709110347150295L;

    private static final String JS_RES_PATH = "scriptingvariables/ui/scriptingvariables.js";

    /**
     * The script engine manager.
     */
    @Reference
    private ScriptEngineManager scriptEngineManager;

    /**
     * The BindingsValuesProviderTracker
     */
    @Reference
    private BindingsValuesProvidersByContext bindingsValuesProviderTracker;

    private BundleContext bundleContext;

    @Activate
    protected void init(final BundleContext context) {
        this.bundleContext = context;
    }

    /**
     * Automatically called from
     * <a href="https://github.com/apache/felix/blob/4a60744d0f88f351551e4cb4673eb60b8fbd21d3/webconsole/src/main/java/org/apache/felix/webconsole/AbstractWebConsolePlugin.java#L510">AbstractWebConsolePlugin#spoolResource</a>
     *
     * @param path the requested path
     * @return either a URL from which to spool the resource requested through the given path or {@code null}
     */
    public URL getResource(String path) {
        if (path.endsWith(JS_RES_PATH)) {
            return this.getClass().getResource("/" + JS_RES_PATH);
        }
        return null;
    }

    @Override
    public String getLabel() {
        return LABEL;
    }

    @Override
    public String getTitle() {
        return TITLE;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        final String path = request.getPathInfo();
        if (FORWARD_PATH.equals(path)) {
            final ResourceResolver resolver =
                    (ResourceResolver) request.getAttribute("org.apache.sling.auth.core.ResourceResolver");
            if (resolver == null) {
                log("Access forbidden as the request was not authenticated through the web console");
                if (!response.isCommitted()) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN);
                }
                return;
            }
            final String resourcePath = request.getParameter(PARAMETER_PATH);
            final String extension = request.getParameter(PARAMETER_EXTENSION);
            // resolve is used to get non existing resources as well
            final Resource resource = resolver.resolve(resourcePath);
            final SlingHttpServletRequest slingRequest = Builders.newRequestBuilder(resource)
                    .useServletContextFrom(request)
                    .useAttributesFrom(request)
                    .build();
            this.showBindings(slingRequest, response, extension);
            return;
        }
        super.doGet(request, response);
    }

    @Override
    protected void renderContent(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        final PrintWriter pw = response.getWriter();
        pw.append("<script type='text/javascript' src='").append(JS_RES_PATH).append("'></script>");
        pw.append("<div id='content'>");
        pw.append("<table class='content'  cellpadding='0' cellspacing='0' width='100%'>");
        pw.append("<tr><th colspan='3' class='content container'>Sling Scripting Variables</th></tr>");
        pw.append(
                "<tr class='content'><td class='content' colspan='3'>Provide a resource path url and script engine (via extension) and then click on 'Retrieve Variables' to expose all script bindings variables for context 'request' which are available for that resource and script engine.</td></tr>");
        pw.append("<tr class='content'>");
        pw.append("<td class='content'>Resource Url (without selectors and extension)</td> ");
        pw.append(
                "<td class='content' colspan='2'><input type ='text' name='form.path' placeholder='path' required='required' value='/' ");
        pw.append("class='input ui-state-default ui-corner-all inputText' size='50' pattern='^/{1}.*'></td></tr>");
        pw.append("<tr class='content'>");
        pw.append("<td class='content'>Script Engine</td> ");
        pw.append("<td class='content' colspan='2'><select name='form.extension'>");
        for (ScriptEngineFactory factory : scriptEngineManager.getEngineFactories()) {
            for (String extension : factory.getExtensions()) {
                pw.append("<option value='" + extension + "'>" + extension + " (" + factory.getEngineName()
                        + ")</option>");
            }
        }
        pw.append("<option value=''>all (unfiltered)</option>");
        pw.append("</select> ");
        pw.append("<button type='button' id='submitButton'> Retrieve Variables </button></td></tr></table>");
        pw.append("<div id='response'></div>");
    }

    protected void showBindings(
            SlingHttpServletRequest request, HttpServletResponse response, final String requestedExtension)
            throws ServletException, IOException {
        response.setContentType("application/json");
        JSONWriter jsonWriter = new JSONWriter(response.getWriter());
        jsonWriter.array();
        // get filter by engine selector
        if (requestedExtension != null && !requestedExtension.isEmpty()) {
            ScriptEngine selectedScriptEngine = scriptEngineManager.getEngineByExtension(requestedExtension);
            if (selectedScriptEngine == null) {
                throw new IllegalArgumentException("Invalid extension requested: " + requestedExtension);
            } else {
                writeBindingsToJsonWriter(jsonWriter, selectedScriptEngine.getFactory(), request);
            }
        } else {
            for (ScriptEngineFactory engineFactory : scriptEngineManager.getEngineFactories()) {
                writeBindingsToJsonWriter(jsonWriter, engineFactory, request);
            }
        }
        jsonWriter.endArray();
    }

    private void writeBindingsToJsonWriter(
            JSONWriter jsonWriter, ScriptEngineFactory engineFactory, SlingHttpServletRequest request)
            throws IOException {
        jsonWriter.object();
        jsonWriter.key("engine");
        jsonWriter.value(engineFactory.getEngineName());
        jsonWriter.key("extensions");
        jsonWriter.value(engineFactory.getExtensions());
        Bindings bindings = getBindingsByEngine(engineFactory, request);
        jsonWriter.key("bindings");
        jsonWriter.array();
        for (Map.Entry<String, Object> entry : bindings.entrySet()) {
            jsonWriter.object();
            jsonWriter.key("name");
            jsonWriter.value(entry.getKey());
            jsonWriter.key("class");
            jsonWriter.value(
                    entry.getValue() == null
                            ? "&lt;NO VALUE&gt;"
                            : entry.getValue().getClass().getName());
            jsonWriter.endObject();
        }
        jsonWriter.endArray();
        jsonWriter.endObject();
    }

    /**
     * Gets the {@link Bindings} object for the given {@link ScriptEngineFactory}.
     * It only considers the default context "request".
     *
     * @see <a href="https://issues.apache.org/jira/browse/SLING-3038">binding contexts(SLING-3083)</a>
     *
     * @param scriptEngineFactory the factory of the script engine, for which to retrieve the bindings
     * @param request the current request (necessary to create the bindings)
     * @param response the current response (necessary to create the bindings)
     * @return the bindings (list of key/value pairs) as defined by {@link Bindings} for the given script engine.
     * @throws IOException
     */
    private Bindings getBindingsByEngine(ScriptEngineFactory scriptEngineFactory, SlingHttpServletRequest request)
            throws IOException {
        String context = SlingScriptAdapterFactory.BINDINGS_CONTEXT; // use default context only
        final Collection<BindingsValuesProvider> bindingsValuesProviders =
                bindingsValuesProviderTracker.getBindingsValuesProviders(scriptEngineFactory, context);

        Resource invalidScriptResource =
                new NonExistingResource(request.getResourceResolver(), "some/invalid/scriptpath");
        DefaultSlingScript defaultSlingScript = new DefaultSlingScript(
                bundleContext,
                invalidScriptResource,
                scriptEngineFactory.getScriptEngine(),
                bindingsValuesProviders,
                null,
                null);

        // prepare the bindings (similar as in DefaultSlingScript#service)
        final SlingBindings initalBindings = new SlingBindings();
        initalBindings.setRequest(request);
        initalBindings.setResponse(Builders.newResponseBuilder().build());
        final Bindings bindings = defaultSlingScript.verifySlingBindings(initalBindings);

        // only thing being added in {DefaultSlingScript#call(...)} is resource resolver
        bindings.put(SlingScriptConstants.ATTR_SCRIPT_RESOURCE_RESOLVER, request.getResourceResolver());

        return bindings;
    }
}
