package dev.akre.maven.plugins.fixtures;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.util.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import static org.twdata.maven.mojoexecutor.MojoExecutor.*;

/**
 * Synthesizes a Central-compliant POM and uses mojo-executor to generate artifacts
 * and sign them, then stages them to piggyback on the central-publishing-maven-plugin.
 */
@Mojo(name = "central-bundle-fixtures", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public class CentralBundleFixturesMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    @Component
    private BuildPluginManager pluginManager;

    @Component
    private MavenProjectHelper projectHelper;

    @Parameter(defaultValue = "Test Fixtures for @name@")
    private String fixtureNameTemplate;

    @Parameter(defaultValue = "Test utilities and fixtures for @description@")
    private String fixtureDescriptionTemplate;

    @Parameter(defaultValue = "${session.executionRootDirectory}/target/central-staging")
    private File centralStagingDirectory;

    @Parameter(defaultValue = "${project.build.directory}/test-fixtures-classes")
    private File fixturesOutputDirectory;

    @Parameter(defaultValue = "${project.build.directory}")
    private File buildDirectory;

    @Override
    public void execute() throws MojoExecutionException {
        // Check if gpg signing was skipped
        boolean skipGpg = Boolean.parseBoolean(session.getUserProperties().getProperty("gpg.skip", "false"));

        // 1. Check for passphrases
        String gpgPassphrase = session.getUserProperties().getProperty("gpg.passphrase");
        if (gpgPassphrase == null) {
            gpgPassphrase = System.getProperty("gpg.passphrase");
        }
        if (gpgPassphrase == null) {
            gpgPassphrase = System.getenv("MAVEN_GPG_PASSPHRASE");
        }
        if (!skipGpg && gpgPassphrase == null) {
            throw new MojoExecutionException("gpg.passphrase property or MAVEN_GPG_PASSPHRASE env var is required for central-bundle-fixtures.");
        }

        // Paths for the artifacts
        String artifactPrefix = project.getArtifactId() + "-test-fixtures-" + project.getVersion();
        File jarFile = new File(buildDirectory, artifactPrefix + ".jar");
        File pomFile = new File(buildDirectory, artifactPrefix + ".pom");
        File sourcesJar = new File(buildDirectory, artifactPrefix + "-sources.jar");
        File javadocJar = new File(buildDirectory, artifactPrefix + "-javadoc.jar");

        String sourcePluginVersion = getPluginVersion("org.apache.maven.plugins", "maven-source-plugin", "3.3.1");
        String jarPluginVersion = getPluginVersion("org.apache.maven.plugins", "maven-jar-plugin", "3.4.1");
        String gpgPluginVersion = getPluginVersion("org.apache.maven.plugins", "maven-gpg-plugin", "3.2.4");

        // 2. Generate -sources.jar using maven-source-plugin via mojo-executor
        executeMojo(
            plugin(
                groupId("org.apache.maven.plugins"),
                artifactId("maven-source-plugin"),
                version(sourcePluginVersion)
            ),
            goal("jar-no-fork"),
            configuration(
                element(name("outputDirectory"), buildDirectory.getAbsolutePath()),
                element(name("finalName"), artifactPrefix),
                element(name("attach"), "false")
            ),
            executionEnvironment(project, session, pluginManager)
        );

        // maven-source-plugin adds the '-sources' classifier, but we need to rename it properly or just move it.
        // It creates finalName-sources.jar. If we set finalName to artifactPrefix, it will be artifactPrefix-sources.jar.

        // 3. Generate -javadoc.jar using maven-jar-plugin via mojo-executor
        File dummyJavadocDir = new File(buildDirectory, "dummy-javadoc");
        dummyJavadocDir.mkdirs();
        try {
            Files.writeString(new File(dummyJavadocDir, "README.md").toPath(), "Dummy javadoc");
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to write dummy javadoc", e);
        }

        executeMojo(
            plugin(
                groupId("org.apache.maven.plugins"),
                artifactId("maven-jar-plugin"),
                version(jarPluginVersion)
            ),
            goal("jar"),
            configuration(
                element(name("classesDirectory"), dummyJavadocDir.getAbsolutePath()),
                element(name("outputDirectory"), buildDirectory.getAbsolutePath()),
                element(name("finalName"), artifactPrefix),
                element(name("classifier"), "javadoc")
            ),
            executionEnvironment(project, session, pluginManager)
        );

        // 4. Sign the 4 files.
        // The instructions say: "execute the maven-gpg-plugin:sign goal via mojo-executor".
        // maven-gpg-plugin:sign signs the project's main artifact, POM, and all attached artifacts.
        // We do NOT want to attach the fixtures directly as main artifacts in the same way, OR maybe we do temporarily?
        // Let's create a synthetic MavenProject!

        MavenProject syntheticProject = new MavenProject();
        syntheticProject.setGroupId(project.getGroupId());
        syntheticProject.setArtifactId(project.getArtifactId() + "-test-fixtures");
        syntheticProject.setVersion(project.getVersion());
        syntheticProject.setPackaging("jar");
        syntheticProject.setFile(pomFile);

        org.apache.maven.artifact.Artifact syntheticArtifact = new org.apache.maven.artifact.DefaultArtifact(
                project.getGroupId(),
                project.getArtifactId() + "-test-fixtures",
                project.getVersion(),
                "compile",
                "jar",
                "",
                new org.apache.maven.artifact.handler.DefaultArtifactHandler("jar")
        );
        syntheticArtifact.setFile(jarFile);
        syntheticProject.setArtifact(syntheticArtifact);

        org.apache.maven.artifact.Artifact sourcesArtifact = new org.apache.maven.artifact.DefaultArtifact(
                project.getGroupId(),
                project.getArtifactId() + "-test-fixtures",
                project.getVersion(),
                "compile",
                "jar",
                "sources",
                new org.apache.maven.artifact.handler.DefaultArtifactHandler("jar")
        );
        sourcesArtifact.setFile(sourcesJar);
        syntheticProject.addAttachedArtifact(sourcesArtifact);

        org.apache.maven.artifact.Artifact javadocArtifact = new org.apache.maven.artifact.DefaultArtifact(
                project.getGroupId(),
                project.getArtifactId() + "-test-fixtures",
                project.getVersion(),
                "compile",
                "jar",
                "javadoc",
                new org.apache.maven.artifact.handler.DefaultArtifactHandler("jar")
        );
        javadocArtifact.setFile(javadocJar);
        syntheticProject.addAttachedArtifact(javadocArtifact);

        MavenSession syntheticSession = session.clone();

        if (!skipGpg) {
            // Use reflection or just executionEnvironment with syntheticProject
            executeMojo(
                plugin(
                    groupId("org.apache.maven.plugins"),
                    artifactId("maven-gpg-plugin"),
                    version(gpgPluginVersion)
                ),
                goal("sign"),
                configuration(
                    element(name("passphrase"), gpgPassphrase),
                    element(name("useAgent"), "false")
                ),
                executionEnvironment(syntheticProject, syntheticSession, pluginManager)
            );
        }

        // 5. Phase D: The Piggyback Staging
        // Destination path: ${centralStagingDirectory}/groupId(slashes)/artifactId/version/
        File destDir = new File(centralStagingDirectory,
            project.getGroupId().replace('.', '/') + "/" +
            project.getArtifactId() + "-test-fixtures/" +
            project.getVersion());
        destDir.mkdirs();

        List<File> filesToCopy = new ArrayList<>();
        filesToCopy.add(jarFile);
        filesToCopy.add(pomFile);
        filesToCopy.add(sourcesJar);
        filesToCopy.add(javadocJar);

        if (!skipGpg) {
            filesToCopy.add(new File(jarFile.getAbsolutePath() + ".asc"));
            filesToCopy.add(new File(pomFile.getAbsolutePath() + ".asc"));
            filesToCopy.add(new File(sourcesJar.getAbsolutePath() + ".asc"));
            filesToCopy.add(new File(javadocJar.getAbsolutePath() + ".asc"));
        }

        for (File f : filesToCopy) {
            if (!f.exists()) {
                throw new MojoExecutionException("Expected file to be copied does not exist: " + f.getAbsolutePath());
            }
            try {
                Files.copy(f.toPath(), new File(destDir, f.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to copy file to staging directory: " + f.getAbsolutePath(), e);
            }
        }

        getLog().info("Successfully staged test-fixtures to " + destDir.getAbsolutePath());
    }

    private String getPluginVersion(String groupId, String artifactId, String defaultVersion) {
        if (project.getBuildPlugins() != null) {
            for (org.apache.maven.model.Plugin plugin : project.getBuildPlugins()) {
                if (groupId.equals(plugin.getGroupId()) && artifactId.equals(plugin.getArtifactId()) && plugin.getVersion() != null) {
                    return plugin.getVersion();
                }
            }
        }
        if (project.getPluginManagement() != null && project.getPluginManagement().getPlugins() != null) {
            for (org.apache.maven.model.Plugin plugin : project.getPluginManagement().getPlugins()) {
                if (groupId.equals(plugin.getGroupId()) && artifactId.equals(plugin.getArtifactId()) && plugin.getVersion() != null) {
                    return plugin.getVersion();
                }
            }
        }
        return defaultVersion;
    }
}
