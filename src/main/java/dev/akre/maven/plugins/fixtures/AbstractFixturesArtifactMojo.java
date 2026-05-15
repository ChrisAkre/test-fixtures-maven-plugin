package dev.akre.maven.plugins.fixtures;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * Base class for Mojos that operate on test fixtures artifacts.
 */
public abstract class AbstractFixturesArtifactMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    protected org.apache.maven.execution.MavenSession session;

    @Parameter(defaultValue = "${project.artifactId}-test-fixtures")
    protected String fixturesArtifactId;

    /**
     * Resolves the fixtures JAR and POM from the project context and returns them as a list of artifacts.
     *
     * @param action the action being performed (e.g., "installation", "deployment")
     * @return a list containing the JAR and POM artifacts, or null if they are not found or do not exist
     */
    protected List<Artifact> getFixturesArtifacts(String action) {
        String jarPath = (String) project.getContextValue("fixturesJar");
        String pomPath = (String) project.getContextValue("fixturesPom");

        if (jarPath == null || pomPath == null) {
            getLog().info("Test fixtures JAR or POM not found. Skipping " + action + ".");
            return null;
        }

        File jarFile = new File(jarPath);
        File pomFile = new File(pomPath);

        if (!jarFile.exists() || !pomFile.exists()) {
            getLog().info("Test fixtures JAR or POM files do not exist. Skipping " + action + ".");
            return null;
        }

        Artifact jarArtifact = createArtifact(jarFile, "jar");
        Artifact pomArtifact = createArtifact(pomFile, "pom");

        return Arrays.asList(jarArtifact, pomArtifact);
    }

    private Artifact createArtifact(File file, String type) {
        Artifact artifact = new DefaultArtifact(
                project.getGroupId(),
                fixturesArtifactId,
                project.getVersion(),
                "compile",
                type,
                "",
                new DefaultArtifactHandler(type)
        );
        artifact.setFile(file);
        return artifact;
    }
}
