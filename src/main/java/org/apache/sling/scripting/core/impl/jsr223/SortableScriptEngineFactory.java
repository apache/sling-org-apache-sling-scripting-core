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
package org.apache.sling.scripting.core.impl.jsr223;

import java.util.List;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;

public class SortableScriptEngineFactory implements ScriptEngineFactory, Comparable {

    private final ScriptEngineFactory delegate;
    private final int serviceRanking;
    private final long bundleId;

    /**
     * Constructor for implicit {@link ScriptEngineFactory} provided by the platform.
     *
     * @param delegate the actual {@link ScriptEngineFactory}
     */
    SortableScriptEngineFactory(ScriptEngineFactory delegate) {
        this.delegate = delegate;
        serviceRanking = 0;
        bundleId = 0;
    }

    /**
     * Constructor for OSGi registered {@link ScriptEngineFactory} instances.
     *
     * @param delegate       the actual {@link ScriptEngineFactory}
     * @param bundleId       the bundle id of the bundle registering the {@link ScriptEngineFactory}
     * @param serviceRanking the service ranking of the {@link ScriptEngineFactory}
     */
    SortableScriptEngineFactory(ScriptEngineFactory delegate, long bundleId, int serviceRanking) {
        this.delegate = delegate;
        this.bundleId = bundleId;
        this.serviceRanking = serviceRanking;
    }

    @Override
    public String getEngineName() {
        return delegate.getEngineName();
    }

    @Override
    public String getEngineVersion() {
        return delegate.getEngineVersion();
    }

    @Override
    public List<String> getExtensions() {
        return delegate.getExtensions();
    }

    @Override
    public List<String> getMimeTypes() {
        return delegate.getMimeTypes();
    }

    @Override
    public List<String> getNames() {
        return delegate.getNames();
    }

    @Override
    public String getLanguageName() {
        return delegate.getLanguageName();
    }

    @Override
    public String getLanguageVersion() {
        return delegate.getLanguageVersion();
    }

    @Override
    public Object getParameter(String key) {
        return delegate.getParameter(key);
    }

    @Override
    public String getMethodCallSyntax(String obj, String m, String... args) {
        return delegate.getMethodCallSyntax(obj, m, args);
    }

    @Override
    public String getOutputStatement(String toDisplay) {
        return delegate.getOutputStatement(toDisplay);
    }

    @Override
    public String getProgram(String... statements) {
        return delegate.getProgram(statements);
    }

    @Override
    public ScriptEngine getScriptEngine() {
        return delegate.getScriptEngine();
    }

    @Override
    public int compareTo(Object o) {
        SortableScriptEngineFactory other = (SortableScriptEngineFactory) o;
        if (equals(other)) {
            return 0;
        }
        if (serviceRanking == other.serviceRanking) {
            if (bundleId > other.bundleId) {
                return 1;
            } else if (bundleId == other.bundleId) {
                return 0;
            }
            return -1;
        } else if (serviceRanking > other.serviceRanking) {
            return 1;
        }
        return -1;
    }
}
