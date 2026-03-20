package dev.akre.maven.plugins.fixtures;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Packages the compiled test fixtures into a JAR and generates a synthetic POM.
 */
@Mojo(
    name = "package-fixtures",
    defaultPhase = LifecyclePhase.PACKAGE,
    threadSafe = true
)
public class PackageFixturesMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${project.build.directory}/test-fixtures-classes")
    private File fixturesOutputDirectory;

    @Parameter(defaultValue = "${project.build.directory}/${project.artifactId}-test-fixtures-${project.version}.jar")
    private File fixturesJar;

    @Parameter(defaultValue = "${project.build.directory}/${project.artifactId}-test-fixtures-${project.version}.pom")
    private File fixturesPom;

    @Parameter
    private List<Dependency> fixtureDependencies = new ArrayList<>();

    @Override
    public void execute() throws MojoExecutionException {
        if (!fixturesOutputDirectory.exists() || !fixturesOutputDirectory.isDirectory()) {
            getLog().info("No test fixtures found to package. Skipping.");
            return;
        }

        getLog().info("Packaging test fixtures to " + fixturesJar.getAbsolutePath());

        // 1. Create the JAR
        createJar();

        // 2. Generate the synthetic POM
        generateSyntheticPom();

        // 3. Attach artifacts to the project Context so install/deploy mojos can find them
        project.setContextValue("fixturesJar", fixturesJar.getAbsolutePath());
        project.setContextValue("fixturesPom", fixturesPom.getAbsolutePath());
    }

    private void createJar() throws MojoExecutionException {
        try {
            Commandline cli = new Commandline();
            cli.setExecutable("jar");
            cli.createArg().setValue("cf");
            cli.createArg().setValue(fixturesJar.getAbsolutePath());
            cli.createArg().setValue("-C");
            cli.createArg().setValue(fixturesOutputDirectory.getAbsolutePath());
            cli.createArg().setValue(".");

            CommandLineUtils.StringStreamConsumer out = new CommandLineUtils.StringStreamConsumer();
            CommandLineUtils.StringStreamConsumer err = new CommandLineUtils.StringStreamConsumer();

            int exitCode = CommandLineUtils.executeCommandLine(cli, out, err);
            if (exitCode != 0) {
                throw new MojoExecutionException("Failed to package test fixtures: " + err.getOutput());
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Error creating test fixtures JAR", e);
        }
    }

    private void generateSyntheticPom() throws MojoExecutionException {
        Model model = new Model();
        model.setModelVersion("4.0.0");
        model.setGroupId(project.getGroupId());
        model.setArtifactId(project.getArtifactId() + "-test-fixtures");
        model.setVersion(project.getVersion());
        model.setPackaging("jar");
        model.setDescription("Test fixtures for " + project.getArtifactId());

        // The test fixtures depend on the main project classes
        Dependency mainProjectDep = new Dependency();
        mainProjectDep.setGroupId(project.getGroupId());
        mainProjectDep.setArtifactId(project.getArtifactId());
        mainProjectDep.setVersion(project.getVersion());
        model.addDependency(mainProjectDep);

        // Add explicit fixture dependencies
        if (fixtureDependencies != null) {
            for (Dependency dep : fixtureDependencies) {
                model.addDependency(dep);
            }
        }

        try (FileOutputStream fos = new FileOutputStream(fixturesPom)) {
            new MavenXpp3Writer().write(fos, model);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to write synthetic POM", e);
        }
    }
}
