/*
 *    Copyright 2022 OICR and UCSC
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
import io.dockstore.common.TestUtility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

import static io.dockstore.client.cli.Client.CONFIG;
import static io.dockstore.client.cli.Client.HELP;
import static io.dockstore.client.cli.YamlVerifyUtility.YAML;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlClientIT extends BaseIT {

    @SystemStub
    public final SystemOut systemOutRule = new SystemOut();

    @SystemStub
    public final SystemErr systemErrRule = new SystemErr();

    public final String yamlHelpMsg = "dockstore yaml " + HELP;
    public final String validateHelpMsg = "dockstore yaml validate " + HELP;

    @BeforeEach
    @Override
    public void resetDBBetweenTests() throws Exception {
        CLICommonTestUtilities.cleanStatePrivate1(SUPPORT, testingPostgres);
        Client.DEBUG.set(false);
    }

    @Test
    void missingPathParameter() throws IOException {
        Client.main(new String[]{CONFIG, TestUtility.getConfigFileLocation(true), YAML, "validate"});
        assertTrue(systemOutRule.getText().contains("The following option is required: [--path]"));
        assertTrue(systemOutRule.getText().contains("Usage: dockstore"));
        systemOutRule.clear();
    }

    @Test
    void verifyErrorMessagesArePrinted() throws IOException {
        final String testDirectory = "src/test/resources/YamlVerifyTestDirectory/some-files-present";
        System.out.println(new String[]{CONFIG, TestUtility.getConfigFileLocation(true), YAML, YamlVerifyUtility.COMMAND_NAME, "--path", testDirectory});
        Client.main(new String[]{CONFIG, TestUtility.getConfigFileLocation(true), YAML, YamlVerifyUtility.COMMAND_NAME, "--path", testDirectory});
        String errorMsg = YamlVerifyUtility.INVALID_FILE_STRUCTURE
            + testDirectory + "/dockstore.wdl.json" + YamlVerifyUtility.FILE_DOES_NOT_EXIST + System.lineSeparator()
            + testDirectory + "/Dockstore.cwl" + YamlVerifyUtility.FILE_DOES_NOT_EXIST + System.lineSeparator();
        System.out.println(errorMsg);
        assertTrue(systemOutRule.getText().contains(errorMsg));
        systemOutRule.clear();
    }

    private void runYamlValidatorAndExpectSuccess(final String testDirectory) throws IOException {
        Client.main(new String[]{CONFIG, TestUtility.getConfigFileLocation(true), YAML, YamlVerifyUtility.COMMAND_NAME, "--path", testDirectory});
        String successMsg = testDirectory + "/" + YamlVerifyUtility.DOCKSTOREYML + YamlVerifyUtility.VALID_YAML_ONLY + System.lineSeparator()
                + testDirectory + "/" + YamlVerifyUtility.DOCKSTOREYML + YamlVerifyUtility.VALID_DOCKSTORE_YML + System.lineSeparator();
        assertTrue(systemOutRule.getText().contains(successMsg));
        systemOutRule.clear();
    }


    @Test
    void completeRun() throws IOException {
        runYamlValidatorAndExpectSuccess("../dockstore-client/src/test/resources/YamlVerifyTestDirectory/correct-directory");
    }

    /** This test is for when a .dockstore.yml file has workflow or tool with a testParameterFiles field, but the field is empty
     *
     * @throws IOException
     */
    @Test
    void completeRunWithDockstoreYmlThatContainsAnEmptyTestParameterField() throws IOException {
        runYamlValidatorAndExpectSuccess("../dockstore-client/src/test/resources/YamlVerifyTestDirectory/empty-test-parameter-files-field/tool");
        runYamlValidatorAndExpectSuccess("../dockstore-client/src/test/resources/YamlVerifyTestDirectory/empty-test-parameter-files-field/workflow");

    }

    /** This test is for when a .dockstore.yml file has workflow or tool with no testParameterFiles field
     *
     * @throws Exception
     */
    @Test
    void completeRunWithDockstoreYmlThatContainsNoTestParameterField() throws IOException {
        runYamlValidatorAndExpectSuccess("../dockstore-client/src/test/resources/YamlVerifyTestDirectory/no-test-parameter-files-field/tool");
        runYamlValidatorAndExpectSuccess("../dockstore-client/src/test/resources/YamlVerifyTestDirectory/no-test-parameter-files-field/workflow");

    }





    // Tests for when Help message should be generated

    @Test
    void testHelpCommands() throws Exception {
        checkCommandForHelp(new String[]{YAML, HELP}, yamlHelpMsg);
        checkCommandForHelp(new String[]{YAML, YamlVerifyUtility.COMMAND_NAME, HELP}, validateHelpMsg);
    }



    // This tests for when a help message should be generated even though no help flag was given
    // it also tests for the appropriate error message
    @Test
    void testHelpCommandsNoHelpFlag() throws Exception {
        checkCommandForHelp(new String[]{YAML}, yamlHelpMsg, YamlClient.ERROR_NO_COMMAND);
        checkCommandForHelp(new String[]{YAML, YamlVerifyUtility.COMMAND_NAME}, validateHelpMsg, "The following option is required: [--path]");
        checkCommandForHelp(new String[]{YAML, "--path", YamlVerifyUtility.COMMAND_NAME}, "Usage: dockstore yaml");
        checkCommandForHelp(new String[]{YAML, YamlVerifyUtility.COMMAND_NAME, "--path"}, validateHelpMsg, "Expected a value after parameter --path");
    }

    private void checkCommandForHelp(String[] argv, String helpMsg) throws Exception {
        checkCommandForHelp(argv, helpMsg, " ");
    }

    private void checkCommandForHelp(String[] argv, String helpMsg, String errorMsg) throws Exception {
        final ArrayList<String> strings = Lists.newArrayList(argv);
        strings.add(CONFIG);
        strings.add(TestUtility.getConfigFileLocation(true));
        Client.main(strings.toArray(new String[0]));
        assertTrue(systemOutRule.getText().contains(helpMsg));
        assertTrue(systemOutRule.getText().contains(errorMsg));
        systemOutRule.clear();
    }
}
