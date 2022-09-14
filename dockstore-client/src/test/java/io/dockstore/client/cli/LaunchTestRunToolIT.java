package io.dockstore.client.cli;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Map;

import com.google.gson.Gson;
import io.dockstore.client.cli.nested.ToolClient;
import io.dockstore.common.FlushingSystemErrRule;
import io.dockstore.common.FlushingSystemOutRule;
import io.dropwizard.testing.ResourceHelpers;
import io.swagger.client.api.ContainersApi;
import io.swagger.client.api.UsersApi;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.rules.ExpectedException;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class LaunchTestRunToolIT {

    @Rule
    public final SystemOutRule systemOutRule = new FlushingSystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new FlushingSystemErrRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final ExpectedSystemExit exit = ExpectedSystemExit.none();

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Rule
    public TestRule watcher = new TestWatcher() {
        protected void starting(Description description) {
            System.out.println("Starting test: " + description.getMethodName());
        }
    };



    @Test
    public void yamlToolCorrect() {
        File cwlFile = new File(ResourceHelpers.resourceFilePath("1st-tool.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("echo-job.yml"));

        ArrayList<String> args = new ArrayList<>();
        args.add("--local-entry");
        args.add("--yaml");
        args.add(cwlJSON.getAbsolutePath());

        ContainersApi api = mock(ContainersApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        // do not use a cache
        runTool(cwlFile, args, api, usersApi, client, false);
    }

    private void runTool(File cwlFile, File cwlJSON) {
        runTool(cwlFile, cwlJSON, false);
    }

    private void runTool(File cwlFile, File cwlJSON, boolean threaded) {
        ArrayList<String> args = new ArrayList<>();
        args.add("--local-entry");
        args.add("--cwl");
        args.add(cwlFile.getAbsolutePath());
        args.add("--json");
        args.add(cwlJSON.getAbsolutePath());

        ContainersApi api = mock(ContainersApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        // do not use a cache
        if (threaded) {
            runToolThreaded(cwlFile, args, api, usersApi, client);
        } else {
            runTool(cwlFile, args, api, usersApi, client, true);
        }
    }

    private void runTool(File cwlFile, ArrayList<String> args, ContainersApi api, UsersApi usersApi, Client client, boolean useCache) {
        client.setConfigFile(ResourceHelpers.resourceFilePath(useCache ? "config.withCache" : "config"));
        Client.SCRIPT.set(true);
        runToolShared(cwlFile, args, api, usersApi, client);
    }

    @Test
    public void runToolWithGlobbedFilesOnOutput() throws IOException {

        File fileDir = new File("/tmp/provision_out_with_files");
        FileUtils.deleteDirectory(fileDir);
        FileUtils.forceMkdir(fileDir);

        File cwlFile = new File(ResourceHelpers.resourceFilePath("splitBlob.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("splitBlob.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add("--local-entry");
        args.add("--cwl");
        args.add(cwlFile.getAbsolutePath());
        args.add("--json");
        args.add(cwlJSON.getAbsolutePath());

        ContainersApi api = mock(ContainersApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        // do not use a cache
        runTool(cwlFile, args, api, usersApi, client, true);

        final int countMatches = StringUtils.countMatches(systemOutRule.getLog(), "Provisioning from");
        assertEquals("output should include multiple provision out events, found " + countMatches, 7, countMatches);
        for (char y = 'a'; y <= 'f'; y++) {
            assertTrue("output should provision out to correct locations",
                systemOutRule.getLog().contains("/tmp/provision_out_with_files/"));
            assertTrue(new File("/tmp/provision_out_with_files/test.a" + y).exists());
        }
    }

    @Test
    public void runToolWithoutProvisionOnOutput() throws IOException {

        FileUtils.deleteDirectory(new File("/tmp/provision_out_with_files"));

        File cwlFile = new File(ResourceHelpers.resourceFilePath("split.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("split_no_provision_out.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add("--local-entry");
        args.add("--cwl");
        args.add(cwlFile.getAbsolutePath());
        args.add("--json");
        args.add(cwlJSON.getAbsolutePath());

        ContainersApi api = mock(ContainersApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        // do not use a cache
        runTool(cwlFile, args, api, usersApi, client, true);

        final int countMatches = StringUtils.countMatches(systemOutRule.getLog(), "Uploading");
        assertEquals("output should include multiple provision out events, found " + countMatches, 0, countMatches);
    }

    @Test
    public void runToolWithDirectoriesConversion() {
        File cwlFile = new File(ResourceHelpers.resourceFilePath("dir6.cwl"));

        ArrayList<String> args = new ArrayList<>();
        args.add("tool");
        args.add("convert");
        args.add("cwl2json");
        args.add("--cwl");
        args.add(cwlFile.getAbsolutePath());

        runClientCommand(args);
        final String log = systemOutRule.getLog();
        Gson gson = new Gson();
        final Map<String, Map<String, Object>> map = gson.fromJson(log, Map.class);
        assertEquals(2, map.size());
        assertEquals("Directory", map.get("indir").get("class"));
    }

    @Test
    public void runToolWithDirectories() {
        File cwlFile = new File(ResourceHelpers.resourceFilePath("dir6.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("dir6.cwl.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add("--local-entry");
        args.add("--cwl");
        args.add(cwlFile.getAbsolutePath());
        args.add("--json");
        args.add(cwlJSON.getAbsolutePath());

        ContainersApi api = mock(ContainersApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        // do not use a cache
        runTool(cwlFile, args, api, usersApi, client, false);
    }

    @Test
    public void runToolWithDirectoriesThreaded() {
        File cwlFile = new File(ResourceHelpers.resourceFilePath("dir6.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("dir6.cwl.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add("--local-entry");
        args.add("--cwl");
        args.add(cwlFile.getAbsolutePath());
        args.add("--json");
        args.add(cwlJSON.getAbsolutePath());

        ContainersApi api = mock(ContainersApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        // do not use a cache
        runToolThreaded(cwlFile, args, api, usersApi, client);
    }

    @Test
    public void runToolWithSecondaryFilesOnOutput() throws IOException {

        FileUtils.deleteDirectory(new File("/tmp/provision_out_with_files"));

        File cwlFile = new File(ResourceHelpers.resourceFilePath("split.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("split.json"));

        runTool(cwlFile, cwlJSON);

        final int countMatches = StringUtils.countMatches(systemOutRule.getLog(), "Provisioning from");
        assertEquals("output should include multiple provision out events, found " + countMatches, 6, countMatches);
        for (char y = 'a'; y <= 'f'; y++) {
            String filename = "/tmp/provision_out_with_files/test.a" + y;
            checkFileAndThenDeleteIt(filename);
        }
    }



    @Test
    public void runToolSecondaryFilesToDirectory() throws IOException {

        FileUtils.deleteDirectory(new File("/tmp/provision_out_with_files"));

        File cwlFile = new File(ResourceHelpers.resourceFilePath("file_provision/split.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("file_provision/split_to_directory.json"));

        runTool(cwlFile, cwlJSON);

        final int countMatches = StringUtils.countMatches(systemOutRule.getLog(), "Provisioning from");
        assertEquals("output should include multiple provision out events, found " + countMatches, 6, countMatches);
        for (char y = 'a'; y <= 'f'; y++) {
            String filename = "/tmp/provision_out_with_files/test.a" + y;
            checkFileAndThenDeleteIt(filename);
        }
    }

    @Test
    public void runToolSecondaryFilesToDirectoryThreaded() throws IOException {

        FileUtils.deleteDirectory(new File("/tmp/provision_out_with_files"));

        File cwlFile = new File(ResourceHelpers.resourceFilePath("file_provision/split.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("file_provision/split_to_directory.json"));

        runTool(cwlFile, cwlJSON, true);

        final int countMatches = StringUtils.countMatches(systemOutRule.getLog(), "Provisioning from");
        assertEquals("output should include multiple provision out events, found " + countMatches, 6, countMatches);
        for (char y = 'a'; y <= 'f'; y++) {
            String filename = "/tmp/provision_out_with_files/test.a" + y;
            checkFileAndThenDeleteIt(filename);
        }
    }

    @Test
    public void runToolSecondaryFilesToCWD() {
        File cwlFile = new File(ResourceHelpers.resourceFilePath("file_provision/split.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("file_provision/split_to_missing_directory.json"));

        runTool(cwlFile, cwlJSON);

        final int countMatches = StringUtils.countMatches(systemOutRule.getLog(), "Provisioning from");
        assertEquals("output should include multiple provision out events, found " + countMatches, 6, countMatches);
        for (char y = 'a'; y <= 'f'; y++) {
            String filename = "./test.a" + y;
            checkFileAndThenDeleteIt(filename);
        }
    }

    @Test
    public void runToolMalformedToCWD() {
        File cwlFile = new File(ResourceHelpers.resourceFilePath("file_provision/split.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("file_provision/split_to_malformed.json"));

        runTool(cwlFile, cwlJSON);

        final int countMatches = StringUtils.countMatches(systemOutRule.getLog(), "Provisioning from");
        assertEquals("output should include multiple provision out events, found " + countMatches, 6, countMatches);
        for (char y = 'a'; y <= 'f'; y++) {
            String filename = "./test.a" + y;
            checkFileAndThenDeleteIt(filename);
        }
    }

    @Test
    public void runToolToMissingS3() {
        File cwlFile = new File(ResourceHelpers.resourceFilePath("file_provision/split.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("file_provision/split_to_s3_failed.json"));
        ByteArrayOutputStream launcherOutput = null;
        try {
            launcherOutput = new ByteArrayOutputStream();
            System.setOut(new PrintStream(launcherOutput));

            thrown.expect(AssertionError.class);
            runTool(cwlFile, cwlJSON);
            final String standardOutput = launcherOutput.toString();
            assertTrue("Error should occur, caused by Amazon S3 Exception",
                standardOutput.contains("Caused by: com.amazonaws.services.s3.model.AmazonS3Exception"));
        } finally {
            try {
                if (launcherOutput != null) {
                    launcherOutput.close();
                }
            } catch (IOException ex) {
                assertTrue("Error closing output stream.", true);
            }
        }
    }

    @Test
    public void runToolDirectoryMalformedToCWD() throws IOException {
        File cwlFile = new File(ResourceHelpers.resourceFilePath("file_provision/split_dir.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("file_provision/split_to_malformed.json"));

        runTool(cwlFile, cwlJSON);

        final int countMatches = StringUtils.countMatches(systemOutRule.getLog(), "Provisioning from");
        assertEquals("output should include one provision out event, found " + countMatches, 1, countMatches);
        String filename = "test1";
        checkFileAndThenDeleteIt(filename);
        FileUtils.deleteDirectory(new File(filename));
    }

    private void runToolThreaded(File cwlFile, ArrayList<String> args, ContainersApi api, UsersApi usersApi, Client client) {
        Client.SCRIPT.set(true);
        client.setConfigFile(ResourceHelpers.resourceFilePath("config.withThreads"));

        runToolShared(cwlFile, args, api, usersApi, client);
    }

    private void runToolShared(File cwlFile, ArrayList<String> args, ContainersApi api, UsersApi usersApi, Client client) {
        ToolClient toolClient = new ToolClient(api, null, usersApi, client, false);
        toolClient.checkEntryFile(cwlFile.getAbsolutePath(), args, null);

        assertTrue("output should include a successful cwltool run", systemOutRule.getLog().contains("Final process status is success"));
    }

    @Test
    public void cwlNullInputParameter() {
        // Tests if a null input parameter is correctly handled when converting json
        File nullCWL = new File(ResourceHelpers.resourceFilePath("nullParam.cwl"));
        File nullJSON = new File(ResourceHelpers.resourceFilePath("nullParam.json"));

        // run simple echo null tool
        runTool(nullCWL, nullJSON, false);
    }

    private void checkFileAndThenDeleteIt(String filename) {
        assertTrue("output should provision out to correct locations, could not find " + filename + " in log",
            systemOutRule.getLog().contains(filename));
        assertTrue("file does not actually exist", Files.exists(Paths.get(filename)));
        // cleanup
        FileUtils.deleteQuietly(Paths.get(filename).toFile());
    }

    private void runClientCommand(ArrayList<String> args) {
        args.add(0, ResourceHelpers.resourceFilePath("config"));
        args.add(0, "--config");
        args.add(0, "--script");
        Client.main(args.toArray(new String[0]));
    }


    @Test
    public void runToolWithSecondaryFilesRenamedOnOutput() throws IOException {

        FileUtils.deleteDirectory(new File("/tmp/provision_out_with_files_renamed"));

        File cwlFile = new File(ResourceHelpers.resourceFilePath("split.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("split.renamed.json"));

        runTool(cwlFile, cwlJSON);

        final int countMatches = StringUtils.countMatches(systemOutRule.getLog(), "Provisioning from");
        assertEquals("output should include multiple provision out events, found " + countMatches, 6, countMatches);
        for (char y = 'a'; y <= 'f'; y++) {
            String filename = "/tmp/provision_out_with_files_renamed/renamed.a" + y;
            checkFileAndThenDeleteIt(filename);
        }
    }

    @Test
    public void runToolWithSecondaryFilesOfVariousKinds() throws IOException {

        FileUtils.deleteDirectory(new File("/tmp/provision_out_with_files_renamed"));

        File cwlFile = new File(ResourceHelpers.resourceFilePath("split.nocaret.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("split.renamed.json"));

        runTool(cwlFile, cwlJSON);

        final int countMatches = StringUtils.countMatches(systemOutRule.getLog(), "Provisioning from");
        assertEquals("output should include multiple provision out events, found " + countMatches, 8, countMatches);
        checkFileAndThenDeleteIt("/tmp/provision_out_with_files_renamed/renamed.aa");
        for (char y = 'b'; y <= 'f'; y++) {
            String filename = "/tmp/provision_out_with_files_renamed/renamed.aa.a" + y + "extra";
            checkFileAndThenDeleteIt(filename);
        }
        checkFileAndThenDeleteIt("/tmp/provision_out_with_files_renamed/renamed.aa.funky.extra.stuff");
        checkFileAndThenDeleteIt("/tmp/provision_out_with_files_renamed/renamed.aa.groovyextrastuff");
    }

    @Test
    public void runToolWithSecondaryFilesOfEvenStrangerKinds() throws IOException {

        FileUtils.deleteDirectory(new File("/tmp/provision_out_with_files_renamed"));

        File cwlFile = new File(ResourceHelpers.resourceFilePath("split.more.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("split.extra.json"));

        runTool(cwlFile, cwlJSON);

        final int countMatches = StringUtils.countMatches(systemOutRule.getLog(), "Provisioning from");
        assertEquals("output should include multiple provision out events, found " + countMatches, 6, countMatches);
        for (char y = 'a'; y <= 'e'; y++) {
            String filename = "/tmp/provision_out_with_files_renamed/renamed.txt.a" + y;
            checkFileAndThenDeleteIt(filename);
        }
        checkFileAndThenDeleteIt("/tmp/provision_out_with_files_renamed/renamed.extra");
    }

}
