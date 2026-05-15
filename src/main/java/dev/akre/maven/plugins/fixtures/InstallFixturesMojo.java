package dev.akre.maven.plugins.fixtures;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.shared.transfer.artifact.install.ArtifactInstaller;
import org.apache.maven.shared.transfer.artifact.install.ArtifactInstallerException;

import java.util.List;

/**
 * Installs the packaged test fixtures and synthetic POM into the local repository.
 */
@Mojo(
    name = "install-fixtures",
    defaultPhase = LifecyclePhase.INSTALL,
    threadSafe = true
)
public class InstallFixturesMojo extends AbstractFixturesArtifactMojo {

    @Component
    private ArtifactInstaller installer;

    @Parameter(property = "install-fixtures.skip", defaultValue = "false")
    private boolean skip;

    @Override
    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("Skipping install-fixtures goal as configured.");
            return;
        }

        List<Artifact> artifacts = getFixturesArtifacts("installation");
        if (artifacts == null) {
            return;
        }

        getLog().info("Installing test fixtures to local repository.");

        try {
            installer.install(session.getProjectBuildingRequest(), artifacts);
        } catch (ArtifactInstallerException e) {
            throw new MojoExecutionException("Error installing test fixtures artifact", e);
        }
    }
}
