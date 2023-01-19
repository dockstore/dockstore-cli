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
public class ClientIT extends BaseIT {

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
    public void testListEntries() throws IOException, ApiException {
        Client.main(new String[] { "--config", TestUtility.getConfigFileLocation(true), "tool", "list" });
        checkToolList(systemOutRule.getText());
    }

    @Test
    public void testDebugModeListEntries() throws IOException, ApiException {
        Client.main(new String[] { "--debug", "--config", TestUtility.getConfigFileLocation(true), "tool", "list" });
        checkToolList(systemOutRule.getText());
    }

    @Test
    public void testListEntriesWithoutCreds() throws Exception {
        int exitCode = catchSystemExit(
                () -> Client.main(new String[] { "--config", TestUtility.getConfigFileLocation(false), "tool", "list" }));
        assertEquals(Client.API_ERROR, exitCode);
    }

    @Test
    public void testListEntriesOnWrongPort() throws Exception {
        int exitCode = catchSystemExit(() -> Client.main(new String[] { "--config", TestUtility.getConfigFileLocation(true, false, false), "tool", "list" }));
        assertEquals(Client.CONNECTION_ERROR, exitCode);
    }

    // Won't work as entry must be valid
    @Disabled
    public void quickRegisterValidEntry() throws IOException {
        Client.main(new String[] { "--config", TestUtility.getConfigFileLocation(true), "tool", "publish", "quay.io/test_org/test6" });

        // verify DB
        final long count = testingPostgres.runSelectStatement("select count(*) from tool where name = 'test6'", long.class);
        assertEquals(1, count, "should see three entries");
    }

