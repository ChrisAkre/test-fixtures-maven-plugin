package dev.akre.maven.plugins.fixtures;

import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Copies the synthetic POM to a target location.
 */
@Mojo(
    name = "copy-pom",
    defaultPhase = LifecyclePhase.PROCESS_CLASSES,
    threadSafe = true
)
public class CopyPomMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${project.artifactId}-test-fixtures")
    private String fixturesArtifactId;

    @Parameter(defaultValue = "${project.build.directory}/${project.artifactId}-test-fixtures-${project.version}.pom")
    private File fixturesPom;

    @Parameter(property = "copyTarget", required = true)
    private File copyTarget;

    @Parameter(property = "copy-pom.skip", defaultValue = "false")
    private boolean skip;

    @Override
    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("Skipping copy-pom goal as configured.");
            return;
        }

        // If fixturesArtifactId is customized, update the file paths if they still point to the default
        String defaultPrefix = project.getArtifactId() + "-test-fixtures-" + project.getVersion();
        String customPrefix = fixturesArtifactId + "-" + project.getVersion();

        if (fixturesPom.getName().startsWith(defaultPrefix) && !fixturesArtifactId.equals(project.getArtifactId() + "-test-fixtures")) {
            fixturesPom = new File(fixturesPom.getParentFile(), fixturesPom.getName().replace(defaultPrefix, customPrefix));
        }

        if (!fixturesPom.exists()) {
            getLog().info("Test fixtures POM not found. Skipping copy.");
            return;
        }

        getLog().info("Copying test fixtures POM to " + copyTarget.getAbsolutePath());

        try {
            if (copyTarget.getParentFile() != null && !copyTarget.getParentFile().exists()) {
                if (!copyTarget.getParentFile().mkdirs()) {
                    throw new MojoExecutionException("Failed to create parent directories for " + copyTarget.getAbsolutePath());
                }
            }

            // Parse the POM
            Model model;
            try (FileInputStream fis = new FileInputStream(fixturesPom)) {
                model = new MavenXpp3Reader().read(fis);
            }

            // Remove skipMain from maven-compiler-plugin
            if (model.getBuild() != null && model.getBuild().getPlugins() != null) {
                for (Plugin plugin : model.getBuild().getPlugins()) {
                    if ("maven-compiler-plugin".equals(plugin.getArtifactId())) {
                        removeSkipMain(plugin.getConfiguration());
                        if (plugin.getExecutions() != null) {
                            for (PluginExecution execution : plugin.getExecutions()) {
                                removeSkipMain(execution.getConfiguration());
                            }
                        }
                    }
                }
            }

            // Write the modified POM
            try (FileOutputStream fos = new FileOutputStream(copyTarget)) {
                new MavenXpp3Writer().write(fos, model);
            }

        } catch (Exception e) {
            throw new MojoExecutionException("Error copying test fixtures POM", e);
        }
    }

    private void removeSkipMain(Object config) {
        if (config instanceof Xpp3Dom) {
            Xpp3Dom dom = (Xpp3Dom) config;
            for (int i = 0; i < dom.getChildCount(); i++) {
                if ("skipMain".equals(dom.getChild(i).getName())) {
                    dom.removeChild(i);
                    // Decrement i to account for the removed element, in case there are multiple
                    i--;
                }
            }
        }
    }
}
