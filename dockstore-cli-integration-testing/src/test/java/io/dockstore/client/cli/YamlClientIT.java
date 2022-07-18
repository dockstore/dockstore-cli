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

import static io.dockstore.client.cli.YamlVerifyUtility.YAML;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.Lists;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.FlushingSystemErrRule;
import io.dockstore.common.FlushingSystemOutRule;
import io.dockstore.common.TestUtility;
import java.io.IOException;
import java.util.ArrayList;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;

public class YamlClientIT extends BaseIT {

    @Rule
    public final SystemOutRule systemOutRule = new FlushingSystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new FlushingSystemErrRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();

    @Before
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate1(SUPPORT);
        Client.DEBUG.set(false);
    }

    @Test
    public void missingPathParameter() throws IOException {
        Client.main(new String[]{"--config", TestUtility.getConfigFileLocation(true), "yaml", "validate"});
        assertTrue(systemOutRule.getLog().contains("The following option is required: [--path]"));
        assertTrue(systemOutRule.getLog().contains("Usage: dockstore"));
        systemOutRule.clearLog();
    }

    @Test
    public void verifyErrorMessagesArePrinted() throws IOException {
        final String testDirectory = "src/test/resources/YamlVerifyTestDirectory/some-files-present";
        Client.main(new String[]{"--config", TestUtility.getConfigFileLocation(true), "yaml", YamlVerifyUtility.COMMAND_NAME, "--path", testDirectory});
        String errorMsg = YamlVerifyUtility.INVALID_FILE_STRUCTURE
            + testDirectory + "/dockstore.wdl.json" + YamlVerifyUtility.FILE_DOES_NOT_EXIST + System.lineSeparator()
            + testDirectory + "/Dockstore.cwl" + YamlVerifyUtility.FILE_DOES_NOT_EXIST + System.lineSeparator();
        System.out.println(errorMsg);
        assertTrue(systemOutRule.getLog().contains(errorMsg));
        systemOutRule.clearLog();
    }

    @Test
    public void completeRun() throws IOException {
        final String testDirectory = "../dockstore-client/src/test/resources/YamlVerifyTestDirectory/correct-directory";
        Client.main(new String[]{"--config", TestUtility.getConfigFileLocation(true), "yaml", YamlVerifyUtility.COMMAND_NAME, "--path", testDirectory});
        String successMsg = testDirectory + "/" + YamlVerifyUtility.DOCKSTOREYML + YamlVerifyUtility.VALID_YAML_ONLY + System.lineSeparator()
            + testDirectory + "/" + YamlVerifyUtility.DOCKSTOREYML + YamlVerifyUtility.VALID_DOCKSTORE_YML + System.lineSeparator();
        assertTrue(systemOutRule.getLog().contains(successMsg));
        systemOutRule.clearLog();
    }


    @Test
    public void testHelpCommands() throws Exception {
        final String yamlHelpMsg = "dockstore yaml --help";
        String validateHelpMsg = "dockstore yaml validate --help";

        checkCommandForHelp(new String[]{YAML}, yamlHelpMsg, YamlClient.ERROR_NO_COMMAND);
        checkCommandForHelp(new String[]{YAML, "--help"}, yamlHelpMsg);

        checkCommandForHelp(new String[]{YAML, "--help", YamlVerifyUtility.COMMAND_NAME}, validateHelpMsg);
        checkCommandForHelp(new String[]{YAML, YamlVerifyUtility.COMMAND_NAME}, validateHelpMsg, "The following option is required: [--path]");
        checkCommandForHelp(new String[]{YAML, YamlVerifyUtility.COMMAND_NAME, "--path"}, validateHelpMsg, "Expected a value after parameter --path");
        checkCommandForHelp(new String[]{YAML, "validate", "--help"}, validateHelpMsg);
    }


    private void checkCommandForHelp(String[] argv, String helpMsg) throws Exception {
        checkCommandForHelp(argv, helpMsg, " ");
    }

    private void checkCommandForHelp(String[] argv, String helpMsg, String errorMsg) throws Exception {
        final ArrayList<String> strings = Lists.newArrayList(argv);
        strings.add("--config");
        strings.add(TestUtility.getConfigFileLocation(true));

        Client.main(strings.toArray(new String[0]));
        System.out.println(strings);
        Assert.assertTrue(systemOutRule.getLog().contains(helpMsg));
        Assert.assertTrue(systemOutRule.getLog().contains(errorMsg));
        systemOutRule.clearLog();
        resetDBBetweenTests();
    }
}
