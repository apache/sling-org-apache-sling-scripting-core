[![Apache Sling](https://sling.apache.org/res/logos/sling.png)](https://sling.apache.org)

&#32;[![Build Status](https://ci-builds.apache.org/job/Sling/job/modules/job/sling-org-apache-sling-scripting-core/job/master/badge/icon)](https://ci-builds.apache.org/job/Sling/job/modules/job/sling-org-apache-sling-scripting-core/job/master/)&#32;[![Test Status](https://img.shields.io/jenkins/tests.svg?jobUrl=https://ci-builds.apache.org/job/Sling/job/modules/job/sling-org-apache-sling-scripting-core/job/master/)](https://ci-builds.apache.org/job/Sling/job/modules/job/sling-org-apache-sling-scripting-core/job/master/test/?width=800&height=600)&#32;[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=apache_sling-org-apache-sling-scripting-core&metric=coverage)](https://sonarcloud.io/dashboard?id=apache_sling-org-apache-sling-scripting-core)&#32;[![Sonarcloud Status](https://sonarcloud.io/api/project_badges/measure?project=apache_sling-org-apache-sling-scripting-core&metric=alert_status)](https://sonarcloud.io/dashboard?id=apache_sling-org-apache-sling-scripting-core)&#32;[![JavaDoc](https://www.javadoc.io/badge/org.apache.sling/org.apache.sling.scripting.core.svg)](https://www.javadoc.io/doc/org.apache.sling/org.apache.sling.scripting.core)&#32;[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.apache.sling/org.apache.sling.scripting.core/badge.svg)](https://search.maven.org/#search%7Cga%7C1%7Cg%3A%22org.apache.sling%22%20a%3A%22org.apache.sling.scripting.core%22)&#32;[![scripting](https://sling.apache.org/badges/group-scripting.svg)](https://github.com/apache/sling-aggregator/blob/master/docs/groups/scripting.md) [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)

# Apache Sling Scripting Core implementation

This module is part of the [Apache Sling](https://sling.apache.org) project.

The Apache Sling Scripting Core bundle provides core scripting runtime functionality for Sling, including script engine integration, script caching, bindings support, and bundled script handling.

## Current baseline

- Java 17 (`<sling.java.version>17</sling.java.version>`)
- Parent POM: `org.apache.sling:sling-bundle-parent:66`
- Current module version: `3.0.3-SNAPSHOT`

## Servlet API support

This module supports both servlet namespaces used in Sling deployments:

- `javax.servlet-api` 4.0.1
- `jakarta.servlet-api` 6.1.0

Recent updates include Jakarta-oriented scripting context handling fixes and hardened Web Console plugin error handling.

## Build and test

Run a full local build (including unit and integration tests):

```bash
mvn clean verify
```

## Repository structure

- `src/main/java` - production code
- `src/main/resources` - OSGi metadata and resources
- `src/test/java` - unit and integration tests
- `src/test/resources` - test resources
