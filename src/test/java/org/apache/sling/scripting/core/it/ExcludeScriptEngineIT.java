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
package org.apache.sling.scripting.core.it;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.CoreOptions.streamBundle;
import static org.ops4j.pax.exam.cm.ConfigurationAdminOptions.newConfiguration;

import java.util.List;

import javax.inject.Inject;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;

import org.apache.sling.scripting.core.impl.jsr223.SlingScriptEngineManager;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.tinybundles.TinyBundle;
import org.ops4j.pax.tinybundles.TinyBundles;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;

/**
 * Verify that loading specific ScriptEngineFactories is affected
 * by the "excludes" value in the configuration.
 */
@RunWith(Enclosed.class)
public class ExcludeScriptEngineIT {

    private ExcludeScriptEngineIT() {
        // private constructor to hide the implicit public one
    }

    /**
     * Base class to share the common parts of LoadedIT and NotLoadedIT
     * tests
     */
    abstract static class BaseIT extends ScriptingCoreTestSupport {

        @Inject
        protected SlingScriptEngineManager scriptEngineManager;

        @Configuration
        public Option[] configuration() {
            return options(
                baseConfiguration(),
                scriptingCoreFragmentBundle(),
                newConfiguration("org.apache.sling.scripting.core.impl.jsr223.SlingScriptEngineManager")
                    .put("excludes", excludesPatterns())
                    .asOption()
            );
        }

        protected abstract String[] excludesPatterns();

        /**
         * Add a fragment to make the org.apache.sling.scripting.core.impl.jsr223 package
         * accessible so the SlingScriptEngineManager reference can be located
         */
        private Option scriptingCoreFragmentBundle() {
            TinyBundle bundle = TinyBundles.bundle()
                .setHeader(Constants.FRAGMENT_HOST, "org.apache.sling.scripting.core")
                .setHeader(Constants.BUNDLE_MANIFESTVERSION, "2")
                .setHeader(Constants.BUNDLE_SYMBOLICNAME, "org.apache.sling.scripting.core.fragment")
                .setHeader(Constants.EXPORT_PACKAGE, "org.apache.sling.scripting.core.impl.jsr223");

            return streamBundle(
                        bundle.build(TinyBundles.bndBuilder())
                    ).noStart();
        }

        /**
         * Locate the nashorn script engine to verify it can be excluded
         */
        protected ScriptEngineFactory getNashornScriptEngineFactory() {
            ScriptEngineFactory factory = null;

            final ScriptEngineManager internalScriptEngineManager;
            final ClassLoader loader = Thread.currentThread().getContextClassLoader();
            try {
                Thread.currentThread().setContextClassLoader(null);
                internalScriptEngineManager = new ScriptEngineManager(ClassLoader.getSystemClassLoader());
            }
            finally {
                Thread.currentThread().setContextClassLoader(loader);
            }

            List<ScriptEngineFactory> engineFactories = internalScriptEngineManager.getEngineFactories();
            for (ScriptEngineFactory scriptEngineFactory : engineFactories) {
                if (scriptEngineFactory.getNames().contains("nashorn")) {
                    factory = scriptEngineFactory;
                    break;
                }
            }
            return factory;
        }

    }

    /**
     * Verify that when exclude pattern in the SlingScriptEngineManager
     * configuration doesn't match that the script engine is accessible
     */
    @RunWith(PaxExam.class)
    public static class NotExcludedIT extends BaseIT {


        @Override
        protected String[] excludesPatterns() {
            return new String[0];
        }

        @Test
        public void testScriptEngineFactoryPresent() throws InvalidSyntaxException {
            ScriptEngineFactory factory = getNashornScriptEngineFactory();
            assertNotNull("Expected nashorn ScriptEngine to exist", factory);

            if (factory != null) {
                // for backward compatibility, the "long name" also matches with the #getEnginesByName call
                assertFalse("Expecting ScriptEngineFactory engines to exist: " + factory.getEngineName(), scriptEngineManager.getEnginesByName(factory.getEngineName()).isEmpty());

                for (final String engineName : factory.getNames()) {
                    assertFalse("Expecting ScriptEngineFactory engines to exist: " + engineName, scriptEngineManager.getEnginesByName(engineName).isEmpty());
                    assertNotNull("Expecting ScriptEngineFactory engine to exist: " + engineName, scriptEngineManager.getEngineByName(engineName));
                }
                for (final String extension : factory.getExtensions()) {
                    assertFalse("Expecting ScriptEngineFactory engines to exist: " + extension, scriptEngineManager.getEnginesByExtension(extension).isEmpty());
                    assertNotNull("Expecting ScriptEngineFactory engine to exist: " + extension, scriptEngineManager.getEngineByExtension(extension));
                }
                for (final String mimetype : factory.getMimeTypes()) {
                    assertFalse("Expecting ScriptEngineFactory engines to exist: " + mimetype, scriptEngineManager.getEnginesByMimeType(mimetype).isEmpty());
                    assertNotNull("Expecting ScriptEngineFactory engine to exist: " + mimetype, scriptEngineManager.getEngineByMimeType(mimetype));
                }
            }
        }

    }

    /**
     * Verify that when exclude pattern in the SlingScriptEngineManager
     * configuration does match that the script engine is accessible
     */
    @RunWith(PaxExam.class)
    public static class ExcludedIT extends BaseIT {

        @Override
        protected String[] excludesPatterns() {
            return new String [] {"fakeNashorn", "nashorn"};
        }

        @Test
        public void testScriptEngineFactoryNotPresent() throws InvalidSyntaxException {
            ScriptEngineFactory factory = getNashornScriptEngineFactory();
            assertNotNull("Expected nashorn ScriptEngine to exist", factory);

            if (factory != null) {
                // for backward compatibility, the "long name" also matches with the #getEnginesByName call
                assertTrue("Expecting ScriptEngineFactory engines to not exist: " + factory.getEngineName(), scriptEngineManager.getEnginesByName(factory.getEngineName()).isEmpty());

                for (final String engineName : factory.getNames()) {
                    assertTrue("Expecting ScriptEngineFactory engines to not exist: " + engineName, scriptEngineManager.getEnginesByName(engineName).isEmpty());
                    assertNull("Expecting ScriptEngineFactory engine to not exist: " + engineName, scriptEngineManager.getEngineByName(engineName));
                }
                for (final String extension : factory.getExtensions()) {
                    assertTrue("Expecting ScriptEngineFactory engines to not exist: " + extension, scriptEngineManager.getEnginesByExtension(extension).isEmpty());
                    assertNull("Expecting ScriptEngineFactory engine to not exist: " + extension, scriptEngineManager.getEngineByExtension(extension));
                }
                for (final String mimetype : factory.getMimeTypes()) {
                    assertTrue("Expecting ScriptEngineFactory engines to not exist: " + mimetype, scriptEngineManager.getEnginesByMimeType(mimetype).isEmpty());
                    assertNull("Expecting ScriptEngineFactory engine to not exist: " + mimetype, scriptEngineManager.getEngineByMimeType(mimetype));
                }
            }
        }

    }

}
