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

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

import org.junit.Test;
import org.mockito.Mockito;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

public class AbstractBundledRenderUnitTest {
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testGetService() throws Exception {
        Object fooBarSvc = new Object();

        ServiceReference sref = Mockito.mock(ServiceReference.class);

        BundleContext bc = Mockito.mock(BundleContext.class);
        Mockito.when(bc.getServiceReference("foo.Bar"))
            .thenReturn(sref);
        Mockito.when(bc.getService(sref))
            .thenReturn(fooBarSvc);

        AbstractBundledRenderUnit abru = new AbstractBundledRenderUnit(
                Collections.emptySet(), bc, Mockito.mock(Bundle.class), "/",
                "testeng", "htl", new ScriptContextProvider()) {

            @Override
            public void eval(
                ScriptEngine scriptEngine,
                ScriptContext context) throws ScriptException {
            }

            @Override
            public String getName() {
                return "test";
            }};

        Field rl = AbstractBundledRenderUnit.class.getDeclaredField("references");
        rl.setAccessible(true);
        assertNull("Precondition", rl.get(abru));

        Field sf = AbstractBundledRenderUnit.class.getDeclaredField("services");
        sf.setAccessible(true);
        assertNull("Precondition", sf.get(abru));

        assertSame(fooBarSvc, abru.getService("foo.Bar"));

        List references = (List) rl.get(abru);
        assertTrue(references.contains(sref));
        Map servicesMap = (Map) sf.get(abru);
        assertSame(fooBarSvc, servicesMap.get("foo.Bar"));

        assertSame("Second invocation should return the service from the cache",
                fooBarSvc, abru.getService("foo.Bar"));
    }
}
