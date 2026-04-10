package dev.akre.maven.plugins.fixtures;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.WorkspaceReader;
import org.eclipse.aether.repository.WorkspaceRepository;

import javax.inject.Named;
import javax.inject.Singleton;
import java.io.File;
import java.util.Collections;
import java.util.List;

@Named("ide")
@Singleton
public class TestFixturesWorkspaceReader implements WorkspaceReader {

    private final WorkspaceRepository repository = new WorkspaceRepository("test-fixtures");

    private MavenSession session;

    public void init(MavenSession session) {
        this.session = session;
    }

    @Override
    public WorkspaceRepository getRepository() {
        return repository;
    }

    @Override
    public File findArtifact(Artifact artifact) {
        if (session == null) {
            return null;
        }

        // 1. Handle our custom test-fixtures artifacts
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

        // 2. Fallback: Handle regular reactor artifacts if they aren't being resolved for some reason
        // This helps in some Invoker environments where the default reactor reader might be partially disabled or shadowed.
        for (MavenProject project : session.getProjects()) {
            if (project.getGroupId().equals(artifact.getGroupId()) && project.getArtifactId().equals(artifact.getArtifactId()) && project.getVersion().equals(artifact.getVersion())) {
                 if ("pom".equals(artifact.getExtension())) {
                     return project.getFile();
                 } else if ("jar".equals(artifact.getExtension())) {
                     // If it's the main artifact, return the classes directory if the jar doesn't exist yet
                     File jar = project.getArtifact().getFile();
                     if (jar != null && jar.exists()) {
                         return jar;
                     }
                     return new File(project.getBuild().getOutputDirectory());
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
