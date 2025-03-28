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
import org.ops4j.pax.exam.options.extra.VMOption;

import static org.apache.sling.testing.paxexam.SlingOptions.awaitility;
import static org.apache.sling.testing.paxexam.SlingOptions.eventadmin;
import static org.apache.sling.testing.paxexam.SlingOptions.scr;
import static org.ops4j.pax.exam.CoreOptions.composite;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.vmOption;
import static org.ops4j.pax.exam.cm.ConfigurationAdminOptions.factoryConfiguration;
import static org.ops4j.pax.exam.cm.ConfigurationAdminOptions.newConfiguration;

public class ScriptingCoreTestSupport extends TestSupport {

    final int httpPort = findFreePort();

    public ModifiableCompositeOption baseConfiguration() {
        return composite(
                super.baseConfiguration(),
                newConfiguration("org.apache.felix.http")
                        .put("org.osgi.service.http.port", httpPort)
                        .asOption(),
                mavenBundle()
                        .groupId("org.osgi")
                        .artifactId("org.osgi.util.converter")
                        .versionAsInProject(),
                mavenBundle()
                        .groupId("org.apache.felix")
                        .artifactId("org.apache.felix.http.servlet-api")
                        .version("3.0.0"),
                mavenBundle()
                        .groupId("org.apache.felix")
                        .artifactId("org.apache.felix.http.jetty12")
                        .version("1.0.26"),
                scr(),
                eventadmin(),
                mavenBundle().groupId("commons-io").artifactId("commons-io").versionAsInProject(),
                mavenBundle()
                        .groupId("org.apache.commons")
                        .artifactId("commons-lang3")
                        .versionAsInProject(),
                mavenBundle()
                        .groupId("org.apache.sling")
                        .artifactId("org.apache.sling.commons.mime")
                        .versionAsInProject(),
                mavenBundle()
                        .groupId("org.apache.sling")
                        .artifactId("org.apache.sling.commons.osgi")
                        .version("2.4.2"),
                mavenBundle()
                        .groupId("org.apache.sling")
                        .artifactId("org.apache.sling.api")
                        .versionAsInProject(),
                mavenBundle()
                        .groupId("org.apache.sling")
                        .artifactId("org.apache.sling.scripting.api")
                        .versionAsInProject(),
                mavenBundle()
                        .groupId("org.apache.sling")
                        .artifactId("org.apache.sling.scripting.spi")
                        .versionAsInProject(),
                mavenBundle()
                        .groupId("org.apache.sling")
                        .artifactId("org.apache.sling.serviceusermapper")
                        .versionAsInProject(),
                mavenBundle()
                        .groupId("org.apache.sling")
                        .artifactId("org.apache.sling.resource.presence")
                        .versionAsInProject(),
                factoryConfiguration("org.apache.sling.serviceusermapping.impl.ServiceUserMapperImpl.amended")
                        .put("user.mapping", new String[] {"org.apache.sling.resource.presence=[sling-readall]"})
                        .asOption(),
                // Sling Scripting Core
                testBundle("bundle.filename"),
                // testing
                mavenBundle().groupId("org.jsoup").artifactId("jsoup").versionAsInProject(),
                awaitility(),
                optionalRemoteDebug());
    }

    /**
     * Optionally configure remote debugging on the port supplied by the "debugPort"
     * system property.
     */
    protected ModifiableCompositeOption optionalRemoteDebug() {
        VMOption option = null;
        String property = System.getProperty("debugPort");
        if (property != null) {
            option = vmOption(String.format("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=%s", property));
        }
        return composite(option);
    }

    protected Option webconsolesecurityprovider() {
        return mavenBundle()
                .groupId("org.apache.sling")
                .artifactId("org.apache.sling.extensions.webconsolesecurityprovider")
                .version("1.2.8");
    }
}
