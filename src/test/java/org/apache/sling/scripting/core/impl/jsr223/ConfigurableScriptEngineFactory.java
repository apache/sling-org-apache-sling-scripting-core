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

import java.util.Arrays;
import java.util.List;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;

import org.apache.sling.scripting.api.AbstractScriptEngineFactory;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.metatype.annotations.Designate;

@Component(
    service = ScriptEngineFactory.class,
    configurationPid = ConfigurableScriptEngineFactory.CONFIGURATION_PID,
    property = {
        Constants.SERVICE_DESCRIPTION + "=Apache Sling Scripting Configurable ScriptEngineFactory",
        Constants.SERVICE_VENDOR + "=The Apache Software Foundation"
    }
)
@Designate(
    ocd = ConfigurableScriptEngineFactoryConfiguration.class
)
public class ConfigurableScriptEngineFactory extends AbstractScriptEngineFactory {

    private ConfigurableScriptEngineFactoryConfiguration configuration;

    public static final String CONFIGURATION_PID = "foo";

    public ConfigurableScriptEngineFactory() {
    }

    @Activate
    private void activate(final ConfigurableScriptEngineFactoryConfiguration configuration) {
        this.configuration = configuration;
    }

    @Modified
    private void modified(final ConfigurableScriptEngineFactoryConfiguration configuration) {
        this.configuration = configuration;
    }

    @Deactivate
    private void deactivate() {
        this.configuration = null;
    }

    @Override
    public String getEngineName() {
        return configuration.engineName();
    }

    @Override
    public String getEngineVersion() {
        return configuration.engineVersion();
    }

    @Override
    public List<String> getExtensions() {
        return Arrays.asList(configuration.extensions());
    }

    @Override
    public List<String> getMimeTypes() {
        return Arrays.asList(configuration.mimeTypes());
    }

    @Override
    public List<String> getNames() {
        return Arrays.asList(configuration.names());
    }

    @Override
    public String getLanguageName() {
        return configuration.languageName();
    }

    @Override
    public String getLanguageVersion() {
        return configuration.languageVersion();
    }

    @Override
    public ScriptEngine getScriptEngine() {
        return new ConfigurableScriptEngine(this);
    }

}
