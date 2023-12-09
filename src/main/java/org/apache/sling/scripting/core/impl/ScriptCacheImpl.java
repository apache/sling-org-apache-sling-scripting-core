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

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.sling.scripting.api.CachedScript;
import org.apache.sling.scripting.api.ScriptCache;
import org.apache.sling.scripting.core.impl.helper.CachingMap;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(
    immediate = true, // cache should be immediate
    service = {ScriptCache.class}
)
@Designate(
    ocd = ScriptCacheImplConfiguration.class
)
/**
 * The {@code ScriptCache} stores information about {@link CompiledScript} instances evaluated by various {@link ScriptEngine}s that
 * implement the {@link Compilable} interface.
 */
public class ScriptCacheImpl implements ScriptCache {

    private final Logger logger = LoggerFactory.getLogger(ScriptCacheImpl.class);

    public static final int DEFAULT_CACHE_SIZE = 65536;

    private final Map<String, SoftReference<CachedScript>> internalMap;

    private final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
    private final Lock readLock = rwl.readLock();
    private final Lock writeLock = rwl.writeLock();

    @Activate
    public ScriptCacheImpl(final ScriptCacheImplConfiguration configuration) {
        this.internalMap = new CachingMap<>(configuration.org_apache_sling_scripting_cache_size());
    }

    @Override
    public CachedScript getScript(String scriptPath) {
        readLock.lock();
        SoftReference<CachedScript> reference = null;
        try {
            reference = internalMap.get(scriptPath);
        } finally {
            readLock.unlock();
        }
        return reference != null ? reference.get() : null;
    }

    @Override
    public void putScript(CachedScript script) {
        writeLock.lock();
        try {
            SoftReference<CachedScript> reference = new SoftReference<>(script);
            internalMap.put(script.getScriptPath(), reference);
            logger.debug("Added script {} to script cache.", script.getScriptPath());
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void clear() {
        writeLock.lock();
        try {
            internalMap.clear();
            logger.debug("Cleared script cache.");
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public boolean removeScript(final String scriptPath) {
        writeLock.lock();
        try {
            boolean result = false;
            if (scriptPath.endsWith("/") ) {
                // prefix removal
                final Set<String> removal = new HashSet<>();
                for(final Map.Entry<String, SoftReference<CachedScript>> entry : internalMap.entrySet()) {
                    if ( entry.getKey().startsWith(scriptPath) ) {
                        removal.add(entry.getKey());
                    }
                }
                for(final String key : removal) {
                    internalMap.remove(key);
                    logger.debug("Detected removal for {} - removed entry {} from the cache.", scriptPath, key);
                    result = true;
                }
            } else {
                result = internalMap.remove(scriptPath) != null;
                if (result) {
                    logger.debug("Removed script {} from script cache.", scriptPath);
                }
            }
            return result;
        } finally {
            writeLock.unlock();
        }
    }

    protected List<String> getCachedScripts() {
        readLock.lock();
        try {
            return new ArrayList<>(internalMap.keySet());
        } finally {
            readLock.unlock();
        }
    }
}
