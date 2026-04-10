package dev.akre.maven.plugins.fixtures;

import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

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
    }
}
