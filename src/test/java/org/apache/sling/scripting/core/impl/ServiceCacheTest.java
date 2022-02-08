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
package org.apache.sling.scripting.core.impl;

import java.util.Dictionary;
import java.util.Hashtable;

import org.apache.sling.testing.mock.osgi.junit.OsgiContext;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ServiceCacheTest {

    @Rule
    public OsgiContext osgiContext = new OsgiContext();

    private BundleContext bundleContext;
    private ServiceCache serviceCache;

    @Before
    public void before() {
        bundleContext = osgiContext.bundleContext();
        serviceCache = new ServiceCache(bundleContext);
    }

    @Test
    public void testGetService() {
        TestService ts1 = new TestService("ts1");
        bundleContext.registerService(TestService.class, ts1, new Hashtable<>());
        assertEquals("The ServiceCache should have returned the same service that was registered first.", ts1,
                serviceCache.getService(TestService.class));
        assertArrayEquals(new TestService[] {ts1}, serviceCache.getServices(TestService.class, null));

        TestService ts2 = new TestService("ts2");
        bundleContext.registerService(TestService.class, ts2, new Hashtable<>());
        assertEquals("The ServiceCache should have returned the first service that was registered.", ts1,
                serviceCache.getService(TestService.class));
        assertArrayEquals(new TestService[] {ts1, ts2}, serviceCache.getServices(TestService.class, null));

        TestService ts3 = new TestService("ts3");
        Dictionary<String, Object> properties = new Hashtable<>();
        properties.put(Constants.SERVICE_RANKING, Integer.MAX_VALUE);
        bundleContext.registerService(TestService.class, ts3, properties);
        assertEquals("The ServiceCache should have now returned the service with the highest ranking.", ts3,
                serviceCache.getService(TestService.class));
        assertArrayEquals(new TestService[] {ts3, ts1, ts2}, serviceCache.getServices(TestService.class, null));
    }

    @Test
    public void testGetServices() {
        TestService ts1 = new TestService("ts1");
        bundleContext.registerService(TestService.class, ts1, new Hashtable<>());
        assertArrayEquals(new TestService[] {ts1}, serviceCache.getServices(TestService.class, null));
        assertEquals("The ServiceCache should have returned the same service that was registered first.", ts1,
                serviceCache.getService(TestService.class));

        TestService ts2 = new TestService("ts2");
        bundleContext.registerService(TestService.class, ts2, new Hashtable<>());
        assertArrayEquals(new TestService[] {ts1, ts2}, serviceCache.getServices(TestService.class, null));
        assertEquals("The ServiceCache should have returned the first service that was registered.", ts1,
                serviceCache.getService(TestService.class));

        TestService ts3 = new TestService("ts3");
        Dictionary<String, Object> properties = new Hashtable<>();
        properties.put(Constants.SERVICE_RANKING, Integer.MAX_VALUE);
        bundleContext.registerService(TestService.class, ts3, properties);
        assertArrayEquals(new TestService[] {ts3, ts1, ts2}, serviceCache.getServices(TestService.class, null));
        assertEquals("The ServiceCache should have now returned the service with the highest ranking.", ts3,
                serviceCache.getService(TestService.class));
    }

    private static final class TestService {
        private final String service;

        public TestService(String service) {
            this.service = service;
        }
    }


}
