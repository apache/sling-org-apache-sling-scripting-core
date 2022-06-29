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
 package org.apache.sling.scripting.core.impl.helper;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import javax.script.Bindings;

import org.apache.sling.api.scripting.LazyBindings;
import org.jetbrains.annotations.NotNull;

public class ProtectedBindings extends LazyBindings implements Bindings {

    private static final long serialVersionUID = -5988579857015221345L;

    private final Bindings wrapped;
    private final Set<String> protectedKeys;

    public ProtectedBindings(Bindings wrapped, Set<String> protectedKeys) {
        this.wrapped = wrapped;
        this.protectedKeys = protectedKeys;
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalArgumentException if the key is protected
     */
    @Override
    public Object put(String key, Object value) {
        if (protectedKeys.contains(key)) {
            throw new IllegalArgumentException(String.format("Key %s is protected.", key));
        }
        return wrapped.put(key, value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void putAll(Map<? extends String, ? extends Object> toMerge) {
        for (Entry<? extends String, ? extends Object> entry : toMerge.entrySet()) {
            String key = entry.getKey();
            if (!protectedKeys.contains(key)) {
                wrapped.put(key, entry.getValue());
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalArgumentException if the key is protected
     */
    @Override
    public Object remove(Object key) {
        if (protectedKeys.contains(key)) {
            throw new IllegalArgumentException(String.format("Key %s is protected.", key));
        }
        return wrapped.remove(key);
    }

    /**
     * The clear operation is not supported.
     */
    @Override
    public void clear() {
        throw new UnsupportedOperationException("ProtectedBindings does not support clear()");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean containsValue(Object value) {
        return wrapped.containsValue(value);
    }

    /**
     * Returns a Set view of the mappings contains in this map. The Set is
     * unmodifiable.
     *
     * @return an unmodifiable Set view of the map
     */
    @Override
    @NotNull
    public Set<Entry<String, Object>> entrySet() {
        return Collections.unmodifiableSet(wrapped.entrySet());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEmpty() {
        return wrapped.isEmpty();
    }

    /**
     * Returns a Set view of the keys contained in this map. The Set is
     * unmodifiable.
     *
     * @return an unmodifiable Set view of the map's keys
     */
    @Override
    @NotNull
    public Set<String> keySet() {
        return Collections.unmodifiableSet(wrapped.keySet());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int size() {
        return wrapped.size();
    }

    /**
     * Returns a Collection view of the values contained in this map. The
     * Collection is unmodifiable.
     *
     * @return an unmodifiable Collection view of the map's values
     */
    @Override
    @NotNull
    public Collection<Object> values() {
        return Collections.unmodifiableCollection(wrapped.values());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean containsKey(Object key) {
        return wrapped.containsKey(key);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object get(Object key) {
        return wrapped.get(key);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((protectedKeys == null) ? 0 : protectedKeys.hashCode());
        result = prime * result + ((wrapped == null) ? 0 : wrapped.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        ProtectedBindings other = (ProtectedBindings) obj;
        if (protectedKeys == null) {
            if (other.protectedKeys != null)
                return false;
        } else if (!protectedKeys.equals(other.protectedKeys))
            return false;
        if (wrapped == null) {
            if (other.wrapped != null)
                return false;
        } else if (!wrapped.equals(other.wrapped))
            return false;
        return true;
    }

}
