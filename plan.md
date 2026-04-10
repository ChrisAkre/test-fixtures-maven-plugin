1. Add a custom `WorkspaceReader` in `TestFixturesWorkspaceReader.java` annotated with `@Named("test-fixtures-reader")`.
2. Implement `findArtifact` in `TestFixturesWorkspaceReader.java`.
   - When Aether asks for an artifact ending in `-test-fixtures` with the extension `jar`, return the absolute path to `module-a/target/test-fixtures-classes`.
   - When Aether asks for the same artifact with the extension `pom`, return the absolute path to the synthetic `module-a/target/test-fixtures.pom`.
3. Modify `PackageFixturesMojo.java` and `CompileFixturesMojo.java` to shift the POM synthesis.
   - Currently, POM synthesis occurs in `PackageFixturesMojo` (phase `package`). It must be moved to `CompileFixturesMojo` (phase `process-classes`) to be available during `test-compile` of dependent modules.
   - Move the generation logic of the synthetic POM into `CompileFixturesMojo.java`.
   - Update `PackageFixturesMojo.java` to use the already generated POM instead of generating it.
4. Run pre-commit instructions and check the build.
