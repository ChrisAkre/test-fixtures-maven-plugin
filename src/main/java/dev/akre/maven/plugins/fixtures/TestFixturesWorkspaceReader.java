package dev.akre.maven.plugins.fixtures;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
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

    private static final String PLUGIN_GROUP_ID = "dev.akre";
    private static final String PLUGIN_ARTIFACT_ID = "test-fixtures-maven-plugin";

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

        for (MavenProject project : session.getProjects()) {
            if (project.getGroupId().equals(artifact.getGroupId())) {
                String fixturesArtifactId = getFixturesArtifactId(project);
                if (artifact.getArtifactId().equals(fixturesArtifactId)) {
                    if ("jar".equals(artifact.getExtension())) {
                        return new File(project.getBuild().getDirectory(), "test-fixtures-classes");
                    } else if ("pom".equals(artifact.getExtension())) {
                        return new File(project.getBuild().getDirectory(), fixturesArtifactId + "-" + project.getVersion() + ".pom");
                    }
                }
            }
        }

        // 2. Fallback: Handle regular reactor artifacts if they aren't being resolved for some reason
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

    private String getFixturesArtifactId(MavenProject project) {
        Plugin plugin = project.getPlugin(PLUGIN_GROUP_ID + ":" + PLUGIN_ARTIFACT_ID);
        if (plugin != null) {
            Object config = plugin.getConfiguration();
            if (config instanceof Xpp3Dom) {
                Xpp3Dom dom = (Xpp3Dom) config;
                Xpp3Dom fixturesArtifactIdDom = dom.getChild("fixturesArtifactId");
                if (fixturesArtifactIdDom != null && fixturesArtifactIdDom.getValue() != null) {
                    return fixturesArtifactIdDom.getValue();
                }
            }
        }
        return project.getArtifactId() + "-test-fixtures";
    }

    @Override
    public List<String> findVersions(Artifact artifact) {
        return Collections.emptyList();
    }
}
