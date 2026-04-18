package dev.akre.maven.plugins.fixtures;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.apache.maven.model.Build;
import org.eclipse.aether.artifact.Artifact;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link TestFixturesWorkspaceReader}.
 * <p>
 * NOTE: While integration tests (using the maven-invoker-plugin) are generally preferred for Maven
 * plugins to ensure alignment with Maven's runtime behavior, the {@code findArtifact} method
 * contains complex branching logic (handling both JAR and POM extensions, and providing fallback
 * paths for regular reactor artifacts). These unit tests provide comprehensive coverage for
 * these specific logical paths and edge cases in isolation.
 */
@ExtendWith(MockitoExtension.class)
public class TestFixturesWorkspaceReaderTest {

    @InjectMocks
    private TestFixturesWorkspaceReader reader;

    @Mock
    private MavenSession session;

    @Mock
    private MavenProject project;

    @Mock
    private Build build;

    @Mock
    private Artifact artifact;

    @Mock
    private org.apache.maven.artifact.Artifact mavenArtifact;

    @BeforeEach
    public void setUp() {
        // Most tests will need the session and project set up
        lenient().when(session.getProjects()).thenReturn(Collections.singletonList(project));
        lenient().when(project.getGroupId()).thenReturn("my.group");
        lenient().when(project.getArtifactId()).thenReturn("my-artifact");
        lenient().when(project.getVersion()).thenReturn("1.0.0");
        lenient().when(project.getBuild()).thenReturn(build);
        lenient().when(build.getDirectory()).thenReturn("target");
        lenient().when(build.getOutputDirectory()).thenReturn("target/classes");
    }

    @Test
    public void testFindArtifactWithNullSession() {
        TestFixturesWorkspaceReader readerWithNullSession = new TestFixturesWorkspaceReader();
        when(artifact.getArtifactId()).thenReturn("my-artifact-test-fixtures");
        assertNull(readerWithNullSession.findArtifact(artifact));
    }

    @Test
    public void testFindArtifactTestFixturesJar() {
        reader.init(session);
        when(artifact.getGroupId()).thenReturn("my.group");
        when(artifact.getArtifactId()).thenReturn("my-artifact-test-fixtures");
        when(artifact.getExtension()).thenReturn("jar");

        File result = reader.findArtifact(artifact);
        assertNotNull(result);
        assertEquals(new File("target", "test-fixtures-classes").getAbsolutePath(), result.getAbsolutePath());
    }

    @Test
    public void testFindArtifactTestFixturesPom() {
        reader.init(session);
        when(artifact.getGroupId()).thenReturn("my.group");
        when(artifact.getArtifactId()).thenReturn("my-artifact-test-fixtures");
        when(artifact.getExtension()).thenReturn("pom");

        File result = reader.findArtifact(artifact);
        assertNotNull(result);
        assertEquals(new File("target", "my-artifact-test-fixtures-1.0.0.pom").getAbsolutePath(), result.getAbsolutePath());
    }

    @Test
    public void testFindArtifactRegularArtifactJarFallback() {
        reader.init(session);
        when(artifact.getGroupId()).thenReturn("my.group");
        when(artifact.getArtifactId()).thenReturn("my-artifact");
        when(artifact.getVersion()).thenReturn("1.0.0");
        when(artifact.getExtension()).thenReturn("jar");
        when(project.getArtifact()).thenReturn(mavenArtifact);
        when(mavenArtifact.getFile()).thenReturn(null);

        File result = reader.findArtifact(artifact);
        assertNotNull(result);
        assertEquals(new File("target/classes").getAbsolutePath(), result.getAbsolutePath());
    }

    @Test
    public void testFindArtifactRegularArtifactPomFallback() {
        reader.init(session);
        when(artifact.getGroupId()).thenReturn("my.group");
        when(artifact.getArtifactId()).thenReturn("my-artifact");
        when(artifact.getVersion()).thenReturn("1.0.0");
        when(artifact.getExtension()).thenReturn("pom");
        when(project.getFile()).thenReturn(new File("pom.xml"));

        File result = reader.findArtifact(artifact);
        assertNotNull(result);
        assertEquals(new File("pom.xml").getAbsolutePath(), result.getAbsolutePath());
    }

    @Test
    public void testFindArtifactNoMatch() {
        reader.init(session);
        when(artifact.getGroupId()).thenReturn("other.group");
        when(artifact.getArtifactId()).thenReturn("other-artifact");
        // For fallback logic
        when(artifact.getVersion()).thenReturn("1.0.0");

        assertNull(reader.findArtifact(artifact));
    }
}
