package dev.akre.maven.plugins.fixtures;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CopyPomMojo}.
 */
@ExtendWith(MockitoExtension.class)
class CopyPomMojoTest {

    @InjectMocks
    private CopyPomMojo mojo;

    @Mock
    private MavenProject project;

    @TempDir
    Path tempDir;

    private void setField(String fieldName, Object value) throws Exception {
        Field field = CopyPomMojo.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(mojo, value);
    }

    @Test
    void testExecuteSkip() throws Exception {
        setField("skip", true);

        mojo.execute();

        // Should return early; if it didn't, it would likely fail on other uninitialized fields
        verifyNoInteractions(project);
    }

    @Test
    void testExecutePomNotFound() throws Exception {
        setField("skip", false);
        setField("fixturesPom", new File(tempDir.toFile(), "non-existent.pom"));
        when(project.getArtifactId()).thenReturn("my-project");
        when(project.getVersion()).thenReturn("1.0.0");
        setField("fixturesArtifactId", "my-project-test-fixtures");

        mojo.execute();
        // Should just log and return
    }

    @Test
    void testExecuteSuccess() throws Exception {
        Path sourcePom = tempDir.resolve("source.pom");
        Files.writeString(sourcePom, "test-pom-content");
        Path targetPom = tempDir.resolve("target/copied.pom");

        when(project.getArtifactId()).thenReturn("my-project");
        when(project.getVersion()).thenReturn("1.0.0");

        setField("skip", false);
        setField("fixturesArtifactId", "my-project-test-fixtures");
        setField("fixturesPom", sourcePom.toFile());
        setField("copyTarget", targetPom.toFile());

        mojo.execute();

        assertTrue(Files.exists(targetPom));
        assertEquals("test-pom-content", Files.readString(targetPom));
    }

    @Test
    void testExecuteWithCustomArtifactId() throws Exception {
        Path sourceDir = tempDir.resolve("source-dir");
        Files.createDirectories(sourceDir);
        Path sourcePom = sourceDir.resolve("custom-fixtures-1.0.0.pom");
        Files.writeString(sourcePom, "custom-content");
        Path targetPom = tempDir.resolve("target.pom");

        when(project.getArtifactId()).thenReturn("my-project");
        when(project.getVersion()).thenReturn("1.0.0");

        setField("skip", false);
        setField("fixturesArtifactId", "custom-fixtures");
        // Initial path points to the default one
        setField("fixturesPom", new File(sourceDir.toFile(), "my-project-test-fixtures-1.0.0.pom"));
        setField("copyTarget", targetPom.toFile());

        mojo.execute();

        assertTrue(Files.exists(targetPom));
        assertEquals("custom-content", Files.readString(targetPom));
    }

    @Test
    void testExecuteCreateParentDirs() throws Exception {
        Path sourcePom = tempDir.resolve("source.pom");
        Files.writeString(sourcePom, "content");
        // Nested target directory that doesn't exist
        Path targetPom = tempDir.resolve("a/b/c/target.pom");

        when(project.getArtifactId()).thenReturn("my-project");
        when(project.getVersion()).thenReturn("1.0.0");

        setField("skip", false);
        setField("fixturesArtifactId", "my-project-test-fixtures");
        setField("fixturesPom", sourcePom.toFile());
        setField("copyTarget", targetPom.toFile());

        mojo.execute();

        assertTrue(Files.exists(targetPom));
        assertTrue(Files.exists(targetPom.getParent()));
    }

    @Test
    void testExecuteError() throws Exception {
        Path sourcePom = tempDir.resolve("source.pom");
        Files.writeString(sourcePom, "content");

        // Create a directory where the file should be, causing an IOException during copy
        Path targetDir = tempDir.resolve("target-dir");
        Files.createDirectories(targetDir);

        when(project.getArtifactId()).thenReturn("my-project");
        when(project.getVersion()).thenReturn("1.0.0");

        setField("skip", false);
        setField("fixturesArtifactId", "my-project-test-fixtures");
        setField("fixturesPom", sourcePom.toFile());
        setField("copyTarget", targetDir.toFile());

        assertThrows(MojoExecutionException.class, () -> mojo.execute());
    }
}
