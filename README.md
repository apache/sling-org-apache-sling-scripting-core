[![Apache Sling](https://sling.apache.org/res/logos/sling.png)](https://sling.apache.org)

&#32;[![Build Status](https://ci-builds.apache.org/job/Sling/job/modules/job/sling-org-apache-sling-scripting-core/job/master/badge/icon)](https://ci-builds.apache.org/job/Sling/job/modules/job/sling-org-apache-sling-scripting-core/job/master/)&#32;[![Test Status](https://img.shields.io/jenkins/tests.svg?jobUrl=https://ci-builds.apache.org/job/Sling/job/modules/job/sling-org-apache-sling-scripting-core/job/master/)](https://ci-builds.apache.org/job/Sling/job/modules/job/sling-org-apache-sling-scripting-core/job/master/test/?width=800&height=600)&#32;[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=apache_sling-org-apache-sling-scripting-core&metric=coverage)](https://sonarcloud.io/dashboard?id=apache_sling-org-apache-sling-scripting-core)&#32;[![Sonarcloud Status](https://sonarcloud.io/api/project_badges/measure?project=apache_sling-org-apache-sling-scripting-core&metric=alert_status)](https://sonarcloud.io/dashboard?id=apache_sling-org-apache-sling-scripting-core)&#32;[![JavaDoc](https://www.javadoc.io/badge/org.apache.sling/org.apache.sling.scripting.core.svg)](https://www.javadoc.io/doc/org.apache.sling/org.apache.sling.scripting.core)&#32;[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.apache.sling/org.apache.sling.scripting.core/badge.svg)](https://search.maven.org/#search%7Cga%7C1%7Cg%3A%22org.apache.sling%22%20a%3A%22org.apache.sling.scripting.core%22)&#32;[![scripting](https://sling.apache.org/badges/group-scripting.svg)](https://github.com/apache/sling-aggregator/blob/master/docs/groups/scripting.md) [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)

# Apache Sling Scripting Core implementation

This module is part of the [Apache Sling](https://sling.apache.org) project.

Apache Sling Scripting Core provides the scripting infrastructure for Sling, including:

- JSR-223 script engine registration and management
- Script execution and adaptation to `SlingScript`
- Script caching and related OSGi services
- Bindings values provider aggregation by context
- Bundled and precompiled script support

## Current baseline

- Java 17 (`<sling.java.version>17</sling.java.version>`)
- OSGi R7 Declarative Services annotations (`org.osgi.service.component.annotations`)
- Sling Bundle Parent `66`
- Dual servlet API support (`javax.servlet` and `jakarta.servlet`)
- Current development version: `3.0.3-SNAPSHOT`

## Build and test

```bash
# Build and package (skip tests)
mvn clean package -DskipTests

# Run unit tests
mvn test

# Run full build including integration tests (Pax Exam)
mvn verify

# Run one unit test class
mvn test -Dtest=ScriptCacheImplTest

# Run one integration test class
mvn verify -Dit.test=HtmlScriptingIT

# Run formatting/license checks
mvn spotless:check apache-rat:check

# Apply formatting fixes
mvn spotless:apply

# Run API baseline check
mvn package bnd-baseline:check
```

## Repository layout

```text
pom.xml                          Maven build descriptor
bnd.bnd                          OSGi bundle instructions
src/
  main/
    java/org/apache/sling/scripting/core/
      ScriptHelper.java
      ScriptNameAwareReader.java
      impl/                      Internal implementation
        bundled/                 Bundled/precompiled script support
        helper/                  Internal helper utilities
        jsr223/                  JSR-223 integration
      servlet/                   Servlet integration
    resources/                   OSGI-INF component descriptors
  test/
    java/                        Unit tests and Pax Exam integration tests
    resources/                   Test content and configurations
target/
  surefire-reports/              Unit test reports
  failsafe-reports/              Integration test reports
  paxexam/                       Pax Exam working directories
```

## Notes

- Integration tests run in a real OSGi container via Pax Exam and are slower than unit tests.
- Internal implementation packages (`impl.*`) are intentionally not part of the public API surface.
