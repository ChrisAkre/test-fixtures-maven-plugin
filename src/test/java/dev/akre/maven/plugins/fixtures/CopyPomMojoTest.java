package dev.akre.maven.plugins.fixtures;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class CopyPomMojoTest {

    private CopyPomMojo mojo;
    private MavenProject project;

    @TempDir
    Path tempDir;

    @BeforeEach
    public void setUp() throws Exception {
        mojo = new CopyPomMojo();
        project = mock(MavenProject.class);
        setField(mojo, "project", project);

        Path baseDir = tempDir.resolve("project").toAbsolutePath();
        Files.createDirectories(baseDir);
        when(project.getBasedir()).thenReturn(baseDir.toFile());
        when(project.getArtifactId()).thenReturn("my-artifact");
        when(project.getVersion()).thenReturn("1.0.0");
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    public void testExecuteVulnerablePath() throws Exception {
        Path baseDir = project.getBasedir().toPath();
        Path fixturesPomPath = baseDir.resolve("target/my-artifact-test-fixtures-1.0.0.pom");
        Files.createDirectories(fixturesPomPath.getParent());
        Files.writeString(fixturesPomPath, "<project/>");

        setField(mojo, "fixturesPom", fixturesPomPath.toFile());
        setField(mojo, "fixturesArtifactId", "my-artifact-test-fixtures");

        // Malicious path outside baseDir
        Path maliciousPath = tempDir.resolve("malicious.pom").toAbsolutePath();
        setField(mojo, "copyTarget", maliciousPath.toFile());

        // Now, this should throw MojoExecutionException
        MojoExecutionException exception = assertThrows(MojoExecutionException.class, () -> {
            mojo.execute();
        });

        assertTrue(exception.getMessage().contains("outside the project base directory"));
        assertFalse(Files.exists(maliciousPath), "File should NOT have been copied to malicious path");
    }

    @Test
    public void testExecuteValidPath() throws Exception {
        Path baseDir = project.getBasedir().toPath();
        Path fixturesPomPath = baseDir.resolve("target/my-artifact-test-fixtures-1.0.0.pom");
        Files.createDirectories(fixturesPomPath.getParent());
        Files.writeString(fixturesPomPath, "<project/>");

        setField(mojo, "fixturesPom", fixturesPomPath.toFile());
        setField(mojo, "fixturesArtifactId", "my-artifact-test-fixtures");

        // Valid path inside baseDir
        Path validPath = baseDir.resolve("test-fixtures/pom.xml");
        setField(mojo, "copyTarget", validPath.toFile());

        mojo.execute();

        assertTrue(Files.exists(validPath), "File should have been copied to valid path");
    }
}
