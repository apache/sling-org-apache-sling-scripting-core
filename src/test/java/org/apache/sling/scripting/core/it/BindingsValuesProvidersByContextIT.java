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
package org.apache.sling.scripting.core.it;

import javax.inject.Inject;
import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import org.apache.sling.scripting.api.BindingsValuesProvider;
import org.apache.sling.scripting.api.BindingsValuesProvidersByContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.util.Filter;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.ops4j.pax.exam.CoreOptions.options;

@RunWith(PaxExam.class)
public class BindingsValuesProvidersByContextIT extends ScriptingCoreTestSupport {

    @Inject
    @Filter(timeout = 300000)
    private BindingsValuesProvidersByContext bvpProvider;

    @Inject
    private BundleContext bundleContext;

    private final List<ServiceRegistration<?>> regs = new ArrayList<>();

    @Before
    public void setup() {
        regs.clear();
    }

    @After
    public void cleanup() {
        for (ServiceRegistration<?> reg : regs) {
            reg.unregister();
        }
    }

    @Configuration
    public Option[] configuration() {
        return options(baseConfiguration());
    }

    private Dictionary<String, Object> getProperties(String context, String engineName) {
        final Dictionary<String, Object> props = new Hashtable<String, Object>();
        if (context != null) {
            props.put(BindingsValuesProvider.CONTEXT, context.split(","));
        }
        if (engineName != null) {
            props.put(ScriptEngine.NAME, engineName);
        }
        return props;
    }

    private void addBVP(final String id, String context, String engineName) {
        final BindingsValuesProvider bvp = new BindingsValuesProvider() {
            @Override
            public String toString() {
                return id;
            }

            @Override
            public void addBindings(Bindings b) {}
        };

        regs.add(bundleContext.registerService(
                BindingsValuesProvider.class.getName(), bvp, getProperties(context, engineName)));
    }

    private void addBVPWithServiceRanking(final String id, String context, String engineName, int serviceRanking) {
        final BindingsValuesProvider bvp = new BindingsValuesProvider() {
            @Override
            public String toString() {
                return id;
            }

            @Override
            public void addBindings(Bindings b) {}
        };
        final Dictionary<String, Object> properties = getProperties(context, engineName);
        properties.put(Constants.SERVICE_RANKING, serviceRanking);
        regs.add(bundleContext.registerService(BindingsValuesProvider.class.getName(), bvp, properties));
    }

    private void addMap(final String id, String context, String engineName) {
        final Map<String, Object> result = new HashMap<String, Object>() {
            private static final long serialVersionUID = 1L;

            @Override
            public String toString() {
                return "M_" + id;
            }
        };

        regs.add(bundleContext.registerService(Map.class.getName(), result, getProperties(context, engineName)));
    }

    private ScriptEngineFactory factory(final String engineName) {
        return new ScriptEngineFactory() {

            @Override
            public ScriptEngine getScriptEngine() {
                return null;
            }

            @Override
            public String getProgram(String... arg0) {
                return null;
            }

            @Override
            public Object getParameter(String arg0) {
                return null;
            }

            @Override
            public String getOutputStatement(String arg0) {
                return null;
            }

            @Override
            public List<String> getNames() {
                final List<String> names = new ArrayList<String>();
                names.add(engineName);
                return names;
            }

            @Override
            public List<String> getMimeTypes() {
                return null;
            }

            @Override
            public String getMethodCallSyntax(String arg0, String arg1, String... arg2) {
                return null;
            }

            @Override
            public String getLanguageVersion() {
                return null;
            }

            @Override
            public String getLanguageName() {
                return null;
            }

            @Override
            public List<String> getExtensions() {
                return null;
            }

            @Override
            public String getEngineVersion() {
                return null;
            }

            @Override
            public String getEngineName() {
                return engineName;
            }
        };
    }

    private String asString(Collection<?> data, boolean sortList) {
        final List<String> maybeSorted = new ArrayList<String>();
        for (Object o : data) {
            maybeSorted.add(o.toString());
        }
        if (sortList) {
            Collections.sort(maybeSorted);
        }

        final StringBuilder sb = new StringBuilder();
        for (String str : maybeSorted) {
            if (sb.length() > 0) {
                sb.append(",");
            }
            sb.append(str);
        }
        return sb.toString();
    }

    private String asString(Collection<?> data) {
        return asString(data, true);
    }

