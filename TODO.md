# TODO

## Implement a Custom WorkspaceReader for Aether Extension

**Problem:**
Right now, the test-fixtures are being compiled directly into `target/test-classes`. While this acts as a functional temporary workaround so that `maven-compiler-plugin` and `maven-surefire-plugin` can seamlessly pick up the classes, it is not "maximally correct". Compiling into `target/test-classes` merges the fixtures into the regular test compilation output, preventing proper isolation and creating potential conflicts between test fixture code and regular test code.

**Solution:**
We should implement a custom `WorkspaceReader` via an Aether Extension.

### Why a Custom WorkspaceReader?
A custom `WorkspaceReader` acts as an interceptor during dependency resolution. By hooking into Eclipse Aether (the dependency resolution engine Maven uses), we can dynamically provide the location of our compiled `test-fixtures` directories or packages before they are even installed into the local repository or packaged into JARs.

This means we can:
1. Compile test fixtures into their own isolated output directory (e.g., `target/test-fixtures-classes`).
2. Prevent pollution of `target/test-classes`.
3. Inform Maven/Aether that whenever a project in the reactor requests the `test-fixtures` artifact (or when we need to add it to the test classpath), it should resolve to `target/test-fixtures-classes`.

### How to Implement:
1. **Create an Aether Extension:** Create a component with `@Named` and `@Singleton` annotations that implements `org.eclipse.aether.repository.WorkspaceReader`.
2. **Implement `findArtifact(Artifact artifact)`:** Check if the requested artifact matches our `groupId`, `artifactId` plus the `-test-fixtures` suffix. If it does, return the `File` pointing to the isolated output directory (`target/test-fixtures-classes`).
3. **Register the Extension:** Make sure the extension is loaded by Maven early in the build process (for instance, via `<extensions>true</extensions>` in the plugin declaration or placing it in `.mvn/extensions.xml`).
4. **Update `CompileFixturesMojo`:** Refactor the compile mojo to no longer dump compiled classes into `target/test-classes` but solely into the isolated package output directory, relying on the `WorkspaceReader` to satisfy resolution.