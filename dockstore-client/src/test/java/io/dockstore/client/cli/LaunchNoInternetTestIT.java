package io.dockstore.client.cli;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import io.dockstore.common.Utilities;
import io.dropwizard.testing.ResourceHelpers;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

import static io.dockstore.client.cli.Client.CONFIG;
import static io.dockstore.client.cli.Client.SCRIPT_FLAG;
import static io.dockstore.client.cli.Client.TOOL;
import static io.dockstore.client.cli.Client.WORKFLOW;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static uk.org.webcompere.systemstubs.SystemStubs.catchSystemExit;

/**
 * Tests CLI launching with image on filesystem instead of internet
 * In general, ubuntu:0118999881999119725...3 is the file that exists only on filesystem and hopefully never on internet
 * All tests will be trying to use that image
 *
 * @author gluu
 * @since 1.6.0
 */
@ExtendWith(SystemStubsExtension.class)
class LaunchNoInternetTestIT {
    private static final Logger LOG = LoggerFactory.getLogger(LaunchNoInternetTestIT.class);
    private static final File DESCRIPTOR_FILE = new File(ResourceHelpers.resourceFilePath("nonexistent_image/CWL/nonexistent_image.cwl"));
    private static final File YAML_FILE = new File(ResourceHelpers.resourceFilePath("echo-job.yml"));
    private static final File DOCKERFILE = new File(ResourceHelpers.resourceFilePath("nonexistent_image/Dockerfile"));
    private static final String FAKE_IMAGE_NAME = "dockstore.org/bashwithbinbash:0118999881999119725...3";
    private static String dockerImageDirectory;
    @SystemStub
    public final SystemOut systemOutRule = new SystemOut();

    @SystemStub
    public final SystemErr systemErrRule = new SystemErr();

    /**
     * Downloading an image with bash (Nextflow needs it) and saving it on the filesystem as something weird that is unlikely to be on the internet
     * to make sure that the Dockstore CLI only uses the image from the filesystem
     *
     * @throws IOException Something has gone terribly wrong with preparing the fake docker image
     */
    @BeforeAll
    public static void downloadCustomDockerImage() throws IOException {
        Utilities.executeCommand("docker build -f " + DOCKERFILE + " . -t " + FAKE_IMAGE_NAME, System.out, System.err);
        dockerImageDirectory = Files.createTempDirectory("docker_images").toAbsolutePath().toString();
        Utilities.executeCommand("docker save -o " + dockerImageDirectory + "/fakeImage " + FAKE_IMAGE_NAME, System.out, System.err);
    }

    @BeforeEach
    void clearImage() {
        try {
            Utilities.executeCommand("docker rmi " + FAKE_IMAGE_NAME);
        } catch (Exception e) {
            LOG.debug("Don't care that it failed, it probably just didn't have the image loaded before");
        }
    }

    /**
     * When Docker image directory is not specified
     */
    @Test
    void directoryNotSpecified() throws Exception {
        List<String> args = new ArrayList<>();
        args.add(TOOL);
        args.add("launch");
        args.add("--local-entry");
        args.add(DESCRIPTOR_FILE.getAbsolutePath());
        args.add("--yaml");
        args.add(YAML_FILE.getAbsolutePath());
        args.add(CONFIG);
        args.add(ResourceHelpers.resourceFilePath("config"));
        args.add(SCRIPT_FLAG);

        int exitCode = catchSystemExit(() -> Client.main(args.toArray(new String[0])));
        assertNotEquals(0, exitCode);
        assertTrue(systemErrRule.getText().contains(
                "Docker is required to run this tool: Command '['docker', 'pull', '" + FAKE_IMAGE_NAME
                        + "']' returned non-zero exit status 1"));
    }

