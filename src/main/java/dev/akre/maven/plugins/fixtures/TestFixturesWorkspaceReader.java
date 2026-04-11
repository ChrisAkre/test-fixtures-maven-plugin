package dev.akre.maven.plugins.fixtures;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.interpolation.MapBasedValueSource;
import org.codehaus.plexus.interpolation.StringSearchInterpolator;
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
import java.util.Optional;
import java.util.function.Supplier;

@Named("ide")
@Singleton
public class TestFixturesWorkspaceReader implements WorkspaceReader {

    private static final String FIXTURES_INIT_TEMPLATE = "${groupId}:${artifactId}-test-fixtures:${extension}";
    private static final String FIXTURES_LOOKUP_TEMPLATE = "${groupId}:${artifactId}:${extension}";
    private static final String REACTOR_TEMPLATE = "${groupId}:${artifactId}:${version}:${extension}";

    private final WorkspaceRepository repository = new WorkspaceRepository("test-fixtures");

    private volatile MavenSession session;
    private volatile Map<String, Supplier<File>> artifactMap = Collections.emptyMap();

    public void init(MavenSession session) {
        this.session = session;
        if (session != null && session.getProjects() != null) {
            Map<String, Supplier<File>> newArtifactMap = new HashMap<>();
            for (MavenProject project : session.getProjects()) {
                // 1. Pre-calculate test-fixtures paths (GA:E)
                format(FIXTURES_INIT_TEMPLATE, project, "jar").ifPresent(k ->
                    newArtifactMap.put(k, () -> new File(project.getBuild().getDirectory(), "test-fixtures-classes")));
                format(FIXTURES_INIT_TEMPLATE, project, "pom").ifPresent(k ->
                    newArtifactMap.put(k, () -> new File(project.getBuild().getDirectory(), project.getArtifactId() + "-test-fixtures-" + project.getVersion() + ".pom")));

                // 2. Pre-calculate regular reactor artifacts (GAV:E)
                format(REACTOR_TEMPLATE, project, "pom").ifPresent(k ->
                    newArtifactMap.put(k, () -> project.getFile()));
                format(REACTOR_TEMPLATE, project, "jar").ifPresent(k ->
                    newArtifactMap.put(k, () -> {
                        // If it's the main artifact, return the classes directory if the jar doesn't exist yet
                        File jar = project.getArtifact().getFile();
                        if (jar != null && jar.exists()) {
                            return jar;
                        }
                        return new File(project.getBuild().getOutputDirectory());
                    }));
            }
            this.artifactMap = Collections.unmodifiableMap(newArtifactMap);
        } else {
            this.artifactMap = Collections.emptyMap();
        }
    }

    private Optional<String> format(String template, MavenProject project, String extension) {
        Map<String, String> values = new HashMap<>();
        values.put("groupId", project.getGroupId());
        values.put("artifactId", project.getArtifactId());
        values.put("version", project.getVersion());
        values.put("extension", extension);
        return interpolate(template, values);
    }

    private Optional<String> format(String template, Artifact artifact) {
        Map<String, String> values = new HashMap<>();
        values.put("groupId", artifact.getGroupId());
        values.put("artifactId", artifact.getArtifactId());
        values.put("version", artifact.getVersion());
        values.put("extension", artifact.getExtension());
        return interpolate(template, values);
    }

    private Optional<String> interpolate(String template, Map<String, String> values) {
        StringSearchInterpolator interpolator = new StringSearchInterpolator();
        interpolator.addValueSource(new MapBasedValueSource(values));
        try {
            return Optional.ofNullable(interpolator.interpolate(template));
        } catch (Exception e) {
            return Optional.empty();
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

        return Optional.ofNullable(artifact)
                .flatMap(a -> format(FIXTURES_LOOKUP_TEMPLATE, a).map(artifactMap::get)
                        .or(() -> format(REACTOR_TEMPLATE, a).map(artifactMap::get)))
                .map(Supplier::get)
                .orElse(null);
    }

    @Override
    public List<String> findVersions(Artifact artifact) {
        return Collections.emptyList();
    }
}
