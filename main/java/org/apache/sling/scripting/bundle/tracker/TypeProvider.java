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
package org.apache.sling.scripting.bundle.tracker;

import org.jetbrains.annotations.NotNull;
import org.osgi.annotation.versioning.ProviderType;
import org.osgi.framework.Bundle;

/**
 * A {@code TypeProvider} keeps an association between a {@link BundledRenderUnitCapability} and the bundle that provides it.
 */
@ProviderType
public interface TypeProvider {

    /**
     * Returns the {@link BundledRenderUnitCapability}.
     *
     * @return the {@link BundledRenderUnitCapability}
     */
    @NotNull BundledRenderUnitCapability getBundledRenderUnitCapability();

    /**
     * Returns the providing bundle.
     *
     * @return the providing bundle
     */
    @NotNull Bundle getBundle();

    /**
     * Returns {@code true} if the bundle provides precompiled scripts.
     *
     * @return {@code true} if the bundle provides precompiled scripts, {@code false} otherwise
     */
    boolean isPrecompiled();
}
