# Maven Test Fixtures Plugin

The **Maven Test Fixtures Plugin** orchestrates the creation, packaging, and publishing of Java test fixtures in a Maven
build. It acts as a shadow build system within the standard Maven lifecycle, managing an isolated compilation phase,
constructing a synthetic POM, and bypassing the standard deployer to push multiple distinct artifact graphs to
the repository.

This plugin ensures a robust first-class experience for consumers while avoiding the need to migrate your entire build
system, achieving seamless interoperability without circular dependencies.

## Usage

To use this plugin and define dependencies that apply strictly to your test fixtures, configure your `pom.xml` as
follows:

```xml

<plugin>
    <groupId>dev.akre</groupId>
    <artifactId>test-fixtures-maven-plugin</artifactId>
    <version>1.0.4-SNAPSHOT</version>
    <extensions>true</extensions>
    <configuration>
        <fixturesArtifactId>my-custom-fixtures</fixturesArtifactId>
        <publishTests>true</publishTests>
        <fixtureDependencies>
            <!-- By default, the enclosing project will be a dependency, add other test fixture dependencies here -->
            <dependency>
                <groupId>org.mockito</groupId>
                <artifactId>mockito-core</artifactId>
                <version>5.11.0</version>
            </dependency>
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
2. Generate a synthetic POM with a direct dependency on your project's main JAR, preserving the specific dependencies
   needed for test fixtures as `compile` scope.
3. Optionally copy the generated POM to a custom location (e.g., for IDE support or manual inspection).
4. Package the classes into an isolated test fixtures JAR.
5. Optionally install and deploy these artifacts to local and remote repositories.

---

## Configuration Options

The following table lists all available configuration parameters, the goal(s) they apply to, and their default values.

| Parameter                      | Goal(s)                                                           | Property                       | Default                                                                                    | Description                                                      |
|--------------------------------|-------------------------------------------------------------------|--------------------------------|--------------------------------------------------------------------------------------------|------------------------------------------------------------------|
| `fixturesArtifactId`           | All                                                               | -                              | `${project.artifactId}-test-fixtures`                                                      | The artifactId for the generated test fixtures artifact.         |
| `fixturesSourceDirectory`      | `compile-fixtures`                                                | -                              | `${project.basedir}/src/testFixtures/java`                                                 | The directory containing test fixture Java sources.              |
| `fixturesResourcesDirectory`   | `compile-fixtures`                                                | -                              | `${project.basedir}/src/testFixtures/resources`                                            | The directory containing test fixture resources.                 |
| `fixturesOutputDirectory`      | `compile-fixtures`, `package-fixtures`, `central-bundle-fixtures` | -                              | `${project.build.testOutputDirectory}` (compile), `target/test-fixtures-classes` (package) | The directory where compiled fixtures are stored.                |
| `fixtureDependencies`          | `compile-fixtures`, `package-fixtures`                            | -                              | `[]`                                                                                       | List of explicit dependencies required by the test fixtures.     |
| `fixturesPom`                  | `compile-fixtures`, `package-fixtures`, `copy-pom`                | -                              | `target/${fixturesArtifactId}-${version}.pom`                                              | The location of the generated synthetic POM.                     |
| `fixturesJar`                  | `package-fixtures`                                                | -                              | `target/${fixturesArtifactId}-${version}.jar`                                              | The location of the generated test fixtures JAR.                 |
| `fixtureNameTemplate`          | `compile-fixtures`, `central-bundle-fixtures`                     | -                              | `Test Fixtures for @name@`                                                                 | Template for the synthetic POM's `<name>`.                       |
| `fixtureDescriptionTemplate`   | `compile-fixtures`, `central-bundle-fixtures`                     | -                              | `Test utilities and fixtures for @description@`                                            | Template for the synthetic POM's `<description>`.                |
| `copyTarget`                   | `copy-pom`                                                        | `copyTarget`                   | -                                                                                          | **Required.** The destination path for the copied synthetic POM. |
| `centralStagingDirectory`      | `central-bundle-fixtures`                                         | -                              | `target/central-staging`                                                                   | Directory where artifacts are staged for Central publishing.     |
| `buildDirectory`               | `central-bundle-fixtures`                                         | -                              | `${project.build.directory}`                                                               | The project build directory.                                     |
| `compile-fixtures.skip`        | `compile-fixtures`                                                | `compile-fixtures.skip`        | `false`                                                                                    | Skip the compilation goal.                                       |
| `package-fixtures.skip`        | `package-fixtures`                                                | `package-fixtures.skip`        | `false`                                                                                    | Skip the packaging goal.                                         |
| `install-fixtures.skip`        | `install-fixtures`                                                | `install-fixtures.skip`        | `false`                                                                                    | Skip the installation goal.                                      |
| `deploy-fixtures.skip`         | `deploy-fixtures`                                                 | `deploy-fixtures.skip`         | `false`                                                                                    | Skip the deployment goal.                                        |
| `central-bundle-fixtures.skip` | `central-bundle-fixtures`                                         | `central-bundle-fixtures.skip` | `false`                                                                                    | Skip the central staging goal.                                   |
| `copy-pom.skip`                | `copy-pom`                                                        | `copy-pom.skip`                | `false`                                                                                    | Skip the POM copying goal.                                       |

### **Example: Full Configuration**

```xml

