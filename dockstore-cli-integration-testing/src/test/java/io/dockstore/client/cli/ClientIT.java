/*
 *    Copyright 2018 OICR
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package io.dockstore.client.cli;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.google.common.collect.Lists;
import io.dockstore.common.CLICommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.Registry;
import io.dockstore.common.TestUtility;
import io.dockstore.common.ToilCompatibleTest;
import io.dockstore.common.ToolTest;
import io.dropwizard.testing.ResourceHelpers;
import io.swagger.client.ApiException;
import org.junit.experimental.categories.Category;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

import static io.dockstore.client.cli.Client.CHECKER;
import static io.dockstore.client.cli.Client.CLEAN_CACHE;
import static io.dockstore.client.cli.Client.CONFIG;
import static io.dockstore.client.cli.Client.DEBUG_FLAG;
import static io.dockstore.client.cli.Client.DEPS;
import static io.dockstore.client.cli.Client.HELP;
import static io.dockstore.client.cli.Client.PLUGIN;
import static io.dockstore.client.cli.Client.SCRIPT_FLAG;
import static io.dockstore.client.cli.Client.SERVER_METADATA;
import static io.dockstore.client.cli.Client.TOOL;
import static io.dockstore.client.cli.Client.VERSION;
import static io.dockstore.client.cli.Client.WORKFLOW;
import static io.dockstore.client.cli.YamlVerifyUtility.YAML;
import static io.dockstore.common.CLICommonTestUtilities.checkToolList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.org.webcompere.systemstubs.SystemStubs.catchSystemExit;

/**
 * @author dyuen
 */
@Tag(ConfidentialTest.NAME)
@Tag(ToolTest.NAME)
@ExtendWith(SystemStubsExtension.class)
class ClientIT extends BaseIT {

    private static final String FIRST_TOOL = ResourceHelpers.resourceFilePath("dockstore-tool-helloworld.cwl");

    @SystemStub
    public final SystemOut systemOutRule = new SystemOut();

    @SystemStub
    public final SystemErr systemErrRule = new SystemErr();

    @BeforeEach
    @Override
    public void resetDBBetweenTests() throws Exception {
        CLICommonTestUtilities.cleanStatePrivate1(SUPPORT, testingPostgres);
        Client.DEBUG.set(false);
    }

    @Test
    void testListEntries() throws IOException, ApiException {
        Client.main(new String[] { CONFIG, TestUtility.getConfigFileLocation(true), TOOL, "list" });
        checkToolList(systemOutRule.getText());
    }

    @Test
    void testDebugModeListEntries() throws IOException, ApiException {
        Client.main(new String[] { DEBUG_FLAG, CONFIG, TestUtility.getConfigFileLocation(true), TOOL, "list" });
        checkToolList(systemOutRule.getText());
    }

    @Test
    void testListEntriesWithoutCreds() throws Exception {
        int exitCode = catchSystemExit(
                () -> Client.main(new String[] { CONFIG, TestUtility.getConfigFileLocation(false), TOOL, "list" }));
        assertEquals(Client.API_ERROR, exitCode);
    }

    @Test
    void testListEntriesOnWrongPort() throws Exception {
        int exitCode = catchSystemExit(() -> Client.main(new String[] { CONFIG, TestUtility.getConfigFileLocation(true, false, false), TOOL, "list" }));
        assertEquals(Client.CONNECTION_ERROR, exitCode);
    }

    //
    @Disabled("Won't work as entry must be valid")
    void quickRegisterValidEntry() throws IOException {
        Client.main(new String[] { CONFIG, TestUtility.getConfigFileLocation(true), TOOL, "publish", "quay.io/test_org/test6" });

        // verify DB
        final long count = testingPostgres.runSelectStatement("select count(*) from tool where name = 'test6'", long.class);
        assertEquals(1, count, "should see three entries");
    }

