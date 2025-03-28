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
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import java.io.IOException;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Objects;

import org.apache.sling.scripting.core.impl.jsr223.ConfigurableScriptEngine;
import org.apache.sling.scripting.core.impl.jsr223.ConfigurableScriptEngineFactory;
import org.apache.sling.scripting.core.impl.jsr223.ConfigurableScriptEngineFactoryConfiguration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.osgi.service.cm.ConfigurationAdmin;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.sling.scripting.core.impl.jsr223.ConfigurableScriptEngineFactory.CONFIGURATION_PID;
import static org.awaitility.Awaitility.with;
import static org.junit.Assert.assertNotNull;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.cm.ConfigurationAdminOptions.newConfiguration;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class SLING_5720IT extends ScriptingCoreTestSupport {

    @Inject
    private ConfigurationAdmin configurationAdmin;

    @Inject
    private ScriptEngineManager scriptEngineManager;

    @Configuration
    public Option[] configuration() {
        return options(
                baseConfiguration(),
                newConfiguration(CONFIGURATION_PID).put("engineVersion", "2.0").asOption(),
                buildBundleWithBnd(
                        ConfigurableScriptEngine.class,
                        ConfigurableScriptEngineFactory.class,
                        ConfigurableScriptEngineFactoryConfiguration.class));
    }

    @Test
    public void testConfigurableScriptEngineFactory() throws IOException {
        final ScriptEngine fooScriptEngine = scriptEngineManager.getEngineByExtension("foo");
        assertNotNull(fooScriptEngine);
        final Dictionary<String, Object> properties = new Hashtable<>();
        final String[] extensions = {"foo", "bar"};
        properties.put("extensions", extensions);
        configurationAdmin.getConfiguration(CONFIGURATION_PID, null).update(properties);
        with().pollInterval(1, SECONDS)
                .then()
                .await()
                .alias("getting script engine by extension")
                .atMost(10, SECONDS)
                .until(() -> Objects.nonNull(getScriptEngineByExtension("bar")));
    }

    private ScriptEngine getScriptEngineByExtension(final String extension) {
        return scriptEngineManager.getEngineByExtension(extension);
    }
}
