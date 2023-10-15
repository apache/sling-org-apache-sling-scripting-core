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

import static org.apache.sling.testing.paxexam.SlingOptions.slingAuthForm;
import static org.apache.sling.testing.paxexam.SlingOptions.slingQuickstartOakTar;
import static org.apache.sling.testing.paxexam.SlingOptions.slingScriptingJavascript;
import static org.apache.sling.testing.paxexam.SlingOptions.versionResolver;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.ops4j.pax.exam.CoreOptions.composite;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.cm.ConfigurationAdminOptions.factoryConfiguration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Callable;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tests for SLING-10147 - verify scripting variables implementation details are not 
 * exposed to not authorized users
 */
@RunWith(Enclosed.class)
public class SLING_10147IT {

    private SLING_10147IT() {
        // private constructor to hide the implicit public one
    }

    /**
     * Base class to share the common parts of LoadedIT and NotLoadedIT
     * tests
     */
    public abstract static class BaseIT extends ScriptingCoreTestSupport {
        protected final Logger logger = LoggerFactory.getLogger(getClass());

        protected CloseableHttpClient httpClient;

        @Rule
        public TestRule watcher = new TestWatcher() {

            /* (non-Javadoc)
             * @see org.junit.rules.TestWatcher#starting(org.junit.runner.Description)
             */
            @Override
            protected void starting(Description description) {
                logger.info("Starting test: {}", description.getMethodName());
            }

            /* (non-Javadoc)
             * @see org.junit.rules.TestWatcher#finished(org.junit.runner.Description)
             */
            @Override
            protected void finished(Description description) {
               logger.info("Finished test: {}", description.getMethodName());
            }

        };

        @Before
        public void before() {
            waitForServices();

            int timeout = 15; // seconds
            RequestConfig config = RequestConfig.custom()
              .setConnectTimeout(timeout * 1000)
              .setConnectionRequestTimeout(timeout * 1000)
              .setSocketTimeout(timeout * 1000).build();
            
            HttpClientBuilder httpClientBuilder = HttpClientBuilder.create()
                    .setRedirectStrategy(new LaxRedirectStrategy())
                    .setDefaultRequestConfig(config);
            httpClient = httpClientBuilder.build();
        }

        /**
         * Wait for services to be available
         */
        protected void waitForServices() {
            final BundleContext bundleContext = FrameworkUtil.getBundle(SLING_10147IT.class).getBundleContext();
            Awaitility.await()
                .atMost(Duration.ofMinutes(5))
                .pollInterval(Duration.ofSeconds(5))
                .until(new Callable<Boolean>() {

                    private String [] servicesLists = new String[] {
                            "org.apache.sling.jcr.api.SlingRepository",
                            "org.apache.sling.engine.auth.Authenticator",
                            "org.apache.sling.api.resource.ResourceResolverFactory",
                            "org.apache.sling.api.servlets.ServletResolver",
                            "javax.script.ScriptEngineManager"
                        };

                    @Override
                    public Boolean call() throws Exception {
                        boolean foundAllServices = true;
                        for (String serviceClass : servicesLists) {
                            ServiceReference<?> serviceReference = bundleContext.getServiceReference(serviceClass);
                            if (serviceReference == null) {
                                foundAllServices = false;
                                break;
                            }
                        }
                        return foundAllServices;
                    }

                });
        }

        @After
        public void after() throws IOException {
            if (httpClient != null) {
                httpClient.close();
                httpClient = null;
            }
        }

        @Configuration
        public Option[] configuration() {
            return composite(
                        baseConfiguration(),
                        slingQuickstartOakTar(String.format("target/%s", getClass().getSimpleName()), httpPort),
                        slingScriptingJavascript(),
                        slingAuthForm(),
                        mavenBundle().groupId("org.apache.httpcomponents").artifactId("httpcore-osgi").version(versionResolver),
                        mavenBundle().groupId("org.apache.httpcomponents").artifactId("httpclient-osgi").version(versionResolver),
                        webconsolesecurityprovider()
                    ).getOptions();
        }

        protected void checkContentType(CloseableHttpResponse response ,String expected) {
            // Remove whatever follows semicolon in content-type
            HttpEntity entity = response.getEntity();
            Header contentTypeHeader = entity.getContentType();
            String contentType = contentTypeHeader == null ? null : contentTypeHeader.getValue();
            if (contentType != null) {
                contentType = contentType.split(";")[0].trim();
            }
            // check for match
            assertEquals(expected, contentType);
        }
        
    }

    /**
     * Verify that when authorized that the scripting variables is accessible
     */
    @RunWith(PaxExam.class)
    @ExamReactorStrategy(PerClass.class)
    public static class AuthorizedIT extends BaseIT {

