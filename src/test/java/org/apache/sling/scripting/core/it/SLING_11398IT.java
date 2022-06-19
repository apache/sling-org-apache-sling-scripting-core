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

import static org.junit.Assert.assertNotNull;
import static org.ops4j.pax.exam.CoreOptions.composite;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.CoreOptions.vmOption;

import javax.inject.Inject;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.options.ModifiableCompositeOption;
import org.ops4j.pax.exam.options.extra.VMOption;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;

/**
 * Tests for SLING-11398 - verify serviceloader ScriptEngineFactory defined in a fragment bundle
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class SLING_11398IT extends ScriptingCoreTestSupport {

    @Inject
    private ScriptEngineManager scriptEngineManager;

    @Configuration
    public Option[] configuration() {
        return options(
            baseConfiguration(),
            optionalRemoteDebug(),
            mavenBundle().groupId("org.codehaus.groovy").artifactId("groovy").version("3.0.9").startLevel(1),
            // factory is defined in this fragment artifact
            mavenBundle().groupId("org.codehaus.groovy").artifactId("groovy-jsr223").version("3.0.9").noStart()
        );
    }

    /**
     * Optionally configure remote debugging on the port supplied by the "debugPort"
     * system property.
     */
    protected ModifiableCompositeOption optionalRemoteDebug() {
        VMOption option = null;
        String property = System.getProperty("debugPort");
        if (property != null) {
            option = vmOption(String.format("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=%s", property));
        }
        return composite(option);
    }

    @Test
    public void testGroovyScriptEngineAvailable() {
        ScriptEngine engineByExtension = scriptEngineManager.getEngineByExtension("groovy");
        assertNotNull(engineByExtension);
    }

}
