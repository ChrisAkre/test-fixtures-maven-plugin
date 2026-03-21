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
import org.apache.maven.shared.transfer.artifact.deploy.ArtifactDeployer;
import org.apache.maven.shared.transfer.artifact.deploy.ArtifactDeployerException;

import java.io.File;
import java.util.Collections;

/**
 * Deploys the packaged test fixtures and synthetic POM to the remote repository.
 */
@Mojo(
    name = "deploy-fixtures",
    defaultPhase = LifecyclePhase.DEPLOY,
    threadSafe = true
)
public class DeployFixturesMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private org.apache.maven.execution.MavenSession session;

    @Component
    private ArtifactDeployer deployer;

    @Override
    public void execute() throws MojoExecutionException {
        String jarPath = (String) project.getContextValue("fixturesJar");
        String pomPath = (String) project.getContextValue("fixturesPom");

        if (jarPath == null || pomPath == null) {
            getLog().info("Test fixtures JAR or POM not found. Skipping deployment.");
            return;
        }

        File jarFile = new File(jarPath);
        File pomFile = new File(pomPath);

        if (!jarFile.exists() || !pomFile.exists()) {
             getLog().info("Test fixtures JAR or POM files do not exist. Skipping deployment.");
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
            deployer.deploy(session.getProjectBuildingRequest(), deploymentRepository, Collections.singletonList(jarArtifact));
            deployer.deploy(session.getProjectBuildingRequest(), deploymentRepository, Collections.singletonList(pomArtifact));
        } catch (ArtifactDeployerException e) {
            throw new MojoExecutionException("Error deploying test fixtures artifact", e);
        }
    }
}
