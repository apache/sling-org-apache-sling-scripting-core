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

import static org.apache.sling.testing.paxexam.SlingOptions.slingQuickstartOakTar;
import static org.apache.sling.testing.paxexam.SlingOptions.slingScriptingJavascript;
import static org.apache.sling.testing.paxexam.SlingOptions.versionResolver;
import static org.junit.Assert.assertEquals;
import static org.ops4j.pax.exam.CoreOptions.composite;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.streamBundle;
import static org.ops4j.pax.exam.CoreOptions.vmOption;
import static org.ops4j.pax.tinybundles.core.TinyBundles.withBnd;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.message.BasicNameValuePair;
import org.apache.sling.scripting.core.impl.ScriptingVariablesTokenFactory;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.options.ModifiableCompositeOption;
import org.ops4j.pax.exam.options.extra.VMOption;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.ops4j.pax.tinybundles.core.TinyBundle;
import org.ops4j.pax.tinybundles.core.TinyBundles;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tests for SLING-10147 - verify scripting variables implementation details are not 
 * exposed to not authorized users
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class SLING_10147IT extends ScriptingCoreTestSupport {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private CloseableHttpClient httpClient;

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

        CredentialsProvider provider = new BasicCredentialsProvider();
        provider.setCredentials(
                AuthScope.ANY,
                new UsernamePasswordCredentials("admin", "admin")
        );

        int timeout = 5; // seconds
        RequestConfig config = RequestConfig.custom()
          .setConnectTimeout(timeout * 1000)
          .setConnectionRequestTimeout(timeout * 1000)
          .setSocketTimeout(timeout * 1000).build();
        
        httpClient = HttpClientBuilder.create()
                .setDefaultCredentialsProvider(provider)
                .setRedirectStrategy(new LaxRedirectStrategy())
                .setDefaultRequestConfig(config)
                .build();
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
        versionResolver.setVersionFromProject("org.apache.sling", "org.apache.sling.api");
        versionResolver.setVersionFromProject("org.apache.sling", "org.apache.sling.resourceresolver");
        versionResolver.setVersionFromProject("org.apache.sling", "org.apache.sling.servlets.resolver");
        versionResolver.setVersionFromProject("org.apache.sling", "org.apache.sling.scripting.api");

        return composite(
                    slingQuickstartOakTar(String.format("target/%s", getClass().getSimpleName()), httpPort),
                    slingScriptingJavascript(),
                    baseConfiguration(),
                    scriptingCoreFragmentBundle(),
                    mavenBundle().groupId("org.apache.httpcomponents").artifactId("httpcore-osgi").version(versionResolver),
                    mavenBundle().groupId("org.apache.httpcomponents").artifactId("httpclient-osgi").version(versionResolver),
                    optionalRemoteDebug()
                ).remove(
                    scriptingCore // remove the old version
                ).getOptions();
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

    /**
     * Add a fragment to make the org.apache.sling.scripting.core.impl package
     * accessible for the tests for the ScriptingVariablesTokenFactory calls
     */
    private Option scriptingCoreFragmentBundle() {
        TinyBundle bundle = TinyBundles.bundle()
            .set(Constants.FRAGMENT_HOST, "org.apache.sling.scripting.core")
            .set(Constants.BUNDLE_MANIFESTVERSION, "2")
            .set(Constants.BUNDLE_SYMBOLICNAME, "org.apache.sling.scripting.core.fragment")
            .set(Constants.EXPORT_PACKAGE, "org.apache.sling.scripting.core.impl");

        return streamBundle(
                    bundle.build(withBnd())
                ).noStart();
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

    @Test
    public void testPostScriptingVariablesConsolePluginWithoutParams() throws IOException {
        // make POST request and verify it redirects and returns the expected response
        HttpPost request = new HttpPost(String.format("http://localhost:%d/system/console/scriptingvariables", httpPort()));
        if (logger.isInfoEnabled()) {
            logger.info("Executing POST Request to: {}", request.getURI());
        }
        try (CloseableHttpResponse response = httpClient.execute(request)) {
            if (logger.isInfoEnabled()) {
                logger.info("Response Content\r\n: {}", IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8));
            }
            assertEquals(HttpServletResponse.SC_OK, response.getStatusLine().getStatusCode());
            checkContentType(response, "application/json");
        }
    }

    @Test
    public void testPostScriptingVariablesConsolePluginWithParams() throws IOException {
        // make POST request and verify it redirects and returns the expected response
        HttpPost request = new HttpPost(String.format("http://localhost:%d/system/console/scriptingvariables", httpPort()));
        List<NameValuePair> urlParameters = new ArrayList<>();
        urlParameters.add(new BasicNameValuePair("path", "/content"));
        urlParameters.add(new BasicNameValuePair("extension", "esp"));
        request.setEntity(new UrlEncodedFormEntity(urlParameters));

        if (logger.isInfoEnabled()) {
            logger.info("Executing POST Request to: {}", request.getURI());
        }
        try (CloseableHttpResponse response = httpClient.execute(request)) {
            if (logger.isInfoEnabled()) {
                logger.info("Response Content\r\n: {}", IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8));
            }
            assertEquals(HttpServletResponse.SC_OK, response.getStatusLine().getStatusCode());
            checkContentType(response, "application/json");
        }
    }

    @Test
    public void testPostScriptingVariablesConsolePluginWithForbiddenPath() throws IOException {
        // make POST request and verify it redirects and returns the expected response
        HttpPost request = new HttpPost(String.format("http://localhost:%d/system/console/scriptingvariables", httpPort()));
        List<NameValuePair> urlParameters = new ArrayList<>();
        urlParameters.add(new BasicNameValuePair("path", "http://example.com/content"));
        urlParameters.add(new BasicNameValuePair("extension", "esp"));
        request.setEntity(new UrlEncodedFormEntity(urlParameters));

        if (logger.isInfoEnabled()) {
            logger.info("Executing POST Request to: {}", request.getURI());
        }
        try (CloseableHttpResponse response = httpClient.execute(request)) {
            if (logger.isInfoEnabled()) {
                logger.info("Response Content\r\n: {}", IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8));
            }
            assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatusLine().getStatusCode());
            checkContentType(response, "text/html");
        }
    }

    @Test
    public void testPostScriptingVariablesConsolePluginWithSyntaxErrorInPath() throws IOException {
        // make POST request and verify it redirects and returns the expected response
        HttpPost request = new HttpPost(String.format("http://localhost:%d/system/console/scriptingvariables", httpPort()));
        List<NameValuePair> urlParameters = new ArrayList<>();
        urlParameters.add(new BasicNameValuePair("path", "/con tent"));
        urlParameters.add(new BasicNameValuePair("extension", "esp"));
        request.setEntity(new UrlEncodedFormEntity(urlParameters));

        if (logger.isInfoEnabled()) {
            logger.info("Executing POST Request to: {}", request.getURI());
        }
        try (CloseableHttpResponse response = httpClient.execute(request)) {
            if (logger.isInfoEnabled()) {
                logger.info("Response Content\r\n: {}", IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8));
            }
            assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatusLine().getStatusCode());
            checkContentType(response, "text/html");
        }
    }

    @Test
    public void testGetSlingBindingsVariablesListJsonWithMissingTokenParams() throws IOException {
        // make GET request and verify it returned a 400 error 
        HttpGet request = new HttpGet(String.format("http://localhost:%d/.SLING_availablebindings.json?extension=esp", httpPort()));

        if (logger.isInfoEnabled()) {
            logger.info("Executing GET Request to: {}", request.getURI());
        }
        try (CloseableHttpResponse response = httpClient.execute(request)) {
            if (logger.isInfoEnabled()) {
                logger.info("Response Content\r\n: {}", IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8));
            }
            // should have bypassed the SLING_availablebindings servlet due to unsatisfied OptingServlet condition,
            //    so should have fallen through to get an error about invalid recursion selector
            assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
            checkContentType(response, "text/html");
        }
    }

    @Test
    public void testGetSlingBindingsVariablesListJsonWithInvalidTokenParams() throws IOException {
        // make GET request and verify it returned a 400 error 
        String token = ScriptingVariablesTokenFactory.createToken();
        String hash = "invalid";
        HttpGet request = new HttpGet(String.format("http://localhost:%d/.SLING_availablebindings.json?extension=esp&t=%s&h=%s", 
                httpPort(),
                token,
                hash));

        if (logger.isInfoEnabled()) {
            logger.info("Executing GET Request to: {}", request.getURI());
        }
        try (CloseableHttpResponse response = httpClient.execute(request)) {
            if (logger.isInfoEnabled()) {
                logger.info("Response Content\r\n: {}", IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8));
            }
            // should have bypassed the SLING_availablebindings servlet due to unsatisfied OptingServlet condition,
            //    so should have fallen through to get an error about invalid recursion selector
            assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
            checkContentType(response, "text/html");
        }
    }

    @Test
    public void testGetSlingBindingsVariablesListJsonWithExpiredTokenParams() throws IOException, InvalidKeyException, NoSuchAlgorithmException {
        // make GET request and verify it returned a 400 error 
        // timestamp token from 6 minutes ago should not be accepted as it is too old
        String token =  Long.toString(System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(6));
        String hash = ScriptingVariablesTokenFactory.toHash(token);
        HttpGet request = new HttpGet(String.format("http://localhost:%d/.SLING_availablebindings.json?extension=esp&t=%s&h=%s",
                httpPort(),
                token,
                hash));

        if (logger.isInfoEnabled()) {
            logger.info("Executing GET Request to: {}", request.getURI());
        }
        try (CloseableHttpResponse response = httpClient.execute(request)) {
            if (logger.isInfoEnabled()) {
                logger.info("Response Content\r\n: {}", IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8));
            }
            // should have bypassed the SLING_availablebindings servlet due to unsatisfied OptingServlet condition,
            //    so should have fallen through to get an error about invalid recursion selector
            assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
            checkContentType(response, "text/html");
        }
    }

    @Test
    public void testGetSlingBindingsVariablesListJsonWithValidTokenParams() throws IOException, InvalidKeyException, NoSuchAlgorithmException {
        // make GET request and verify it returned ok with valid params
        String token = ScriptingVariablesTokenFactory.createToken();
        String hash = ScriptingVariablesTokenFactory.toHash(token);
        HttpGet request = new HttpGet(String.format("http://localhost:%d/.SLING_availablebindings.json?extension=esp&t=%s&h=%s", 
                httpPort(),
                token,
                hash));

        if (logger.isInfoEnabled()) {
            logger.info("Executing GET Request to: {}", request.getURI());
        }
        try (CloseableHttpResponse response = httpClient.execute(request)) {
            if (logger.isInfoEnabled()) {
                logger.info("Response Content\r\n: {}", IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8));
            }
            // should have been accepted by the SLING_availablebindings servlet due to satisfied OptingServlet condition
            assertEquals(HttpServletResponse.SC_OK, response.getStatusLine().getStatusCode());
            checkContentType(response, "application/json");
        }
    }

}