    @Test
    public void testAny() {
        addBVP("one", null, "js");
        addBVP("two", null, null);
        addBVP("three", null, "*");
        addBVP("four", null, "ANY");
        addBVP("five", null, "basic");

        assertEquals("four,one,three,two", asString(bvpProvider.getBindingsValuesProviders(factory("js"), null)));
        assertEquals("five,four,three,two", asString(bvpProvider.getBindingsValuesProviders(factory("basic"), null)));
        assertEquals("four,three,two", asString(bvpProvider.getBindingsValuesProviders(factory("other"), null)));

        final String unsorted = asString(bvpProvider.getBindingsValuesProviders(factory("js"), null), false);
        assertTrue("Expecting js language-specific BVP at the end", unsorted.endsWith("one"));
    }

    @Test
    public void testContextsAndLanguages() {
        addBVP("foo", null, "js");
        addBVP("bar", null, null);
        addBVP("r1", "request", "js");
        addBVP("r2", "request", null);
        addBVP("o1", "other", "js");
        addBVP("o2", "other", null);
        addBVP("o3", "other,request", null);
        addBVP("o4", "python", null);
        addBVP("python", "python", "python");
        assertEquals("bar,foo,o3,r1,r2", asString(bvpProvider.getBindingsValuesProviders(factory("js"), "request")));
        assertEquals(
                "With default content",
                "bar,foo,o3,r1,r2",
                asString(bvpProvider.getBindingsValuesProviders(factory("js"), null)));
        assertEquals("o1,o2,o3", asString(bvpProvider.getBindingsValuesProviders(factory("js"), "other")));
        assertEquals("o4,python", asString(bvpProvider.getBindingsValuesProviders(factory("python"), "python")));
        assertEquals("", asString(bvpProvider.getBindingsValuesProviders(factory("js"), "unusedContext")));

        final String unsorted = asString(bvpProvider.getBindingsValuesProviders(factory("python"), "python"), false);
        assertTrue("Expecting python language-specific BVP at the end", unsorted.endsWith("python"));
    }

    @Test
    public void testMapsAndBvps() {
        addBVP("foo", null, "js");
        addMap("bar", null, null);
        addMap("r1", "request", "js");
        addMap("r2", "request", null);
        addMap("o1", "other", "js");
        addBVP("o2", "other", null);
        addMap("o3", "other,request", null);
        addBVP("o4", "python", null);
        addMap("python", "python", "python");
        assertEquals(
                "M_bar,M_o3,M_r1,M_r2,foo", asString(bvpProvider.getBindingsValuesProviders(factory("js"), "request")));
        assertEquals(
                "With default content",
                "M_bar,M_o3,M_r1,M_r2,foo",
                asString(bvpProvider.getBindingsValuesProviders(factory("js"), null)));
        assertEquals("M_o1,M_o3,o2", asString(bvpProvider.getBindingsValuesProviders(factory("js"), "other")));
        assertEquals("", asString(bvpProvider.getBindingsValuesProviders(factory("js"), "unusedContext")));
        assertEquals("M_python,o4", asString(bvpProvider.getBindingsValuesProviders(factory("python"), "python")));

        final String unsorted = asString(bvpProvider.getBindingsValuesProviders(factory("python"), "python"), false);
        assertTrue("Expecting python language-specific BVP at the end", unsorted.endsWith("M_python"));
    }

    @Test
    public void testBVPsWithServiceRankingA() {
        addBVPWithServiceRanking("last", null, "js", Integer.MAX_VALUE);
        addBVPWithServiceRanking("second", null, "js", 0);
        addBVPWithServiceRanking("first", null, "js", Integer.MIN_VALUE);
        assertEquals("first,second,last", asString(bvpProvider.getBindingsValuesProviders(factory("js"), null), false));
    }

    @Test
    public void testBVPsWithServiceRankingB() {
        addBVPWithServiceRanking("first", null, "js", Integer.MIN_VALUE);
        addBVPWithServiceRanking("second", null, "js", 0);
        addBVPWithServiceRanking("last", null, "js", Integer.MAX_VALUE);
        assertEquals("first,second,last", asString(bvpProvider.getBindingsValuesProviders(factory("js"), null), false));
    }

    @Test
    public void testBVPsWithServiceRankingC() {
        addBVPWithServiceRanking("second", "request", "js", 0);
        addBVPWithServiceRanking("first", "request", "js", Integer.MIN_VALUE);
        addBVPWithServiceRanking("genericThree", "request", null, 42);
        addBVPWithServiceRanking("genericTwo", "request", null, 0);
        addBVPWithServiceRanking("last", "request", "js", Integer.MAX_VALUE);
        addBVPWithServiceRanking("genericOne", "request", null, -42);
        assertEquals(
                "genericOne,genericTwo,genericThree,first,second,last",
                asString(bvpProvider.getBindingsValuesProviders(factory("js"), "request"), false));
    }
}
