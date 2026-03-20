package dev.akre.maven.plugins.fixtures;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.transfer.artifact.install.ArtifactInstaller;
import org.apache.maven.shared.transfer.artifact.install.ArtifactInstallerException;

import java.io.File;
import java.util.Collections;

/**
 * Installs the packaged test fixtures and synthetic POM into the local repository.
 */
@Mojo(
    name = "install-fixtures",
    defaultPhase = LifecyclePhase.TEST,
    threadSafe = true
)
public class InstallFixturesMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private org.apache.maven.execution.MavenSession session;

    @Component
    private ArtifactInstaller installer;

    @Override
    public void execute() throws MojoExecutionException {
        String jarPath = (String) project.getContextValue("fixturesJar");
        String pomPath = (String) project.getContextValue("fixturesPom");

        if (jarPath == null || pomPath == null) {
            getLog().info("Test fixtures JAR or POM not found. Skipping installation.");
            return;
        }

        File jarFile = new File(jarPath);
        File pomFile = new File(pomPath);

        if (!jarFile.exists() || !pomFile.exists()) {
             getLog().info("Test fixtures JAR or POM files do not exist. Skipping installation.");
             return;
        }

        getLog().info("Installing test fixtures to local repository.");

        Artifact jarArtifact = new DefaultArtifact(
                project.getGroupId(),
                project.getArtifactId() + "-test-fixtures",
                project.getVersion(),
                "compile",
                "jar",
                "",
                new DefaultArtifactHandler("jar")
        );
        jarArtifact.setFile(jarFile);
        
        Artifact pomArtifact = new DefaultArtifact(
                project.getGroupId(),
                project.getArtifactId() + "-test-fixtures",
                project.getVersion(),
                "compile",
                "pom",
                "",
                new DefaultArtifactHandler("pom")
        );
        pomArtifact.setFile(pomFile);


        try {
            installer.install(session.getProjectBuildingRequest(), Collections.singletonList(jarArtifact));
            installer.install(session.getProjectBuildingRequest(), Collections.singletonList(pomArtifact));
        } catch (ArtifactInstallerException e) {
            throw new MojoExecutionException("Error installing test fixtures artifact", e);
        }
    }
}
