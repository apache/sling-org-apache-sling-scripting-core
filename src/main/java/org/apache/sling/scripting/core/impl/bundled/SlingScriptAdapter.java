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
package org.apache.sling.scripting.core.impl.bundled;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.SyntheticResource;
import org.apache.sling.api.scripting.SlingBindings;
import org.apache.sling.api.scripting.SlingScript;
import org.jetbrains.annotations.NotNull;

class SlingScriptAdapter extends SyntheticResource implements SlingScript
{
    SlingScriptAdapter(ResourceResolver resolver, String path, String type) {
        super(resolver, path, type);
    }

    @Override
    public @NotNull Resource getScriptResource()
    {
        return this;
    }

    @Override
    public Object eval(@NotNull SlingBindings props)
    {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public Object call(@NotNull SlingBindings props, @NotNull String method, Object... args)
    {
        throw new IllegalStateException("Not implemented");
    }
}
