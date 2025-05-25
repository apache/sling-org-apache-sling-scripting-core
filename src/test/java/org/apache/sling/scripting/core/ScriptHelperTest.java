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
package org.apache.sling.scripting.core;

import java.util.Dictionary;
import java.util.Hashtable;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.SlingJakartaHttpServletRequest;
import org.apache.sling.api.SlingJakartaHttpServletResponse;
import org.apache.sling.api.wrappers.JakartaToJavaxRequestWrapper;
import org.apache.sling.api.wrappers.JakartaToJavaxResponseWrapper;
import org.apache.sling.api.wrappers.JavaxToJakartaRequestWrapper;
import org.apache.sling.api.wrappers.JavaxToJakartaResponseWrapper;
import org.apache.sling.api.wrappers.SlingHttpServletRequestWrapper;
import org.apache.sling.api.wrappers.SlingHttpServletResponseWrapper;
import org.apache.sling.api.wrappers.SlingJakartaHttpServletRequestWrapper;
import org.apache.sling.api.wrappers.SlingJakartaHttpServletResponseWrapper;
import org.apache.sling.scripting.core.impl.helper.OnDemandReaderJakartaRequest;
import org.apache.sling.scripting.core.impl.helper.OnDemandReaderRequest;
import org.apache.sling.scripting.core.impl.helper.OnDemandWriterJakartaResponse;
import org.apache.sling.scripting.core.impl.helper.OnDemandWriterResponse;
import org.apache.sling.testing.mock.osgi.junit.OsgiContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;
import org.osgi.framework.Constants;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ScriptHelperTest {

    @Rule
    public OsgiContext sling = new OsgiContext();

    private ScriptHelper sh;
    private final int[] RANKINGS = {42, 62, -12, 76, -123, 0, 7432, -21};

    @Before
    public void setup() {
        sh = new ScriptHelper(sling.bundleContext(), null);

        for (int rank : RANKINGS) {
            final Integer svc = rank;
            final Dictionary<String, Object> props = new Hashtable<String, Object>();
            props.put(Constants.SERVICE_RANKING, rank);
            sling.bundleContext().registerService(Integer.class.getName(), svc, props);
        }
    }

    private void assertHigherRankingComesFirst(Integer... values) {
        Integer previous = null;
        for (Integer current : values) {
            if (previous != null && current > previous) {
                fail("Ranking " + current + " is higher than previous " + previous);
            }
            previous = current;
        }
    }

    @Test
    public void testNullRefs() {
        assertNull("Expecting null if no services found", sh.getService(ScriptHelperTest.class));
    }

    @Test
    public void testGetServicesOrdering() {
        final Integer[] svc = sh.getServices(Integer.class, null);
        assertNotNull(svc);
        assertEquals(RANKINGS.length, svc.length);
        assertHigherRankingComesFirst(svc);
    }

    @Test
    public void testJakartaGetRequestResponseNoWrap() {
        final SlingJakartaHttpServletRequest req1 = Mockito.mock(OnDemandReaderJakartaRequest.class);
        final SlingJakartaHttpServletRequest request = new SlingJakartaHttpServletRequestWrapper(req1);
        final SlingJakartaHttpServletResponse res1 = Mockito.mock(OnDemandWriterJakartaResponse.class);
        final SlingJakartaHttpServletResponse response = new SlingJakartaHttpServletResponseWrapper(res1);

        final ScriptHelper scriptHelper = new ScriptHelper(sling.bundleContext(), null, request, response);

        assertNotNull(scriptHelper.getJakartaRequest());
        assertSame(request, scriptHelper.getJakartaRequest());

        assertNotNull(scriptHelper.getRequest());
        assertTrue(scriptHelper.getRequest() instanceof JakartaToJavaxRequestWrapper);
        assertSame(
                scriptHelper.getJakartaRequest(),
                ((JakartaToJavaxRequestWrapper) scriptHelper.getRequest()).getRequest());

        assertNotNull(scriptHelper.getJakartaResponse());
        assertSame(response, scriptHelper.getJakartaResponse());

        assertNotNull(scriptHelper.getResponse());
        assertTrue(scriptHelper.getResponse() instanceof JakartaToJavaxResponseWrapper);
        assertSame(
                scriptHelper.getJakartaResponse(),
                ((JakartaToJavaxResponseWrapper) scriptHelper.getResponse()).getResponse());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testGetRequestResponseNoWrap() {
        final SlingHttpServletRequest req1 = Mockito.mock(OnDemandReaderRequest.class);
        final SlingHttpServletRequest request = new SlingHttpServletRequestWrapper(req1);
        final SlingHttpServletResponse res1 = Mockito.mock(OnDemandWriterResponse.class);
        final SlingHttpServletResponse response = new SlingHttpServletResponseWrapper(res1);

        final ScriptHelper scriptHelper = new ScriptHelper(sling.bundleContext(), null, request, response);

        assertNotNull(scriptHelper.getRequest());
        assertSame(request, scriptHelper.getRequest());

        assertNotNull(scriptHelper.getJakartaRequest());
        assertTrue(scriptHelper.getJakartaRequest() instanceof JavaxToJakartaRequestWrapper);
        assertSame(
                scriptHelper.getRequest(),
                ((JavaxToJakartaRequestWrapper) scriptHelper.getJakartaRequest()).getRequest());

        assertNotNull(scriptHelper.getResponse());
        assertSame(response, scriptHelper.getResponse());

        assertNotNull(scriptHelper.getJakartaResponse());
        assertTrue(scriptHelper.getJakartaResponse() instanceof JavaxToJakartaResponseWrapper);
        assertSame(
                scriptHelper.getResponse(),
                ((JavaxToJakartaResponseWrapper) scriptHelper.getJakartaResponse()).getResponse());
    }

    @Test
    public void testJakartaGetRequestResponseWrap() {
        final SlingJakartaHttpServletRequest request = Mockito.mock(SlingJakartaHttpServletRequest.class);
        final SlingJakartaHttpServletResponse response = Mockito.mock(SlingJakartaHttpServletResponse.class);

        final ScriptHelper scriptHelper = new ScriptHelper(sling.bundleContext(), null, request, response);

        assertNotNull(scriptHelper.getJakartaRequest());
        assertTrue(scriptHelper.getJakartaRequest() instanceof OnDemandReaderJakartaRequest);
        assertSame(request, ((OnDemandReaderJakartaRequest) scriptHelper.getJakartaRequest()).getRequest());

        assertNotNull(scriptHelper.getRequest());
        assertTrue(scriptHelper.getRequest() instanceof JakartaToJavaxRequestWrapper);
        assertSame(
                scriptHelper.getJakartaRequest(),
                ((JakartaToJavaxRequestWrapper) scriptHelper.getRequest()).getRequest());

        assertNotNull(scriptHelper.getJakartaResponse());
        assertTrue(scriptHelper.getJakartaResponse() instanceof OnDemandWriterJakartaResponse);
        assertSame(response, ((OnDemandWriterJakartaResponse) scriptHelper.getJakartaResponse()).getResponse());

        assertNotNull(scriptHelper.getResponse());
        assertTrue(scriptHelper.getResponse() instanceof JakartaToJavaxResponseWrapper);
        assertSame(
                scriptHelper.getJakartaResponse(),
                ((JakartaToJavaxResponseWrapper) scriptHelper.getResponse()).getResponse());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testGetRequestResponseWrap() {
        final SlingHttpServletRequest request = Mockito.mock(SlingHttpServletRequest.class);
        final SlingHttpServletResponse response = Mockito.mock(SlingHttpServletResponse.class);

        final ScriptHelper scriptHelper = new ScriptHelper(sling.bundleContext(), null, request, response);

        assertNotNull(scriptHelper.getRequest());
        assertTrue(scriptHelper.getRequest() instanceof OnDemandReaderRequest);
        assertSame(request, ((OnDemandReaderRequest) scriptHelper.getRequest()).getRequest());

        assertNotNull(scriptHelper.getJakartaRequest());
        assertTrue(scriptHelper.getJakartaRequest() instanceof JavaxToJakartaRequestWrapper);
        assertSame(
                scriptHelper.getRequest(),
                ((JavaxToJakartaRequestWrapper) scriptHelper.getJakartaRequest()).getRequest());

        assertNotNull(scriptHelper.getResponse());
        assertTrue(scriptHelper.getResponse() instanceof OnDemandWriterResponse);
        assertSame(response, ((OnDemandWriterResponse) scriptHelper.getResponse()).getResponse());

        assertNotNull(scriptHelper.getJakartaResponse());
        assertTrue(scriptHelper.getJakartaResponse() instanceof JavaxToJakartaResponseWrapper);
        assertSame(
                scriptHelper.getResponse(),
                ((JavaxToJakartaResponseWrapper) scriptHelper.getJakartaResponse()).getResponse());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testJakartaGetRequestResponseCrossNoWrap() {
        final SlingHttpServletRequest req1 = Mockito.mock(OnDemandReaderRequest.class);
        final SlingJakartaHttpServletRequest req2 = JavaxToJakartaRequestWrapper.toJakartaRequest(req1);
        final SlingJakartaHttpServletRequest request = new SlingJakartaHttpServletRequestWrapper(req2);
        final SlingHttpServletResponse res1 = Mockito.mock(OnDemandWriterResponse.class);
        final SlingJakartaHttpServletResponse res2 = JavaxToJakartaResponseWrapper.toJakartaResponse(res1);
        final SlingJakartaHttpServletResponse response = new SlingJakartaHttpServletResponseWrapper(res2);

        final ScriptHelper scriptHelper = new ScriptHelper(sling.bundleContext(), null, request, response);

        assertNotNull(scriptHelper.getJakartaRequest());
        assertSame(request, scriptHelper.getJakartaRequest());

        assertNotNull(scriptHelper.getRequest());
        assertTrue(scriptHelper.getRequest() instanceof JakartaToJavaxRequestWrapper);
        assertSame(
                scriptHelper.getJakartaRequest(),
                ((JakartaToJavaxRequestWrapper) scriptHelper.getRequest()).getRequest());

        assertNotNull(scriptHelper.getJakartaResponse());
        assertSame(response, scriptHelper.getJakartaResponse());

        assertNotNull(scriptHelper.getResponse());
        assertTrue(scriptHelper.getResponse() instanceof JakartaToJavaxResponseWrapper);
        assertSame(
                scriptHelper.getJakartaResponse(),
                ((JakartaToJavaxResponseWrapper) scriptHelper.getResponse()).getResponse());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testGetRequestResponseCrossNoWrap() {
        final SlingJakartaHttpServletRequest req1 = Mockito.mock(OnDemandReaderJakartaRequest.class);
        final SlingHttpServletRequest req2 = JakartaToJavaxRequestWrapper.toJavaxRequest(req1);
        final SlingHttpServletRequest request = new SlingHttpServletRequestWrapper(req2);
        final SlingJakartaHttpServletResponse res1 = Mockito.mock(OnDemandWriterJakartaResponse.class);
        final SlingHttpServletResponse res2 = JakartaToJavaxResponseWrapper.toJavaxResponse(res1);
        final SlingHttpServletResponse response = new SlingHttpServletResponseWrapper(res2);

        final ScriptHelper scriptHelper = new ScriptHelper(sling.bundleContext(), null, request, response);

        assertNotNull(scriptHelper.getRequest());
        assertSame(request, scriptHelper.getRequest());

        assertNotNull(scriptHelper.getJakartaRequest());
        assertTrue(scriptHelper.getJakartaRequest() instanceof JavaxToJakartaRequestWrapper);
        assertSame(
                scriptHelper.getRequest(),
                ((JavaxToJakartaRequestWrapper) scriptHelper.getJakartaRequest()).getRequest());

        assertNotNull(scriptHelper.getResponse());
        assertSame(response, scriptHelper.getResponse());

        assertNotNull(scriptHelper.getJakartaResponse());
        assertTrue(scriptHelper.getJakartaResponse() instanceof JavaxToJakartaResponseWrapper);
        assertSame(
                scriptHelper.getResponse(),
                ((JavaxToJakartaResponseWrapper) scriptHelper.getJakartaResponse()).getResponse());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testJakartaGetRequestResponseCross2NoWrap() {
        final SlingJakartaHttpServletRequest req1 = Mockito.mock(OnDemandReaderJakartaRequest.class);
        final SlingHttpServletRequest req2 = JakartaToJavaxRequestWrapper.toJavaxRequest(req1);
        final SlingJakartaHttpServletRequest req3 = JavaxToJakartaRequestWrapper.toJakartaRequest(req2);
        final SlingJakartaHttpServletRequest request = new SlingJakartaHttpServletRequestWrapper(req3);
        final SlingJakartaHttpServletResponse res1 = Mockito.mock(OnDemandWriterJakartaResponse.class);
        final SlingHttpServletResponse res2 = JakartaToJavaxResponseWrapper.toJavaxResponse(res1);
        final SlingJakartaHttpServletResponse res3 = JavaxToJakartaResponseWrapper.toJakartaResponse(res2);
        final SlingJakartaHttpServletResponse response = new SlingJakartaHttpServletResponseWrapper(res3);

        final ScriptHelper scriptHelper = new ScriptHelper(sling.bundleContext(), null, request, response);

        assertNotNull(scriptHelper.getJakartaRequest());
        assertSame(request, scriptHelper.getJakartaRequest());

        assertNotNull(scriptHelper.getRequest());
        assertTrue(scriptHelper.getRequest() instanceof JakartaToJavaxRequestWrapper);
        assertSame(
                scriptHelper.getJakartaRequest(),
                ((JakartaToJavaxRequestWrapper) scriptHelper.getRequest()).getRequest());

        assertNotNull(scriptHelper.getJakartaResponse());
        assertSame(response, scriptHelper.getJakartaResponse());

        assertNotNull(scriptHelper.getResponse());
        assertTrue(scriptHelper.getResponse() instanceof JakartaToJavaxResponseWrapper);
        assertSame(
                scriptHelper.getJakartaResponse(),
                ((JakartaToJavaxResponseWrapper) scriptHelper.getResponse()).getResponse());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testGetRequestResponseCross2NoWrap() {
        final SlingHttpServletRequest req1 = Mockito.mock(OnDemandReaderRequest.class);
        final SlingJakartaHttpServletRequest req2 = JavaxToJakartaRequestWrapper.toJakartaRequest(req1);
        final SlingHttpServletRequest req3 = JakartaToJavaxRequestWrapper.toJavaxRequest(req2);
        final SlingHttpServletRequest request = new SlingHttpServletRequestWrapper(req3);
        final SlingHttpServletResponse res1 = Mockito.mock(OnDemandWriterResponse.class);
        final SlingJakartaHttpServletResponse res2 = JavaxToJakartaResponseWrapper.toJakartaResponse(res1);
        final SlingHttpServletResponse res3 = JakartaToJavaxResponseWrapper.toJavaxResponse(res2);
        final SlingHttpServletResponse response = new SlingHttpServletResponseWrapper(res3);

        final ScriptHelper scriptHelper = new ScriptHelper(sling.bundleContext(), null, request, response);

        assertNotNull(scriptHelper.getRequest());
        assertSame(request, scriptHelper.getRequest());

        assertNotNull(scriptHelper.getJakartaRequest());
        assertTrue(scriptHelper.getJakartaRequest() instanceof JavaxToJakartaRequestWrapper);
        assertSame(
                scriptHelper.getRequest(),
                ((JavaxToJakartaRequestWrapper) scriptHelper.getJakartaRequest()).getRequest());

        assertNotNull(scriptHelper.getResponse());
        assertSame(response, scriptHelper.getResponse());

        assertNotNull(scriptHelper.getJakartaResponse());
        assertTrue(scriptHelper.getJakartaResponse() instanceof JavaxToJakartaResponseWrapper);
        assertSame(
                scriptHelper.getResponse(),
                ((JavaxToJakartaResponseWrapper) scriptHelper.getJakartaResponse()).getResponse());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testJakartaGetRequestResponseCross3NoWrap() {
        final SlingHttpServletRequest req1 = Mockito.mock(OnDemandReaderRequest.class);
        final SlingJakartaHttpServletRequest req2 = JavaxToJakartaRequestWrapper.toJakartaRequest(req1);
        final SlingHttpServletRequest req3 = JakartaToJavaxRequestWrapper.toJavaxRequest(req2);
        final SlingJakartaHttpServletRequest req4 = JavaxToJakartaRequestWrapper.toJakartaRequest(req3);
        final SlingJakartaHttpServletRequest request = new SlingJakartaHttpServletRequestWrapper(req4);
        final SlingHttpServletResponse res1 = Mockito.mock(OnDemandWriterResponse.class);
        final SlingJakartaHttpServletResponse res2 = JavaxToJakartaResponseWrapper.toJakartaResponse(res1);
        final SlingHttpServletResponse res3 = JakartaToJavaxResponseWrapper.toJavaxResponse(res2);
        final SlingJakartaHttpServletResponse res4 = JavaxToJakartaResponseWrapper.toJakartaResponse(res3);
        final SlingJakartaHttpServletResponse response = new SlingJakartaHttpServletResponseWrapper(res4);

        final ScriptHelper scriptHelper = new ScriptHelper(sling.bundleContext(), null, request, response);

        assertNotNull(scriptHelper.getJakartaRequest());
        assertSame(request, scriptHelper.getJakartaRequest());

        assertNotNull(scriptHelper.getRequest());
        assertTrue(scriptHelper.getRequest() instanceof JakartaToJavaxRequestWrapper);
        assertSame(
                scriptHelper.getJakartaRequest(),
                ((JakartaToJavaxRequestWrapper) scriptHelper.getRequest()).getRequest());

        assertNotNull(scriptHelper.getJakartaResponse());
        assertSame(response, scriptHelper.getJakartaResponse());

        assertNotNull(scriptHelper.getResponse());
        assertTrue(scriptHelper.getResponse() instanceof JakartaToJavaxResponseWrapper);
        assertSame(
                scriptHelper.getJakartaResponse(),
                ((JakartaToJavaxResponseWrapper) scriptHelper.getResponse()).getResponse());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testGetRequestResponseCross3NoWrap() {
        final SlingJakartaHttpServletRequest req1 = Mockito.mock(OnDemandReaderJakartaRequest.class);
        final SlingHttpServletRequest req2 = JakartaToJavaxRequestWrapper.toJavaxRequest(req1);
        final SlingJakartaHttpServletRequest req3 = JavaxToJakartaRequestWrapper.toJakartaRequest(req2);
        final SlingHttpServletRequest req4 = JakartaToJavaxRequestWrapper.toJavaxRequest(req3);
        final SlingHttpServletRequest request = new SlingHttpServletRequestWrapper(req4);
        final SlingJakartaHttpServletResponse res1 = Mockito.mock(OnDemandWriterJakartaResponse.class);
        final SlingHttpServletResponse res2 = JakartaToJavaxResponseWrapper.toJavaxResponse(res1);
        final SlingJakartaHttpServletResponse res3 = JavaxToJakartaResponseWrapper.toJakartaResponse(res2);
        final SlingHttpServletResponse res4 = JakartaToJavaxResponseWrapper.toJavaxResponse(res3);
        final SlingHttpServletResponse response = new SlingHttpServletResponseWrapper(res4);

        final ScriptHelper scriptHelper = new ScriptHelper(sling.bundleContext(), null, request, response);

        assertNotNull(scriptHelper.getRequest());
        assertSame(request, scriptHelper.getRequest());

        assertNotNull(scriptHelper.getJakartaRequest());
        assertTrue(scriptHelper.getJakartaRequest() instanceof JavaxToJakartaRequestWrapper);
        assertSame(
                scriptHelper.getRequest(),
                ((JavaxToJakartaRequestWrapper) scriptHelper.getJakartaRequest()).getRequest());

        assertNotNull(scriptHelper.getResponse());
        assertSame(response, scriptHelper.getResponse());

        assertNotNull(scriptHelper.getJakartaResponse());
        assertTrue(scriptHelper.getJakartaResponse() instanceof JavaxToJakartaResponseWrapper);
        assertSame(
                scriptHelper.getResponse(),
                ((JavaxToJakartaResponseWrapper) scriptHelper.getJakartaResponse()).getResponse());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testJakartaGetRequestResponseCross3Wrap() {
        final SlingHttpServletRequest req1 = Mockito.mock(SlingHttpServletRequest.class);
        final SlingJakartaHttpServletRequest req2 = JavaxToJakartaRequestWrapper.toJakartaRequest(req1);
        final SlingHttpServletRequest req3 = JakartaToJavaxRequestWrapper.toJavaxRequest(req2);
        final SlingJakartaHttpServletRequest req4 = JavaxToJakartaRequestWrapper.toJakartaRequest(req3);
        final SlingJakartaHttpServletRequest request = new SlingJakartaHttpServletRequestWrapper(req4);
        final SlingHttpServletResponse res1 = Mockito.mock(SlingHttpServletResponse.class);
        final SlingJakartaHttpServletResponse res2 = JavaxToJakartaResponseWrapper.toJakartaResponse(res1);
        final SlingHttpServletResponse res3 = JakartaToJavaxResponseWrapper.toJavaxResponse(res2);
        final SlingJakartaHttpServletResponse res4 = JavaxToJakartaResponseWrapper.toJakartaResponse(res3);
        final SlingJakartaHttpServletResponse response = new SlingJakartaHttpServletResponseWrapper(res4);

        final ScriptHelper scriptHelper = new ScriptHelper(sling.bundleContext(), null, request, response);

        assertNotNull(scriptHelper.getJakartaRequest());
        assertTrue(scriptHelper.getJakartaRequest() instanceof OnDemandReaderJakartaRequest);
        assertSame(request, ((OnDemandReaderJakartaRequest) scriptHelper.getJakartaRequest()).getRequest());

        assertNotNull(scriptHelper.getRequest());
        assertTrue(scriptHelper.getRequest() instanceof JakartaToJavaxRequestWrapper);
        assertSame(
                scriptHelper.getJakartaRequest(),
                ((JakartaToJavaxRequestWrapper) scriptHelper.getRequest()).getRequest());

        assertNotNull(scriptHelper.getJakartaResponse());
        assertTrue(scriptHelper.getJakartaResponse() instanceof OnDemandWriterJakartaResponse);
        assertSame(response, ((OnDemandWriterJakartaResponse) scriptHelper.getJakartaResponse()).getResponse());

        assertNotNull(scriptHelper.getResponse());
        assertTrue(scriptHelper.getResponse() instanceof JakartaToJavaxResponseWrapper);
        assertSame(
                scriptHelper.getJakartaResponse(),
                ((JakartaToJavaxResponseWrapper) scriptHelper.getResponse()).getResponse());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testGetRequestResponseCross3Wrap() {
        final SlingJakartaHttpServletRequest req1 = Mockito.mock(SlingJakartaHttpServletRequest.class);
        final SlingHttpServletRequest req2 = JakartaToJavaxRequestWrapper.toJavaxRequest(req1);
        final SlingJakartaHttpServletRequest req3 = JavaxToJakartaRequestWrapper.toJakartaRequest(req2);
        final SlingHttpServletRequest req4 = JakartaToJavaxRequestWrapper.toJavaxRequest(req3);
        final SlingHttpServletRequest request = new SlingHttpServletRequestWrapper(req4);
        final SlingJakartaHttpServletResponse res1 = Mockito.mock(SlingJakartaHttpServletResponse.class);
        final SlingHttpServletResponse res2 = JakartaToJavaxResponseWrapper.toJavaxResponse(res1);
        final SlingJakartaHttpServletResponse res3 = JavaxToJakartaResponseWrapper.toJakartaResponse(res2);
        final SlingHttpServletResponse res4 = JakartaToJavaxResponseWrapper.toJavaxResponse(res3);
        final SlingHttpServletResponse response = new SlingHttpServletResponseWrapper(res4);

        final ScriptHelper scriptHelper = new ScriptHelper(sling.bundleContext(), null, request, response);

        assertNotNull(scriptHelper.getRequest());
        assertTrue(scriptHelper.getRequest() instanceof OnDemandReaderRequest);
        assertSame(request, ((OnDemandReaderRequest) scriptHelper.getRequest()).getRequest());

        assertNotNull(scriptHelper.getJakartaRequest());
        assertTrue(scriptHelper.getJakartaRequest() instanceof JavaxToJakartaRequestWrapper);
        assertSame(
                scriptHelper.getRequest(),
                ((JavaxToJakartaRequestWrapper) scriptHelper.getJakartaRequest()).getRequest());

        assertNotNull(scriptHelper.getResponse());
        assertTrue(scriptHelper.getResponse() instanceof OnDemandWriterResponse);
        assertSame(response, ((OnDemandWriterResponse) scriptHelper.getResponse()).getResponse());

        assertNotNull(scriptHelper.getJakartaResponse());
        assertTrue(scriptHelper.getJakartaResponse() instanceof JavaxToJakartaResponseWrapper);
        assertSame(
                scriptHelper.getResponse(),
                ((JavaxToJakartaResponseWrapper) scriptHelper.getJakartaResponse()).getResponse());
    }
}
