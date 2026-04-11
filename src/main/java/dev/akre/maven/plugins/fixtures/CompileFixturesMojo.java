package dev.akre.maven.plugins.fixtures;

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
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
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
import java.util.List;
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

    @Parameter(defaultValue = "${project.build.testOutputDirectory}")
    private File fixturesOutputDirectory;

    @Parameter(defaultValue = "${project.build.directory}/test-fixtures-classes", readonly = true)
    private File packageOutputDirectory;

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
        
        if (!packageOutputDirectory.exists() && !packageOutputDirectory.mkdirs()) {
            throw new MojoExecutionException("Failed to create package output directory: " + packageOutputDirectory);
        }

        // Process Resources
        if (hasResources) {
            processResources();
        }

        // Compile Java Sources
        if (hasJavaSources) {
            compileSources();
        }

        // Inject explicit dependencies into the standard Maven test classpath
        try {
            if (fixtureDependencies != null) {
                for (Dependency dep : fixtureDependencies) {
                    Dependency testDep = dep.clone();
                    testDep.setScope("test");
                    project.getModel().addDependency(testDep);
                }
            }
            
            // Also explicitly resolve and add them to test classpath elements just in case
            List<String> resolvedDeps = resolveFixtureDependencies();
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

        // 2. Prepare the compilation environment
        List<String> originalSourceRoots = new ArrayList<>(project.getCompileSourceRoots());
        String originalOutputDirectory = project.getBuild().getOutputDirectory();

        List<String> fixtureDepPaths = resolveFixtureDependencies();

        try {
            // 3. Temporarily swap project state to point to fixtures
            project.getCompileSourceRoots().clear();
            project.addCompileSourceRoot(fixturesSourceDirectory.getAbsolutePath());
            project.getBuild().setOutputDirectory(fixturesOutputDirectory.getAbsolutePath());

            // 4. Prepare the configuration DOM for the compiler plugin
            Xpp3Dom pomConfig = (Xpp3Dom) compilerPlugin.getConfiguration();
            Xpp3Dom configuration = new Xpp3Dom("configuration");
            if (pomConfig != null) {
                // Clone existing configuration except for the parts we're overriding
                for (Xpp3Dom child : pomConfig.getChildren()) {
                    if (!"compileSourceRoots".equals(child.getName()) &&
                        !"outputDirectory".equals(child.getName()) &&
                        !"classpathElements".equals(child.getName())) {
                        configuration.addChild(new Xpp3Dom(child));
                    }
                }
            }

            // Override outputDirectory
            setConfigurationValue(configuration, "outputDirectory", fixturesOutputDirectory.getAbsolutePath());

            // Pass the specialized classpath via configuration
            Xpp3Dom classpathElementsDom = new Xpp3Dom("classpathElements");
            List<String> compilationClasspath = new ArrayList<>();
            compilationClasspath.add(originalOutputDirectory);
            compilationClasspath.addAll(project.getTestClasspathElements());
            compilationClasspath.addAll(fixtureDepPaths);
            compilationClasspath.stream().distinct().forEach(element -> {
                Xpp3Dom el = new Xpp3Dom("element");
                el.setValue(element);
                classpathElementsDom.addChild(el);
            });
            configuration.addChild(classpathElementsDom);

            // Set source roots in configuration as well
            Xpp3Dom sourceRootsDom = new Xpp3Dom("compileSourceRoots");
            Xpp3Dom root = new Xpp3Dom("compileSourceRoot");
            root.setValue(fixturesSourceDirectory.getAbsolutePath());
            sourceRootsDom.addChild(root);
            configuration.addChild(sourceRootsDom);

            // 5. Execute the compiler:compile goal
            MojoExecution execution = new MojoExecution(mojoDescriptor, configuration);
            pluginManager.executeMojo(session, execution);

        } catch (Exception e) {
            throw new MojoExecutionException("Failed to execute maven-compiler-plugin:compile", e);
        } finally {
            // 6. Restore original project state
            project.getCompileSourceRoots().clear();
            for (String root : originalSourceRoots) {
                project.addCompileSourceRoot(root);
            }
            project.getBuild().setOutputDirectory(originalOutputDirectory);
        }

        // 7. Copy compiled classes to package output directory for packaging
        copyDirectory(fixturesOutputDirectory, packageOutputDirectory);
    }

    private void setConfigurationValue(Xpp3Dom config, String name, String value) {
        Xpp3Dom child = config.getChild(name);
        if (child == null) {
            child = new Xpp3Dom(name);
            config.addChild(child);
        }
        child.setValue(value);
    }

    private void executeMojo(Plugin plugin, String goal, Xpp3Dom configuration) throws MojoExecutionException {
        // This method is now partially replaced by inline logic in compileSources to handle
        // descriptor loading correctly as per Maven 3 API.
    }

    private void processResources() throws MojoExecutionException {
        try (Stream<Path> paths = Files.walk(fixturesResourcesDirectory.toPath())) {
            paths.filter(Files::isRegularFile).forEach(source -> {
                Path destOutput = fixturesOutputDirectory.toPath().resolve(fixturesResourcesDirectory.toPath().relativize(source));
                Path destPackage = packageOutputDirectory.toPath().resolve(fixturesResourcesDirectory.toPath().relativize(source));
                try {
                    Files.createDirectories(destOutput.getParent());
                    Files.copy(source, destOutput, StandardCopyOption.REPLACE_EXISTING);
                    
                    Files.createDirectories(destPackage.getParent());
                    Files.copy(source, destPackage, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
             throw new MojoExecutionException("Failed to process test fixture resources", e);
        }
    }

    private void copyDirectory(File sourceLocation, File targetLocation) throws MojoExecutionException {
        try (Stream<Path> paths = Files.walk(sourceLocation.toPath())) {
            paths.forEach(source -> {
                Path destination = targetLocation.toPath().resolve(sourceLocation.toPath().relativize(source));
                try {
                    if (Files.isDirectory(source)) {
                        if (!Files.exists(destination)) {
                            Files.createDirectory(destination);
                        }
                    } else {
                        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to copy compiled fixtures for packaging", e);
        }
    }

    private List<String> resolveFixtureDependencies() throws MojoExecutionException {
        List<String> resolvedPaths = new ArrayList<>();
        
        if (fixtureDependencies == null || fixtureDependencies.isEmpty()) {
            return resolvedPaths;
        }

        for (Dependency dep : fixtureDependencies) {
            Artifact artifact = new DefaultArtifact(
                    dep.getGroupId(),
                    dep.getArtifactId(),
                    dep.getClassifier(),
                    dep.getType() != null ? dep.getType() : "jar",
                    dep.getVersion()
            );

            CollectRequest collectRequest = new CollectRequest();
            collectRequest.setRoot(new org.eclipse.aether.graph.Dependency(artifact, JavaScopes.COMPILE));
            collectRequest.setRepositories(remoteRepositories);

            DependencyFilter classpathFlter = DependencyFilterUtils.classpathFilter(JavaScopes.COMPILE);
            DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, classpathFlter);

            try {
                DependencyResult dependencyResult = repoSystem.resolveDependencies(repoSession, dependencyRequest);
                for (org.eclipse.aether.graph.DependencyNode node : dependencyResult.getRoot().getChildren()) {
                     if (node.getArtifact() != null && node.getArtifact().getFile() != null) {
                         resolvedPaths.add(node.getArtifact().getFile().getAbsolutePath());
                     }
                }
                if (dependencyResult.getRoot() != null && dependencyResult.getRoot().getArtifact() != null && dependencyResult.getRoot().getArtifact().getFile() != null) {
                    resolvedPaths.add(dependencyResult.getRoot().getArtifact().getFile().getAbsolutePath());
                }
            } catch (DependencyResolutionException e) {
                getLog().warn("Failed to resolve fixture dependency: " + artifact + ". It might not have a file available.");
            }
        }
        return resolvedPaths;
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
