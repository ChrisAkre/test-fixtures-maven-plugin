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
    private java.util.Map<String, MavenProject> fixturesArtifactMap = new java.util.HashMap<>();
    private java.util.Map<String, MavenProject> regularArtifactMap = new java.util.HashMap<>();

    public void init(MavenSession session) {
        this.session = session;
        if (session != null) {
            for (MavenProject project : session.getProjects()) {
                Plugin plugin = project.getPlugin(PLUGIN_GROUP_ID + ":" + PLUGIN_ARTIFACT_ID);
                Xpp3Dom config = (plugin != null && plugin.getConfiguration() instanceof Xpp3Dom) ? (Xpp3Dom) plugin.getConfiguration() : null;

                String fixturesArtifactId = getFixturesArtifactId(project, config);
                fixturesArtifactMap.put(project.getGroupId() + ":" + fixturesArtifactId, project);
                regularArtifactMap.put(project.getGroupId() + ":" + project.getArtifactId() + ":" + project.getVersion(), project);
            }
        }
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

        MavenProject project = fixturesArtifactMap.get(artifact.getGroupId() + ":" + artifact.getArtifactId());
        if (project != null) {
            Plugin plugin = project.getPlugin(PLUGIN_GROUP_ID + ":" + PLUGIN_ARTIFACT_ID);
            Xpp3Dom config = (plugin != null && plugin.getConfiguration() instanceof Xpp3Dom) ? (Xpp3Dom) plugin.getConfiguration() : null;

            if ("jar".equals(artifact.getExtension())) {
                return getFixturesOutputDirectory(project, config);
            } else if ("pom".equals(artifact.getExtension())) {
                return new File(project.getBuild().getDirectory(), artifact.getArtifactId() + "-" + project.getVersion() + ".pom");
            }
        }

        // 2. Fallback: Handle regular reactor artifacts if they aren't being resolved for some reason
        project = regularArtifactMap.get(artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion());
        if (project != null) {
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

        return null;
    }

    private String getFixturesArtifactId(MavenProject project, Xpp3Dom config) {
        if (config != null) {
            Xpp3Dom dom = config.getChild("fixturesArtifactId");
            if (dom != null && dom.getValue() != null) {
                return dom.getValue();
            }
        }
        return project.getArtifactId() + "-test-fixtures";
    }

    private File getFixturesOutputDirectory(MavenProject project, Xpp3Dom config) {
        if (config != null) {
            Xpp3Dom dom = config.getChild("fixturesOutputDirectory");
            if (dom != null && dom.getValue() != null) {
                return new File(dom.getValue());
            }
        }
        return new File(project.getBuild().getDirectory(), "test-fixtures-classes");
    }

    @Override
    public List<String> findVersions(Artifact artifact) {
        return Collections.emptyList();
    }
}