    /**
     * Docker image directory specified but doesn't exist
     */
    @Test
    void directorySpecifiedDoesNotExist() throws Exception {
        String toWrite = "docker-images = src/test/resources/nonexistent_image/docker_images/thisDirectoryShouldNotExist";
        File configPath = createTempFile(toWrite);
        if (configPath == null) {
            fail("Could create temp config file");
        }

        List<String> args = new ArrayList<>();
        args.add(TOOL);
        args.add("launch");
        args.add("--local-entry");
        args.add(DESCRIPTOR_FILE.getAbsolutePath());
        args.add("--yaml");
        args.add(YAML_FILE.getAbsolutePath());
        args.add(CONFIG);
        args.add(configPath.getAbsolutePath());
        args.add(SCRIPT_FLAG);

        int exitCode = catchSystemExit(() -> Client.main(args.toArray(new String[0])));
        assertNotEquals(0, exitCode);
        assertTrue(systemOutRule.getText().contains("The specified Docker image directory not found:"));
        assertTrue(systemErrRule.getText().contains(
                "Docker is required to run this tool: Command '['docker', 'pull', '" + FAKE_IMAGE_NAME
                        + "']' returned non-zero exit status 1"));
    }

    /**
     * Docker image directory specified is actually a file
     */
    @Test
    void directorySpecifiedButIsAFile() throws Exception {
        String toWrite = "docker-images = " + dockerImageDirectory + "/fakeImage";
        File configPath = createTempFile(toWrite);
        if (configPath == null) {
            fail("Could create temp config file");
        }
        ArrayList<String> args = new ArrayList<>();
        args.add(TOOL);
        args.add("launch");
        args.add("--local-entry");
        args.add(DESCRIPTOR_FILE.getAbsolutePath());
        args.add("--yaml");
        args.add(YAML_FILE.getAbsolutePath());
        args.add(CONFIG);
        args.add(configPath.getAbsolutePath());
        args.add(SCRIPT_FLAG);

        int exitCode = catchSystemExit(() -> Client.main(args.toArray(new String[0])));
        assertNotEquals(0, exitCode);
        assertTrue(systemOutRule.getText().contains("The specified Docker image directory is a file:"));
        assertTrue(systemErrRule.getText().contains(
                "Docker is required to run this tool: Command '['docker', 'pull', '" + FAKE_IMAGE_NAME
                        + "']' returned non-zero exit status 1"));
    }

    /**
     * Docker image directory specified but has no files in there
     */
    @Test
    void directorySpecifiedButNoImages() throws Exception {
        Path emptyTestDirectory = createEmptyTestDirectory();
        if (emptyTestDirectory == null) {
            fail("Could not create empty temp directory");
        }
        String toWrite = "docker-images = " + emptyTestDirectory.toAbsolutePath();
        File configPath = createTempFile(toWrite);
        if (configPath == null) {
            fail("Could not create temp config file");
        }
        ArrayList<String> args = new ArrayList<>();
        args.add(TOOL);
        args.add("launch");
        args.add("--local-entry");
        args.add(DESCRIPTOR_FILE.getAbsolutePath());
        args.add("--yaml");
        args.add(YAML_FILE.getAbsolutePath());
        args.add(CONFIG);
        args.add(configPath.getAbsolutePath());
        args.add(SCRIPT_FLAG);

        int exitCode = catchSystemExit(() -> Client.main(args.toArray(new String[0])));
        assertNotEquals(0, exitCode);
        assertTrue(systemOutRule.getText().contains("There are no files in the docker image directory"));
        assertTrue(systemErrRule.getText().contains(
                "Docker is required to run this tool: Command '['docker', 'pull', '" + FAKE_IMAGE_NAME
                        + "']' returned non-zero exit status 1"));
    }

