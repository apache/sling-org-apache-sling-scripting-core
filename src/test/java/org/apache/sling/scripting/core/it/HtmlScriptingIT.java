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

import static org.junit.Assert.assertEquals;
import static org.ops4j.pax.exam.CoreOptions.composite;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.CoreOptions.streamBundle;
import static org.ops4j.pax.exam.cm.ConfigurationAdminOptions.factoryConfiguration;
import static org.ops4j.pax.exam.cm.ConfigurationAdminOptions.newConfiguration;

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
import org.ops4j.pax.exam.options.ModifiableCompositeOption;
import org.ops4j.pax.exam.options.UrlProvisionOption;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.ops4j.pax.exam.util.Filter;
import org.ops4j.pax.tinybundles.TinyBundle;
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

    private static ModifiableCompositeOption management() {
        return composite(
            mavenBundle().groupId("org.apache.aries").artifactId("org.apache.aries.util").version("1.1.3"),
            mavenBundle().groupId("org.apache.aries.jmx").artifactId("org.apache.aries.jmx.api").version("1.1.5"),
            mavenBundle().groupId("org.apache.aries.jmx").artifactId("org.apache.aries.jmx.core").version("1.1.8"),
            mavenBundle().groupId("org.apache.aries.jmx").artifactId("org.apache.aries.jmx.whiteboard").version("1.2.0")
        );
    }

    private static ModifiableCompositeOption sling() {
        return composite(
            management(),
            mavenBundle().groupId("org.apache.commons").artifactId("commons-math").version("2.2"),
            factoryConfiguration("org.apache.sling.serviceusermapping.impl.ServiceUserMapperImpl.amended")
                .put("user.mapping", new String[]{"org.apache.sling.auth.core=[sling-readall]"})
                .asOption(),
            factoryConfiguration("org.apache.sling.serviceusermapping.impl.ServiceUserMapperImpl.amended")
                .put("user.mapping", new String[]{"org.apache.sling.resourceresolver:mapping=[sling-mapping]", "org.apache.sling.resourceresolver:hierarchy=[sling-readall]", "org.apache.sling.resourceresolver:observation=[sling-readall]", "org.apache.sling.resourceresolver:console=[sling-readall]"})
                .asOption()
        );
    }

    private static ModifiableCompositeOption slingCommonsCompiler() {
        return composite(
            slingCommonsClassloader(),
            mavenBundle().groupId("org.apache.sling").artifactId("org.apache.sling.commons.compiler").version("2.4.0")
        );
    }

    private static ModifiableCompositeOption slingCommonsClassloader() {
        return composite(
            mavenBundle().groupId("org.apache.sling").artifactId("org.apache.sling.commons.fsclassloader").version("1.0.16"),
            mavenBundle().groupId("org.apache.sling").artifactId("org.apache.sling.commons.classloader").version("1.4.4")
        );
    }

    private static ModifiableCompositeOption slingAdapter() {
        return composite(
            sling(),
            mavenBundle().groupId("org.apache.sling").artifactId("org.apache.sling.adapter").version("2.2.0")
        );
    }

    private static ModifiableCompositeOption slingDiscovery() {
        return composite(
            sling(),
            mavenBundle().groupId("org.apache.sling").artifactId("org.apache.sling.discovery.api").version("1.0.4")
        );
    }

    private static ModifiableCompositeOption slingDiscoveryStandalone() {
        return composite(
            slingDiscovery(),
            mavenBundle().groupId("org.apache.sling").artifactId("org.apache.sling.discovery.standalone").version("1.0.2")
        );
    }

    private static ModifiableCompositeOption slingI18n() {
        return composite(
            sling(),
            mavenBundle().groupId("org.apache.sling").artifactId("org.apache.sling.i18n").version("2.6.2"),
            factoryConfiguration("org.apache.sling.serviceusermapping.impl.ServiceUserMapperImpl.amended")
                .put("user.mapping", new String[]{"org.apache.sling.i18n=[sling-readall]"})
                .asOption()
        );
    }

    private static ModifiableCompositeOption slingModels() {
        return composite(
            sling(),
            slingAdapter(),
            slingScripting(),
            mavenBundle().groupId("org.apache.sling").artifactId("org.apache.sling.models.api").version("1.4.2"),
            mavenBundle().groupId("org.apache.sling").artifactId("org.apache.sling.models.impl").version("1.5.4"),
            mavenBundle().groupId("org.apache.servicemix.specs").artifactId("org.apache.servicemix.specs.annotation-api-1.3").version("1.3_3")
        );
    }

    private static ModifiableCompositeOption slingXss() {
        return composite(
            sling(),
            httpcomponentsClient(),
            factoryConfiguration("org.apache.sling.jcr.repoinit.RepositoryInitializer")
                .put("scripts", new String[]{"create service user sling-xss with path system/sling\n\nset principal ACL for sling-xss\n  allow   jcr:read    on /apps/sling/xss\n  allow   jcr:read    on /libs/sling/xss\nend\n\ncreate path (sling:Folder) /apps/sling/xss\ncreate path (sling:Folder) /libs/sling/xss"})
                .asOption(),
            factoryConfiguration("org.apache.sling.serviceusermapping.impl.ServiceUserMapperImpl.amended")
                .put("user.mapping", new String[]{"org.apache.sling.xss=[sling-xss]"})
                .asOption()
        );
    }

    private static ModifiableCompositeOption slingJcr() {
        return composite(
            sling(),
            jackrabbit(),
            jackrabbitOak(),
            tika(),
            mavenBundle().groupId("org.apache.sling").artifactId("org.apache.sling.jcr.api").version("2.4.0"),
            mavenBundle().groupId("org.apache.sling").artifactId("org.apache.sling.jcr.base").version("3.1.12"),
            mavenBundle().groupId("org.apache.sling").artifactId("org.apache.sling.jcr.contentloader").version("2.5.2"),
            mavenBundle().groupId("org.apache.sling").artifactId("org.apache.sling.jcr.resource").version("3.3.2"),
            factoryConfiguration("org.apache.sling.jcr.base.internal.LoginAdminWhitelist.fragment")
                .put("whitelist.bundles", new String[]{"org.apache.sling.jcr.base", "org.apache.sling.jcr.classloader", "org.apache.sling.jcr.oak.server", "org.apache.sling.jcr.repoinit", "org.apache.sling.jcr.webconsole"})
                .put("whitelist.name", "sling-jcr")
                .asOption(),
            factoryConfiguration("org.apache.sling.jcr.repoinit.RepositoryInitializer")
                .put("scripts", new String[]{"create service user sling-jcr-contentloader with path system/sling\n\nset principal ACL for sling-jcr-contentloader\n  allow   jcr:all    on /\nend"})
                .asOption(),
            factoryConfiguration("org.apache.sling.serviceusermapping.impl.ServiceUserMapperImpl.amended")
                .put("user.mapping", new String[]{"org.apache.sling.jcr.resource:observation=[sling-readall]", "org.apache.sling.jcr.resource:validation=[sling-readall]"})
                .asOption(),
            factoryConfiguration("org.apache.sling.serviceusermapping.impl.ServiceUserMapperImpl.amended")
                .put("user.mapping", new String[]{"org.apache.sling.jcr.contentloader=[sling-jcr-contentloader]"})
                .asOption()
        );
    }

    private static ModifiableCompositeOption slingJcrRepoinit() {
        return composite(
            sling(),
            slingJcr(),
            mavenBundle().groupId("org.apache.sling").artifactId("org.apache.sling.jcr.repoinit").version("1.1.44"),
            mavenBundle().groupId("org.apache.sling").artifactId("org.apache.sling.repoinit.parser").version("1.9.0"),
            mavenBundle().groupId("org.apache.sling").artifactId("org.apache.sling.provisioning.model").version("1.8.6")
        );
    }

    private static ModifiableCompositeOption slingScripting() {
        return composite(
            sling(),
            slingCommonsCompiler(),
            factoryConfiguration("org.apache.sling.jcr.repoinit.RepositoryInitializer")
                .put("scripts", new String[]{"create service user sling-scripting with path system/sling\n\nset principal ACL for sling-scripting\n  allow   jcr:read    on /apps\n  allow   jcr:read    on /libs\nend"})
                .asOption(),
            factoryConfiguration("org.apache.sling.serviceusermapping.impl.ServiceUserMapperImpl.amended")
                .put("user.mapping", new String[]{"org.apache.sling.scripting.core=[sling-scripting]"})
                .asOption()
        );
    }

    private static ModifiableCompositeOption slingScriptingJavascript() {
        return composite(
            slingScripting(),
            mavenBundle().groupId("org.apache.sling").artifactId("org.apache.sling.scripting.javascript").version("3.1.4"),
            mavenBundle().groupId("javax.jcr").artifactId("jcr").version("2.0"),
            mavenBundle().groupId("org.apache.servicemix.bundles").artifactId("org.apache.servicemix.bundles.rhino").version("1.7.14_2")
        );
    }

    private static ModifiableCompositeOption slingScriptingHtl() {
        return composite(
            sling(),
            slingJcr(),
            slingScripting(),
            slingScriptingJavascript(),
            slingI18n(),
            slingModels(),
            slingXss(),
            slingCommonsCompiler(),
            mavenBundle().groupId("org.apache.sling").artifactId("org.apache.sling.scripting.sightly").version("1.4.26-1.4.0"),
            mavenBundle().groupId("org.apache.sling").artifactId("org.apache.sling.scripting.sightly.runtime").version("1.2.6-1.4.0"),
            mavenBundle().groupId("org.apache.sling").artifactId("org.apache.sling.scripting.sightly.compiler").version("1.2.14-1.4.0"),
            mavenBundle().groupId("org.apache.sling").artifactId("org.apache.sling.scripting.sightly.compiler.java").version("1.2.2-1.4.0"),
            mavenBundle().groupId("org.apache.sling").artifactId("org.apache.sling.scripting.sightly.js.provider").version("1.2.12"),
            mavenBundle().groupId("org.antlr").artifactId("antlr4-runtime").version("4.9.3"),
            factoryConfiguration("org.apache.sling.serviceusermapping.impl.ServiceUserMapperImpl.amended")
                .put("user.mapping", new String[]{"org.apache.sling.scripting.sightly.js.provider=[sling-scripting]"})
                .asOption()
        );
    }

    private static ModifiableCompositeOption slingScriptingThymeleaf() {
        return composite(
            sling(),
            slingScripting(),
            mavenBundle().groupId("org.apache.sling").artifactId("org.apache.sling.scripting.thymeleaf").version("2.0.2"),
            mavenBundle().groupId("org.apache.servicemix.bundles").artifactId("org.apache.servicemix.bundles.thymeleaf").version("3.0.12.RELEASE_1"),
            mavenBundle().groupId("org.attoparser").artifactId("attoparser").version("2.0.5.RELEASE"),
            mavenBundle().groupId("org.unbescape").artifactId("unbescape").version("1.1.6.RELEASE"),
            mavenBundle().groupId("org.apache.servicemix.bundles").artifactId("org.apache.servicemix.bundles.ognl").version("3.2.1_1"),
            mavenBundle().groupId("org.javassist").artifactId("javassist").version("3.22.0-GA"),
            factoryConfiguration("org.apache.sling.serviceusermapping.impl.ServiceUserMapperImpl.amended")
                .put("user.mapping", new String[]{"org.apache.sling.scripting.thymeleaf=[sling-scripting]"})
                .asOption()
        );
    }

    private static ModifiableCompositeOption slingServlets() {
        return composite(
            sling(),
            slingScripting(),
            slingXss(),
            mavenBundle().groupId("org.apache.sling").artifactId("org.apache.sling.servlets.get").version("2.2.0"),
            mavenBundle().groupId("org.apache.sling").artifactId("org.apache.sling.servlets.post").version("2.6.0"),
            factoryConfiguration("org.apache.sling.jcr.base.internal.LoginAdminWhitelist.fragment")
                .put("whitelist.bundles", new String[]{"org.apache.sling.servlets.post"})
                .put("whitelist.name", "sling-servlets")
                .asOption(),
            factoryConfiguration("org.apache.sling.serviceusermapping.impl.ServiceUserMapperImpl.amended")
                .put("user.mapping", new String[]{"org.apache.sling.servlets.resolver:console=[sling-readall]", "org.apache.sling.servlets.resolver:scripts=[sling-scripting]"})
                .asOption()
        );
    }

    private static ModifiableCompositeOption httpcomponentsClient() {
        return composite(
            mavenBundle().groupId("org.apache.httpcomponents").artifactId("httpclient-osgi").version("4.5.14"),
            mavenBundle().groupId("org.apache.httpcomponents").artifactId("httpcore-osgi").version("4.4.16")
        );
    }

    private static ModifiableCompositeOption jackrabbit() {
        return composite(
            httpcomponentsClient(),
            mavenBundle().groupId("org.apache.jackrabbit").artifactId("oak-jackrabbit-api").version("1.48.0"),
            mavenBundle().groupId("org.apache.jackrabbit").artifactId("jackrabbit-data").version("2.20.8"),
            mavenBundle().groupId("org.apache.jackrabbit").artifactId("jackrabbit-jcr-commons").version("2.20.8"),
            mavenBundle().groupId("org.apache.jackrabbit").artifactId("jackrabbit-jcr-rmi").version("2.20.8"),
            mavenBundle().groupId("org.apache.jackrabbit").artifactId("jackrabbit-spi").version("2.20.8"),
            mavenBundle().groupId("org.apache.jackrabbit").artifactId("jackrabbit-spi-commons").version("2.20.8"),
            mavenBundle().groupId("org.apache.jackrabbit").artifactId("jackrabbit-webdav").version("2.20.8"),
            mavenBundle().groupId("javax.jcr").artifactId("jcr").version("2.0"),
            mavenBundle().groupId("org.apache.geronimo.specs").artifactId("geronimo-atinject_1.0_spec").version("1.2"),
            mavenBundle().groupId("org.apache.geronimo.specs").artifactId("geronimo-el_2.2_spec").version("1.1"),
            mavenBundle().groupId("org.apache.geronimo.specs").artifactId("geronimo-interceptor_1.1_spec").version("1.0"),
            mavenBundle().groupId("org.apache.geronimo.specs").artifactId("geronimo-jcdi_1.0_spec").version("1.1"),
            mavenBundle().groupId("org.apache.geronimo.specs").artifactId("geronimo-jta_1.1_spec").version("1.1.1")
        );
    }

    private static ModifiableCompositeOption jackrabbitOak() {
        return composite(
            jackrabbit(),
            tika(),
            mavenBundle().groupId("org.apache.jackrabbit").artifactId("oak-api").version("1.48.0"),
            mavenBundle().groupId("org.apache.jackrabbit").artifactId("oak-authorization-principalbased").version("1.48.0"),
            mavenBundle().groupId("org.apache.jackrabbit").artifactId("oak-blob").version("1.48.0"),
            mavenBundle().groupId("org.apache.jackrabbit").artifactId("oak-blob-plugins").version("1.48.0"),
            mavenBundle().groupId("org.apache.jackrabbit").artifactId("oak-commons").version("1.48.0"),
            mavenBundle().groupId("org.apache.jackrabbit").artifactId("oak-core").version("1.48.0"),
            mavenBundle().groupId("org.apache.jackrabbit").artifactId("oak-core-spi").version("1.48.0"),
            mavenBundle().groupId("org.apache.jackrabbit").artifactId("oak-jcr").version("1.48.0"),
            mavenBundle().groupId("org.apache.jackrabbit").artifactId("oak-query-spi").version("1.48.0"),
            mavenBundle().groupId("org.apache.jackrabbit").artifactId("oak-security-spi").version("1.48.0"),
            mavenBundle().groupId("org.apache.jackrabbit").artifactId("oak-store-composite").version("1.48.0"),
            mavenBundle().groupId("org.apache.jackrabbit").artifactId("oak-store-spi").version("1.48.0"),
            mavenBundle().groupId("com.google.guava").artifactId("guava").version("15.0"),
            mavenBundle().groupId("org.apache.felix").artifactId("org.apache.felix.jaas").version("1.0.2")
        );
    }

    private static ModifiableCompositeOption tika() {
        return composite(
            mavenBundle().groupId("org.apache.tika").artifactId("tika-core").version("1.28.5"),
            mavenBundle().groupId("org.apache.tika").artifactId("tika-parsers").version("1.28.5")
        );
    }

   private static ModifiableCompositeOption slingQuickstartOak() {
        return composite(
            sling(),
            slingServlets(),
            slingJcr(),
            slingJcrRepoinit(),
            slingAdapter(),
            slingDiscoveryStandalone(),
            factoryConfiguration("org.apache.felix.jaas.Configuration.factory")
                .put("jaas.classname", "org.apache.jackrabbit.oak.spi.security.authentication.GuestLoginModule")
                .put("jaas.controlFlag", "optional")
                .put("jaas.ranking", 300)
                .asOption(),
            factoryConfiguration("org.apache.felix.jaas.Configuration.factory")
                .put("jaas.classname", "org.apache.jackrabbit.oak.security.authentication.user.LoginModuleImpl")
                .put("jaas.controlFlag", "required")
                .asOption(),
            factoryConfiguration("org.apache.felix.jaas.Configuration.factory")
                .put("jaas.classname", "org.apache.jackrabbit.oak.security.authentication.token.TokenLoginModule")
                .put("jaas.controlFlag", "sufficient")
                .put("jaas.ranking", 200)
                .asOption(),
            newConfiguration("org.apache.felix.jaas.ConfigurationSpi")
                .put("jaas.configProviderName", "FelixJaasProvider")
                .put("jaas.defaultRealmName", "jackrabbit.oak")
                .asOption(),
            newConfiguration("org.apache.jackrabbit.oak.security.authentication.AuthenticationConfigurationImpl")
                .put("org.apache.jackrabbit.oak.authentication.configSpiName", "FelixJaasProvider")
                .asOption(),
            newConfiguration("org.apache.jackrabbit.oak.security.authorization.AuthorizationConfigurationImpl")
                .put("importBehavior", "besteffort")
                .asOption(),
            newConfiguration("org.apache.jackrabbit.oak.security.internal.SecurityProviderRegistration")
                .put("requiredServicePids", new String[]{"org.apache.jackrabbit.oak.security.authorization.AuthorizationConfigurationImpl", "org.apache.jackrabbit.oak.security.principal.PrincipalConfigurationImpl", "org.apache.jackrabbit.oak.security.authentication.token.TokenConfigurationImpl", "org.apache.jackrabbit.oak.spi.security.user.action.DefaultAuthorizableActionProvider", "org.apache.jackrabbit.oak.security.authorization.restriction.RestrictionProviderImpl", "org.apache.jackrabbit.oak.security.user.UserAuthenticationFactoryImpl", "org.apache.jackrabbit.oak.spi.security.authorization.principalbased.impl.PrincipalBasedAuthorizationConfiguration"})
                .asOption(),
            newConfiguration("org.apache.jackrabbit.oak.security.user.UserConfigurationImpl")
                .put("defaultDepth", 1)
                .put("groupsPath", "/home/groups")
                .put("importBehavior", "besteffort")
                .put("usersPath", "/home/users")
                .asOption(),
            newConfiguration("org.apache.jackrabbit.oak.security.user.RandomAuthorizableNodeName")
                .put("length", 21)
                .asOption(),
            newConfiguration("org.apache.jackrabbit.oak.spi.security.authorization.principalbased.impl.FilterProviderImpl")
                .put("path", "/home/users/system/sling")
                .asOption(),
            newConfiguration("org.apache.jackrabbit.oak.spi.security.authorization.principalbased.impl.PrincipalBasedAuthorizationConfiguration")
                .put("enableAggregationFilter", true)
                .asOption(),
            newConfiguration("org.apache.jackrabbit.oak.spi.security.user.action.DefaultAuthorizableActionProvider")
                .put("enabledActions", new String[]{"org.apache.jackrabbit.oak.spi.security.user.action.AccessControlAction"})
                .put("groupPrivilegeNames", new String[]{"jcr:read"})
                .put("userPrivilegeNames", new String[]{"jcr:all"})
                .asOption(),
            newConfiguration("org.apache.jackrabbit.vault.packaging.impl.PackagingImpl")
                .put("authIdsForHookExecution", new String[]{"sling-installer-factory-packages"})
                .put("authIdsForRootInstallation", new String[]{"sling-installer-factory-packages"})
                .put("packageRoots", new String[]{"/var/packages"})
                .asOption(),
            factoryConfiguration("org.apache.sling.jcr.repoinit.RepositoryInitializer")
                .put("scripts", new String[]{"create path (sling:Folder) /apps\ncreate path (sling:Folder) /libs\ncreate path (sling:Folder) /var\ncreate path (sling:OrderedFolder) /content"})
                .asOption(),
            factoryConfiguration("org.apache.sling.jcr.repoinit.RepositoryInitializer")
                .put("scripts", new String[]{"create service user sling-mapping with path system/sling\n\nset principal ACL for sling-mapping\n  allow   jcr:read    on /\nend"})
                .asOption(),
            factoryConfiguration("org.apache.sling.jcr.repoinit.RepositoryInitializer")
                .put("scripts", new String[]{"create service user sling-readall with path system/sling\n\nset principal ACL for sling-readall\n  allow   jcr:read    on /\nend"})
                .asOption()
        );
    }

    private static ModifiableCompositeOption slingQuickstartOakTar(final String workingDirectory) {
        final String slingHome = String.format("%s/sling", workingDirectory);
        final String repositoryHome = String.format("%s/repository", slingHome);
        final String localIndexDir = String.format("%s/index", repositoryHome);
        return composite(
            slingQuickstartOak(),
            mavenBundle().groupId("org.apache.jackrabbit").artifactId("oak-lucene").version("1.48.0"),
            mavenBundle().groupId("org.apache.jackrabbit").artifactId("oak-segment-tar").version("1.48.0"),
            mavenBundle().groupId("org.apache.jackrabbit").artifactId("oak-store-document").version("1.48.0"),
            mavenBundle().groupId("org.apache.sling").artifactId("org.apache.sling.jcr.oak.server").version("1.3.0"),
            newConfiguration("org.apache.jackrabbit.oak.segment.SegmentNodeStoreService")
                .put("repository.home", repositoryHome)
                .put("name", "Default NodeStore")
                .asOption(),
            newConfiguration("org.apache.jackrabbit.oak.plugins.index.lucene.LuceneIndexProviderService")
                .put("localIndexDir", localIndexDir)
                .asOption()
        );
    }

    @Configuration
    public Option[] configuration() {
        final String workingDirectory = workingDirectory();
        return options(
            composite(
                super.baseConfiguration(),
                mavenBundle().groupId("org.apache.sling").artifactId("org.apache.sling.commons.johnzon").version("1.2.16"),
                mavenBundle().groupId("org.apache.sling").artifactId("org.apache.sling.commons.johnzon").version("2.0.0"),
                mavenBundle().groupId("commons-codec").artifactId("commons-codec").version("1.15"),
                mavenBundle().groupId("org.apache.commons").artifactId("commons-collections4").version("4.4"),
                mavenBundle().groupId("org.apache.felix").artifactId("org.apache.felix.healthcheck.api").version("2.0.4"),
                mavenBundle().groupId("org.apache.sling").artifactId("org.apache.sling.auth.core").versionAsInProject(),
                mavenBundle().groupId("org.apache.sling").artifactId("org.apache.sling.resourceresolver").versionAsInProject(),
                mavenBundle().groupId("org.apache.sling").artifactId("org.apache.sling.settings").version("1.4.2"),
                mavenBundle().groupId("org.apache.commons").artifactId("commons-fileupload2-core").version("2.0.0-M2"),
                mavenBundle().groupId("org.apache.commons").artifactId("commons-fileupload2-jakarta-servlet5").version("2.0.0-M2"),
                mavenBundle().groupId("org.apache.sling").artifactId("org.apache.sling.engine").versionAsInProject(),
                mavenBundle().groupId("org.apache.sling").artifactId("org.apache.sling.servlets.resolver").versionAsInProject(),

                factoryConfiguration("org.apache.sling.serviceusermapping.impl.ServiceUserMapperImpl.amended")
                    .put("user.mapping", new String[]{"org.apache.sling.servlets.resolver:console=sling-readall", "org.apache.sling.servlets.resolver:scripts=sling-scripting"})
                    .asOption(),
                mavenBundle().groupId("org.owasp.encoder").artifactId("encoder").version("1.3.1"),
                mavenBundle().groupId("org.codehaus.woodstox").artifactId("stax2-api").version("4.2.2"),
                mavenBundle().groupId("com.fasterxml.jackson.core").artifactId("jackson-annotations").version("2.16.1"),
                mavenBundle().groupId("com.fasterxml.jackson.core").artifactId("jackson-core").version("2.16.1"),
                mavenBundle().groupId("com.fasterxml.jackson.core").artifactId("jackson-databind").version("2.16.1"),
                mavenBundle().groupId("com.fasterxml.jackson.dataformat").artifactId("jackson-dataformat-xml").version("2.16.1"),
                mavenBundle().groupId("org.apache.commons").artifactId("commons-text").version("1.11.0"),
                mavenBundle().groupId("org.apache.sling").artifactId("org.apache.sling.xss").version("2.4.6"),
                mavenBundle().groupId("io.dropwizard.metrics").artifactId("metrics-core").version("3.2.3"),
                mavenBundle().groupId("org.apache.sling").artifactId("org.apache.sling.commons.metrics").version("1.2.12"),
                mavenBundle().groupId("org.apache.sling").artifactId("org.apache.sling.commons.threads").version("3.2.22"),
                mavenBundle().groupId("org.apache.sling").artifactId("org.apache.sling.commons.scheduler").version("2.7.12"),

                buildBundleWithBnd(MockXSSApi.class),

                slingQuickstartOakTar(workingDirectory),
                slingScriptingHtl(),
                slingScriptingThymeleaf(),
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
            )
        );
    }

   /**
     * Builds an OSGi bundle with bnd from given classes and provides it as provisioning option.
     *
     * @param classes the classes to include in the OSGi bundle
     * @return the provisioning option
     */
    public static UrlProvisionOption buildBundleWithBnd(final Class... classes) {
        final TinyBundle bundle = org.ops4j.pax.tinybundles.TinyBundles.bundle();
        for (final Class clazz : classes) {
            bundle.addClass(clazz);
        }
        return streamBundle(
            bundle.build()
        ).start();
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
        assertEquals(document.title(), "Sling Scripting HTL");
    }

    @Test
    public void testThymeleafTitle() throws IOException {
        final String url = String.format("http://localhost:%s/scripting/thymeleaf.html", httpPort());
        final Document document = Jsoup.connect(url).get();
        assertEquals(document.title(), "Sling Scripting Thymeleaf");
    }
}
