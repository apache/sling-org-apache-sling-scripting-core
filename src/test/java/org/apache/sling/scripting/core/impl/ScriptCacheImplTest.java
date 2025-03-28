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
package org.apache.sling.scripting.core.impl;

import java.util.List;

import org.apache.sling.scripting.api.CachedScript;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ScriptCacheImplTest {

    @Test
    public void testRemoval() {
        final ScriptCacheImplConfiguration config = Mockito.mock(ScriptCacheImplConfiguration.class);
        Mockito.when(config.org_apache_sling_scripting_cache_size()).thenReturn(10);
        final ScriptCacheImpl cache = new ScriptCacheImpl(config);

        final CachedScript script1 = Mockito.mock(CachedScript.class);
        Mockito.when(script1.getScriptPath()).thenReturn("/foo/bar/script1");
        cache.putScript(script1);

        final CachedScript script2 = Mockito.mock(CachedScript.class);
        Mockito.when(script2.getScriptPath()).thenReturn("/foo/bar/script2");
        cache.putScript(script2);

        final CachedScript script3 = Mockito.mock(CachedScript.class);
        Mockito.when(script3.getScriptPath()).thenReturn("/foobar");
        cache.putScript(script3);

        List<String> scripts = cache.getCachedScripts();
        assertEquals(3, scripts.size());
        assertTrue(scripts.contains(script1.getScriptPath()));
        assertTrue(scripts.contains(script2.getScriptPath()));
        assertTrue(scripts.contains(script3.getScriptPath()));

        cache.removeScript("/foo");
        scripts = cache.getCachedScripts();
        assertEquals(1, scripts.size());
        assertTrue(scripts.contains(script3.getScriptPath()));

        cache.removeScript("/foobar");
        scripts = cache.getCachedScripts();
        assertTrue(scripts.isEmpty());
    }
}
