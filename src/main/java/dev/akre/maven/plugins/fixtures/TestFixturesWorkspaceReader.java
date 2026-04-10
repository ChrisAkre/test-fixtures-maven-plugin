package dev.akre.maven.plugins.fixtures;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.WorkspaceReader;
import org.eclipse.aether.repository.WorkspaceRepository;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.File;
import java.util.Collections;
import java.util.List;

@Named("test-fixtures-reader")
@Singleton
public class TestFixturesWorkspaceReader implements WorkspaceReader {

    private final WorkspaceRepository repository = new WorkspaceRepository("test-fixtures");

    private MavenSession session;

    @Inject
    public TestFixturesWorkspaceReader(MavenSession session) {
        this.session = session;
    }

    @Override
    public WorkspaceRepository getRepository() {
        return repository;
    }

    @Override
    public File findArtifact(Artifact artifact) {
        if (artifact.getArtifactId() != null && artifact.getArtifactId().endsWith("-test-fixtures")) {
            String baseArtifactId = artifact.getArtifactId().substring(0, artifact.getArtifactId().length() - "-test-fixtures".length());
            for (MavenProject project : session.getProjects()) {
                if (project.getGroupId().equals(artifact.getGroupId()) && project.getArtifactId().equals(baseArtifactId)) {
                    if ("jar".equals(artifact.getExtension())) {
                        return new File(project.getBuild().getDirectory(), "test-fixtures-classes");
                    } else if ("pom".equals(artifact.getExtension())) {
                        return new File(project.getBuild().getDirectory(), project.getArtifactId() + "-test-fixtures-" + project.getVersion() + ".pom");
                    }
                }
            }
        }
        return null;
    }

    @Override
    public List<String> findVersions(Artifact artifact) {
        return Collections.emptyList();
    }
}
