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

import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.FlushingSystemErrRule;
import io.dockstore.common.FlushingSystemOutRule;
import io.dockstore.common.TestUtility;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;

import static org.junit.Assert.assertTrue;

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
        Client.main(new String[] { "--config", TestUtility.getConfigFileLocation(true), "yaml", "validate" });
        assertTrue(systemOutRule.getLog().contains("The following option is required: [--path]"));
        assertTrue(systemOutRule.getLog().contains("Usage: dockstore"));
        systemOutRule.clearLog();
    }

    @Test
    public void verifyErrorMessagesArePrinted() throws IOException {
        final String testDirectory = "src/test/resources/YamlVerifyTestDirectory/some-files-present";
        Client.main(new String[] { "--config", TestUtility.getConfigFileLocation(true), "yaml", "--path", testDirectory, YamlVerifyUtility.COMMAND_NAME });
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
        Client.main(new String[] { "--config", TestUtility.getConfigFileLocation(true), "yaml", "--path", testDirectory, YamlVerifyUtility.COMMAND_NAME });
        String successMsg = testDirectory + "/" + YamlVerifyUtility.DOCKSTOREYML + YamlVerifyUtility.VALID_YAML_ONLY + System.lineSeparator()
            + testDirectory + "/" + YamlVerifyUtility.DOCKSTOREYML + YamlVerifyUtility.VALID_DOCKSTORE_YML + System.lineSeparator();
        assertTrue(systemOutRule.getLog().contains(successMsg));
        systemOutRule.clearLog();
    }

}
