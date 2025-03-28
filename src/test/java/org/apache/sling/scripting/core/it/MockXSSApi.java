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

import org.apache.sling.xss.XSSAPI;
import org.osgi.service.component.annotations.Component;

@Component(service = XSSAPI.class)
public class MockXSSApi implements XSSAPI {

    @Override
    public String encodeForCSSString(String arg0) {
        return arg0;
    }

    @Override
    public String encodeForHTML(String arg0) {
        return arg0;
    }

    @Override
    public String encodeForHTMLAttr(String arg0) {
        return arg0;
    }

    @Override
    public String encodeForJSString(String arg0) {
        return arg0;
    }

    @Override
    public String encodeForXML(String arg0) {
        return arg0;
    }

    @Override
    public String encodeForXMLAttr(String arg0) {
        return arg0;
    }

    @Override
    public String filterHTML(String arg0) {
        return arg0;
    }

    @Override
    public String getValidCSSColor(String arg0, String arg1) {
        return arg0;
    }

    @Override
    public String getValidDimension(String arg0, String arg1) {
        return arg0;
    }

    @Override
    public Double getValidDouble(String arg0, double arg1) {
        return arg1;
    }

    @Override
    public String getValidHref(String arg0) {
        return arg0;
    }

    @Override
    public Integer getValidInteger(String arg0, int arg1) {
        return arg1;
    }

    @Override
    public String getValidJSON(String arg0, String arg1) {
        return arg0;
    }

    @Override
    public String getValidJSToken(String arg0, String arg1) {
        return arg0;
    }

    @Override
    public Long getValidLong(String arg0, long arg1) {
        return arg1;
    }

    @Override
    public String getValidMultiLineComment(String arg0, String arg1) {
        return arg0;
    }

    @Override
    public String getValidStyleToken(String arg0, String arg1) {
        return arg0;
    }

    @Override
    public String getValidXML(String arg0, String arg1) {
        return arg0;
    }
}
