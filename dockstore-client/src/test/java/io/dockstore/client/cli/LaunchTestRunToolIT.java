package io.dockstore.client.cli;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import io.dockstore.client.cli.nested.ToolClient;
import io.dockstore.common.FlushingSystemErr;
import io.dockstore.common.FlushingSystemOut;
import io.dropwizard.testing.ResourceHelpers;
import io.swagger.client.api.ContainersApi;
import io.swagger.client.api.UsersApi;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

import static io.dockstore.client.cli.Client.CONFIG;
import static io.dockstore.client.cli.Client.PLUGIN;
import static io.dockstore.client.cli.Client.SCRIPT_FLAG;
import static io.dockstore.client.cli.Client.TOOL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static uk.org.webcompere.systemstubs.SystemStubs.catchSystemExit;

@ExtendWith(LaunchTestIT.TestStatus.class)
@ExtendWith(SystemStubsExtension.class)
class LaunchTestRunToolIT {

    @SystemStub
    public final SystemOut systemOutRule = new FlushingSystemOut();

    @SystemStub
    public final SystemErr systemErrRule = new FlushingSystemErr();

    @BeforeEach
    void clearLogs() {
        systemErrRule.clear();
        systemOutRule.clear();
    }

    @Test
    void yamlToolCorrect() {
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
    void runToolWithGlobbedFilesOnOutput() throws IOException {

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

        final int countMatches = StringUtils.countMatches(systemOutRule.getText(), "Provisioning from");
        assertEquals(7, countMatches, "output should include multiple provision out events, found " + countMatches);
        for (char y = 'a'; y <= 'f'; y++) {
            assertTrue(systemOutRule.getText().contains("/tmp/provision_out_with_files/"),
                    "output should provision out to correct locations");
            assertTrue(new File("/tmp/provision_out_with_files/test.a" + y).exists());
        }
    }

    @Test
    void runToolWithoutProvisionOnOutput() throws IOException {

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

        final int countMatches = StringUtils.countMatches(systemOutRule.getText(), "Uploading");
        assertEquals(0, countMatches, "output should include multiple provision out events, found " + countMatches);
    }

    @Test
    void runToolWithDirectoriesConversion() {
        File cwlFile = new File(ResourceHelpers.resourceFilePath("dir6.cwl"));

        ArrayList<String> args = new ArrayList<>();
        args.add(TOOL);
        args.add("convert");
        args.add("cwl2json");
        args.add("--cwl");
        args.add(cwlFile.getAbsolutePath());

        runClientCommand(args);
        final String log = systemOutRule.getText();
        Gson gson = new Gson();
        final Map<String, Map<String, Object>> map = gson.fromJson(log, Map.class);
        assertEquals(2, map.size());
        assertEquals("Directory", map.get("indir").get("class"));
    }

    @Test
    void runToolWithDirectories() {
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
    void runToolWithDirectoriesThreaded() {
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
    void runToolWithSecondaryFilesOnOutput() throws IOException {

        FileUtils.deleteDirectory(new File("/tmp/provision_out_with_files"));

        File cwlFile = new File(ResourceHelpers.resourceFilePath("split.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("split.json"));

        runTool(cwlFile, cwlJSON);

        final int countMatches = StringUtils.countMatches(systemOutRule.getText(), "Provisioning from");
        assertEquals(6, countMatches, "output should include multiple provision out events, found " + countMatches);
        for (char y = 'a'; y <= 'f'; y++) {
            String filename = "/tmp/provision_out_with_files/test.a" + y;
            checkFileAndThenDeleteIt(filename);
        }
    }



    @Test
    void runToolSecondaryFilesToDirectory() throws IOException {

        FileUtils.deleteDirectory(new File("/tmp/provision_out_with_files"));

        File cwlFile = new File(ResourceHelpers.resourceFilePath("file_provision/split.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("file_provision/split_to_directory.json"));

        runTool(cwlFile, cwlJSON);

        final int countMatches = StringUtils.countMatches(systemOutRule.getText(), "Provisioning from");
        assertEquals(6, countMatches, "output should include multiple provision out events, found " + countMatches);
        for (char y = 'a'; y <= 'f'; y++) {
            String filename = "/tmp/provision_out_with_files/test.a" + y;
            checkFileAndThenDeleteIt(filename);
        }
    }

    @Test
    void runToolSecondaryFilesToDirectoryThreaded() throws IOException {

        FileUtils.deleteDirectory(new File("/tmp/provision_out_with_files"));

        File cwlFile = new File(ResourceHelpers.resourceFilePath("file_provision/split.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("file_provision/split_to_directory.json"));

        runTool(cwlFile, cwlJSON, true);

        final int countMatches = StringUtils.countMatches(systemOutRule.getText(), "Provisioning from");
        assertEquals(6, countMatches, "output should include multiple provision out events, found " + countMatches);
        for (char y = 'a'; y <= 'f'; y++) {
            String filename = "/tmp/provision_out_with_files/test.a" + y;
            checkFileAndThenDeleteIt(filename);
        }
    }

    @Test
    void runToolSecondaryFilesToCWD() {
        File cwlFile = new File(ResourceHelpers.resourceFilePath("file_provision/split.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("file_provision/split_to_missing_directory.json"));

        runTool(cwlFile, cwlJSON);

        final int countMatches = StringUtils.countMatches(systemOutRule.getText(), "Provisioning from");
        assertEquals(6, countMatches, "output should include multiple provision out events, found " + countMatches);
        for (char y = 'a'; y <= 'f'; y++) {
            String filename = "./test.a" + y;
            checkFileAndThenDeleteIt(filename);
        }
    }

    @Test
    void runToolMalformedToCWD() {
        File cwlFile = new File(ResourceHelpers.resourceFilePath("file_provision/split.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("file_provision/split_to_malformed.json"));

        runTool(cwlFile, cwlJSON);

        final int countMatches = StringUtils.countMatches(systemOutRule.getText(), "Provisioning from");
        assertEquals(6, countMatches, "output should include multiple provision out events, found " + countMatches);
        for (char y = 'a'; y <= 'f'; y++) {
            String filename = "./test.a" + y;
            checkFileAndThenDeleteIt(filename);
        }
    }

    @Test
    void runToolToMissingS3() throws Exception {
        File cwlFile = new File(ResourceHelpers.resourceFilePath("file_provision/split.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("file_provision/split_to_s3_failed.json"));
        // failure relies on file provisioning plugins, oy!
        runClientCommand(new ArrayList<>(List.of(PLUGIN, "download")));
        catchSystemExit(() -> runTool(cwlFile, cwlJSON));
        assertTrue(systemErrRule.getText().contains("Caused by: com.amazonaws.services.s3.model.AmazonS3Exception"),
                "Error should occur, caused by Amazon S3 Exception, err output looked like: " + systemErrRule.getText() + "std out looked like" + systemOutRule.getText());
    }

    @Test
    void runToolDirectoryMalformedToCWD() throws IOException {
        File cwlFile = new File(ResourceHelpers.resourceFilePath("file_provision/split_dir.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("file_provision/split_to_malformed.json"));

        runTool(cwlFile, cwlJSON);

        final int countMatches = StringUtils.countMatches(systemOutRule.getText(), "Provisioning from");
        assertEquals(1, countMatches, "output should include one provision out event, found " + countMatches);
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

        assertTrue(systemOutRule.getText().contains("Final process status is success"),
                "output should include a successful cwltool run");
    }

    @Test
    void cwlNullInputParameter() {
        // Tests if a null input parameter is correctly handled when converting json
        File nullCWL = new File(ResourceHelpers.resourceFilePath("nullParam.cwl"));
        File nullJSON = new File(ResourceHelpers.resourceFilePath("nullParam.json"));

        // run simple echo null tool
        runTool(nullCWL, nullJSON, false);
    }

    private void checkFileAndThenDeleteIt(String filename) {
        assertTrue(systemOutRule.getText().contains(filename),
                "output should provision out to correct locations, could not find " + filename + " in log");
        assertTrue(Files.exists(Paths.get(filename)), "file does not actually exist");
        // cleanup
        FileUtils.deleteQuietly(Paths.get(filename).toFile());
    }

    private void runClientCommand(List<String> args) {
        args.add(0, ResourceHelpers.resourceFilePath("config"));
        args.add(0, CONFIG);
        args.add(0, SCRIPT_FLAG);
        Client.main(args.toArray(new String[0]));
    }


    @Test
    void runToolWithSecondaryFilesRenamedOnOutput() throws IOException {

        FileUtils.deleteDirectory(new File("/tmp/provision_out_with_files_renamed"));

        File cwlFile = new File(ResourceHelpers.resourceFilePath("split.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("split.renamed.json"));

        runTool(cwlFile, cwlJSON);

        final int countMatches = StringUtils.countMatches(systemOutRule.getText(), "Provisioning from");
        assertEquals(6, countMatches, "output should include multiple provision out events, found " + countMatches);
        for (char y = 'a'; y <= 'f'; y++) {
            String filename = "/tmp/provision_out_with_files_renamed/renamed.a" + y;
            checkFileAndThenDeleteIt(filename);
        }
    }

    @Test
    void runToolWithSecondaryFilesOfVariousKinds() throws IOException {

        FileUtils.deleteDirectory(new File("/tmp/provision_out_with_files_renamed"));

        File cwlFile = new File(ResourceHelpers.resourceFilePath("split.nocaret.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("split.renamed.json"));

        runTool(cwlFile, cwlJSON);

        final int countMatches = StringUtils.countMatches(systemOutRule.getText(), "Provisioning from");
        assertEquals(8, countMatches, "output should include multiple provision out events, found " + countMatches);
        checkFileAndThenDeleteIt("/tmp/provision_out_with_files_renamed/renamed.aa");
        for (char y = 'b'; y <= 'f'; y++) {
            String filename = "/tmp/provision_out_with_files_renamed/renamed.aa.a" + y + "extra";
            checkFileAndThenDeleteIt(filename);
        }
        checkFileAndThenDeleteIt("/tmp/provision_out_with_files_renamed/renamed.aa.funky.extra.stuff");
        checkFileAndThenDeleteIt("/tmp/provision_out_with_files_renamed/renamed.aa.groovyextrastuff");
    }

    @Test
    void runToolWithSecondaryFilesOfEvenStrangerKinds() throws IOException {

        FileUtils.deleteDirectory(new File("/tmp/provision_out_with_files_renamed"));

        File cwlFile = new File(ResourceHelpers.resourceFilePath("split.more.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("split.extra.json"));

        runTool(cwlFile, cwlJSON);

        final int countMatches = StringUtils.countMatches(systemOutRule.getText(), "Provisioning from");
        assertEquals(6, countMatches, "output should include multiple provision out events, found " + countMatches);
        for (char y = 'a'; y <= 'e'; y++) {
            String filename = "/tmp/provision_out_with_files_renamed/renamed.txt.a" + y;
            checkFileAndThenDeleteIt(filename);
        }
        checkFileAndThenDeleteIt("/tmp/provision_out_with_files_renamed/renamed.extra");
    }

}
