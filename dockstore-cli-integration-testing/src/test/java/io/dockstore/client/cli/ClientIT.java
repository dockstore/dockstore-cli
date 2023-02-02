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
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

import static io.dockstore.client.cli.ArgumentUtility.CONVERT;
import static io.dockstore.client.cli.ArgumentUtility.DOWNLOAD;
import static io.dockstore.client.cli.ArgumentUtility.LAUNCH;
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
    @Disabled("Since dockstore now checks for associated tags for Quay container, manual publishing of nonexistant images won't work")
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

        checkCommandForHelp(new String[] { TOOL, SEARCH });
        checkCommandForHelp(new String[] { TOOL, INFO });
        checkCommandForHelp(new String[] { TOOL, "cwl" });
        checkCommandForHelp(new String[] { TOOL, "wdl" });
        checkCommandForHelp(new String[] { TOOL, LABEL });
        checkCommandForHelp(new String[] { TOOL, TEST_PARAMETER });
        checkCommandForHelp(new String[] { TOOL, CONVERT });
        checkCommandForHelp(new String[] { TOOL, LAUNCH });
        checkCommandForHelp(new String[] { TOOL, VERSION_TAG });
        checkCommandForHelp(new String[] { TOOL, UPDATE_TOOL });

        checkCommandForHelp(new String[] { TOOL, CONVERT, ENTRY_2_JSON });
        checkCommandForHelp(new String[] { TOOL, CONVERT, CWL_2_YAML });
        checkCommandForHelp(new String[] { TOOL, CONVERT, CWL_2_JSON });
        checkCommandForHelp(new String[] { TOOL, CONVERT, WDL_2_JSON });

        checkCommandForHelp(new String[] {});
        checkCommandForHelp(new String[] { TOOL });
        checkCommandForHelp(new String[] { TOOL, DOWNLOAD, HELP });
        checkCommandForHelp(new String[] { TOOL, LIST, HELP });
        checkCommandForHelp(new String[] { TOOL, SEARCH, HELP });
        checkCommandForHelp(new String[] { TOOL, PUBLISH, HELP });
        checkCommandForHelp(new String[] { TOOL, INFO, HELP });
        checkCommandForHelp(new String[] { TOOL, "cwl", HELP });
        checkCommandForHelp(new String[] { TOOL, "wdl", HELP });
        checkCommandForHelp(new String[] { TOOL, REFRESH, HELP });
        checkCommandForHelp(new String[] { TOOL, LABEL, HELP });
        checkCommandForHelp(new String[] { TOOL, CONVERT, HELP });
        checkCommandForHelp(new String[] { TOOL, CONVERT, CWL_2_JSON, HELP });
        checkCommandForHelp(new String[] { TOOL, CONVERT, CWL_2_YAML, HELP });
        checkCommandForHelp(new String[] { TOOL, CONVERT, WDL_2_JSON, HELP });
        checkCommandForHelp(new String[] { TOOL, CONVERT, ENTRY_2_JSON, HELP });
        checkCommandForHelp(new String[] { TOOL, LAUNCH, HELP });
        checkCommandForHelp(new String[] { TOOL, VERSION_TAG, HELP });
        checkCommandForHelp(new String[] { TOOL, VERSION_TAG, "remove", HELP });
        checkCommandForHelp(new String[] { TOOL, VERSION_TAG, "update", HELP });
        checkCommandForHelp(new String[] { TOOL, VERSION_TAG, "add", HELP });
        checkCommandForHelp(new String[] { TOOL, UPDATE_TOOL, HELP });
        checkCommandForHelp(new String[] { TOOL, MANUAL_PUBLISH, HELP });
        checkCommandForHelp(new String[] { TOOL, STAR, HELP });
        checkCommandForHelp(new String[] { TOOL, TEST_PARAMETER, HELP });
        checkCommandForHelp(new String[] { TOOL, VERIFY, HELP });
        checkCommandForHelp(new String[] { TOOL });

        checkCommandForHelp(new String[] { WORKFLOW, CONVERT, ENTRY_2_JSON });
        checkCommandForHelp(new String[] { WORKFLOW, CONVERT, CWL_2_YAML });
        checkCommandForHelp(new String[] { WORKFLOW, CONVERT, CWL_2_JSON });
        checkCommandForHelp(new String[] { WORKFLOW, CONVERT, WDL_2_JSON });

        checkCommandForHelp(new String[] { WORKFLOW, SEARCH });
        checkCommandForHelp(new String[] { WORKFLOW, INFO });
        checkCommandForHelp(new String[] { WORKFLOW, "cwl" });
        checkCommandForHelp(new String[] { WORKFLOW, "wdl" });
        checkCommandForHelp(new String[] { WORKFLOW, LABEL });
        checkCommandForHelp(new String[] { WORKFLOW, TEST_PARAMETER });
        checkCommandForHelp(new String[] { WORKFLOW, CONVERT });
        checkCommandForHelp(new String[] { WORKFLOW, LAUNCH });
        checkCommandForHelp(new String[] { WORKFLOW, VERSION_TAG });
        checkCommandForHelp(new String[] { WORKFLOW, UPDATE_WORKFLOW });
        checkCommandForHelp(new String[] { WORKFLOW, "restub" });

        checkCommandForHelp(new String[] { WORKFLOW, DOWNLOAD, HELP });
        checkCommandForHelp(new String[] { WORKFLOW, LIST, HELP });
        checkCommandForHelp(new String[] { WORKFLOW, SEARCH, HELP });
        checkCommandForHelp(new String[] { WORKFLOW, PUBLISH, HELP });
        checkCommandForHelp(new String[] { WORKFLOW, INFO, HELP });
        checkCommandForHelp(new String[] { WORKFLOW, "cwl", HELP });
        checkCommandForHelp(new String[] { WORKFLOW, "wdl", HELP });
        checkCommandForHelp(new String[] { WORKFLOW, REFRESH, HELP });
        checkCommandForHelp(new String[] { WORKFLOW, LABEL, HELP });
        checkCommandForHelp(new String[] { WORKFLOW, CONVERT, HELP });
        checkCommandForHelp(new String[] { WORKFLOW, CONVERT, CWL_2_JSON, HELP });
        checkCommandForHelp(new String[] { WORKFLOW, CONVERT, CWL_2_YAML, HELP });
        checkCommandForHelp(new String[] { WORKFLOW, CONVERT, "wd2json", HELP });
        checkCommandForHelp(new String[] { WORKFLOW, CONVERT, ENTRY_2_JSON, HELP });
        checkCommandForHelp(new String[] { WORKFLOW, LAUNCH, HELP });
        checkCommandForHelp(new String[] { WORKFLOW, VERSION_TAG, HELP });
        checkCommandForHelp(new String[] { WORKFLOW, UPDATE_WORKFLOW, HELP });
        checkCommandForHelp(new String[] { WORKFLOW, MANUAL_PUBLISH, HELP });
        checkCommandForHelp(new String[] { WORKFLOW, "restub", HELP });
        checkCommandForHelp(new String[] { WORKFLOW, STAR, HELP });
        checkCommandForHelp(new String[] { WORKFLOW, TEST_PARAMETER, HELP });
        checkCommandForHelp(new String[] { WORKFLOW, VERIFY, HELP });
        checkCommandForHelp(new String[] { WORKFLOW });

        checkCommandForHelp(new String[] { PLUGIN, LIST, HELP });
        checkCommandForHelp(new String[] { PLUGIN, DOWNLOAD, HELP });
        checkCommandForHelp(new String[] { PLUGIN });

        checkCommandForHelp(new String[] { CHECKER });
        checkCommandForHelp(new String[] { CHECKER, DOWNLOAD, HELP });
        checkCommandForHelp(new String[] { CHECKER, LAUNCH, HELP });
        checkCommandForHelp(new String[] { CHECKER, "add", HELP });
        checkCommandForHelp(new String[] { CHECKER, "update", HELP });
        checkCommandForHelp(new String[] { CHECKER, "update_version", HELP });
        checkCommandForHelp(new String[] { CHECKER, TEST_PARAMETER, HELP });

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
    public void touchAllInvalidCommands() throws IOException {
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

    }

    /** This method performs two tests, in the first test commandToBeTested is capitalised, and it ensures that
     * commandToBeTested is given as a suggestion. In the second test, the first letter of commandToBeTested is changed,
     * and it ensures that commandToBeTested is given as a suggestion.
     *
     * @param validCommands A list of commands that are valid (these commands will not be modified by this method)
     * @param commandToBeTested A command that is valid, but will be modified to become invalid by this method
     */
    private void checkSuggestionIsGiven(String[] validCommands, String commandToBeTested) throws IOException {
        String formmatedValidCommandString = "";
        for (String command: validCommands) {
            formmatedValidCommandString += " ";
            formmatedValidCommandString += command;
        }

        final ArrayList<String> commandsForCapitalizationTest = Lists.newArrayList(validCommands);
        commandsForCapitalizationTest.add(commandToBeTested.toUpperCase());
        commandsForCapitalizationTest.add(CONFIG);
        commandsForCapitalizationTest.add(TestUtility.getConfigFileLocation(true));

        systemOutRule.clear();
        Client.main(commandsForCapitalizationTest.toArray(new String[0]));
        assertTrue(systemOutRule.getText().contains("dockstore" + formmatedValidCommandString + ": '" + commandToBeTested.toUpperCase()
                        + "' is not a dockstore command. See 'dockstore" + formmatedValidCommandString + " " + HELP
                        + "'.\n\n" + "The most similar command is:\n    " + commandToBeTested + "\n"));

        systemOutRule.clear();

        final ArrayList<String> commandsForLetterChangeTest = Lists.newArrayList(validCommands);
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
        assertTrue(systemOutRule.getText().contains("dockstore" + formmatedValidCommandString + ": '" + commandWithLetterChanged
                        + "' is not a dockstore command. See 'dockstore" + formmatedValidCommandString + " " + HELP
                        + "'.\n\n" + "The most similar command is:\n    " + commandToBeTested + "\n"));

    }


}
