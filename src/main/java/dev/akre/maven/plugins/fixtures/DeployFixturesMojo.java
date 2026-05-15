package dev.akre.maven.plugins.fixtures;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.shared.transfer.artifact.deploy.ArtifactDeployer;
import org.apache.maven.shared.transfer.artifact.deploy.ArtifactDeployerException;

import java.util.List;

/**
 * Deploys the packaged test fixtures and synthetic POM to the remote repository.
 */
@Mojo(
    name = "deploy-fixtures",
    defaultPhase = LifecyclePhase.DEPLOY,
    threadSafe = true
)
public class DeployFixturesMojo extends AbstractFixturesArtifactMojo {

    @Component
    private ArtifactDeployer deployer;

    @Parameter(property = "deploy-fixtures.skip", defaultValue = "false")
    private boolean skip;

    @Override
    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("Skipping deploy-fixtures goal as configured.");
            return;
        }

        List<Artifact> artifacts = getFixturesArtifacts("deployment");
        if (artifacts == null) {
            return;
        }

        org.apache.maven.artifact.repository.ArtifactRepository deploymentRepository = project.getDistributionManagementArtifactRepository();
        
        if (project.getArtifact().isSnapshot() && project.getDistributionManagementArtifactRepository() != null && project.getDistributionManagementArtifactRepository().isUniqueVersion()) {
            // Handled automatically by Maven 
        }

        if (deploymentRepository == null) {
            getLog().info("No deployment repository configured. Skipping test fixtures deployment.");
            return;
        }

        getLog().info("Deploying test fixtures to remote repository " + deploymentRepository.getId() + " (" + deploymentRepository.getUrl() + ")");

        try {
            deployer.deploy(session.getProjectBuildingRequest(), deploymentRepository, artifacts);
        } catch (ArtifactDeployerException e) {
            throw new MojoExecutionException("Error deploying test fixtures artifact", e);
        }
    }
}
