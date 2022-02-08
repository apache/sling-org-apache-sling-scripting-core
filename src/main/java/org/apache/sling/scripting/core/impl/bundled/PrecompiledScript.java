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

import java.io.StringReader;
import java.util.Set;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.scripting.core.impl.ServiceCache;
import org.apache.sling.scripting.spi.bundle.TypeProvider;
import org.jetbrains.annotations.NotNull;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

class PrecompiledScript extends AbstractBundledRenderUnit {

    private static final StringReader EMPTY_READER = new StringReader(StringUtils.EMPTY);
    private final Class<?> clazz;
    private volatile Object instance;

    PrecompiledScript(@NotNull Set<TypeProvider> providers, @NotNull BundleContext context, @NotNull Bundle bundle, @NotNull String path, @NotNull Class<?> clazz,
                      @NotNull String scriptEngineName, @NotNull String scriptExtension,
                      @NotNull ScriptContextProvider scriptContextProvider, @NotNull ServiceCache serviceCache) {
        super(providers, context, bundle, path, scriptEngineName, scriptExtension, scriptContextProvider, serviceCache);
        this.clazz = clazz;
    }

    @Override
    @NotNull
    public String getName() {
        return clazz.getName();
    }

    @Override
    public void eval(@NotNull ScriptEngine scriptEngine, @NotNull ScriptContext context) throws ScriptException {
        scriptEngine.eval(EMPTY_READER, context);
    }

    @Override
    public @NotNull Object getUnit() {
        Object localInstance = instance;
        if (localInstance == null) {
            synchronized (this) {
                localInstance = instance;
                if (localInstance == null) {
                    try {
                        localInstance = clazz.getDeclaredConstructor().newInstance();
                        instance = localInstance;
                    } catch (Exception e) {
                        throw new IllegalStateException("Cannot instantiate class " + clazz.getName(), e);
                    }
                }
            }
        }
        return localInstance;
    }

}
