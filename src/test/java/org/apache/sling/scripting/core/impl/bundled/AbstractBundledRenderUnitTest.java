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

import java.util.Collections;
import java.util.Hashtable;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

import org.apache.sling.scripting.core.impl.ServiceCache;
import org.apache.sling.testing.mock.osgi.junit.OsgiContext;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertSame;

public class AbstractBundledRenderUnitTest {

    private static final String CLASS_TYPE = Object.class.getName();

    @Rule
    public OsgiContext osgiContext = new OsgiContext();

    private ServiceCache serviceCache;

    @Before
    public void before() {
        serviceCache = new ServiceCache(osgiContext.bundleContext());
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testGetService() throws Exception {
        Object fooBarSvc = new Object();
        osgiContext.bundleContext().registerService(Object.class, fooBarSvc, new Hashtable<>());
        AbstractBundledRenderUnit abru = new AbstractBundledRenderUnit(
                Collections.emptySet(), osgiContext.bundleContext(), osgiContext.bundleContext().getBundle(), "/",
                "testeng", "htl", new ScriptContextProvider(), serviceCache) {

            @Override
            public void eval(
                ScriptEngine scriptEngine,
                ScriptContext context) throws ScriptException {
            }

            @Override
            public String getName() {
                return "test";
            }

            @Override
            public @NotNull ServiceCache getServiceCache() {
                return serviceCache;
            }
        };

        assertSame(fooBarSvc, abru.getService(CLASS_TYPE));

        // Make the service registry return a new service now
        Object newSvc = new Object();
        osgiContext.bundleContext().registerService(Object.class, newSvc, new Hashtable<>());

        assertSame("Second invocation should return still the first service from the cache",
                fooBarSvc, abru.getService(CLASS_TYPE));

        serviceCache.dispose();

        assertSame("After cleaning the cache check that still the first service is returned, since it has the highest priority.",
                fooBarSvc, abru.getService(CLASS_TYPE));
    }
}
