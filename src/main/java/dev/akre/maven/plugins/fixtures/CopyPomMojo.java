package dev.akre.maven.plugins.fixtures;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Copies the synthetic POM to a target location.
 */
@Mojo(
    name = "copy-pom",
    defaultPhase = LifecyclePhase.PROCESS_CLASSES,
    threadSafe = true
)
public class CopyPomMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${project.artifactId}-test-fixtures")
    private String fixturesArtifactId;

    @Parameter(defaultValue = "${project.build.directory}/${project.artifactId}-test-fixtures-${project.version}.pom")
    private File fixturesPom;

    @Parameter(property = "copyTarget", required = true)
    private File copyTarget;

    @Parameter(property = "copy-pom.skip", defaultValue = "false")
    private boolean skip;

    @Override
    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("Skipping copy-pom goal as configured.");
            return;
        }

        // If fixturesArtifactId is customized, update the file paths if they still point to the default
        String defaultPrefix = project.getArtifactId() + "-test-fixtures-" + project.getVersion();
        String customPrefix = fixturesArtifactId + "-" + project.getVersion();

        if (fixturesPom.getName().startsWith(defaultPrefix) && !fixturesArtifactId.equals(project.getArtifactId() + "-test-fixtures")) {
            fixturesPom = new File(fixturesPom.getParentFile(), fixturesPom.getName().replace(defaultPrefix, customPrefix));
        }

        if (!fixturesPom.exists()) {
            getLog().info("Test fixtures POM not found. Skipping copy.");
            return;
        }

        getLog().info("Copying test fixtures POM to " + copyTarget.getAbsolutePath());

        try {
            if (copyTarget.getParentFile() != null && !copyTarget.getParentFile().exists()) {
                if (!copyTarget.getParentFile().mkdirs()) {
                    throw new MojoExecutionException("Failed to create parent directories for " + copyTarget.getAbsolutePath());
                }
            }
            Files.copy(fixturesPom.toPath(), copyTarget.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new MojoExecutionException("Error copying test fixtures POM", e);
        }
    }
}
