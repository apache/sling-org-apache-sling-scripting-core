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
package org.apache.sling.scripting.core.it;

import static org.apache.sling.testing.paxexam.SlingOptions.slingQuickstartOakTar;
import static org.apache.sling.testing.paxexam.SlingOptions.slingScriptingHtl;
import static org.apache.sling.testing.paxexam.SlingOptions.slingScriptingThymeleaf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.ops4j.pax.exam.CoreOptions.composite;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.cm.ConfigurationAdminOptions.factoryConfiguration;

import java.io.IOException;
import javax.inject.Inject;
import javax.script.ScriptEngineFactory;

import org.apache.sling.api.adapter.AdapterFactory;
import org.apache.sling.api.servlets.ServletResolver;
import org.apache.sling.resource.presence.ResourcePresence;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.ProbeBuilder;
import org.ops4j.pax.exam.TestProbeBuilder;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.ops4j.pax.exam.util.Filter;
import org.osgi.service.http.HttpService;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class HtmlScriptingIT extends ScriptingCoreTestSupport {

    @Inject
    protected ServletResolver servletResolver;

    // SlingScriptAdapterFactory
    @Inject
    @Filter(value = "(adapters=org.apache.sling.api.scripting.SlingScript)")
    protected AdapterFactory adapterFactory;

    @Inject
    protected HttpService httpService;

    @Inject
    @Filter(value = "(names=htl)")
    protected ScriptEngineFactory htlScriptEngineFactory;

    @Inject
    @Filter(value = "(names=thymeleaf)")
    protected ScriptEngineFactory thymeleafScriptEngineFactory;

    @Inject
    @Filter(value = "(path=/apps/htl)")
    private ResourcePresence htl;

    @Inject
    @Filter(value = "(path=/apps/thymeleaf)")
    private ResourcePresence thymeleaf;

    @Inject
    @Filter(value = "(path=/content/scripting)")
    private ResourcePresence scripting;

    @Configuration
    public Option[] configuration() {
        final String workingDirectory = workingDirectory();
        return options(
            composite(
                super.baseConfiguration(),
                slingScriptingHtl(),
                slingScriptingThymeleaf(),
                slingQuickstartOakTar(workingDirectory, httpPort),
                factoryConfiguration("org.apache.sling.jcr.repoinit.RepositoryInitializer")
                    .put("scripts", new String[]{
                        "create path (sling:OrderedFolder) /content\nset ACL for everyone\nallow jcr:read on /content\nend"
                    })
                    .asOption(),
                factoryConfiguration("org.apache.sling.resource.presence.internal.ResourcePresenter")
                    .put("path", "/apps/htl")
                    .asOption(),
                factoryConfiguration("org.apache.sling.resource.presence.internal.ResourcePresenter")
                    .put("path", "/apps/thymeleaf")
                    .asOption(),
                factoryConfiguration("org.apache.sling.resource.presence.internal.ResourcePresenter")
                    .put("path", "/content/scripting")
                    .asOption()
            ).remove(
                scriptingCore
            )
        );
    }

    @ProbeBuilder
    public TestProbeBuilder probeConfiguration(final TestProbeBuilder testProbeBuilder) {
        testProbeBuilder.setHeader("Sling-Initial-Content", "initial-content");
        return testProbeBuilder;
    }

    @Test
    public void testHtlTitle() throws IOException {
        final String url = String.format("http://localhost:%s/scripting/htl.html", httpPort());
        final Document document = Jsoup.connect(url).get();
        assertThat(document.title(), is("Sling Scripting HTL"));
    }

    @Test
    public void testThymeleafTitle() throws IOException {
        final String url = String.format("http://localhost:%s/scripting/thymeleaf.html", httpPort());
        final Document document = Jsoup.connect(url).get();
        assertThat(document.title(), is("Sling Scripting Thymeleaf"));
    }

}
