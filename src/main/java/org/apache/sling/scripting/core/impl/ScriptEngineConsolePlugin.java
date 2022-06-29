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
package org.apache.sling.scripting.core.impl;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.List;

import javax.script.ScriptEngineFactory;
import javax.servlet.Servlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.felix.webconsole.AbstractWebConsolePlugin;
import org.apache.felix.webconsole.WebConsoleConstants;
import org.apache.sling.scripting.core.impl.jsr223.SlingScriptEngineManager;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(
        property = {
                Constants.SERVICE_DESCRIPTION + "=Web Console Plugin for ScriptEngine implementations",
                Constants.SERVICE_VENDOR + "=The Apache Software Foundation",
                WebConsoleConstants.PLUGIN_LABEL + "=" + ScriptEngineConsolePlugin.CONSOLE_LABEL,
                WebConsoleConstants.PLUGIN_TITLE + "=" + ScriptEngineConsolePlugin.CONSOLE_TITLE,
                WebConsoleConstants.CONFIG_PRINTER_MODES + "=always",
                WebConsoleConstants.PLUGIN_CATEGORY + "=Status"
        },
        service = { Servlet.class }
)
public class ScriptEngineConsolePlugin extends AbstractWebConsolePlugin {

    private static final long serialVersionUID = -6444729200748932100L;

    public static final String CONSOLE_LABEL = "slingscripting";
    public static final String CONSOLE_TITLE = "Script Engines";

    @Reference
    private SlingScriptEngineManager slingScriptEngineManager;

    @Override
    public String getTitle() {
        return CONSOLE_TITLE;
    }

    @Override
    public String getLabel() {
        return CONSOLE_LABEL;
    }

    @Override
    protected void renderContent(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse)
            throws IOException {
        PrintWriter pw = httpServletResponse.getWriter();
        pw.println("<div id='content' class='ui-widget'><br>");
        pw.println("<pre>");
        pw.println("Available Script Engines");
        pw.println("========================");

        List<?> factories = slingScriptEngineManager.getEngineFactories();
        for (Iterator<?> fi = factories.iterator(); fi.hasNext();) {

            final ScriptEngineFactory factory = (ScriptEngineFactory) fi.next();

            pw.println();
            pw.print(factory.getEngineName());
            pw.print(" ");
            pw.println(factory.getEngineVersion());
            pw.println("-------------------------------------");
            pw.print("- Language : ");
            pw.print(factory.getLanguageName());
            pw.print(", ");
            pw.println(factory.getLanguageVersion());

            pw.print("- Extensions : ");
            printArray(pw, factory.getExtensions());

            pw.print("- MIME Types : ");
            printArray(pw, factory.getMimeTypes());

            pw.print("- Names : ");
            printArray(pw, factory.getNames());
        }
        pw.println("</pre>");
        pw.println("</div>");
    }

    private void printArray(PrintWriter pw, List<?> values) {
        if (values == null || values.isEmpty()) {
            pw.println("-");
        } else {
            for (Iterator<?> vi = values.iterator(); vi.hasNext();) {
                pw.print(vi.next());
                if (vi.hasNext()) {
                    pw.print(", ");
                }
            }
            pw.println();
        }
    }

}
