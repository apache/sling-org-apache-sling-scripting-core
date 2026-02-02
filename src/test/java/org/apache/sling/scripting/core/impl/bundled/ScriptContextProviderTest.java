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
package org.apache.sling.scripting.core.impl.bundled;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngineManager;

import java.util.Collections;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.SlingJakartaHttpServletRequest;
import org.apache.sling.api.SlingJakartaHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.scripting.SlingBindings;
import org.apache.sling.scripting.api.BindingsValuesProvider;
import org.apache.sling.scripting.api.BindingsValuesProvidersByContext;
import org.apache.sling.scripting.api.resource.ScriptingResourceResolverProvider;
import org.apache.sling.scripting.core.impl.bundled.ScriptContextProvider.ExecutableContext;
import org.apache.sling.scripting.core.impl.jsr223.DummyScriptEngineFactory;
import org.apache.sling.testing.mock.osgi.junit.OsgiContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ScriptContextProviderTest {

    @Rule
    public OsgiContext osgiContext = new OsgiContext();

    private DummyScriptEngineFactory scriptEngineFactory;

    @Before
    public void setup() throws Exception {

        // we need a ScriptEngineFactory that will match the executable unit
        ScriptEngineManager dummyScriptManager = new ScriptEngineManager();
        scriptEngineFactory = new DummyScriptEngineFactory();
        dummyScriptManager.registerEngineName(scriptEngineFactory.getEngineName(), scriptEngineFactory);
        osgiContext.registerService(ScriptEngineManager.class, dummyScriptManager);

        // the additional BVPs are only needed for extra bindings, we don't need them in
        // this test
        BindingsValuesProvidersByContext bvps = Mockito.mock(BindingsValuesProvidersByContext.class);
        Mockito.when(bvps.getBindingsValuesProviders(scriptEngineFactory, BindingsValuesProvider.DEFAULT_CONTEXT))
                .thenReturn(Collections.emptyList());
        osgiContext.registerService(
                BindingsValuesProvidersByContext.class, Mockito.mock(BindingsValuesProvidersByContext.class));

        // the resource resolver provider is not used in this test
        osgiContext.registerService(
                ScriptingResourceResolverProvider.class, Mockito.mock(ScriptingResourceResolverProvider.class));

        osgiContext.registerInjectActivateService(ScriptContextProvider.class);
    }

    @Test
    public void testPrepareScriptContextContainsAllBindings() throws Exception {

        ArgumentCaptor<ScriptContext> scriptContextCaptor = ArgumentCaptor.forClass(ScriptContext.class);

        ScriptContextProvider scp = osgiContext.getService(ScriptContextProvider.class);

        SlingJakartaHttpServletRequest request = Mockito.mock(SlingJakartaHttpServletRequest.class);
        SlingJakartaHttpServletResponse response = Mockito.mock(SlingJakartaHttpServletResponse.class);
        ResourceResolver resourceResolver = Mockito.mock(ResourceResolver.class);
        Resource resource = Mockito.mock(Resource.class);

        Mockito.when(request.getResourceResolver()).thenReturn(resourceResolver);
        Mockito.when(request.getResource()).thenReturn(resource);
        Mockito.when(resource.getResourceResolver()).thenReturn(resourceResolver);

        String scriptExtension = scriptEngineFactory.getExtensions().get(0);

        ExecutableUnit executableUnit = Mockito.mock(ExecutableUnit.class);
        Mockito.when(executableUnit.getBundleContext()).thenReturn(osgiContext.bundleContext());
        Mockito.when(executableUnit.getPath()).thenReturn("/apps/test/script." + scriptExtension);
        Mockito.when(executableUnit.getScriptEngineName()).thenReturn(scriptEngineFactory.getEngineName());

        Mockito.when(executableUnit.getScriptExtension()).thenReturn(scriptExtension);
        Mockito.when(executableUnit.getName()).thenReturn("script." + scriptExtension);

        ExecutableContext executableContext = scp.prepareScriptContext(request, response, executableUnit);
        executableContext.eval();

        Mockito.verify(executableUnit).eval(Mockito.any(), scriptContextCaptor.capture());

        ScriptContext scriptContext = scriptContextCaptor.getValue();

        assertNotNull("scriptContext", scriptContext);

        Bindings bindings = scriptContext.getBindings(ScriptContext.ENGINE_SCOPE);
        assertNotNull("bindings", bindings);
        // cannot use assertSame because of wrapping to OnDemandReaderXXX
        assertBindingIsOfType("Javax request binding", bindings, SlingBindings.REQUEST, SlingHttpServletRequest.class);
        assertBindingIsOfType(
                "Javax response binding", bindings, SlingBindings.RESPONSE, SlingHttpServletResponse.class);
        assertBindingIsOfType(
                "Jakarta request binding",
                bindings,
                SlingBindings.JAKARTA_REQUEST,
                SlingJakartaHttpServletRequest.class);
        assertBindingIsOfType(
                "Jakarta response binding",
                bindings,
                SlingBindings.JAKARTA_RESPONSE,
                SlingJakartaHttpServletResponse.class);
    }

    private static void assertBindingIsOfType(String message, Bindings bindings, Object key, Class<?> expectedType) {
        Object value = bindings.get(key);
        assertNotNull(message + " exists", value);
        assertTrue(message + " is of type " + expectedType.getName(), expectedType.isInstance(value));
    }
}