    /**
     * Everything correctly configured with CWL tool
     */
    @Test
    void correctCWL() throws IOException {
        File descriptorFile = new File(ResourceHelpers.resourceFilePath("nonexistent_image/CWL/nonexistent_image.cwl"));
        String toWrite = "docker-images = " + dockerImageDirectory;
        File configPath = createTempFile(toWrite);
        if (configPath == null) {
            fail("Could create temp config file");
        }
        ArrayList<String> args = new ArrayList<>();
        args.add(TOOL);
        args.add("launch");
        args.add("--local-entry");
        args.add(descriptorFile.getAbsolutePath());
        args.add("--yaml");
        args.add(YAML_FILE.getAbsolutePath());
        args.add(CONFIG);
        args.add(configPath.getAbsolutePath());
        args.add(SCRIPT_FLAG);

        Client.main(args.toArray(new String[0]));
        assertTrue(systemOutRule.getText().contains("Final process status is success"), "Final process status was not success");
    }

    /**
     * Everything correctly configured with NFL workflow
     */
    @Test
    void correctNFL() throws IOException {
        copyNFLFiles();
        File descriptorFile = new File(ResourceHelpers.resourceFilePath("nonexistent_image/NFL/nextflow.config"));
        File jsonFile = new File(ResourceHelpers.resourceFilePath("nextflow_rnatoy/test.json"));
        String toWrite = "docker-images = " + dockerImageDirectory;
        File configPath = createTempFile(toWrite);
        if (configPath == null) {
            fail("Could create temp config file");
        }
        ArrayList<String> args = new ArrayList<>();
        args.add(WORKFLOW);
        args.add("launch");
        args.add("--local-entry");
        args.add(descriptorFile.getAbsolutePath());
        args.add("--json");
        args.add(jsonFile.getAbsolutePath());
        args.add(CONFIG);
        args.add(configPath.getAbsolutePath());

        Client.main(args.toArray(new String[0]));
        assertTrue(systemOutRule.getText().contains("Saving copy of Nextflow stdout to: "),
                "Final process status was not success");
    }

    /**
     * Everything correctly configured with WDL workflow
     */
    @Test
    void correctWDL() throws IOException {
        File descriptorFile = new File(ResourceHelpers.resourceFilePath("nonexistent_image/WDL/nonexistent_image.wdl"));
        File jsonFile = new File(ResourceHelpers.resourceFilePath("nonexistent_image/WDL/test.json"));
        String toWrite = "docker-images = " + dockerImageDirectory;
        File configPath = createTempFile(toWrite);
        if (configPath == null) {
            fail("Could create temp config file");
        }
        ArrayList<String> args = new ArrayList<>();
        args.add(WORKFLOW);
        args.add("launch");
        args.add("--local-entry");
        args.add(descriptorFile.getAbsolutePath());
        args.add("--json");
        args.add(jsonFile.getAbsolutePath());
        args.add(CONFIG);
        args.add(configPath.getAbsolutePath());

        Client.main(args.toArray(new String[0]));
        assertTrue(systemOutRule.getText().contains("Output files left in place"), "Final process status was not success");
    }

    /**
     * Create empty test directory because don't want to add empty directory to Git
     */
    private Path createEmptyTestDirectory() throws IOException {
        return Files.createTempDirectory("empty_docker_images");

    }

    /**
     * Create a temp config file to avoid add so many to Git
     *
     * @param contents Contents of the config file
     * @return The temp config file
     */
    private File createTempFile(String contents) throws IOException {
        File tmpFile = File.createTempFile("config", ".tmp");
        try (FileWriter writer = new FileWriter(tmpFile)) {
            writer.write(contents);
            return tmpFile;
        }
    }

    /**
     * Nextflow with Dockstore CLI requires main.nf to be at same directory of execution, copying file over
     *
     * @throws IOException Something has gone terribly wrong with copying the Nextflow files
     */
    private void copyNFLFiles() throws IOException {
        File userDir = new File(System.getProperty("user.dir"));
        File testFileDirectory = new File("src/test/resources/nonexistent_image/NFL");
        FileUtils.copyDirectory(testFileDirectory, userDir);
    }
}
