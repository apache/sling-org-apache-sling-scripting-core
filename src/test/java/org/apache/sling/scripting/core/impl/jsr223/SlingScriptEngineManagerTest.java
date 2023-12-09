/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sling.scripting.core.impl.jsr223;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.security.SecureClassLoader;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;

import org.apache.sling.testing.mock.osgi.junit.OsgiContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;

public class SlingScriptEngineManagerTest {

    private static Class<?> SCRIPT_ENGINE_FACTORY = DummyScriptEngineFactory.class;

    @Rule
    public final OsgiContext context = new OsgiContext();


    @Before
    public void setUp() {
        context.registerInjectActivateService(new SlingScriptEngineManager());
    }

    @Test
    public void testPlatformScriptEngines() {
        int jvmProvidedScriptEngineFactoryCount = jvmProvidedScriptEngineFactoryCount();
        ScriptEngineManager scriptEngineManager = context.getService(ScriptEngineManager.class);
        assertNotNull("Expected a ScriptEngineManager would be already registered.", scriptEngineManager);
        assertEquals("The ScriptEngineManager should have had " + jvmProvidedScriptEngineFactoryCount + " ScriptEngineFactory registered.", jvmProvidedScriptEngineFactoryCount, scriptEngineManager.getEngineFactories().size());
    }

    @Test
    public void testOSGiRegisteredFactoriesDifferentServiceRanking() throws Exception {
        int numberOfOSGiRegisteredFactories = 2;
        int jvmProvidedScriptEngineFactoryCount = jvmProvidedScriptEngineFactoryCount();
        // we register 2 factories, then unregister 1 of them
        int expectedEvents = 3;
        CountDownLatch latch = new CountDownLatch(expectedEvents);
        TestEventHandler eventHandler = new TestEventHandler
                (latch, "org/apache/sling/scripting/core/impl/jsr223/SlingScriptEngineManager/UPDATED");
        context.registerService(
                EventHandler.class, eventHandler,
                new HashMap<String, Object>() {
                    private static final long serialVersionUID = -826334194042415106L;
                {
                    put(EventConstants.EVENT_TOPIC, "org/apache/sling/scripting/core/impl/jsr223/SlingScriptEngineManager/*");
                }}
        );

        ScriptEngineFactory f1 = mockScriptEngineFactory("f1", "1.0", Collections.singletonList("f1"), "f1", "1.0", Collections
                .singletonList("f1/text"));
        ScriptEngineFactory f2 = mockScriptEngineFactory("f2", "1.0", Collections.singletonList("f2"), "f2", "1.0", Collections
                .singletonList("f2/text"));

        ServiceRegistration<ScriptEngineFactory> f1SR = context.bundleContext().registerService(ScriptEngineFactory.class, f1, new
                Hashtable<String, Object>() {
                    private static final long serialVersionUID = 3476669145432094983L;
                {
                    put(Constants.SERVICE_RANKING, 2);
                }});
        context.bundleContext().registerService(ScriptEngineFactory.class, f2, new
                Hashtable<String, Object>() {
                    private static final long serialVersionUID = 625624336896085659L;
                {
                    put(Constants.SERVICE_RANKING, 1);
                }});

        ScriptEngineManager scriptEngineManager = context.getService(ScriptEngineManager.class);
        assertNotNull("Expected a ScriptEngineManager would be already registered.", scriptEngineManager);
        List<ScriptEngineFactory> factories = scriptEngineManager.getEngineFactories();
        int expectedScriptEngineFactories = numberOfOSGiRegisteredFactories + jvmProvidedScriptEngineFactoryCount;
        assertEquals("The ScriptEngineManager should have had " + expectedScriptEngineFactories + " ScriptEngineFactories registered.", expectedScriptEngineFactories, factories.size());
        assertEquals(f1.getEngineName(), factories.get(expectedScriptEngineFactories - 1).getEngineName());
        assertEquals(f2.getEngineName(), factories.get(expectedScriptEngineFactories - 2).getEngineName());

        SlingScriptEngineManager slingScriptEngineManager = context.getService(SlingScriptEngineManager.class);
        assertEquals(2, slingScriptEngineManager.getServiceProperties(f1).get(Constants.SERVICE_RANKING));

        f1SR.unregister();
        expectedScriptEngineFactories--;

        factories = scriptEngineManager.getEngineFactories();
        assertEquals("The ScriptEngineManager should have had " + expectedScriptEngineFactories + " ScriptEngineFactories registered.", expectedScriptEngineFactories, factories.size());
        assertEquals(f2.getEngineName(), factories.get(expectedScriptEngineFactories - 1).getEngineName());

        assertEquals(f2, scriptEngineManager.getEngineByName("f2").getFactory());
        assertEquals(f2, scriptEngineManager.getEngineByExtension("f2").getFactory());
        assertEquals(f2, scriptEngineManager.getEngineByMimeType("f2/text").getFactory());
        assertNull("Did not expect references to the already unregistered f1 ScriptEngineFactory", scriptEngineManager.getEngineByName
                ("f1"));
        assertNull("Did not expect references to the already unregistered f1 ScriptEngineFactory", scriptEngineManager
                .getEngineByExtension("f1"));
        assertNull("Did not expect references to the already unregistered f1 ScriptEngineFactory", scriptEngineManager
                .getEngineByMimeType("f1/text"));

        latch.await(2, TimeUnit.SECONDS);
        assertEquals("Expected a different number of processed " + SlingScriptEngineManager.EVENT_TOPIC_SCRIPT_MANAGER_UPDATED + " events.",
                expectedEvents, eventHandler.processedEvents);
    }