<configuration>
    <fixturesArtifactId>custom-fixtures</fixturesArtifactId>
    <fixturesSourceDirectory>${project.basedir}/src/fixtures/java</fixturesSourceDirectory>
    <copyTarget>${project.basedir}/test-fixtures/pom.xml</copyTarget>
    <fixtureDependencies>
        <dependency>
            <groupId>org.assertj</groupId>
            <artifactId>assertj-core</artifactId>
            <version>3.24.2</version>
        </dependency>
    </fixtureDependencies>
    <compile-fixtures.skip>${skipFixtures}</compile-fixtures.skip>
</configuration>
```

---

## Technical Specifications

### **1. Plugin Overview & Lifecycle Mapping**

The plugin consists of multiple Mojos bound to specific phases of the standard `default` lifecycle.

* **`process-classes` (`compile-fixtures`):** Compile the test fixtures using a custom classpath.
* **`process-classes` (`copy-pom`):** Copies the generated synthetic POM to a target location.
* **`generate-test-sources`:** Inject the compiled fixtures into the standard Maven test classpath.
* **`package` (`package-fixtures`):** Generates the synthetic POMs and archives the class directories into separate
  JARs.
* **`install` (`install-fixtures`):** Installs the packaged test fixtures and synthetic POM into the local repository.
* **`deploy` (`deploy-fixtures`):** Uses Eclipse Aether to publish the artifacts and their synthetic POMs.
* **`deploy` (`central-bundle-fixtures`):** Stages artifacts for Central Repository publishing (GPG signing and
  staging).

### **2. Mojo Architecture & Implementation Details**

#### **Mojo 1: The Isolated Compiler (`compile-fixtures`)**

* **Phase:** `process-classes` (Executes immediately after `maven-compiler-plugin:compile`).
* **Input:** `src/test-fixtures/java`.
* **Action:**
    1. Resolve the `<fixtureDependencies>` defined in the plugin config using the Eclipse Aether / Maven Project Builder
       API.
    2. Construct a custom classpath array: `target/classes` + resolved `fixtureDependencies` JARs.
    3. Programmatically invoke the system `javax.tools.JavaCompiler`.
* **Output:** Compiled `.class` files output to `target/test-fixtures-classes`.

#### **Mojo 2: The POM Copier (`copy-pom`)**

* **Phase:** `process-classes`.
* **Action:** Copies the generated synthetic POM to the path specified by `<copyTarget>`. This is useful for exposing
  the POM coordinates to other tools or IDEs.

#### **Mojo 3: The Packager & POM Synthesizer (`package-fixtures`)**

* **Phase:** `package`.
* **Action (POM Synthesis):**
    1. Instantiate a new Maven `Model` object.
    2. Set coordinates: `groupId`, `fixturesArtifactId`, and `version`.
    3. Inject a `<dependency>` on the main artifact (`${project.artifactId}`).
    4. Inject all `<fixtureDependencies>` as `<scope>compile</scope>` dependencies.
    5. Write the model to `target/${fixturesArtifactId}-${version}.pom`.
* **Action (Archiving):**
    1. Archives compiled fixtures from `target/test-fixtures-classes`.
    2. Output to `target/${fixturesArtifactId}-${version}.jar`.

#### **Mojo 4 & 5: The Deployer & Installer (`install-fixtures` & `deploy-fixtures`)**

* **Phase:** `install` and `deploy`.
* **Action:**
    1. Injects `ArtifactDeployer` or `ArtifactInstaller`.
    2. Adds the synthetic `.pom` and `.jar` artifacts to the request.
    3. Executes the install/deployment directly, bypassing standard plugins to maintain a unique dependency graph for
       fixtures.

#### **Mojo 6: The Staging Aggregator (`central-bundle-fixtures`)**

* **Phase:** `deploy`.
* **Action:** Manages GPG signing and stages all artifacts (JAR, POM, Sources, Javadoc) into a directory structure
  compatible with the Central Repository bundle requirements.