    @Test
    void testPluginEnable() {
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("pluginsTest1/configWithPlugins"), PLUGIN, "download" });
        systemOutRule.clear();
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("pluginsTest1/configWithPlugins"), PLUGIN, "list" });
        assertTrue(systemOutRule.getText().contains("dockstore-file-synapse-plugin"));
        assertTrue(systemOutRule.getText().contains("dockstore-file-s3-plugin"));
        assertFalse(systemOutRule.getText().contains("dockstore-icgc-storage-client-plugin"));
    }

    @Test
    void testPluginDisable() {
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("pluginsTest2/configWithPlugins"), PLUGIN, "download" });
        systemOutRule.clear();
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("pluginsTest2/configWithPlugins"), PLUGIN, "list" });
        assertFalse(systemOutRule.getText().contains("dockstore-file-synapse-plugin"));
        assertFalse(systemOutRule.getText().contains("dockstore-file-s3-plugin"));
        assertTrue(systemOutRule.getText().contains("dockstore-file-icgc-storage-client-plugin"));
    }

    @Disabled("seems to have been disabled for ages")
    void quickRegisterDuplicateEntry() throws IOException {
        Client.main(new String[] { CONFIG, TestUtility.getConfigFileLocation(true), TOOL, "publish", "quay.io/test_org/test6" });
        Client.main(
            new String[] { CONFIG, TestUtility.getConfigFileLocation(true), TOOL, "publish", "quay.io/test_org/test6", "view1" });
        Client.main(
            new String[] { CONFIG, TestUtility.getConfigFileLocation(true), TOOL, "publish", "quay.io/test_org/test6", "view2" });

        // verify DB
        final long count = testingPostgres.runSelectStatement("select count(*) from container where name = 'test6'", long.class);
        assertEquals(3, count, "should see three entries");
    }

    @Test
    void quickRegisterInValidEntry() throws Exception {
        int exitCode = catchSystemExit(() -> Client.main(new String[] { CONFIG, TestUtility.getConfigFileLocation(true), TOOL, "publish", "quay.io/test_org/test1" }));
        assertEquals(Client.CLIENT_ERROR, exitCode);
    }

    @Test
    void quickRegisterUnknownEntry() throws Exception {
        int exitCode = catchSystemExit(() -> Client.main(
                new String[] { CONFIG, TestUtility.getConfigFileLocation(true), TOOL, "publish",
                        "quay.io/funky_container_that_does_not_exist" }));
        assertEquals(Client.CLIENT_ERROR, exitCode);
    }

    /* When you manually publish on the dockstore CLI, it will now refresh the container after it is added.
     Since the below containers use dummy data and don't connect with Github/Bitbucket/Quay, the refresh will throw an error.
     Todo: Set up these tests with real data (not confidential)
     */
    @Disabled("Since dockstore now checks for associated tags for Quay container, manual publishing of nonexistant images won't work")
    void manualRegisterABunchOfValidEntries() throws IOException {
        Client.main(new String[] { CONFIG, TestUtility.getConfigFileLocation(true), TOOL, "manual_publish", "--registry",
            Registry.QUAY_IO.toString(), "--namespace", "pypi", "--name", "bd2k-python-lib", "--git-url",
            "git@github.com:funky-user/test2.git", "--git-reference", "refs/head/master" });
        Client.main(new String[] { CONFIG, TestUtility.getConfigFileLocation(true), TOOL, "manual_publish", "--registry",
            Registry.QUAY_IO.toString(), "--namespace", "pypi", "--name", "bd2k-python-lib", "--git-url",
            "git@github.com:funky-user/test2.git", "--git-reference", "refs/head/master", "--toolname", "test1" });
        Client.main(new String[] { CONFIG, TestUtility.getConfigFileLocation(true), TOOL, "manual_publish", "--registry",
            Registry.QUAY_IO.toString(), "--namespace", "pypi", "--name", "bd2k-python-lib", "--git-url",
            "git@github.com:funky-user/test2.git", "--git-reference", "refs/head/master", "--toolname", "test2" });
        Client.main(new String[] { CONFIG, TestUtility.getConfigFileLocation(true), TOOL, "manual_publish", "--registry",
            Registry.DOCKER_HUB.toString(), "--namespace", "pypi", "--name", "bd2k-python-lib", "--git-url",
            "git@github.com:funky-user/test2.git", "--git-reference", "refs/head/master" });
        Client.main(new String[] { CONFIG, TestUtility.getConfigFileLocation(true), TOOL, "manual_publish", "--registry",
            Registry.DOCKER_HUB.toString(), "--namespace", "pypi", "--name", "bd2k-python-lib", "--git-url",
            "git@github.com:funky-user/test2.git", "--git-reference", "refs/head/master", "--toolname", "test1" });

        // verify DB
        final long count = testingPostgres.runSelectStatement("select count(*) from container where name = 'bd2k-python-lib'", long.class);
        assertEquals(5, count, "should see three entries");
    }

    @Test
    void manualRegisterADuplicate() throws Exception {
        //TODO: this test is actually dying on the first command, I suspect that this has been broken for a while before migration to junit5
        int exitCode = catchSystemExit(() -> {
            Client.main(new String[] { CONFIG, TestUtility.getConfigFileLocation(true), TOOL, "manual_publish", "--registry",
                    Registry.QUAY_IO.name(), Registry.QUAY_IO.toString(), "--namespace", "pypi", "--name", "bd2k-python-lib", "--git-url",
                    "git@github.com:funky-user/test2.git", "--git-reference", "refs/head/master" });
            Client.main(new String[] { CONFIG, TestUtility.getConfigFileLocation(true), TOOL, "manual_publish", "--registry",
                    Registry.QUAY_IO.name(), Registry.QUAY_IO.toString(), "--namespace", "pypi", "--name", "bd2k-python-lib", "--git-url",
                    "git@github.com:funky-user/test2.git", "--git-reference", "refs/head/master", "--toolname", "test1" });
            Client.main(new String[] { CONFIG, TestUtility.getConfigFileLocation(true), TOOL, "manual_publish", "--registry",
                    Registry.QUAY_IO.name(), Registry.QUAY_IO.toString(), "--namespace", "pypi", "--name", "bd2k-python-lib", "--git-url",
                    "git@github.com:funky-user/test2.git", "--git-reference", "refs/head/master", "--toolname", "test1" });
        });
        assertEquals(Client.API_ERROR, exitCode);
    }

    @Test
    @Category(ToilCompatibleTest.class)
    void launchingCWLWorkflow() throws IOException {
        final String firstWorkflowCWL = ResourceHelpers.resourceFilePath("1st-workflow.cwl");
        final String firstWorkflowJSON = ResourceHelpers.resourceFilePath("1st-workflow-job.json");
        Client.main(new String[] { SCRIPT_FLAG, CONFIG, TestUtility.getConfigFileLocation(true), WORKFLOW, "launch", "--local-entry",
            firstWorkflowCWL, "--json", firstWorkflowJSON });
    }

    @Test
    @Category(ToilCompatibleTest.class)
    void launchingCWLToolWithRemoteParameters() throws IOException {
        Client.main(
            new String[] { SCRIPT_FLAG, CONFIG, TestUtility.getConfigFileLocation(true), TOOL, "launch", "--local-entry", FIRST_TOOL,
                "--json",
                "https://raw.githubusercontent.com/dockstore/dockstore/f343bcd6e4465a8ef790208f87740bd4d5a9a4da/dockstore-client/src/test/resources/test.cwl.json" });
    }

    @Test
    void testMetadataMethods() throws IOException {
        Client.main(new String[] { CONFIG, TestUtility.getConfigFileLocation(true), VERSION });
        assertTrue(systemOutRule.getText().contains("Dockstore version"));
        systemOutRule.clear();
        Client.main(new String[] { CONFIG, TestUtility.getConfigFileLocation(true), SERVER_METADATA });
        assertTrue(systemOutRule.getText().contains("version"));
        systemOutRule.clear();
    }

    @Test
    void testCacheCleaning() throws IOException {
        Client.main(new String[] { CONFIG, TestUtility.getConfigFileLocation(true), CLEAN_CACHE });
        systemOutRule.clear();
    }

    @Test
    void pluginDownload() throws IOException {
        Client.main(new String[] { CONFIG, TestUtility.getConfigFileLocation(true), PLUGIN, "download" });
    }

    /**
     * Tests the 'dockstore deps' command with a client version and python 3 version
     * Passes if the returned result contains 'avro-cwl' as its dependency and the other common dependencies
     *
     * @throws IOException
     */
    @Test
    void testDepsCommandWithVersionAndPython3() throws IOException {
        Client.main(
            new String[] { CONFIG, TestUtility.getConfigFileLocation(true), DEPS, "--client-version", "1.7.0", "--python-version",
                "3" });
        assertFalse(systemOutRule.getText().contains("monotonic=="));
        assertDepsCommandOutput();
    }

    /**
     * Tests the 'dockstore deps' command with an unrecognized runner
     * Passes if there was an error and log message
     *
     * @throws IOException
     */
    @Test
    @Disabled("Ignored until there are more than one runner.")
    void testDepsCommandWithUnknownRunners() throws Exception {
        int exitCode = catchSystemExit(() -> Client.main(new String[] { CONFIG, TestUtility.getConfigFileLocation(true), DEPS, "--runner", "cromwell" }));
        assertEquals(Client.API_ERROR, exitCode);
        assertTrue(systemOutRule.getText().contains("Could not get runner dependencies"));
        Client.main(new String[] { CONFIG, TestUtility.getConfigFileLocation(true), DEPS, "--runner", "cromwell" });
    }

    /**
     * Tests the 'dockstore deps' command with default and no additional flag
     * Passes if the returned result contains 'avro' as its dependency and the other common dependencies
     *
     * @throws IOException
     */
    @Test
    void testDepsCommand() throws IOException {
        Client.main(new String[] { CONFIG, TestUtility.getConfigFileLocation(true), DEPS });
        assertFalse(systemOutRule.getText().contains("monotonic=="), "Python 3 does not have monotonic as a dependency");
        assertDepsCommandOutput();
    }

    /**
     * Tests the 'dockstore deps --help' command.
     * Passes if it contains the right title
     *
     * @throws IOException
     */
    @Test
    void testDepsCommandHelp() throws IOException {
        Client.main(new String[] { CONFIG, TestUtility.getConfigFileLocation(true), DEPS, "HELP" });
        assertTrue(systemOutRule.getText().contains("Print cwltool runner dependencies"));
    }

    private void assertDepsCommandOutput() {
        // cwl-runner is an alias for cwlref-runner, both should be fine
        assertTrue(systemOutRule.getText().contains("cwlref-runner") || systemOutRule.getText().contains("cwl-runner"));
        assertTrue(systemOutRule.getText().contains("cwltool=="));
        assertTrue(systemOutRule.getText().contains("schema-salad=="));
        assertTrue(systemOutRule.getText().contains("ruamel.yaml=="));
        assertTrue(systemOutRule.getText().contains("requests=="));
    }

    @Test
    void touchOnAllHelpMessages() throws IOException {

        checkCommandForHelp(new String[] { TOOL, "search" });
        checkCommandForHelp(new String[] { TOOL, "info" });
        checkCommandForHelp(new String[] { TOOL, "cwl" });
        checkCommandForHelp(new String[] { TOOL, "wdl" });
        checkCommandForHelp(new String[] { TOOL, "label" });
        checkCommandForHelp(new String[] { TOOL, "test_parameter" });
        checkCommandForHelp(new String[] { TOOL, "convert" });
        checkCommandForHelp(new String[] { TOOL, "launch" });
        checkCommandForHelp(new String[] { TOOL, "version_tag" });
        checkCommandForHelp(new String[] { TOOL, "update_tool" });

        checkCommandForHelp(new String[] { TOOL, "convert", "entry2json" });
        checkCommandForHelp(new String[] { TOOL, "convert", "cwl2yaml" });
        checkCommandForHelp(new String[] { TOOL, "convert", "cwl2json" });
        checkCommandForHelp(new String[] { TOOL, "convert", "wdl2json" });

        checkCommandForHelp(new String[] {});
        checkCommandForHelp(new String[] { TOOL });
        checkCommandForHelp(new String[] { TOOL, "download", "HELP" });
        checkCommandForHelp(new String[] { TOOL, "list", "HELP" });
        checkCommandForHelp(new String[] { TOOL, "search", HELP });
        checkCommandForHelp(new String[] { TOOL, "publish", HELP });
        checkCommandForHelp(new String[] { TOOL, "info", HELP });
        checkCommandForHelp(new String[] { TOOL, "cwl", HELP });
        checkCommandForHelp(new String[] { TOOL, "wdl", HELP });
        checkCommandForHelp(new String[] { TOOL, "refresh", HELP });
        checkCommandForHelp(new String[] { TOOL, "label", HELP });
        checkCommandForHelp(new String[] { TOOL, "convert", HELP });
        checkCommandForHelp(new String[] { TOOL, "convert", "cwl2json", HELP });
        checkCommandForHelp(new String[] { TOOL, "convert", "cwl2yaml", HELP });
        checkCommandForHelp(new String[] { TOOL, "convert", "wdl2json", HELP });
        checkCommandForHelp(new String[] { TOOL, "convert", "entry2json", HELP });
        checkCommandForHelp(new String[] { TOOL, "launch", HELP });
        checkCommandForHelp(new String[] { TOOL, "version_tag", HELP });
        checkCommandForHelp(new String[] { TOOL, "version_tag", "remove", HELP });
        checkCommandForHelp(new String[] { TOOL, "version_tag", "update", HELP });
        checkCommandForHelp(new String[] { TOOL, "version_tag", "add", HELP });
        checkCommandForHelp(new String[] { TOOL, "update_tool", HELP });
        checkCommandForHelp(new String[] { TOOL, "manual_publish", HELP });
        checkCommandForHelp(new String[] { TOOL, "star", HELP });
        checkCommandForHelp(new String[] { TOOL, "test_parameter", HELP });
        checkCommandForHelp(new String[] { TOOL, "verify", HELP });
        checkCommandForHelp(new String[] { TOOL });

        checkCommandForHelp(new String[] { WORKFLOW, "convert", "entry2json" });
        checkCommandForHelp(new String[] { WORKFLOW, "convert", "cwl2yaml" });
        checkCommandForHelp(new String[] { WORKFLOW, "convert", "cwl2json" });
        checkCommandForHelp(new String[] { WORKFLOW, "convert", "wdl2json" });

        checkCommandForHelp(new String[] { WORKFLOW, "search" });
        checkCommandForHelp(new String[] { WORKFLOW, "info" });
        checkCommandForHelp(new String[] { WORKFLOW, "cwl" });
        checkCommandForHelp(new String[] { WORKFLOW, "wdl" });
        checkCommandForHelp(new String[] { WORKFLOW, "label" });
        checkCommandForHelp(new String[] { WORKFLOW, "test_parameter" });
        checkCommandForHelp(new String[] { WORKFLOW, "convert" });
        checkCommandForHelp(new String[] { WORKFLOW, "launch" });
        checkCommandForHelp(new String[] { WORKFLOW, "version_tag" });
        checkCommandForHelp(new String[] { WORKFLOW, "update_workflow" });
        checkCommandForHelp(new String[] { WORKFLOW, "restub" });

        checkCommandForHelp(new String[] { WORKFLOW, "download", HELP });
        checkCommandForHelp(new String[] { WORKFLOW, "list", HELP });
        checkCommandForHelp(new String[] { WORKFLOW, "search", HELP });
        checkCommandForHelp(new String[] { WORKFLOW, "publish", HELP });
        checkCommandForHelp(new String[] { WORKFLOW, "info", HELP });
        checkCommandForHelp(new String[] { WORKFLOW, "cwl", HELP });
        checkCommandForHelp(new String[] { WORKFLOW, "wdl", HELP });
        checkCommandForHelp(new String[] { WORKFLOW, "refresh", HELP });
        checkCommandForHelp(new String[] { WORKFLOW, "label", HELP });
        checkCommandForHelp(new String[] { WORKFLOW, "convert", HELP });
        checkCommandForHelp(new String[] { WORKFLOW, "convert", "cwl2json", HELP });
        checkCommandForHelp(new String[] { WORKFLOW, "convert", "cwl2yaml", HELP });
        checkCommandForHelp(new String[] { WORKFLOW, "convert", "wd2json", HELP });
        checkCommandForHelp(new String[] { WORKFLOW, "convert", "entry2json", HELP });
        checkCommandForHelp(new String[] { WORKFLOW, "launch", HELP });
        checkCommandForHelp(new String[] { WORKFLOW, "version_tag", HELP });
        checkCommandForHelp(new String[] { WORKFLOW, "update_workflow", HELP });
        checkCommandForHelp(new String[] { WORKFLOW, "manual_publish", HELP });
        checkCommandForHelp(new String[] { WORKFLOW, "restub", HELP });
        checkCommandForHelp(new String[] { WORKFLOW, "star", HELP });
        checkCommandForHelp(new String[] { WORKFLOW, "test_parameter", HELP });
        checkCommandForHelp(new String[] { WORKFLOW, "verify", HELP });
        checkCommandForHelp(new String[] { WORKFLOW });

        checkCommandForHelp(new String[] { PLUGIN, "list", HELP });
        checkCommandForHelp(new String[] { PLUGIN, "download", HELP });
        checkCommandForHelp(new String[] { PLUGIN });

        checkCommandForHelp(new String[] { CHECKER });
        checkCommandForHelp(new String[] { CHECKER, "download", HELP });
        checkCommandForHelp(new String[] { CHECKER, "launch", HELP });
        checkCommandForHelp(new String[] { CHECKER, "add", HELP });
        checkCommandForHelp(new String[] { CHECKER, "update", HELP });
        checkCommandForHelp(new String[] { CHECKER, "update_version", HELP });
        checkCommandForHelp(new String[] { CHECKER, "test_parameter", HELP });
    }

    private void checkCommandForHelp(String[] argv) throws IOException {
        final ArrayList<String> strings = Lists.newArrayList(argv);
        strings.add(CONFIG);
        strings.add(TestUtility.getConfigFileLocation(true));

        Client.main(strings.toArray(new String[0]));
        assertTrue(systemOutRule.getText().contains("Usage: dockstore"));
        systemOutRule.clear();
    }

    @Test
    public void noSuggestions() {
        List<String> acceptedCommands = new ArrayList<String>();
        acceptedCommands.add(TOOL);
        acceptedCommands.add(WORKFLOW);
        acceptedCommands.add(CHECKER);
        acceptedCommands.add(PLUGIN);
        acceptedCommands.add(DEPS);
        acceptedCommands.add(YAML);
        ArgumentUtility.invalid("", "z", acceptedCommands);
        System.out.println("HELLO");
        String output = systemOutRule.getText();
        Assertions.assertEquals("test", output);
    }

}
