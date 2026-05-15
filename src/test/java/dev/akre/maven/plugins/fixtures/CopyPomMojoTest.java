package dev.akre.maven.plugins.fixtures;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class CopyPomMojoTest {

    private CopyPomMojo mojo;

    @Mock
    private MavenProject project;

    @Mock
    private Log log;

    @TempDir
    Path tempDir;

    private AutoCloseable closeable;

    @BeforeEach
    public void setUp() throws Exception {
        closeable = MockitoAnnotations.openMocks(this);
        mojo = new CopyPomMojo();
        mojo.setLog(log);

        setField(mojo, "project", project);
        setField(mojo, "fixturesArtifactId", "my-artifact-test-fixtures");
        setField(mojo, "skip", false);

        when(project.getArtifactId()).thenReturn("my-artifact");
        when(project.getVersion()).thenReturn("1.0.0");
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (closeable != null) {
            closeable.close();
        }
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    public void testExecuteSkip() throws Exception {
        setField(mojo, "skip", true);
        mojo.execute();
        verify(log).info("Skipping copy-pom goal as configured.");
        verifyNoInteractions(project);
    }

    @Test
    public void testExecutePomNotFound() throws Exception {
        File nonExistentPom = tempDir.resolve("non-existent.pom").toFile();
        setField(mojo, "fixturesPom", nonExistentPom);
        setField(mojo, "copyTarget", tempDir.resolve("target.pom").toFile());

        mojo.execute();

        verify(log).info("Test fixtures POM not found. Skipping copy.");
    }

    @Test
    public void testExecuteSuccess() throws Exception {
        Path fixturesPomPath = tempDir.resolve("my-artifact-test-fixtures-1.0.0.pom");
        Files.writeString(fixturesPomPath, "pom content");
        File fixturesPom = fixturesPomPath.toFile();

        Path copyTargetPath = tempDir.resolve("copied-target.pom");
        File copyTarget = copyTargetPath.toFile();

        setField(mojo, "fixturesPom", fixturesPom);
        setField(mojo, "copyTarget", copyTarget);

        mojo.execute();

        assertTrue(Files.exists(copyTargetPath));
        assertEquals("pom content", Files.readString(copyTargetPath));
        verify(log).info("Copying test fixtures POM to " + copyTarget.getAbsolutePath());
    }

    @Test
    public void testExecuteSuccessWithMkdirs() throws Exception {
        Path fixturesPomPath = tempDir.resolve("my-artifact-test-fixtures-1.0.0.pom");
        Files.writeString(fixturesPomPath, "pom content");
        File fixturesPom = fixturesPomPath.toFile();

        Path copyTargetPath = tempDir.resolve("new-subdir/copied-target.pom");
        File copyTarget = copyTargetPath.toFile();

        setField(mojo, "fixturesPom", fixturesPom);
        setField(mojo, "copyTarget", copyTarget);

        mojo.execute();

        assertTrue(Files.exists(copyTargetPath));
        assertEquals("pom content", Files.readString(copyTargetPath));
    }

    @Test
    public void testExecuteCustomArtifactId() throws Exception {
        setField(mojo, "fixturesArtifactId", "custom-fixtures");

        Path fixturesPomPath = tempDir.resolve("custom-fixtures-1.0.0.pom");
        Files.writeString(fixturesPomPath, "custom content");
        File fixturesPomPlaceholder = tempDir.resolve("my-artifact-test-fixtures-1.0.0.pom").toFile();

        Path copyTargetPath = tempDir.resolve("copied-target.pom");
        File copyTarget = copyTargetPath.toFile();

        setField(mojo, "fixturesPom", fixturesPomPlaceholder);
        setField(mojo, "copyTarget", copyTarget);

        mojo.execute();

        assertTrue(Files.exists(copyTargetPath));
        assertEquals("custom content", Files.readString(copyTargetPath));
    }

    @Test
    public void testExecuteCopyFailure() throws Exception {
        Path fixturesPomPath = tempDir.resolve("my-artifact-test-fixtures-1.0.0.pom");
        Files.writeString(fixturesPomPath, "pom content");
        File fixturesPom = fixturesPomPath.toFile();

        // Make copyTarget a directory so Files.copy fails
        Path copyTargetPath = tempDir.resolve("target-dir");
        Files.createDirectory(copyTargetPath);
        Files.writeString(copyTargetPath.resolve("existing-file"), "something");

        File copyTarget = copyTargetPath.toFile();

        setField(mojo, "fixturesPom", fixturesPom);
        setField(mojo, "copyTarget", copyTarget);

        assertThrows(MojoExecutionException.class, () -> mojo.execute());
    }

    @Test
    public void testExecuteMkdirsFailure() throws Exception {
        Path fixturesPomPath = tempDir.resolve("my-artifact-test-fixtures-1.0.0.pom");
        Files.writeString(fixturesPomPath, "pom content");
        File fixturesPom = fixturesPomPath.toFile();

        // To make mkdirs fail, we can create a file where a parent directory should be
        Path parentFilePath = tempDir.resolve("a-file");
        Files.writeString(parentFilePath, "I am a file");

        // parent will be tempDir/a-file/sub-dir, which cannot be created because a-file is a file
        Path copyTargetPath = parentFilePath.resolve("sub-dir/target.pom");
        File copyTarget = copyTargetPath.toFile();

        setField(mojo, "fixturesPom", fixturesPom);
        setField(mojo, "copyTarget", copyTarget);

        assertThrows(MojoExecutionException.class, () -> mojo.execute());
    }
}
