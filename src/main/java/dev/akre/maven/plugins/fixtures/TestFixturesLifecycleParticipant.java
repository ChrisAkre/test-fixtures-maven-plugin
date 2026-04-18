package dev.akre.maven.plugins.fixtures;

import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

@Named("test-fixtures-participant")
@Singleton
public class TestFixturesLifecycleParticipant extends AbstractMavenLifecycleParticipant {

    private final TestFixturesWorkspaceReader workspaceReader;

    @Inject
    public TestFixturesLifecycleParticipant(TestFixturesWorkspaceReader workspaceReader) {
        this.workspaceReader = workspaceReader;
    }

    @Override
    public void afterProjectsRead(MavenSession session) throws MavenExecutionException {
        workspaceReader.init(session);

        // Inject -test-fixtures dependency into projects that use this plugin.
        // This allows the WorkspaceReader to resolve it from the isolated output directory
        // for same-module tests.
        for (MavenProject project : session.getProjects()) {
            if (hasPlugin(project)) {
                injectTestFixturesDependency(project);
            }
        }
    }

    private boolean hasPlugin(MavenProject project) {
        for (Plugin plugin : project.getBuildPlugins()) {
            if ("dev.akre".equals(plugin.getGroupId()) && "test-fixtures-maven-plugin".equals(plugin.getArtifactId())) {
                return true;
            }
        }
        return false;
    }

    private void injectTestFixturesDependency(MavenProject project) {
        String fixturesArtifactId = project.getArtifactId() + "-test-fixtures";

        // Check if already present
        for (Dependency dep : project.getDependencies()) {
            if (project.getGroupId().equals(dep.getGroupId()) && fixturesArtifactId.equals(dep.getArtifactId())) {
                return;
            }
        }

        Dependency dep = new Dependency();
        dep.setGroupId(project.getGroupId());
        dep.setArtifactId(fixturesArtifactId);
        dep.setVersion(project.getVersion());
        dep.setScope("test");

        project.getModel().addDependency(dep);
    }
}
