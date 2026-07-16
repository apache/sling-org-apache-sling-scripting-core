# Security

<!-- sling-security-default:start -->
The threat model for this project is https://github.com/apache/sling/blob/master/docs/threat-model.md .
<!-- sling-security-default:end -->

## Console plugin hardening

Recent console plugin updates introduced stricter servlet error handling patterns in:

- `src/main/java/org/apache/sling/scripting/core/impl/ScriptCacheConsolePlugin.java`
- `src/main/java/org/apache/sling/scripting/core/impl/ScriptingVariablesConsolePlugin.java`

When changing Web Console servlets in this module:

- Wrap `doGet` / `doPost` logic in narrow `try/catch` blocks for checked and runtime failures relevant to the method body.
- Log the failure with contextual messages, and send explicit `500` responses when possible (only if the response is not already committed).
- For classpath resource streaming, null-check resource streams and return `404` when the resource is missing.
- Keep existing authorization and request validation behavior intact; fail closed with explicit HTTP error codes.

## Dependencies, commands, and structure

- No new build/test commands were introduced by the recent console plugin fix.
- No new module-level dependencies or repository structure changes were introduced.
