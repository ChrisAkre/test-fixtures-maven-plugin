package dev.akre.maven.plugins.fixtures;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.model.Plugin;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
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
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Compiles the test fixtures in isolation during the process-classes phase.
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

    @Parameter(defaultValue = "${project.basedir}/src/testFixtures/java")
    private File fixturesSourceDirectory;

    @Parameter(defaultValue = "${project.basedir}/src/testFixtures/resources")
    private File fixturesResourcesDirectory;

    // Output directory for fixtures. Temporary workaround: compile directly into test-classes
    // so maven-compiler-plugin and surefire pick it up seamlessly.
    @Parameter(defaultValue = "${project.build.testOutputDirectory}")
    private File fixturesOutputDirectory;

    // Temporary holding directory for packaging later
    @Parameter(defaultValue = "${project.build.directory}/test-fixtures-classes", readonly = true)
    private File packageOutputDirectory;

    @Parameter(defaultValue = "${project.build.directory}/${project.artifactId}-test-fixtures-${project.version}.pom")
    private File fixturesPom;

    @Parameter(defaultValue = "Test Fixtures for @name@")
    private String fixtureNameTemplate;

    @Parameter(defaultValue = "Test utilities and fixtures for @description@")
    private String fixtureDescriptionTemplate;

    @Parameter
    private List<Dependency> fixtureDependencies = new ArrayList<>();

    @Inject
    private RepositorySystem repoSystem;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true, required = true)
    private RepositorySystemSession repoSession;

    @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true, required = true)
    private List<RemoteRepository> remoteRepositories;

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
            List<String> sourceFiles = findJavaFiles(fixturesSourceDirectory.toPath());
            if (sourceFiles.isEmpty()) {
                getLog().info("No Java sources found in test fixtures directory.");
            } else {
                compileSources(sourceFiles);
            }
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
            resolveFixtureDependencies().stream()
                .distinct()
                .filter(Predicate.not(new HashSet<>(project.getTestClasspathElements())::contains))
                .forEach(project.getTestClasspathElements()::add);

            getLog().debug("Added fixture dependencies to Maven test model.");
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to inject fixtures into test classpath", e);
        }

        // Generate synthetic POM for the fixtures
        generateSyntheticPom();
    }

    private void compileSources(List<String> sourceFiles) throws MojoExecutionException {
        String classpath = buildCompilerClasspath();

        // Prepare the compiler arguments
        List<String> compilerArgs = new ArrayList<>();
        compilerArgs.add("-d");
        compilerArgs.add(fixturesOutputDirectory.getAbsolutePath());
        compilerArgs.add("-cp");
        compilerArgs.add(classpath);
        if (project.getBuildPlugins() != null) {
            for (Plugin plugin : project.getBuildPlugins()) {
                if ("org.apache.maven.plugins".equals(plugin.getGroupId()) && "maven-compiler-plugin".equals(plugin.getArtifactId())) {
                    Object config = plugin.getConfiguration();
                    if (config instanceof Xpp3Dom) {
                        Xpp3Dom dom = (Xpp3Dom) config;
                        Xpp3Dom releaseDom = dom.getChild("release");
                        if (releaseDom != null && releaseDom.getValue() != null && !releaseDom.getValue().trim().isEmpty()) {
                            compilerArgs.add("--release");
                            compilerArgs.add(releaseDom.getValue().trim());
                        } else {
                            Xpp3Dom sourceDom = dom.getChild("source");
                            if (sourceDom != null && sourceDom.getValue() != null && !sourceDom.getValue().trim().isEmpty()) {
                                compilerArgs.add("-source");
                                compilerArgs.add(sourceDom.getValue().trim());
                            }
                            Xpp3Dom targetDom = dom.getChild("target");
                            if (targetDom != null && targetDom.getValue() != null && !targetDom.getValue().trim().isEmpty()) {
                                compilerArgs.add("-target");
                                compilerArgs.add(targetDom.getValue().trim());
                            }
                        }
                    }
                    break;
                }
            }
        }

        compilerArgs.addAll(sourceFiles);

        // Invoke the system Java compiler
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new MojoExecutionException("Cannot find System Java Compiler. Ensure you are running a JDK, not a JRE.");
        }

        int result = compiler.run(null, null, null, compilerArgs.toArray(new String[0]));
        if (result != 0) {
            throw new MojoExecutionException("Test fixtures compilation failed.");
        }

        // Copy compiled classes to package output directory for packaging
        copyDirectory(fixturesOutputDirectory, packageOutputDirectory);
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
                    throw new UncheckedIOException(e);
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
                    throw new UncheckedIOException(e);
                }
            });
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to copy compiled fixtures for packaging", e);
        }
    }

    private String buildCompilerClasspath() throws MojoExecutionException {
        List<String> classpathElements = new ArrayList<>();

        try {
            classpathElements.add(project.getBuild().getOutputDirectory());
            classpathElements.addAll(project.getTestClasspathElements());
            
            List<String> resolvedDependencies = resolveFixtureDependencies();
            classpathElements.addAll(resolvedDependencies);
            
        } catch (Exception e) {
            throw new MojoExecutionException("Error building classpath", e);
        }

        return classpathElements.stream()
                .distinct()
                .collect(Collectors.joining(File.pathSeparator));
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

    private List<String> findJavaFiles(Path sourceDir) throws MojoExecutionException {
        try (Stream<Path> paths = Files.walk(sourceDir)) {
            return paths.filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".java"))
                        .map(Path::toString)
                        .collect(Collectors.toList());
        } catch (IOException e) {
            throw new MojoExecutionException("Error scanning for Java files in " + sourceDir, e);
        }
    }

    private void generateSyntheticPom() throws MojoExecutionException {
        Model model = new Model();
        model.setModelVersion("4.0.0");
        model.setGroupId(project.getGroupId());
        model.setArtifactId(project.getArtifactId() + "-test-fixtures");
        model.setVersion(project.getVersion());
        model.setPackaging("jar");

        String name = project.getName() != null ? project.getName() : project.getArtifactId();
        model.setName(fixtureNameTemplate.replace("@name@", name));

        String description = project.getDescription() != null ? project.getDescription() : "";
        model.setDescription(fixtureDescriptionTemplate.replace("@description@", description));

        if (project.getModel().getUrl() != null) {
            model.setUrl(project.getModel().getUrl());
        }
        if (project.getModel().getLicenses() != null) {
            model.setLicenses(new ArrayList<>(project.getModel().getLicenses()));
        }
        if (project.getModel().getDevelopers() != null) {
            model.setDevelopers(new ArrayList<>(project.getModel().getDevelopers()));
        }
        if (project.getModel().getScm() != null) {
            model.setScm(project.getModel().getScm().clone());
        }

        // The test fixtures depend on the main project classes
        Dependency mainProjectDep = new Dependency();
        mainProjectDep.setGroupId(project.getGroupId());
        mainProjectDep.setArtifactId(project.getArtifactId());
        mainProjectDep.setVersion(project.getVersion());
        model.addDependency(mainProjectDep);

        // Add explicit fixture dependencies
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
