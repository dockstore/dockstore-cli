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

import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.ToilCompatibleTest;
import io.dockstore.common.ToolTest;
import io.dropwizard.testing.ResourceHelpers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.experimental.categories.Category;

/**
 * Extra confidential integration tests, don't rely on the type of repository used (Github, Dockerhub, Quay.io, Bitbucket)
 *
 * @author aduncan
 */
@Category({ ConfidentialTest.class, ToolTest.class })
public class GeneralIT extends BaseIT {

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();

    @Before
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false);
    }

    /**
     * Tests that a developer can launch a CWL Tool locally, instead of getting files from Dockstore
     */
    @Test
    @Category(ToilCompatibleTest.class)
    public void testLocalLaunchCWL() {
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "launch", "--local-entry",
            ResourceHelpers.resourceFilePath("arrays.cwl"), "--json",
            ResourceHelpers.resourceFilePath("testArrayHttpInputLocalOutput.json"), "--script" });
    }

    /**
     * This tests that attempting to launch a CWL tool locally, where no file exists, an IOError will occur
     */
    @Test
    public void testLocalLaunchCWLNoFile() {
        systemExit.expectSystemExitWithStatus(Client.ENTRY_NOT_FOUND);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "launch", "--local-entry",
            "imnotreal.cwl", "--json", "filtercount-job.json", "--script" });
    }

    /**
     * This tests that attempting to launch a WDL tool locally, where no file exists, an IOError will occur
     */
    @Test
    public void testLocalLaunchWDLNoFile() {
        systemExit.expectSystemExitWithStatus(Client.ENTRY_NOT_FOUND);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "launch", "--local-entry",
            "imnotreal.wdl", "--json", "imnotreal-job.json", "--descriptor", "wdl", "--script" });
    }

    /**
     * This tests that attempting to launch a CWL tool remotely, where no file exists, an APIError will occur
     */
    @Test
    public void testRemoteLaunchCWLNoFile() {
        systemExit.expectSystemExitWithStatus(Client.IO_ERROR);
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "launch", "--entry", "imnotreal.cwl",
                "--json", "imnotreal-job.json", "--script" });
    }

    /**
     * This tests that attempting to launch a WDL tool remotely, where no file exists, an APIError will occur
     */
    @Test
    public void testRemoteLaunchWDLNoFile() {
        systemExit.expectSystemExitWithStatus(Client.ENTRY_NOT_FOUND);
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "launch", "--entry", "imnotreal.wdl",
                "--json", "imnotreal-job.json", "--descriptor", "wdl", "--script" });
    }

}
