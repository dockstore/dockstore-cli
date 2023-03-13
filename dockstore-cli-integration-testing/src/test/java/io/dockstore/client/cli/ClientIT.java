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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

import static io.dockstore.client.cli.ArgumentUtility.CONVERT;
import static io.dockstore.client.cli.ArgumentUtility.DOWNLOAD;
import static io.dockstore.client.cli.ArgumentUtility.LAUNCH;
import static io.dockstore.client.cli.CheckerClient.ADD;
import static io.dockstore.client.cli.CheckerClient.UPDATE;
import static io.dockstore.client.cli.CheckerClient.UPDATE_VERSION;
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
import static io.dockstore.client.cli.Client.UPGRADE;
import static io.dockstore.client.cli.Client.VERSION;
import static io.dockstore.client.cli.Client.WORKFLOW;
import static io.dockstore.client.cli.nested.AbstractEntryClient.CANCEL;
import static io.dockstore.client.cli.nested.AbstractEntryClient.CWL_2_JSON;
import static io.dockstore.client.cli.nested.AbstractEntryClient.CWL_2_YAML;
import static io.dockstore.client.cli.nested.AbstractEntryClient.ENTRY_2_JSON;
import static io.dockstore.client.cli.nested.AbstractEntryClient.INFO;
import static io.dockstore.client.cli.nested.AbstractEntryClient.LABEL;
import static io.dockstore.client.cli.nested.AbstractEntryClient.LIST;
import static io.dockstore.client.cli.nested.AbstractEntryClient.LOGS;
import static io.dockstore.client.cli.nested.AbstractEntryClient.MANUAL_PUBLISH;
import static io.dockstore.client.cli.nested.AbstractEntryClient.PUBLISH;
import static io.dockstore.client.cli.nested.AbstractEntryClient.REFRESH;
import static io.dockstore.client.cli.nested.AbstractEntryClient.SEARCH;
import static io.dockstore.client.cli.nested.AbstractEntryClient.SERVICE_INFO;
import static io.dockstore.client.cli.nested.AbstractEntryClient.STAR;
import static io.dockstore.client.cli.nested.AbstractEntryClient.STATUS;
import static io.dockstore.client.cli.nested.AbstractEntryClient.TEST_PARAMETER;
import static io.dockstore.client.cli.nested.AbstractEntryClient.VERIFY;
import static io.dockstore.client.cli.nested.AbstractEntryClient.WDL_2_JSON;
import static io.dockstore.client.cli.nested.DepCommand.CLIENT_VERSION;
import static io.dockstore.client.cli.nested.DepCommand.PYTHON_VERSION;
import static io.dockstore.client.cli.nested.ToolClient.UPDATE_TOOL;
import static io.dockstore.client.cli.nested.ToolClient.VERSION_TAG;
import static io.dockstore.client.cli.nested.WesCommandParser.ID;
import static io.dockstore.client.cli.nested.WesCommandParser.INLINE_WORKFLOW;
import static io.dockstore.client.cli.nested.WesCommandParser.JSON;
import static io.dockstore.client.cli.nested.WesCommandParser.PAGE_TOKEN;
import static io.dockstore.client.cli.nested.WesCommandParser.WES_URL;
import static io.dockstore.client.cli.nested.WorkflowClient.UPDATE_WORKFLOW;
import static io.dockstore.common.CLICommonTestUtilities.checkToolList;
import static io.github.collaboratory.cwl.CWLClient.WES;
import static java.lang.String.join;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.org.webcompere.systemstubs.SystemStubs.catchSystemExit;

/**
 * @author dyuen
 */
