package dev.akre.maven.plugins.fixtures;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.filter.DependencyFilterUtils;

import javax.inject.Inject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Compiles the test fixtures in isolation during the process-classes phase.
 * It leverages the configured maven-compiler-plugin to ensure all project settings
 * (like annotation processors, compiler flags, etc.) are respected.
 */
@Mojo(
    name = "compile-fixtures",
    defaultPhase = LifecyclePhase.PROCESS_CLASSES,
    requiresDependencyResolution = ResolutionScope.TEST,
    threadSafe = true
)
public class CompileFixturesMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    @Parameter(defaultValue = "${project.basedir}/src/testFixtures/java")
    private File fixturesSourceDirectory;

    @Parameter(defaultValue = "${project.basedir}/src/testFixtures/resources")
    private File fixturesResourcesDirectory;

    // Output directory for fixtures. Aligned with PackageFixturesMojo and WorkspaceReader.
    @Parameter(defaultValue = "${project.build.directory}/test-fixtures-classes")
    private File fixturesOutputDirectory;

    @Parameter(defaultValue = "${project.build.directory}/${project.artifactId}-test-fixtures-${project.version}.pom")
    private File fixturesPom;

    @Parameter
    private List<Dependency> fixtureDependencies = new ArrayList<>();

    @Inject
    private RepositorySystem repoSystem;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true, required = true)
    private RepositorySystemSession repoSession;

    @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true, required = true)
    private List<RemoteRepository> remoteRepositories;

    @Inject
    private BuildPluginManager pluginManager;

    @Inject
    private ArtifactHandlerManager artifactHandlerManager;

    @Override
    public void execute() throws MojoExecutionException {
        boolean hasJavaSources = fixturesSourceDirectory.exists() && fixturesSourceDirectory.isDirectory();
        boolean hasResources = fixturesResourcesDirectory.exists() && fixturesResourcesDirectory.isDirectory();

        if (!hasJavaSources && !hasResources) {
            getLog().info("No test fixtures sources or resources found. Skipping.");
            return;
        }

        getLog().info("Compiling and processing test fixtures to " + fixturesOutputDirectory.getAbsolutePath());

        if (!fixturesOutputDirectory.exists() && !fixturesOutputDirectory.mkdirs()) {
            throw new MojoExecutionException("Failed to create test output directory: " + fixturesOutputDirectory);
        }

        // Process Resources
        if (hasResources) {
            processResources();
        }

        // Compile Java Sources
        if (hasJavaSources) {
            compileSources();
        }

        // Inject explicit dependencies into the standard Maven test classpath for downstream plugins/tests
        try {
            // Add the fixtures output directory itself to the test classpath
            if (!project.getTestClasspathElements().contains(fixturesOutputDirectory.getAbsolutePath())) {
                project.getTestClasspathElements().add(fixturesOutputDirectory.getAbsolutePath());
            }

            if (fixtureDependencies != null) {
                for (Dependency dep : fixtureDependencies) {
                    Dependency testDep = dep.clone();
                    testDep.setScope("test");
                    project.getModel().addDependency(testDep);
                }
            }
            
            List<String> resolvedDeps = resolveFixtureDependencyPaths();
            for (String depPath : resolvedDeps) {
                if (!project.getTestClasspathElements().contains(depPath)) {
                    project.getTestClasspathElements().add(depPath);
                }
            }

            getLog().debug("Added fixture dependencies to Maven test model.");
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to inject fixtures into test classpath", e);
        }

        // Generate synthetic POM for the fixtures
        generateSyntheticPom();
    }

    private void compileSources() throws MojoExecutionException {
        Plugin compilerPlugin = project.getPlugin("org.apache.maven.plugins:maven-compiler-plugin");
        if (compilerPlugin == null) {
            throw new MojoExecutionException("maven-compiler-plugin not found in project. Cannot compile fixtures.");
        }

        // 1. Load the plugin and mojo descriptor
        PluginDescriptor pluginDescriptor;
        try {
            pluginDescriptor = pluginManager.loadPlugin(compilerPlugin, remoteRepositories, repoSession);
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to load maven-compiler-plugin", e);
        }

        MojoDescriptor mojoDescriptor = pluginDescriptor.getMojo("compile");
        if (mojoDescriptor == null) {
            throw new MojoExecutionException("Could not find goal 'compile' in maven-compiler-plugin");
        }

        // 2. Prepare the compilation environment by swapping project state
        List<String> originalSourceRoots = new ArrayList<>(project.getCompileSourceRoots());
        String originalOutputDirectory = project.getBuild().getOutputDirectory();
        Set<Artifact> originalArtifacts = project.getArtifacts() != null ?
            new LinkedHashSet<>(project.getArtifacts()) : new LinkedHashSet<>();

        try {
            // 3. Swap state to point to fixtures
            project.getCompileSourceRoots().clear();
            project.addCompileSourceRoot(fixturesSourceDirectory.getAbsolutePath());
            project.getBuild().setOutputDirectory(fixturesOutputDirectory.getAbsolutePath());

            // Synthesize the classpath required for fixtures:
            // - The main project classes
            // - All existing project artifacts (including test dependencies), forced to compile scope
            // - Explicitly defined fixture dependencies
            Set<Artifact> fixtureCompilationArtifacts = new LinkedHashSet<>();

            // Add the main project's classes as a dependency
            Artifact mainArtifact = new DefaultArtifact(
                project.getGroupId(), project.getArtifactId(),
                VersionRange.createFromVersion(project.getVersion()),
                "compile", "jar", null, artifactHandlerManager.getArtifactHandler("jar")
            );
            mainArtifact.setFile(new File(originalOutputDirectory));
            mainArtifact.setResolved(true);
            fixtureCompilationArtifacts.add(mainArtifact);

            // Add all original artifacts, forced to compile scope so the 'compile' goal uses them
            for (Artifact art : originalArtifacts) {
                Artifact copy = new DefaultArtifact(
                    art.getGroupId(), art.getArtifactId(), art.getVersionRange(),
                    "compile", art.getType(), art.getClassifier(), art.getArtifactHandler()
                );
                copy.setFile(art.getFile());
                copy.setResolved(art.isResolved());
                fixtureCompilationArtifacts.add(copy);
            }

            // Resolve and add custom fixture dependencies
            List<org.eclipse.aether.artifact.Artifact> extraArtifacts = resolveFixtureArtifacts();
            for (org.eclipse.aether.artifact.Artifact aetherArt : extraArtifacts) {
                if (aetherArt.getFile() != null) {
                    Artifact mavenArt = new DefaultArtifact(
                        aetherArt.getGroupId(), aetherArt.getArtifactId(),
                        VersionRange.createFromVersion(aetherArt.getVersion()),
                        "compile", aetherArt.getExtension(), aetherArt.getClassifier(),
                        artifactHandlerManager.getArtifactHandler(aetherArt.getExtension())
                    );
                    mavenArt.setFile(aetherArt.getFile());
                    mavenArt.setResolved(true);
                    fixtureCompilationArtifacts.add(mavenArt);
                }
            }
            project.setArtifacts(fixtureCompilationArtifacts);

            // 4. Prepare the configuration DOM for the compiler plugin
            Xpp3Dom configuration = new Xpp3Dom("configuration");
            Xpp3Dom pomConfig = (Xpp3Dom) compilerPlugin.getConfiguration();
            if (pomConfig != null) {
                for (Xpp3Dom child : pomConfig.getChildren()) {
                    String name = child.getName();
                    // Do not override source roots or output directories that we manage via project state
                    if (!"compileSourceRoots".equals(name) && !"outputDirectory".equals(name) && !"classpathElements".equals(name)) {
                        configuration.addChild(new Xpp3Dom(child));
                    }
                }
            }

            // Force outputDirectory and ensure skip is false
            setConfigurationValue(configuration, "outputDirectory", fixturesOutputDirectory.getAbsolutePath());
            setConfigurationValue(configuration, "skip", "false");

            // 5. Execute the compiler goal
            MojoExecution execution = new MojoExecution(mojoDescriptor, configuration);
            pluginManager.executeMojo(session, execution);

        } catch (Exception e) {
            throw new MojoExecutionException("Failed to execute maven-compiler-plugin via BuildPluginManager", e);
        } finally {
            // 6. Restore original project state
            project.getCompileSourceRoots().clear();
            for (String root : originalSourceRoots) {
                project.addCompileSourceRoot(root);
            }
            project.getBuild().setOutputDirectory(originalOutputDirectory);
            project.setArtifacts(originalArtifacts);
        }

    }

    private void setConfigurationValue(Xpp3Dom config, String name, String value) {
        Xpp3Dom child = config.getChild(name);
        if (child == null) {
            child = new Xpp3Dom(name);
            config.addChild(child);
        }
        child.setValue(value);
    }

    private void processResources() throws MojoExecutionException {
        try (Stream<Path> paths = Files.walk(fixturesResourcesDirectory.toPath())) {
            paths.filter(Files::isRegularFile).forEach(source -> {
                Path destOutput = fixturesOutputDirectory.toPath().resolve(fixturesResourcesDirectory.toPath().relativize(source));
                try {
                    Files.createDirectories(destOutput.getParent());
                    Files.copy(source, destOutput, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
             throw new MojoExecutionException("Failed to process test fixture resources", e);
        }
    }

    private List<String> resolveFixtureDependencyPaths() throws MojoExecutionException {
        return resolveFixtureArtifacts().stream()
                .filter(a -> a.getFile() != null)
                .map(a -> a.getFile().getAbsolutePath())
                .collect(Collectors.toList());
    }

    private List<org.eclipse.aether.artifact.Artifact> resolveFixtureArtifacts() throws MojoExecutionException {
        List<org.eclipse.aether.artifact.Artifact> resolvedArtifacts = new ArrayList<>();
        
        if (fixtureDependencies == null || fixtureDependencies.isEmpty()) {
            return resolvedArtifacts;
        }

        for (Dependency dep : fixtureDependencies) {
            org.eclipse.aether.artifact.Artifact artifact = new org.eclipse.aether.artifact.DefaultArtifact(
                    dep.getGroupId(),
                    dep.getArtifactId(),
                    dep.getClassifier(),
                    dep.getType() != null ? dep.getType() : "jar",
                    dep.getVersion()
            );

            CollectRequest collectRequest = new CollectRequest();
            collectRequest.setRoot(new org.eclipse.aether.graph.Dependency(artifact, JavaScopes.COMPILE));
            collectRequest.setRepositories(remoteRepositories);

            DependencyFilter classpathFilter = DependencyFilterUtils.classpathFilter(JavaScopes.COMPILE);
            DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, classpathFilter);

            try {
                DependencyResult dependencyResult = repoSystem.resolveDependencies(repoSession, dependencyRequest);
                for (org.eclipse.aether.resolution.ArtifactResult ar : dependencyResult.getArtifactResults()) {
                    if (ar.getArtifact() != null) {
                        resolvedArtifacts.add(ar.getArtifact());
                    }
                }
            } catch (DependencyResolutionException e) {
                getLog().warn("Failed to resolve fixture dependency: " + artifact);
            }
        }
        return resolvedArtifacts;
    }

    private void generateSyntheticPom() throws MojoExecutionException {
        Model model = new Model();
        model.setModelVersion("4.0.0");
        model.setGroupId(project.getGroupId());
        model.setArtifactId(project.getArtifactId() + "-test-fixtures");
        model.setVersion(project.getVersion());
        model.setPackaging("jar");
        model.setDescription("Test fixtures for " + project.getArtifactId());

        Dependency mainProjectDep = new Dependency();
        mainProjectDep.setGroupId(project.getGroupId());
        mainProjectDep.setArtifactId(project.getArtifactId());
        mainProjectDep.setVersion(project.getVersion());
        model.addDependency(mainProjectDep);

        if (fixtureDependencies != null) {
            for (Dependency dep : fixtureDependencies) {
                model.addDependency(dep);
            }
        }

        try (FileOutputStream fos = new FileOutputStream(fixturesPom)) {
            new MavenXpp3Writer().write(fos, model);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to write synthetic POM", e);
        }
    }
}
