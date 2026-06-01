# Project Overview

Apache Sling Scripting Core is an OSGi bundle that provides the scripting infrastructure for Apache Sling. It manages script engine registration (via JSR-223), script caching, bindings values providers, bundled precompiled scripts, and the `SlingScript` adapter factory. The bundle runs inside an OSGi container (Felix) and integrates tightly with the Sling API, resource resolver, and servlet resolution chain.

# Core Commands

```bash
# Build and package (skips integration tests)
mvn clean package -DskipTests

# Run unit tests only (maven-surefire)
mvn test

# Run full suite including integration tests (requires OSGi container via Pax Exam)
mvn verify

# Run a single unit test class
mvn test -Dtest=ScriptCacheImplTest

# Run a single integration test class
mvn verify -Dit.test=HtmlScriptingIT

# Check license headers and code style (Spotless + RAT)
mvn spotless:check apache-rat:check

# Auto-fix Spotless formatting issues
mvn spotless:apply

# Baseline API check (bnd)
mvn package bnd-baseline:check
```

No dev server — this is an OSGi bundle deployed into a Sling instance, not a standalone application.

# Project Layout

```
pom.xml                          Maven build descriptor
bnd.bnd                          OSGi bundle manifest / baseline config
src/
  main/
    java/org/apache/sling/scripting/core/
      ScriptHelper.java           Public API helper
      ScriptNameAwareReader.java  Public API reader
      impl/
        DefaultSlingScript.java   Core script execution logic
        ScriptCacheImpl.java      Script caching OSGi service
        SlingScriptAdapterFactory.java  Adapts resources to SlingScript
        BindingsValuesProvidersByContextImpl.java  Bindings aggregation
        ScriptingResourceResolverProviderImpl.java
        jsr223/                   JSR-223 script engine management
        bundled/                  Bundled/precompiled script support
        helper/                   Internal helpers (ProtectedBindings, etc.)
      servlet/                    Servlet integration
    resources/                    OSGI-INF component descriptors
  test/
    java/                         Unit tests (JUnit 4 + OSGi mock / sling-mock)
    resources/                    Test content / OSGi configs for Pax Exam ITs
target/
  surefire-reports/               Unit test results
  failsafe-reports/               Integration test results
  paxexam/                        Pax Exam container working directories
```

# Development Patterns & Constraints

- **Java 17**, no preview features.
- **OSGi R7** component model: use `@Component`, `@Reference`, `@Activate`/`@Deactivate` from `org.osgi.service.component.annotations`. No Felix SCR annotations.
- Constructor injection is preferred for mandatory references; field injection only for optional or dynamic references.
- Metatype configuration via `@ObjectClassDefinition` in a separate `*Configuration` interface (see `ScriptCacheImplConfiguration`).
- All `impl.*` and sub-packages are internal; do not expose internal types in public API (`package-info.java` enforces `@Version`).
- Code style enforced by **Spotless** (Eclipse formatter config from parent POM). Run `mvn spotless:apply` before committing.
- License headers required on every source file; checked by Apache RAT.
- No `null` returns on public APIs — use `@Nullable`/`@NotNull` (JetBrains annotations).
- Logging via SLF4J only (`LoggerFactory.getLogger(getClass())`).

# Git Workflow

- Branches: feature branches off `master`; use `SLING-XXXXX` JIRA issue prefix (e.g., `SLING-12345-fix-cache-eviction`).
- Commit messages: start with the JIRA issue key — `SLING-XXXXX Fix short description`.
- PRs target `master`; CI runs via Jenkins (`Jenkinsfile` at repo root).
- Contributions require a signed Apache CLA. See https://sling.apache.org/contributing.html.

# Testing Guidelines

- **Unit tests**: JUnit 4, located in `src/test/java`. Use `org.apache.sling.testing.osgi-mock` and `sling-mock` for OSGi/Sling context. Mockito 5 for mocking.
- **Integration tests**: Pax Exam 4 in `src/test/java/.../it/`. Class names end in `IT`. Run with `mvn verify` (maven-failsafe).
- Place test resources under `src/test/resources/`.
- Coverage is not enforced by a gate; aim to cover OSGi lifecycle paths (`@Activate`/`@Deactivate`) explicitly.

# Gotchas

- Integration tests spin up a full Felix OSGi container via Pax Exam — they are slow (~minutes) and require network access to download bundles on first run.
- The `bnd.bnd` baseline file tracks exported package versions. If you change a public API without bumping the package version, `bnd-baseline:check` will fail at build time.
- `impl` packages are excluded from Javadoc generation intentionally — don't add `package-info.java` exports there without a version bump discussion.
- Both `javax.servlet` and `jakarta.servlet` APIs are dependencies; the bundle supports dual-stack environments. Don't break either import.
- Spotless reformats on `spotless:apply` but only stages changes — commit them separately.
