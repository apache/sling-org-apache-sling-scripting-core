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
package org.apache.sling.scripting.core.it;

import org.apache.sling.testing.paxexam.TestSupport;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.options.ModifiableCompositeOption;

import static org.apache.sling.testing.paxexam.SlingOptions.awaitility;
import static org.apache.sling.testing.paxexam.SlingOptions.slingResourcePresence;
import static org.apache.sling.testing.paxexam.SlingOptions.slingScripting;
import static org.apache.sling.testing.paxexam.SlingOptions.versionResolver;
import static org.ops4j.pax.exam.CoreOptions.composite;
import static org.ops4j.pax.exam.CoreOptions.junitBundles;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.cm.ConfigurationAdminOptions.newConfiguration;

public class ScriptingCoreTestSupport extends TestSupport {

    final int httpPort = findFreePort();

    final Option scriptingCore = mavenBundle().groupId("org.apache.sling").artifactId("org.apache.sling.scripting.core").version(versionResolver);

    public ModifiableCompositeOption baseConfiguration() {
        versionResolver.setVersionFromProject("org.apache.sling", "org.apache.sling.api");
        versionResolver.setVersionFromProject("org.apache.sling", "org.apache.sling.resourceresolver");
        versionResolver.setVersionFromProject("org.apache.sling", "org.apache.sling.servlets.resolver");
        return composite(
            super.baseConfiguration(),
            // Sling Scripting
            slingScripting(),
            newConfiguration("org.apache.felix.http")
                .put("org.osgi.service.http.port", httpPort)
                .asOption(),
            // Sling Scripting Core
            testBundle("bundle.filename"),
            // debugging
            mavenBundle().groupId("org.apache.felix").artifactId("org.apache.felix.inventory").version(versionResolver),
            mavenBundle().groupId("org.apache.felix").artifactId("org.apache.felix.webconsole.plugins.ds").version(versionResolver),
            // testing
            slingResourcePresence(),
            mavenBundle().groupId("org.jsoup").artifactId("jsoup").versionAsInProject(),
            mavenBundle().groupId("org.apache.servicemix.bundles").artifactId("org.apache.servicemix.bundles.hamcrest").versionAsInProject(),
            mavenBundle().groupId("org.apache.sling").artifactId("org.apache.sling.commons.compiler").versionAsInProject(),
            junitBundles(),
            awaitility()
        ).add(
            mavenBundle().groupId("org.apache.felix").artifactId("org.apache.felix.converter").version("1.0.12") // new Sling API dependency
        ).remove(
            scriptingCore
        );
    }

}
