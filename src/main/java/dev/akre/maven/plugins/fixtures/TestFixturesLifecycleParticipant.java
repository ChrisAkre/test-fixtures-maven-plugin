package dev.akre.maven.plugins.fixtures;

import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.WorkspaceReader;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

@Named("test-fixtures-participant")
@Singleton
public class TestFixturesLifecycleParticipant extends AbstractMavenLifecycleParticipant {

    private final TestFixturesWorkspaceReader workspaceReader;

    @Inject
    public TestFixturesLifecycleParticipant(TestFixturesWorkspaceReader workspaceReader) {
        this.workspaceReader = workspaceReader;
    }

    @Override
    public void afterProjectsRead(MavenSession session) throws MavenExecutionException {
        workspaceReader.init(session);
        
        RepositorySystemSession repoSession = session.getRepositorySession();
        if (repoSession == null) {
            return;
        }

        try {
            Method setWorkspaceReaderMethod = repoSession.getClass().getMethod("setWorkspaceReader", WorkspaceReader.class);
            setWorkspaceReaderMethod.invoke(repoSession, workspaceReader);
        } catch (Exception e) {
            try {
                Field field = findField(repoSession.getClass(), "workspaceReader");
                if (field != null) {
                    field.setAccessible(true);
                    field.set(repoSession, workspaceReader);
                }
            } catch (Exception e2) {
                // Ignore
            }
        }
    }

    private Field findField(Class<?> clazz, String name) {
        while (clazz != null) {
            try {
                return clazz.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }
}