    @Test
    public void testPluginEnable() {
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("pluginsTest1/configWithPlugins"), "plugin", "download" });
        systemOutRule.clear();
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("pluginsTest1/configWithPlugins"), "plugin", "list" });
        assertTrue(systemOutRule.getText().contains("dockstore-file-synapse-plugin"));
        assertTrue(systemOutRule.getText().contains("dockstore-file-s3-plugin"));
        assertFalse(systemOutRule.getText().contains("dockstore-icgc-storage-client-plugin"));
    }

    @Test
    public void testPluginDisable() {
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("pluginsTest2/configWithPlugins"), "plugin", "download" });
        systemOutRule.clear();
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("pluginsTest2/configWithPlugins"), "plugin", "list" });
        assertFalse(systemOutRule.getText().contains("dockstore-file-synapse-plugin"));
        assertFalse(systemOutRule.getText().contains("dockstore-file-s3-plugin"));
        assertTrue(systemOutRule.getText().contains("dockstore-file-icgc-storage-client-plugin"));
    }

    @Disabled
    public void quickRegisterDuplicateEntry() throws IOException {
        Client.main(new String[] { "--config", TestUtility.getConfigFileLocation(true), "tool", "publish", "quay.io/test_org/test6" });
        Client.main(
            new String[] { "--config", TestUtility.getConfigFileLocation(true), "tool", "publish", "quay.io/test_org/test6", "view1" });
        Client.main(
            new String[] { "--config", TestUtility.getConfigFileLocation(true), "tool", "publish", "quay.io/test_org/test6", "view2" });

        // verify DB
        final long count = testingPostgres.runSelectStatement("select count(*) from container where name = 'test6'", long.class);
        assertEquals(3, count, "should see three entries");
    }

    @Test
    public void quickRegisterInValidEntry() throws Exception {
        int exitCode = catchSystemExit(() -> Client.main(new String[] { "--config", TestUtility.getConfigFileLocation(true), "tool", "publish", "quay.io/test_org/test1" }));
        assertEquals(Client.CLIENT_ERROR, exitCode);
    }

    @Test
    public void quickRegisterUnknownEntry() throws Exception {
        int exitCode = catchSystemExit(() -> Client.main(
                new String[] { "--config", TestUtility.getConfigFileLocation(true), "tool", "publish",
                        "quay.io/funky_container_that_does_not_exist" }));
        assertEquals(Client.CLIENT_ERROR, exitCode);
    }

    /* When you manually publish on the dockstore CLI, it will now refresh the container after it is added.
     Since the below containers use dummy data and don't connect with Github/Bitbucket/Quay, the refresh will throw an error.
     Todo: Set up these tests with real data (not confidential)
     */
    @Disabled("Since dockstore now checks for associated tags for Quay container, manual publishing of nonexistant images won't work")
    public void manualRegisterABunchOfValidEntries() throws IOException {
        Client.main(new String[] { "--config", TestUtility.getConfigFileLocation(true), "tool", "manual_publish", "--registry",
            Registry.QUAY_IO.toString(), "--namespace", "pypi", "--name", "bd2k-python-lib", "--git-url",
            "git@github.com:funky-user/test2.git", "--git-reference", "refs/head/master" });
        Client.main(new String[] { "--config", TestUtility.getConfigFileLocation(true), "tool", "manual_publish", "--registry",
            Registry.QUAY_IO.toString(), "--namespace", "pypi", "--name", "bd2k-python-lib", "--git-url",
            "git@github.com:funky-user/test2.git", "--git-reference", "refs/head/master", "--toolname", "test1" });
        Client.main(new String[] { "--config", TestUtility.getConfigFileLocation(true), "tool", "manual_publish", "--registry",
            Registry.QUAY_IO.toString(), "--namespace", "pypi", "--name", "bd2k-python-lib", "--git-url",
            "git@github.com:funky-user/test2.git", "--git-reference", "refs/head/master", "--toolname", "test2" });
        Client.main(new String[] { "--config", TestUtility.getConfigFileLocation(true), "tool", "manual_publish", "--registry",
            Registry.DOCKER_HUB.toString(), "--namespace", "pypi", "--name", "bd2k-python-lib", "--git-url",
            "git@github.com:funky-user/test2.git", "--git-reference", "refs/head/master" });
        Client.main(new String[] { "--config", TestUtility.getConfigFileLocation(true), "tool", "manual_publish", "--registry",
            Registry.DOCKER_HUB.toString(), "--namespace", "pypi", "--name", "bd2k-python-lib", "--git-url",
            "git@github.com:funky-user/test2.git", "--git-reference", "refs/head/master", "--toolname", "test1" });

        // verify DB
        final long count = testingPostgres.runSelectStatement("select count(*) from container where name = 'bd2k-python-lib'", long.class);
        assertEquals(5, count, "should see three entries");
    }

    @Test
    public void manualRegisterADuplicate() throws Exception {
        //TODO: this test is actually dying on the first command, I suspect that this has been broken for a while before migration to junit5
        int exitCode = catchSystemExit(() -> {
            Client.main(new String[] { "--config", TestUtility.getConfigFileLocation(true), "tool", "manual_publish", "--registry",
                    Registry.QUAY_IO.name(), Registry.QUAY_IO.toString(), "--namespace", "pypi", "--name", "bd2k-python-lib", "--git-url",
                    "git@github.com:funky-user/test2.git", "--git-reference", "refs/head/master" });
            Client.main(new String[] { "--config", TestUtility.getConfigFileLocation(true), "tool", "manual_publish", "--registry",
                    Registry.QUAY_IO.name(), Registry.QUAY_IO.toString(), "--namespace", "pypi", "--name", "bd2k-python-lib", "--git-url",
                    "git@github.com:funky-user/test2.git", "--git-reference", "refs/head/master", "--toolname", "test1" });
            Client.main(new String[] { "--config", TestUtility.getConfigFileLocation(true), "tool", "manual_publish", "--registry",
                    Registry.QUAY_IO.name(), Registry.QUAY_IO.toString(), "--namespace", "pypi", "--name", "bd2k-python-lib", "--git-url",
                    "git@github.com:funky-user/test2.git", "--git-reference", "refs/head/master", "--toolname", "test1" });
        });
        assertEquals(Client.API_ERROR, exitCode);
    }

    @Test
    @Category(ToilCompatibleTest.class)
    public void launchingCWLWorkflow() throws IOException {
        final String firstWorkflowCWL = ResourceHelpers.resourceFilePath("1st-workflow.cwl");
        final String firstWorkflowJSON = ResourceHelpers.resourceFilePath("1st-workflow-job.json");
        Client.main(new String[] { "--script", "--config", TestUtility.getConfigFileLocation(true), "workflow", "launch", "--local-entry",
            firstWorkflowCWL, "--json", firstWorkflowJSON });
    }

    @Test
    @Category(ToilCompatibleTest.class)
    public void launchingCWLToolWithRemoteParameters() throws IOException {
        Client.main(
            new String[] { "--script", "--config", TestUtility.getConfigFileLocation(true), "tool", "launch", "--local-entry", FIRST_TOOL,
                "--json",
                "https://raw.githubusercontent.com/dockstore/dockstore/f343bcd6e4465a8ef790208f87740bd4d5a9a4da/dockstore-client/src/test/resources/test.cwl.json" });
    }

    @Test
    public void testMetadataMethods() throws IOException {
        Client.main(new String[] { "--config", TestUtility.getConfigFileLocation(true), "--version" });
        assertTrue(systemOutRule.getText().contains("Dockstore version"));
        systemOutRule.clear();
        Client.main(new String[] { "--config", TestUtility.getConfigFileLocation(true), "--server-metadata" });
        assertTrue(systemOutRule.getText().contains("version"));
        systemOutRule.clear();
    }

    @Test
    public void testCacheCleaning() throws IOException {
        Client.main(new String[] { "--config", TestUtility.getConfigFileLocation(true), "--clean-cache" });
        systemOutRule.clear();
    }

    @Test
    public void pluginDownload() throws IOException {
        Client.main(new String[] { "--config", TestUtility.getConfigFileLocation(true), "plugin", "download" });
    }

    /**
     * Tests the 'dockstore deps' command with a client version and python 3 version
     * Passes if the returned result contains 'avro-cwl' as its dependency and the other common dependencies
     *
     * @throws IOException
     */
    @Test
    public void testDepsCommandWithVersionAndPython3() throws IOException {
        Client.main(
            new String[] { "--config", TestUtility.getConfigFileLocation(true), "deps", "--client-version", "1.7.0", "--python-version",
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
    public void testDepsCommandWithUnknownRunners() throws Exception {
        int exitCode = catchSystemExit(() -> Client.main(new String[] { "--config", TestUtility.getConfigFileLocation(true), "deps", "--runner", "cromwell" }));
        assertEquals(Client.API_ERROR, exitCode);
        assertTrue(systemOutRule.getText().contains("Could not get runner dependencies"));
        Client.main(new String[] { "--config", TestUtility.getConfigFileLocation(true), "deps", "--runner", "cromwell" });
    }

    /**
     * Tests the 'dockstore deps' command with default and no additional flag
     * Passes if the returned result contains 'avro' as its dependency and the other common dependencies
     *
     * @throws IOException
     */
    @Test
    public void testDepsCommand() throws IOException {
        Client.main(new String[] { "--config", TestUtility.getConfigFileLocation(true), "deps" });
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
    public void testDepsCommandHelp() throws IOException {
        Client.main(new String[] { "--config", TestUtility.getConfigFileLocation(true), "deps", "--help" });
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
    public void touchOnAllHelpMessages() throws IOException {

        checkCommandForHelp(new String[] { "tool", "search" });
        checkCommandForHelp(new String[] { "tool", "info" });
        checkCommandForHelp(new String[] { "tool", "cwl" });
        checkCommandForHelp(new String[] { "tool", "wdl" });
        checkCommandForHelp(new String[] { "tool", "label" });
        checkCommandForHelp(new String[] { "tool", "test_parameter" });
        checkCommandForHelp(new String[] { "tool", "convert" });
        checkCommandForHelp(new String[] { "tool", "launch" });
        checkCommandForHelp(new String[] { "tool", "version_tag" });
        checkCommandForHelp(new String[] { "tool", "update_tool" });

        checkCommandForHelp(new String[] { "tool", "convert", "entry2json" });
        checkCommandForHelp(new String[] { "tool", "convert", "cwl2yaml" });
        checkCommandForHelp(new String[] { "tool", "convert", "cwl2json" });
        checkCommandForHelp(new String[] { "tool", "convert", "wdl2json" });

        checkCommandForHelp(new String[] {});
        checkCommandForHelp(new String[] { "tool" });
        checkCommandForHelp(new String[] { "tool", "download", "--help" });
        checkCommandForHelp(new String[] { "tool", "list", "--help" });
        checkCommandForHelp(new String[] { "tool", "search", "--help" });
        checkCommandForHelp(new String[] { "tool", "publish", "--help" });
        checkCommandForHelp(new String[] { "tool", "info", "--help" });
        checkCommandForHelp(new String[] { "tool", "cwl", "--help" });
        checkCommandForHelp(new String[] { "tool", "wdl", "--help" });
        checkCommandForHelp(new String[] { "tool", "refresh", "--help" });
        checkCommandForHelp(new String[] { "tool", "label", "--help" });
        checkCommandForHelp(new String[] { "tool", "convert", "--help" });
        checkCommandForHelp(new String[] { "tool", "convert", "cwl2json", "--help" });
        checkCommandForHelp(new String[] { "tool", "convert", "cwl2yaml", "--help" });
        checkCommandForHelp(new String[] { "tool", "convert", "wdl2json", "--help" });
        checkCommandForHelp(new String[] { "tool", "convert", "entry2json", "--help" });
        checkCommandForHelp(new String[] { "tool", "launch", "--help" });
        checkCommandForHelp(new String[] { "tool", "version_tag", "--help" });
        checkCommandForHelp(new String[] { "tool", "version_tag", "remove", "--help" });
        checkCommandForHelp(new String[] { "tool", "version_tag", "update", "--help" });
        checkCommandForHelp(new String[] { "tool", "version_tag", "add", "--help" });
        checkCommandForHelp(new String[] { "tool", "update_tool", "--help" });
        checkCommandForHelp(new String[] { "tool", "manual_publish", "--help" });
        checkCommandForHelp(new String[] { "tool", "star", "--help" });
        checkCommandForHelp(new String[] { "tool", "test_parameter", "--help" });
        checkCommandForHelp(new String[] { "tool", "verify", "--help" });
        checkCommandForHelp(new String[] { "tool" });

        checkCommandForHelp(new String[] { "workflow", "convert", "entry2json" });
        checkCommandForHelp(new String[] { "workflow", "convert", "cwl2yaml" });
        checkCommandForHelp(new String[] { "workflow", "convert", "cwl2json" });
        checkCommandForHelp(new String[] { "workflow", "convert", "wdl2json" });

        checkCommandForHelp(new String[] { "workflow", "search" });
        checkCommandForHelp(new String[] { "workflow", "info" });
        checkCommandForHelp(new String[] { "workflow", "cwl" });
        checkCommandForHelp(new String[] { "workflow", "wdl" });
        checkCommandForHelp(new String[] { "workflow", "label" });
        checkCommandForHelp(new String[] { "workflow", "test_parameter" });
        checkCommandForHelp(new String[] { "workflow", "convert" });
        checkCommandForHelp(new String[] { "workflow", "launch" });
        checkCommandForHelp(new String[] { "workflow", "version_tag" });
        checkCommandForHelp(new String[] { "workflow", "update_workflow" });
        checkCommandForHelp(new String[] { "workflow", "restub" });

        checkCommandForHelp(new String[] { "workflow", "download", "--help" });
        checkCommandForHelp(new String[] { "workflow", "list", "--help" });
        checkCommandForHelp(new String[] { "workflow", "search", "--help" });
        checkCommandForHelp(new String[] { "workflow", "publish", "--help" });
        checkCommandForHelp(new String[] { "workflow", "info", "--help" });
        checkCommandForHelp(new String[] { "workflow", "cwl", "--help" });
        checkCommandForHelp(new String[] { "workflow", "wdl", "--help" });
        checkCommandForHelp(new String[] { "workflow", "refresh", "--help" });
        checkCommandForHelp(new String[] { "workflow", "label", "--help" });
        checkCommandForHelp(new String[] { "workflow", "convert", "--help" });
        checkCommandForHelp(new String[] { "workflow", "convert", "cwl2json", "--help" });
        checkCommandForHelp(new String[] { "workflow", "convert", "cwl2yaml", "--help" });
        checkCommandForHelp(new String[] { "workflow", "convert", "wd2json", "--help" });
        checkCommandForHelp(new String[] { "workflow", "convert", "entry2json", "--help" });
        checkCommandForHelp(new String[] { "workflow", "launch", "--help" });
        checkCommandForHelp(new String[] { "workflow", "version_tag", "--help" });
        checkCommandForHelp(new String[] { "workflow", "update_workflow", "--help" });
        checkCommandForHelp(new String[] { "workflow", "manual_publish", "--help" });
        checkCommandForHelp(new String[] { "workflow", "restub", "--help" });
        checkCommandForHelp(new String[] { "workflow", "star", "--help" });
        checkCommandForHelp(new String[] { "workflow", "test_parameter", "--help" });
        checkCommandForHelp(new String[] { "workflow", "verify", "--help" });
        checkCommandForHelp(new String[] { "workflow" });

        checkCommandForHelp(new String[] { "plugin", "list", "--help" });
        checkCommandForHelp(new String[] { "plugin", "download", "--help" });
        checkCommandForHelp(new String[] { "plugin" });

        checkCommandForHelp(new String[] { "checker" });
        checkCommandForHelp(new String[] { "checker", "download", "--help" });
        checkCommandForHelp(new String[] { "checker", "launch", "--help" });
        checkCommandForHelp(new String[] { "checker", "add", "--help" });
        checkCommandForHelp(new String[] { "checker", "update", "--help" });
        checkCommandForHelp(new String[] { "checker", "update_version", "--help" });
        checkCommandForHelp(new String[] { "checker", "test_parameter", "--help" });
    }

    private void checkCommandForHelp(String[] argv) throws IOException {
        final ArrayList<String> strings = Lists.newArrayList(argv);
        strings.add("--config");
        strings.add(TestUtility.getConfigFileLocation(true));

        Client.main(strings.toArray(new String[0]));
        assertTrue(systemOutRule.getText().contains("Usage: dockstore"));
        systemOutRule.clear();
    }

}
