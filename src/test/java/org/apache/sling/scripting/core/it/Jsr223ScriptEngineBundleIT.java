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
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;

import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.options;

/**
 * Verify that adding a JSR223 script engine bundle causes the corresponding
 * ScriptEngineFactory service to be registered (SLING-7545)
 */
@RunWith(PaxExam.class)
public class Jsr223ScriptEngineBundleIT extends ScriptingCoreTestSupport {

    @Inject
    private BundleContext bundleContext;

    @Inject
    private ScriptEngineManager scriptEngineManager;

    private static final String TEST_BUNDLE_ID = "groovy-all";

    private static final String TEST_ENGINE_NAME = "Groovy Scripting Engine";

    @Configuration
    public Option[] configuration() {
        return options(
                baseConfiguration(),
                mavenBundle()
                        .groupId("org.codehaus.groovy")
                        .artifactId(TEST_BUNDLE_ID)
                        .version("2.4.14"));
    }

    @Test
    public void testScriptEngineFactoryPresent() throws InvalidSyntaxException {

        // The added bundle should be active
        Bundle testBundle = null;
        for (Bundle b : bundleContext.getBundles()) {
            if (b.getSymbolicName().equals(TEST_BUNDLE_ID)) {
                testBundle = b;
            }
        }
        assertNotNull("Expecting bundle to be present:" + TEST_BUNDLE_ID, testBundle);
        assertEquals("Expecting bundle to be active:" + TEST_BUNDLE_ID, Bundle.ACTIVE, testBundle.getState());

        // And the corresponding ScriptEngineFactory activated
        ScriptEngineFactory testFactory = null;
        List<ScriptEngineFactory> fac = scriptEngineManager.getEngineFactories();
        for (ScriptEngineFactory f : fac) {
            if (f.getEngineName().equals(TEST_ENGINE_NAME)) {
                testFactory = f;
            }
        }
        assertNotNull("Expecting ScriptEngineFactory to be active: " + TEST_ENGINE_NAME, testFactory);
    }
}
