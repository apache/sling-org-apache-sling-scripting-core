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
package org.apache.sling.scripting.core.impl;

import java.util.List;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;

import org.apache.sling.api.resource.Resource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(
    service = {SlingScriptEnginePicker.class},
    property = {
        Constants.SERVICE_VENDOR + "=The Apache Software Foundation",
        Constants.SERVICE_DESCRIPTION + "=Apache Sling Scripting SlingScriptEnginePicker"
    }
)
public class SlingScriptEnginePicker {

    /**
     * The property contains the required language mapping
     *
     * "sling:scripting": [
     * "html=The HTL Templating Language:1.4"
     * ]
     * "sling:scripting": [
     * "html=Thymeleaf:3.0"
     * ]
     */
    private static String SLING_SCRIPTING = "sling:scripting";

    private final Logger logger = LoggerFactory.getLogger(SlingScriptEnginePicker.class);

    public SlingScriptEnginePicker() {
    }

    @Nullable ScriptEngine pickScriptEngine(@NotNull final List<ScriptEngine> scriptEngines, @NotNull Resource resource, @NotNull String extension) {
        final String scriptingMapping = findScriptingMapping(resource, extension);
        logger.debug("scripting mapping: {}", scriptingMapping);
        if (scriptingMapping == null || scriptingMapping.isEmpty()) {
            return scriptEngines.size() > 0 ? scriptEngines.get(0) : null;
        }

        final Conditions conditions = parseScriptingMapping(scriptingMapping);
        logger.debug("scripting conditions: {}", conditions);
        for (final ScriptEngine scriptEngine : scriptEngines) {
            final ScriptEngineFactory scriptEngineFactory = scriptEngine.getFactory();
            if (conditions.matches(scriptEngineFactory.getLanguageName())) {
                logger.debug("script engine {} found for conditions {}", scriptEngineFactory.getEngineName(), conditions);
                return scriptEngine;
            }
        }
        return null;
    }

    private String findScriptingMapping(@NotNull final Resource resource, @NotNull final String extension) {
        final String[] mappings = resource.getValueMap().get(SLING_SCRIPTING, String[].class);
        if (mappings != null) {
            final String start = String.format("%s=", extension);
            for (final String mapping : mappings) {
                if (mapping.startsWith(start)) {
                    return mapping.substring(start.length());
                }
            }
            return resource.getParent() != null ? findScriptingMapping(resource.getParent(), extension) : null;
        } else if (resource.getParent() != null) {
            return findScriptingMapping(resource.getParent(), extension);
        } else {
            return null;
        }
    }

    /**
     * TODO Take language version, engine name and engine version into account
     */
    private Conditions parseScriptingMapping(@NotNull final String scriptingMapping) {
        final String[] values = scriptingMapping.split(":");
        final String languageName = values[0];
        final String languageVersion = values.length > 1 ? values[1] : null;
        // values[2] -> engine name
        // values[3] -> engine version
        return new Conditions(languageName, languageVersion);
    }

    private class Conditions {

        final String languageName;

        final String languageVersion;

        Conditions(@NotNull final String languageName, @Nullable final String languageVersion) {
            this.languageName = languageName;
            this.languageVersion = languageVersion;
        }

        boolean matches(final String languageName) {
            return this.languageName.equalsIgnoreCase(languageName);
        }

        @Override
        public String toString() {
            return String.format("%s, %s", languageName, languageVersion);
        }

    }

}