    @Test
    public void testBundledScriptEngineFactory() throws Exception {
        int jvmProvidedScriptEngineFactoryCount = jvmProvidedScriptEngineFactoryCount();
        final URL url = createFactoryFile().toURI().toURL();
        Bundle bundle = mock(Bundle.class);
        BundleWiring wiring = mock(BundleWiring.class);
        ClassLoader loader = new SecureClassLoader(){
            @Override
            protected Class<?> loadClass(String name, boolean resolve) {
                return name.equals(SCRIPT_ENGINE_FACTORY.getName()) ? SCRIPT_ENGINE_FACTORY : null;
            }

            @Override
            public Enumeration<URL> getResources(String name) {
                Vector<URL> v = new Vector<>();
                v.add(url);
                return v.elements();
            }
        };

        when(bundle.getBundleId()).thenReturn(1L);
        when(bundle.adapt(BundleWiring.class)).thenReturn(wiring);
        when(wiring.getClassLoader()).thenReturn(loader);

        when(bundle.findEntries(SlingScriptEngineManager.META_INF_SERVICES, SlingScriptEngineManager.FACTORY_NAME, false)).thenReturn(Collections.enumeration(Collections.singleton(url)));

        BundleEvent bundleEvent = new BundleEvent(BundleEvent.STARTED, bundle);
        SlingScriptEngineManager slingScriptEngineManager = context.getService(SlingScriptEngineManager.class);
        assertNotNull("Expected that the SlingScriptEngineManager would already be registered.", slingScriptEngineManager);
        slingScriptEngineManager.bundleChanged(bundleEvent);
        List<ScriptEngineFactory> factories = slingScriptEngineManager.getEngineFactories();
        int expectedScriptEngineFactories = jvmProvidedScriptEngineFactoryCount + 1;
        assertEquals("Expected " + expectedScriptEngineFactories + " ScriptEngineFactories.", expectedScriptEngineFactories, factories.size());
        assertEquals("Dummy Scripting Engine", factories.get(expectedScriptEngineFactories - 1).getEngineName());

        bundleEvent = new BundleEvent(BundleEvent.STOPPED, bundle);
        slingScriptEngineManager.bundleChanged(bundleEvent);
        expectedScriptEngineFactories--;

        factories = slingScriptEngineManager.getEngineFactories();
        assertEquals("Expected " + expectedScriptEngineFactories + " ScriptEngineFactory.", expectedScriptEngineFactories, factories.size());
        assertNull("Did not expect references to the already unregistered DummyScriptEngineFactory", slingScriptEngineManager
                .getEngineByExtension("dummy"));
        assertNull("Did not expect references to the already unregistered DummyScriptEngineFactory",
                slingScriptEngineManager.getEngineByMimeType("application/x-dummy"));
        assertNull("Did not expect references to the already unregistered DummyScriptEngineFactory",
                slingScriptEngineManager.getEngineByName("Dummy"));
    }

    private int jvmProvidedScriptEngineFactoryCount() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(null);
            return new ScriptEngineManager(ClassLoader.getSystemClassLoader()).getEngineFactories().size();
        }
        finally {
            Thread.currentThread().setContextClassLoader(loader);
        }
    }

    private ScriptEngineFactory mockScriptEngineFactory(String engineName, String engineVersion, List<String> extensions, String
            languageName, String languageVersion, List<String> mimeTypes) {
        ScriptEngineFactory factory = mock(ScriptEngineFactory.class);
        when(factory.getEngineName()).thenReturn(engineName);
        when(factory.getNames()).thenReturn(Collections.singletonList(engineName));
        when(factory.getEngineVersion()).thenReturn(engineVersion);
        when(factory.getExtensions()).thenReturn(extensions);
        when(factory.getLanguageName()).thenReturn(languageName);
        when(factory.getLanguageVersion()).thenReturn(languageVersion);
        when(factory.getMimeTypes()).thenReturn(mimeTypes);
        ScriptEngine scriptEngine = mock(ScriptEngine.class);
        when(factory.getScriptEngine()).thenReturn(scriptEngine);
        when(scriptEngine.getFactory()).thenReturn(factory);
        return factory;
    }

    private File createFactoryFile() throws IOException {
        File tempFile = File.createTempFile("scriptEngine", "tmp");
        tempFile.deleteOnExit();
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write("#I'm a test-comment\n".getBytes());
            fos.write(SCRIPT_ENGINE_FACTORY.getName().getBytes());
        }
        return tempFile;
    }


    private static class TestEventHandler implements EventHandler {

        String topic;
        CountDownLatch latch;
        int processedEvents = 0;

        TestEventHandler(CountDownLatch latch, String topic) {
            this.topic = topic;
            this.latch = latch;
        }

        @Override
        public void handleEvent(Event event) {
            if (event.getTopic().equals(topic)) {
                processedEvents++;
                latch.countDown();
            }
        }
    }
}