        @Test
        public void testGetSlingBindingsVariablesListJsonWithAuthorizedUser() throws IOException, AuthenticationException {
            // make GET request and verify it returned a 200 ok 
            HttpGet request = new HttpGet(String.format("http://localhost:%d/.SLING_availablebindings.json?extension=esp", httpPort()));
            // pre-emptive basic authentication
            request.addHeader(new BasicScheme().authenticate(new UsernamePasswordCredentials("admin", "admin"), request, null));
            
            if (logger.isInfoEnabled()) {
                logger.info("Executing GET Request to: {}", request.getURI());
            }
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                if (logger.isInfoEnabled()) {
                    logger.info("Response Content\r\n: {}", IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8));
                }
                // should have passed all security checks and returned the JSON payload
                assertEquals(HttpServletResponse.SC_OK, response.getStatusLine().getStatusCode());
                checkContentType(response, "application/json");
            }
        }
    }
    
    /**
     * Verify that when not authorized that the scripting variables is not accessible
     */
    @RunWith(PaxExam.class)
    @ExamReactorStrategy(PerClass.class)
    public static class NotAuthorizedIT extends BaseIT {

        @Configuration
        @Override
        public Option[] configuration() {
            return composite(super.configuration())
                    .add(// testing - add a user to use to login and verify
                         factoryConfiguration("org.apache.sling.jcr.repoinit.RepositoryInitializer")
                            .put("scripts", new String[] {
                                    "create user sling10147 with password sling10147"
                                })
                            .asOption()
                        )
                    .getOptions();
        }

        @Test
        public void testGetSlingBindingsVariablesListJsonWithNotAuthorizedUser() throws IOException, AuthenticationException {
            // make GET request and verify it is denied for user without sufficient rights 
            HttpGet request = new HttpGet(String.format("http://localhost:%d/.SLING_availablebindings.json?extension=esp", httpPort()));
            // pre-emptive basic authentication
            request.addHeader(new BasicScheme().authenticate(new UsernamePasswordCredentials("sling10147", "sling10147"), request, null));

            if (logger.isInfoEnabled()) {
                logger.info("Executing GET Request to: {}", request.getURI());
            }
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                if (logger.isInfoEnabled()) {
                    logger.info("Response Content\r\n: {}", IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8));
                }
                // should have been denied access by WebConsoleSecurityProvider2 service access not granted to the user
                assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatusLine().getStatusCode());
                checkContentType(response, "text/html");
            }
        }

        @Test
        public void testGetSlingBindingsVariablesListJsonWithAnonymousUser() throws IOException, AuthenticationException {
            // make GET request and verify it returned a forms login page challenge 
            HttpGet request = new HttpGet(String.format("http://localhost:%d/.SLING_availablebindings.json?extension=esp", httpPort()));

            if (logger.isInfoEnabled()) {
                logger.info("Executing GET Request to: {}", request.getURI());
            }
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                String content = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
                if (logger.isInfoEnabled()) {
                    logger.info("Response Content\r\n: {}", content);
                }
                // should have been challenged for authentication by the WebConsoleSecurity2#authenitcate call
                assertEquals(HttpServletResponse.SC_OK, response.getStatusLine().getStatusCode());
                checkContentType(response, "text/html");
                assertTrue("Expected forms based login page", content.contains("Login to Apache Sling"));
            }
        }
    }

    /**
     * Verify that when no WebConsoleSecurityProvider2 service is available that
     * the scripting variables is not accessible
     */
    @RunWith(PaxExam.class)
    @ExamReactorStrategy(PerClass.class)
    public static class HasNoWebConsoleSecurityProvider2IT extends BaseIT {

        @Configuration
        @Override
        public Option[] configuration() {
            // remove the security provider bundle from the configuration
            return composite(super.configuration())
                    .remove(webconsolesecurityprovider())
                    .getOptions();
        }

        @Test
        public void testGetSlingBindingsVariablesListJsonWithoutWebConsoleSecurityProvider2() throws IOException, AuthenticationException {
            // make GET request and verify it returned a 403 error 
            HttpGet request = new HttpGet(String.format("http://localhost:%d/.SLING_availablebindings.json?extension=esp", httpPort()));
            // pre-emptive basic authentication
            request.addHeader(new BasicScheme().authenticate(new UsernamePasswordCredentials("admin", "admin"), request, null));

            if (logger.isInfoEnabled()) {
                logger.info("Executing GET Request to: {}", request.getURI());
            }
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                if (logger.isInfoEnabled()) {
                    logger.info("Response Content\r\n: {}", IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8));
                }
                // should have failed fast due to the missing WebConsoleSecurityProvider2 service
                assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatusLine().getStatusCode());
                checkContentType(response, "text/html");
            }
        }

    }

}