@Tag(ConfidentialTest.NAME)
@Tag(ToolTest.NAME)
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
        Client.main(new String[] { CONFIG, TestUtility.getConfigFileLocation(true), TOOL, LIST });
        checkToolList(systemOutRule.getText());
    }

    @Test
    void testDebugModeListEntries() throws IOException, ApiException {
        Client.main(new String[] { DEBUG_FLAG, CONFIG, TestUtility.getConfigFileLocation(true), TOOL, LIST });
        checkToolList(systemOutRule.getText());
    }

    @Test
    void testListEntriesWithoutCreds() throws Exception {
        int exitCode = catchSystemExit(
                () -> Client.main(new String[] { CONFIG, TestUtility.getConfigFileLocation(false), TOOL, LIST }));
        assertEquals(Client.API_ERROR, exitCode);
    }

    @Test
    void testListEntriesOnWrongPort() throws Exception {
        int exitCode = catchSystemExit(() -> Client.main(new String[] { CONFIG, TestUtility.getConfigFileLocation(true, false, false), TOOL, LIST }));
        assertEquals(Client.CONNECTION_ERROR, exitCode);
    }

    //
    @Disabled("Won't work as entry must be valid")
    void quickRegisterValidEntry() throws IOException {
        Client.main(new String[] { CONFIG, TestUtility.getConfigFileLocation(true), TOOL, PUBLISH, "quay.io/test_org/test6" });

        // verify DB
        final long count = testingPostgres.runSelectStatement("select count(*) from tool where name = 'test6'", long.class);
        assertEquals(1, count, "should see three entries");
    }

    @Test
    void testPluginEnable() {
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("pluginsTest1/configWithPlugins"), PLUGIN, DOWNLOAD });
        systemOutRule.clear();
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("pluginsTest1/configWithPlugins"), PLUGIN, LIST });
        assertTrue(systemOutRule.getText().contains("dockstore-file-synapse-plugin"));
        assertTrue(systemOutRule.getText().contains("dockstore-file-s3-plugin"));
        assertFalse(systemOutRule.getText().contains("dockstore-icgc-storage-client-plugin"));
    }

    @Test
    void testPluginDisable() {
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("pluginsTest2/configWithPlugins"), PLUGIN, DOWNLOAD });
        systemOutRule.clear();
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("pluginsTest2/configWithPlugins"), PLUGIN, LIST });
        assertFalse(systemOutRule.getText().contains("dockstore-file-synapse-plugin"));
        assertFalse(systemOutRule.getText().contains("dockstore-file-s3-plugin"));
        assertTrue(systemOutRule.getText().contains("dockstore-file-icgc-storage-client-plugin"));
    }

    @Disabled("seems to have been disabled for ages")
    void quickRegisterDuplicateEntry() throws IOException {
        Client.main(new String[] { CONFIG, TestUtility.getConfigFileLocation(true), TOOL, PUBLISH, "quay.io/test_org/test6" });
        Client.main(
            new String[] { CONFIG, TestUtility.getConfigFileLocation(true), TOOL, PUBLISH, "quay.io/test_org/test6", "view1" });
        Client.main(
            new String[] { CONFIG, TestUtility.getConfigFileLocation(true), TOOL, PUBLISH, "quay.io/test_org/test6", "view2" });

        // verify DB
        final long count = testingPostgres.runSelectStatement("select count(*) from container where name = 'test6'", long.class);
        assertEquals(3, count, "should see three entries");
    }

    @Test
    void quickRegisterInValidEntry() throws Exception {
        int exitCode = catchSystemExit(() -> Client.main(new String[] { CONFIG, TestUtility.getConfigFileLocation(true), TOOL, PUBLISH, "quay.io/test_org/test1" }));
        assertEquals(Client.CLIENT_ERROR, exitCode);
    }

    @Test
    void quickRegisterUnknownEntry() throws Exception {
        int exitCode = catchSystemExit(() -> Client.main(
                new String[] { CONFIG, TestUtility.getConfigFileLocation(true), TOOL, PUBLISH,
                        "quay.io/funky_container_that_does_not_exist" }));
        assertEquals(Client.CLIENT_ERROR, exitCode);
    }

    /* When you manually publish on the dockstore CLI, it will now refresh the container after it is added.
     Since the below containers use dummy data and don't connect with Github/Bitbucket/Quay, the refresh will throw an error.
     Todo: Set up these tests with real data (not confidential)
     */
    @Disabled("Since dockstore now checks for associated tags for Quay container, manual publishing of nonexistent images won't work")
    void manualRegisterABunchOfValidEntries() throws IOException {
        Client.main(new String[] { CONFIG, TestUtility.getConfigFileLocation(true), TOOL, MANUAL_PUBLISH, "--registry",
            Registry.QUAY_IO.toString(), "--namespace", "pypi", "--name", "bd2k-python-lib", "--git-url",
            "git@github.com:funky-user/test2.git", "--git-reference", "refs/head/master" });
        Client.main(new String[] { CONFIG, TestUtility.getConfigFileLocation(true), TOOL, MANUAL_PUBLISH, "--registry",
            Registry.QUAY_IO.toString(), "--namespace", "pypi", "--name", "bd2k-python-lib", "--git-url",
            "git@github.com:funky-user/test2.git", "--git-reference", "refs/head/master", "--toolname", "test1" });
        Client.main(new String[] { CONFIG, TestUtility.getConfigFileLocation(true), TOOL, MANUAL_PUBLISH, "--registry",
            Registry.QUAY_IO.toString(), "--namespace", "pypi", "--name", "bd2k-python-lib", "--git-url",
            "git@github.com:funky-user/test2.git", "--git-reference", "refs/head/master", "--toolname", "test2" });
        Client.main(new String[] { CONFIG, TestUtility.getConfigFileLocation(true), TOOL, MANUAL_PUBLISH, "--registry",
            Registry.DOCKER_HUB.toString(), "--namespace", "pypi", "--name", "bd2k-python-lib", "--git-url",
            "git@github.com:funky-user/test2.git", "--git-reference", "refs/head/master" });
        Client.main(new String[] { CONFIG, TestUtility.getConfigFileLocation(true), TOOL, MANUAL_PUBLISH, "--registry",
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
            Client.main(new String[] { CONFIG, TestUtility.getConfigFileLocation(true), TOOL, MANUAL_PUBLISH, "--registry",
                    Registry.QUAY_IO.name(), Registry.QUAY_IO.toString(), "--namespace", "pypi", "--name", "bd2k-python-lib", "--git-url",
                    "git@github.com:funky-user/test2.git", "--git-reference", "refs/head/master" });
            Client.main(new String[] { CONFIG, TestUtility.getConfigFileLocation(true), TOOL, MANUAL_PUBLISH, "--registry",
                    Registry.QUAY_IO.name(), Registry.QUAY_IO.toString(), "--namespace", "pypi", "--name", "bd2k-python-lib", "--git-url",
                    "git@github.com:funky-user/test2.git", "--git-reference", "refs/head/master", "--toolname", "test1" });
            Client.main(new String[] { CONFIG, TestUtility.getConfigFileLocation(true), TOOL, MANUAL_PUBLISH, "--registry",
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
        Client.main(new String[] { SCRIPT_FLAG, CONFIG, TestUtility.getConfigFileLocation(true), WORKFLOW, LAUNCH, "--local-entry",
            firstWorkflowCWL, JSON, firstWorkflowJSON });
    }

    @Test
    @Category(ToilCompatibleTest.class)
    void launchingCWLToolWithRemoteParameters() throws IOException {
        Client.main(
            new String[] { SCRIPT_FLAG, CONFIG, TestUtility.getConfigFileLocation(true), TOOL, LAUNCH, "--local-entry", FIRST_TOOL,
                JSON,
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
        Client.main(new String[] { CONFIG, TestUtility.getConfigFileLocation(true), PLUGIN, DOWNLOAD });
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
            new String[] { CONFIG, TestUtility.getConfigFileLocation(true), DEPS, CLIENT_VERSION, "1.7.0", PYTHON_VERSION,
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
        Client.main(new String[] { CONFIG, TestUtility.getConfigFileLocation(true), DEPS, HELP });
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

        checkCommandForHelp(new String[] { TOOL, SEARCH }, false);
        checkCommandForHelp(new String[] { TOOL, INFO }, false);
        checkCommandForHelp(new String[] { TOOL, "CWL" }, false);
        checkCommandForHelp(new String[] { TOOL, "WDL" }, false);
        checkCommandForHelp(new String[] { TOOL, LABEL }, false);
        checkCommandForHelp(new String[] { TOOL, TEST_PARAMETER }, false);
        checkCommandForHelp(new String[] { TOOL, CONVERT }, false);
        checkCommandForHelp(new String[] { TOOL, LAUNCH }, false);
        checkCommandForHelp(new String[] { TOOL, VERSION_TAG }, false);
        checkCommandForHelp(new String[] { TOOL, UPDATE_TOOL }, false);

        checkCommandForHelp(new String[] { TOOL, CONVERT, ENTRY_2_JSON }, false);
        checkCommandForHelp(new String[] { TOOL, CONVERT, CWL_2_YAML }, false);
        checkCommandForHelp(new String[] { TOOL, CONVERT, CWL_2_JSON }, false);
        checkCommandForHelp(new String[] { TOOL, CONVERT, WDL_2_JSON }, false);

        checkCommandForHelp(new String[] {}, false);
        checkCommandForHelp(new String[] { TOOL }, false);
        checkCommandForHelp(new String[] { TOOL, DOWNLOAD }, true);
        checkCommandForHelp(new String[] { TOOL, LIST }, true);
        checkCommandForHelp(new String[] { TOOL, SEARCH }, true);
        checkCommandForHelp(new String[] { TOOL, PUBLISH }, true);
        checkCommandForHelp(new String[] { TOOL, INFO }, true);
        checkCommandForHelp(new String[] { TOOL, "CWL" }, true);
        checkCommandForHelp(new String[] { TOOL, "WDL" }, true);
        checkCommandForHelp(new String[] { TOOL, REFRESH }, true);
        checkCommandForHelp(new String[] { TOOL, LABEL }, true);
        checkCommandForHelp(new String[] { TOOL, CONVERT }, true);
        checkCommandForHelp(new String[] { TOOL, CONVERT, CWL_2_JSON }, true);
        checkCommandForHelp(new String[] { TOOL, CONVERT, CWL_2_YAML }, true);
        checkCommandForHelp(new String[] { TOOL, CONVERT, WDL_2_JSON }, true);
        checkCommandForHelp(new String[] { TOOL, CONVERT, ENTRY_2_JSON }, true);
        checkCommandForHelp(new String[] { TOOL, LAUNCH }, true);
        checkCommandForHelp(new String[] { TOOL, VERSION_TAG }, true);
        checkCommandForHelp(new String[] { TOOL, VERSION_TAG, "remove" }, true);
        checkCommandForHelp(new String[] { TOOL, VERSION_TAG, UPDATE }, true);
        checkCommandForHelp(new String[] { TOOL, VERSION_TAG, ADD }, true);
        checkCommandForHelp(new String[] { TOOL, UPDATE_TOOL }, true);
        checkCommandForHelp(new String[] { TOOL, MANUAL_PUBLISH }, true);
        checkCommandForHelp(new String[] { TOOL, STAR }, true);
        checkCommandForHelp(new String[] { TOOL, TEST_PARAMETER }, true);
        checkCommandForHelp(new String[] { TOOL, VERIFY }, true);
        checkCommandForHelp(new String[] { TOOL }, false);

        checkCommandForHelp(new String[] { WORKFLOW, CONVERT, ENTRY_2_JSON }, false);
        checkCommandForHelp(new String[] { WORKFLOW, CONVERT, CWL_2_YAML }, false);
        checkCommandForHelp(new String[] { WORKFLOW, CONVERT, CWL_2_JSON }, false);
        checkCommandForHelp(new String[] { WORKFLOW, CONVERT, WDL_2_JSON }, false);

        checkCommandForHelp(new String[] { WORKFLOW, SEARCH }, false);
        checkCommandForHelp(new String[] { WORKFLOW, INFO }, false);
        checkCommandForHelp(new String[] { WORKFLOW, "CWL" }, false);
        checkCommandForHelp(new String[] { WORKFLOW, "WDL" }, false);
        checkCommandForHelp(new String[] { WORKFLOW, LABEL }, false);
        checkCommandForHelp(new String[] { WORKFLOW, TEST_PARAMETER }, false);
        checkCommandForHelp(new String[] { WORKFLOW, CONVERT }, false);
        checkCommandForHelp(new String[] { WORKFLOW, LAUNCH }, false);
        checkCommandForHelp(new String[] { WORKFLOW, VERSION_TAG }, false);
        checkCommandForHelp(new String[] { WORKFLOW, UPDATE_WORKFLOW }, false);
        checkCommandForHelp(new String[] { WORKFLOW, "restub" }, false);

        checkCommandForHelp(new String[] { WORKFLOW, DOWNLOAD }, true);
        checkCommandForHelp(new String[] { WORKFLOW, LIST }, true);
        checkCommandForHelp(new String[] { WORKFLOW, SEARCH }, true);
        checkCommandForHelp(new String[] { WORKFLOW, PUBLISH }, true);
        checkCommandForHelp(new String[] { WORKFLOW, INFO }, true);
        checkCommandForHelp(new String[] { WORKFLOW, "CWL" }, true);
        checkCommandForHelp(new String[] { WORKFLOW, "WDL" }, true);
        checkCommandForHelp(new String[] { WORKFLOW, REFRESH }, true);
        checkCommandForHelp(new String[] { WORKFLOW, LABEL }, true);
        checkCommandForHelp(new String[] { WORKFLOW, CONVERT }, true);
        checkCommandForHelp(new String[] { WORKFLOW, CONVERT, CWL_2_JSON }, true);
        checkCommandForHelp(new String[] { WORKFLOW, CONVERT, CWL_2_YAML }, true);
        checkCommandForHelp(new String[] { WORKFLOW, CONVERT, "wdl2json" }, true);
        checkCommandForHelp(new String[] { WORKFLOW, CONVERT, ENTRY_2_JSON }, true);
        checkCommandForHelp(new String[] { WORKFLOW, LAUNCH }, true);
        checkCommandForHelp(new String[] { WORKFLOW, VERSION_TAG }, true);
        checkCommandForHelp(new String[] { WORKFLOW, UPDATE_WORKFLOW }, true);
        checkCommandForHelp(new String[] { WORKFLOW, MANUAL_PUBLISH }, true);
        checkCommandForHelp(new String[] { WORKFLOW, "restub" }, true);
        checkCommandForHelp(new String[] { WORKFLOW, STAR }, true);
        checkCommandForHelp(new String[] { WORKFLOW, TEST_PARAMETER }, true);
        checkCommandForHelp(new String[] { WORKFLOW, VERIFY }, true);
        checkCommandForHelp(new String[] { WORKFLOW }, false);

        checkCommandForHelp(new String[] { PLUGIN, LIST }, true);
        checkCommandForHelp(new String[] { PLUGIN, DOWNLOAD }, true);
        checkCommandForHelp(new String[] { PLUGIN }, false);

        checkCommandForHelp(new String[] { CHECKER }, false);
        checkCommandForHelp(new String[] { CHECKER, DOWNLOAD }, true);
        checkCommandForHelp(new String[] { CHECKER, LAUNCH }, true);
        checkCommandForHelp(new String[] { CHECKER, ADD }, true);
        checkCommandForHelp(new String[] { CHECKER, UPDATE }, true);
        checkCommandForHelp(new String[] { CHECKER, UPDATE_VERSION }, true);
        checkCommandForHelp(new String[] { CHECKER, TEST_PARAMETER }, true);

    }

    /**
     * This method passes all the values in argv and --help if includeHelpFlag is true to Client.main and ensures
     * that the output contains,
     * 1) "Usage: dockstore", this ensures that some help page is being displayed
     * 2) "dockstore" followed by the strings in argv, for example, if argv = {"test", "blue", "green"}, it would ensure
     * that the output contained the string "dockstore test blue green"
     *
     * @param argv the arguments to pass to Client.main, do not pass "--help"
     * @param includeHelpFlag If true, "--help" will be passed to Client.main after the arguments in argv
     * @throws IOException
     */
    private void checkCommandForHelp(String[] argv, Boolean includeHelpFlag) throws IOException {
        final ArrayList<String> strings = Lists.newArrayList(argv);
        if (includeHelpFlag) {
            strings.add(HELP);
        }
        strings.add(CONFIG);
        strings.add(TestUtility.getConfigFileLocation(true));

        Client.main(strings.toArray(new String[0]));
        assertTrue(systemOutRule.getText().contains("Usage: dockstore"), "'Usage: dockstore' isn't being displayed, this probably means" +
                "that no help page is being shown");
        assertTrue(systemOutRule.getText().contains("dockstore " + join(" ", argv)), "`Usage: dockstore` is being displayed" +
                "but it does not contain the strings found in argv, this probably means the wrong help page is being displayed");
        systemOutRule.clear();
    }


    @Test
    void testAllValidCommandsWithSlightPermutation() throws IOException {
        checkSuggestionIsGiven(new String[] { }, CHECKER);
        checkSuggestionIsGiven(new String[] { }, PLUGIN);
        checkSuggestionIsGiven(new String[] { }, UPGRADE);
        checkSuggestionIsGiven(new String[] { }, HELP);

        checkSuggestionIsGiven(new String[] {TOOL}, CONVERT);
        checkSuggestionIsGiven(new String[] {TOOL}, TEST_PARAMETER);
        checkSuggestionIsGiven(new String[] {TOOL}, HELP);

        checkSuggestionIsGiven(new String[] {WORKFLOW}, CONVERT);
        checkSuggestionIsGiven(new String[] {WORKFLOW}, TEST_PARAMETER);
        checkSuggestionIsGiven(new String[] {WORKFLOW}, HELP);

        checkSuggestionIsGiven(new String[] {TOOL, CONVERT}, HELP);
        checkSuggestionIsGiven(new String[] {TOOL, CONVERT}, CWL_2_JSON);

        checkSuggestionIsGiven(new String[] {WORKFLOW, CONVERT}, HELP);
        checkSuggestionIsGiven(new String[] {WORKFLOW, CONVERT}, CWL_2_JSON);

        checkSuggestionIsGiven(new String[] {WORKFLOW, WES}, LAUNCH);
        checkSuggestionIsGiven(new String[] {WORKFLOW, WES}, STATUS);

        checkSuggestionIsGiven(new String[] {WORKFLOW, WES, LAUNCH}, INLINE_WORKFLOW);
        checkSuggestionIsGiven(new String[] {WORKFLOW, WES, STATUS}, ID);
        checkSuggestionIsGiven(new String[] {WORKFLOW, WES, LOGS}, ID);
        checkSuggestionIsGiven(new String[] {WORKFLOW, WES, CANCEL}, ID);
        checkSuggestionIsGiven(new String[] {WORKFLOW, WES, SERVICE_INFO}, WES_URL);
        checkSuggestionIsGiven(new String[] {WORKFLOW, WES, LIST}, PAGE_TOKEN);

        checkSuggestionIsGiven(new String[] {PLUGIN}, HELP);
        checkSuggestionIsGiven(new String[] {PLUGIN}, LIST);
        checkSuggestionIsGiven(new String[] {PLUGIN}, DOWNLOAD);

        checkSuggestionIsGiven(new String[] {DEPS}, CLIENT_VERSION);
        checkSuggestionIsGiven(new String[] {DEPS}, HELP);

        checkSuggestionIsGiven(new String[] {CHECKER}, HELP);
        checkSuggestionIsGiven(new String[] {CHECKER}, DOWNLOAD);

    }

    /** This method performs two tests, in the first test commandToBeTested is capitalised, and it ensures that
     * commandToBeTested is given as a suggestion. In the second test, the first letter of commandToBeTested is changed,
     * and it ensures that commandToBeTested is given as a suggestion.
     *
     * @param validCommands A list of commands that are valid (these commands will not be modified by this method)
     * @param commandToBeTested A command that is valid, but will be modified to become invalid by this method
     */
    private void checkSuggestionIsGiven(String[] validCommands, String commandToBeTested) throws IOException {
        String formattedValidCommandString = join(" ", validCommands);
        if (!formattedValidCommandString.isBlank()) {
            formattedValidCommandString = " " + formattedValidCommandString;
        }

        final List<String> commandsForCapitalizationTest = Lists.newArrayList(validCommands);
        commandsForCapitalizationTest.add(commandToBeTested.toUpperCase());
        commandsForCapitalizationTest.add(CONFIG);
        commandsForCapitalizationTest.add(TestUtility.getConfigFileLocation(true));

        systemOutRule.clear();
        Client.main(commandsForCapitalizationTest.toArray(new String[0]));
        assertTrue(systemOutRule.getText().contains("dockstore" + formattedValidCommandString + ": '" + commandToBeTested.toUpperCase()
                        + "' is not a dockstore command. See 'dockstore" + formattedValidCommandString + " " + HELP
                        + "'.\n\n" + "The most similar command is:\n    " + commandToBeTested + "\n"));

        systemOutRule.clear();

        final List<String> commandsForLetterChangeTest = Lists.newArrayList(validCommands);
        String commandWithLetterChanged;
        if (commandToBeTested.startsWith("z")) {
            commandWithLetterChanged = "a" + commandToBeTested.substring(1);
        } else {
            commandWithLetterChanged = "z" + commandToBeTested.substring(1);
        }

        commandsForLetterChangeTest.add(commandWithLetterChanged);
        commandsForLetterChangeTest.add(CONFIG);
        commandsForLetterChangeTest.add(TestUtility.getConfigFileLocation(true));
        Client.main(commandsForLetterChangeTest.toArray(new String[0]));
        assertTrue(systemOutRule.getText().contains("dockstore" + formattedValidCommandString + ": '" + commandWithLetterChanged
                        + "' is not a dockstore command. See 'dockstore" + formattedValidCommandString + " " + HELP
                        + "'.\n\n" + "The most similar command is:\n    " + commandToBeTested + "\n"));

    }


}
