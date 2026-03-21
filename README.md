# Maven Test Fixtures Plugin

The **Maven Test Fixtures Plugin** orchestrates the creation, packaging, and publishing of Java test fixtures in a Maven build. It acts as a shadow build system within the standard Maven lifecycle, managing an isolated compilation phase, constructing a synthetic POM in memory, and bypassing the standard deployer to push multiple distinct artifact graphs to the repository.

This plugin ensures a robust first-class experience for consumers while avoiding the need to migrate your entire build system, achieving seamless interoperability without circular dependencies.

## Usage

To use this plugin and define dependencies that apply strictly to your test fixtures, configure your `pom.xml` as follows:

```xml
<plugin>
    <groupId>dev.akre</groupId>
    <artifactId>test-fixtures-maven-plugin</artifactId>
    <version>1.0.0</version>
    <extensions>true</extensions>
    <configuration>
        <publishTests>true</publishTests>
        <fixtureDependencies>
            <dependency>
                <groupId>org.mockito</groupId>
                <artifactId>mockito-core</artifactId>
                <version>5.11.0</version>
            </dependency>
            <!-- Add other test fixture dependencies here -->
        </fixtureDependencies>
    </configuration>
    <executions>
        <execution>
            <goals>
                <goal>compile-fixtures</goal>
                <goal>package-fixtures</goal>
                <goal>install-fixtures</goal>
                <goal>deploy-fixtures</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

By adding this configuration, the plugin will:
1. Compile the test fixtures separately using dependencies defined in the `<fixtureDependencies>` section.
2. Package the classes into an isolated test fixtures JAR.
3. Generate a synthetic POM with a direct dependency on your project's main JAR, preserving the specific dependencies needed for test fixtures as `compile` scope.
4. Optionally install and deploy these artifacts to local and remote repositories.

---

## Technical Specifications

### **1. Plugin Overview & Lifecycle Mapping**

The plugin consists of multiple Mojos bound to specific phases of the standard `default` lifecycle. It intercepts the build between the compilation of main sources and the compilation of test sources.

* **`process-classes` (`compile-fixtures`):** Compile the test fixtures using a custom classpath.
* **`generate-test-sources`:** Inject the compiled fixtures into the standard Maven test classpath.
* **`package` (`package-fixtures`):** Generates the synthetic POMs for the `-test-fixtures` artifacts and archives the class directories into separate JARs.
* **`install` (`install-fixtures`):** Installs the packaged test fixtures and synthetic POM into the local repository.
* **`deploy` (`deploy-fixtures`):** Uses Eclipse Aether to publish the artifacts and their synthetic POMs.

### **2. Mojo Architecture & Implementation Details**

#### **Mojo 1: The Isolated Compiler (`compile-fixtures`)**
* **Phase:** `process-classes` (Executes immediately after `maven-compiler-plugin:compile`).
* **Input:** `src/test-fixtures/java`.
* **Action:**
    1. Resolve the `<fixtureDependencies>` defined in the plugin config using the Eclipse Aether / Maven Project Builder API.
    2. Construct a custom classpath array: `target/classes` + resolved `fixtureDependencies` JARs.
    3. Programmatically invoke the system `javax.tools.JavaCompiler`.
* **Output:** Compiled `.class` files output to an isolated output directory.

#### **Mojo 2: The Packager & POM Synthesizer (`package-fixtures`)**
* **Phase:** `package`.
* **Action (POM Synthesis):**
    1. Instantiate a new Maven `Model` object.
    2. Set coordinates: `groupId`, `artifactId` (appended with `-test-fixtures`), and `version`.
    3. Inject a `<dependency>` on the main artifact (`${project.artifactId}`).
    4. Inject all `<fixtureDependencies>` as `<scope>compile</scope>` dependencies.
    5. Write the model to a generated POM file.
* **Action (Archiving):**
    1. Uses `jar` tool or plexus archiver to zip compiled fixtures.
    2. Output to `target/${artifactId}-test-fixtures-${version}.jar`.

#### **Mojo 3 & 4: The Deployer & Installer (`install-fixtures` & `deploy-fixtures`)**
* **Phase:** `install` and `deploy`.
* **Constraint:** Bypasses `maven-deploy-plugin` and `maven-install-plugin`. Standard deployment will try to attach fixtures as a `<classifier>`, which prevents it from having its own dependency graph.
* **Action:**
    1. Injects `ArtifactDeployer` or `ArtifactInstaller`.
    2. Constructs `DeployRequest` and `InstallRequest`.
    3. Adds the synthetic `-test-fixtures.pom` as a `.pom` artifact and `-test-fixtures.jar` as a `.jar` artifact to the request.
    4. Executes the install/deployment directly.
