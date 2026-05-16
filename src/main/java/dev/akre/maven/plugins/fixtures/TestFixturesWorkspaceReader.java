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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Named("ide")
@Singleton
public class TestFixturesWorkspaceReader implements WorkspaceReader {

    private static final String PLUGIN_GROUP_ID = "dev.akre";
    private static final String PLUGIN_ARTIFACT_ID = "test-fixtures-maven-plugin";

    private final WorkspaceRepository repository = new WorkspaceRepository("test-fixtures");

    private MavenSession session;
    private final Map<String, Supplier<File>> artifactMap = new HashMap<>();

    public void init(MavenSession session) {
        this.session = session;
        artifactMap.clear();
        if (session != null) {
            for (MavenProject project : session.getProjects()) {
                String fixturesArtifactId = getFixturesArtifactId(project);

                // 1. Regular artifacts
                String reactorBaseKey = project.getGroupId() + ":" + project.getArtifactId() + ":";
                artifactMap.put(reactorBaseKey + "pom", project::getFile);
                artifactMap.put(reactorBaseKey + "jar", () -> {
                    File jar = project.getArtifact().getFile();
                    if (jar != null && jar.exists()) {
                        return jar;
                    }
                    return new File(project.getBuild().getOutputDirectory());
                });

                // 2. Test-fixtures artifacts
                String fixturesBaseKey = project.getGroupId() + ":" + fixturesArtifactId + ":";
                artifactMap.put(fixturesBaseKey + "jar", () -> new File(project.getBuild().getDirectory(), "test-fixtures-classes"));
                artifactMap.put(fixturesBaseKey + "pom", () -> new File(project.getBuild().getDirectory(), fixturesArtifactId + "-" + project.getVersion() + ".pom"));
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

        return artifactMap.getOrDefault(artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getExtension(), () -> null).get();
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
