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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.sling.testing.paxexam.TestSupport;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.options.CompositeOption;
import org.ops4j.pax.exam.options.ModifiableCompositeOption;

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
    final Option slingApi = mavenBundle().groupId("org.apache.sling").artifactId("org.apache.sling.api").version(versionResolver);
    final Option osgiConverter = mavenBundle().groupId("org.osgi").artifactId("org.osgi.util.converter").version("1.0.0");



    public ModifiableCompositeOption baseConfiguration() {
        final Option slingScripting = slingScripting().remove(scriptingCore);
        return composite(
            super.baseConfiguration(),
            // Sling Scripting
            composite(remove(new Option[] {slingScripting}, slingApi)),
            mavenBundle().groupId("org.apache.sling").artifactId("org.apache.sling.api").versionAsInProject(),
            osgiConverter,
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
            junitBundles()
        );
    }

    // move below helpers for deep removal to Pax Exam

    private static List<Option> expand(final Option[] options) {
        final List<Option> expanded = new ArrayList<>();
        if (options != null) {
            for (final Option option : options) {
                if (option != null) {
                    if (option instanceof CompositeOption) {
                        expanded.addAll(Arrays.asList(((CompositeOption) option).getOptions()));
                    } else {
                        expanded.add(option);
                    }
                }
            }
        }
        return expanded;
    }

    static Option[] remove(final Option[] options, final Option ... toRemove) {
        final List<Option> expanded = expand(options);
        for (Option toRemoveOption : toRemove) {
            if (toRemoveOption instanceof CompositeOption) {
                expanded.removeAll(Arrays.asList(((CompositeOption) toRemoveOption).getOptions()));
            } else {
                expanded.removeAll(Collections.singleton(toRemoveOption));
            }
        }
        return expanded.toArray(new Option[0]);
    }

}
