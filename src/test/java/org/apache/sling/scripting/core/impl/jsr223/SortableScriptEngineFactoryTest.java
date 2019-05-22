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

import java.util.Collections;
import java.util.List;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SortableScriptEngineFactoryTest {

    @Test
    public void testDelegateConstructor() {
        ScriptEngineFactory delegate = mock(ScriptEngineFactory.class);
        SortableScriptEngineFactory sortableScriptEngineFactory = new SortableScriptEngineFactory(delegate);
        assertEquals(0, sortableScriptEngineFactory.compareTo(getCompareFactory(0, 0)));
    }

    @Test
    public void getEngineName() {
        String answer = "answer";
        ScriptEngineFactory scriptEngineFactory = mock(ScriptEngineFactory.class);
        when(scriptEngineFactory.getEngineName()).thenReturn(answer);
        SortableScriptEngineFactory sortableScriptEngineFactory = new SortableScriptEngineFactory(scriptEngineFactory, 0, 0);
        assertEquals(answer, sortableScriptEngineFactory.getEngineName());
    }

    @Test
    public void getEngineVersion() {
        String answer = "answer";
        ScriptEngineFactory scriptEngineFactory = mock(ScriptEngineFactory.class);
        when(scriptEngineFactory.getEngineVersion()).thenReturn(answer);
        SortableScriptEngineFactory sortableScriptEngineFactory = new SortableScriptEngineFactory(scriptEngineFactory, 0, 0);
        assertEquals(answer, sortableScriptEngineFactory.getEngineVersion());
    }

    @Test
    public void getExtensions() {
        List<String> answer = Collections.emptyList();
        ScriptEngineFactory scriptEngineFactory = mock(ScriptEngineFactory.class);
        when(scriptEngineFactory.getExtensions()).thenReturn(answer);
        SortableScriptEngineFactory sortableScriptEngineFactory = new SortableScriptEngineFactory(scriptEngineFactory, 0, 0);
        assertEquals(answer, sortableScriptEngineFactory.getExtensions());
    }

    @Test
    public void getMimeTypes() {
        List<String> answer = Collections.emptyList();
        ScriptEngineFactory scriptEngineFactory = mock(ScriptEngineFactory.class);
        when(scriptEngineFactory.getMimeTypes()).thenReturn(answer);
        SortableScriptEngineFactory sortableScriptEngineFactory = new SortableScriptEngineFactory(scriptEngineFactory, 0, 0);
        assertEquals(answer, sortableScriptEngineFactory.getMimeTypes());
    }

    @Test
    public void getNames() {
        List<String> answer = Collections.emptyList();
        ScriptEngineFactory scriptEngineFactory = mock(ScriptEngineFactory.class);
        when(scriptEngineFactory.getNames()).thenReturn(answer);
        SortableScriptEngineFactory sortableScriptEngineFactory = new SortableScriptEngineFactory(scriptEngineFactory, 0, 0);
        assertEquals(answer, sortableScriptEngineFactory.getNames());
    }

    @Test
    public void getLanguageName() {
        String answer = "answer";
        ScriptEngineFactory scriptEngineFactory = mock(ScriptEngineFactory.class);
        when(scriptEngineFactory.getLanguageName()).thenReturn(answer);
        SortableScriptEngineFactory sortableScriptEngineFactory = new SortableScriptEngineFactory(scriptEngineFactory, 0, 0);
        assertEquals(answer, sortableScriptEngineFactory.getLanguageName());
    }

    @Test
    public void getLanguageVersion() {
        String answer = "answer";
        ScriptEngineFactory scriptEngineFactory = mock(ScriptEngineFactory.class);
        when(scriptEngineFactory.getLanguageVersion()).thenReturn(answer);
        SortableScriptEngineFactory sortableScriptEngineFactory = new SortableScriptEngineFactory(scriptEngineFactory, 0, 0);
        assertEquals(answer, sortableScriptEngineFactory.getLanguageVersion());
    }

    @Test
    public void getParameter() {
        String answer = "answer";
        String key = "key";
        ScriptEngineFactory scriptEngineFactory = mock(ScriptEngineFactory.class);
        when(scriptEngineFactory.getParameter(key)).thenReturn(answer);
        SortableScriptEngineFactory sortableScriptEngineFactory = new SortableScriptEngineFactory(scriptEngineFactory, 0, 0);
        assertEquals(answer, sortableScriptEngineFactory.getParameter(key));
    }

    @Test
    public void getMethodCallSyntax() {
        String answer = "answer";
        String obj = "obj";
        String m = "m";
        String[] args = new String[0];
        ScriptEngineFactory scriptEngineFactory = mock(ScriptEngineFactory.class);
        when(scriptEngineFactory.getMethodCallSyntax(obj, m, args)).thenReturn(answer);
        SortableScriptEngineFactory sortableScriptEngineFactory = new SortableScriptEngineFactory(scriptEngineFactory, 0, 0);
        assertEquals(answer, sortableScriptEngineFactory.getMethodCallSyntax(obj, m, args));
    }

    @Test
    public void getOutputStatement() {
        String answer = "answer";
        String toDisplay = "toDisplay";
        ScriptEngineFactory scriptEngineFactory = mock(ScriptEngineFactory.class);
        when(scriptEngineFactory.getOutputStatement(toDisplay)).thenReturn(answer);
        SortableScriptEngineFactory sortableScriptEngineFactory = new SortableScriptEngineFactory(scriptEngineFactory, 0, 0);
        assertEquals(answer, sortableScriptEngineFactory.getOutputStatement(toDisplay));
    }

    @Test
    public void getProgram() {
        String answer = "answer";
        String[] statements = new String[0];
        ScriptEngineFactory scriptEngineFactory = mock(ScriptEngineFactory.class);
        when(scriptEngineFactory.getProgram(statements)).thenReturn(answer);
        SortableScriptEngineFactory sortableScriptEngineFactory = new SortableScriptEngineFactory(scriptEngineFactory, 0, 0);
        assertEquals(answer, sortableScriptEngineFactory.getProgram(statements));
    }

    @Test
    public void getScriptEngine() {
        ScriptEngine scriptEngine = mock(ScriptEngine.class);
        ScriptEngineFactory scriptEngineFactory = mock(ScriptEngineFactory.class);
        when(scriptEngineFactory.getScriptEngine()).thenReturn(scriptEngine);
        SortableScriptEngineFactory sortableScriptEngineFactory = new SortableScriptEngineFactory(scriptEngineFactory, 0, 0);
        assertEquals(scriptEngine, sortableScriptEngineFactory.getScriptEngine());
    }

    @Test
    public void compareTo() {
        SortableScriptEngineFactory same = getCompareFactory(1, 2);
        assertEquals(0, same.compareTo(same));
        assertEquals(0, getCompareFactory(0, 0).compareTo(getCompareFactory(0, 0)));
        assertEquals(1, getCompareFactory(1, 0).compareTo(getCompareFactory(0, 0)));
        assertEquals(1, getCompareFactory(0, 1).compareTo(getCompareFactory(0, 0)));
        assertEquals(1, getCompareFactory(1, 1).compareTo(getCompareFactory(1, 0)));
        assertEquals(1, getCompareFactory(1, 1).compareTo(getCompareFactory(0, 1)));
        assertEquals(-1, getCompareFactory(0, 0).compareTo(getCompareFactory(1, 0)));
        assertEquals(-1, getCompareFactory(0, 0).compareTo(getCompareFactory(0, 1)));
        assertEquals(-1, getCompareFactory(0, 1).compareTo(getCompareFactory(1, 1)));
        assertEquals(-1, getCompareFactory(0, 0).compareTo(getCompareFactory(0, 1)));
    }

    private SortableScriptEngineFactory getCompareFactory(long bundleId, int serviceRanking) {
        return new SortableScriptEngineFactory(mock(ScriptEngineFactory.class), bundleId, serviceRanking);
    }
}
