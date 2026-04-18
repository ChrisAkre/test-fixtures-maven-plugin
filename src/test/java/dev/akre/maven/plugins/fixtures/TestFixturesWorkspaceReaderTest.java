package dev.akre.maven.plugins.fixtures;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.apache.maven.model.Build;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class TestFixturesWorkspaceReaderTest {

    private TestFixturesWorkspaceReader reader;
    private StubMavenSession session;
    private StubMavenProject project;

    @Before
    public void setUp() {
        reader = new TestFixturesWorkspaceReader();
        session = new StubMavenSession();
        project = new StubMavenProject("my.group", "my-artifact", "1.0.0");
        session.setProjects(Arrays.asList(project));
    }

    @Test
    public void testFindArtifactWithNullSession() {
        Artifact artifact = new DefaultArtifact("my.group:my-artifact-test-fixtures:jar:1.0.0");
        assertNull(reader.findArtifact(artifact));
    }

    @Test
    public void testFindArtifactTestFixturesJar() {
        reader.init(session);
        Artifact artifact = new DefaultArtifact("my.group:my-artifact-test-fixtures:jar:1.0.0");
        File result = reader.findArtifact(artifact);
        assertNotNull(result);
        assertEquals(new File(project.getBuild().getDirectory(), "test-fixtures-classes").getAbsolutePath(), result.getAbsolutePath());
    }

    @Test
    public void testFindArtifactTestFixturesPom() {
        reader.init(session);
        Artifact artifact = new DefaultArtifact("my.group:my-artifact-test-fixtures:pom:1.0.0");
        File result = reader.findArtifact(artifact);
        assertNotNull(result);
        assertEquals(new File(project.getBuild().getDirectory(), "my-artifact-test-fixtures-1.0.0.pom").getAbsolutePath(), result.getAbsolutePath());
    }

    @Test
    public void testFindArtifactRegularArtifactJarFallback() {
        reader.init(session);
        Artifact artifact = new DefaultArtifact("my.group:my-artifact:jar:1.0.0");
        File result = reader.findArtifact(artifact);
        assertNotNull(result);
        // Jar doesn't exist, so returns output directory
        assertEquals(new File(project.getBuild().getOutputDirectory()).getAbsolutePath(), result.getAbsolutePath());
    }

    @Test
    public void testFindArtifactRegularArtifactPomFallback() {
        reader.init(session);
        Artifact artifact = new DefaultArtifact("my.group:my-artifact:pom:1.0.0");
        File result = reader.findArtifact(artifact);
        assertNotNull(result);
        assertEquals(project.getFile().getAbsolutePath(), result.getAbsolutePath());
    }

    @Test
    public void testFindArtifactNoMatch() {
        reader.init(session);
        Artifact artifact = new DefaultArtifact("other.group:other-artifact:jar:1.0.0");
        assertNull(reader.findArtifact(artifact));
    }

    // --- Stubs ---

    private static class StubMavenSession extends MavenSession {
        private List<MavenProject> projects;

        @SuppressWarnings("deprecation")
        public StubMavenSession() {
            super(null, null, null, null, null, null, null, null, null);
        }

        public void setProjects(List<MavenProject> projects) {
            this.projects = projects;
        }

        @Override
        public List<MavenProject> getProjects() {
            return projects;
        }
    }

    private static class StubMavenProject extends MavenProject {
        private final String groupId;
        private final String artifactId;
        private final String version;
        private final Build build;
        private final File file;
        private final org.apache.maven.artifact.Artifact mavenArtifact;

        public StubMavenProject(String groupId, String artifactId, String version) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.version = version;
            this.build = new Build();
            this.build.setDirectory("target");
            this.build.setOutputDirectory("target/classes");
            this.file = new File("pom.xml");
            this.mavenArtifact = new StubMavenArtifact();
        }

        @Override public String getGroupId() { return groupId; }
        @Override public String getArtifactId() { return artifactId; }
        @Override public String getVersion() { return version; }
        @Override public Build getBuild() { return build; }
        @Override public File getFile() { return file; }
        @Override public org.apache.maven.artifact.Artifact getArtifact() { return mavenArtifact; }
    }

    private static class StubMavenArtifact extends org.apache.maven.artifact.DefaultArtifact {
        public StubMavenArtifact() {
            super("g", "a", "1", "s", "t", "c", null);
        }
        @Override public File getFile() { return null; }
    }
}
