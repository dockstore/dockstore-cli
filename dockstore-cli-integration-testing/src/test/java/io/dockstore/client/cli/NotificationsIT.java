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

import io.dockstore.common.TestUtility;
import io.dockstore.common.ToolTest;
import io.dropwizard.testing.ResourceHelpers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.experimental.categories.Category;

/**
 * @author gluu
 * @since 16/01/18
 */
@Category({ ToolTest.class })
public class NotificationsIT extends BaseIT {
    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final ExpectedSystemExit exit = ExpectedSystemExit.none();
    private final static String sampleCWLDescriptor = ResourceHelpers.resourceFilePath("dockstore-tool-helloworld.cwl");
    private final static String sampleWDLDescriptor = ResourceHelpers.resourceFilePath("wdl.wdl");
    private final static String sampleCWLTestJson = "https://raw.githubusercontent.com/dockstore/dockstore/f343bcd6e4465a8ef790208f87740bd4d5a9a4da/dockstore-client/src/test/resources/test.cwl.json";
    private final static String sampleWDLTestJson = ResourceHelpers.resourceFilePath("wdl.json");
    private final static String SLACK_DESTINATION = "Destination is Slack. Message is not 100% compatible.";
    private final static String SENDING_NOTIFICATION = "Sending notifications message.";
    private final static String GENERATING_UUID = "The UUID generated for this specific execution is ";


    @Override
    @Before
    public void resetDBBetweenTests() {
        systemOutRule.clearLog();
        systemErrRule.clearLog();
    }

    /**
     * Tests if an error is displayed when UUID is specified with no webhook URL
     *
     * @throws IOException
     */
    @Test
    public void launchCWLToolWithNotificationsUUIDNoURL() throws IOException {
        exit.expectSystemExitWithStatus(Client.CLIENT_ERROR);
        exit.checkAssertionAfterwards(() -> {
            String log = systemErrRule.getLog();
            Assert.assertTrue(log, log.contains("Aborting launch."));
        });
        Client.main(
                new String[] { "--config", TestUtility.getConfigFileLocation(true), "tool", "launch", "--local-entry", sampleCWLDescriptor, "--json",
                        sampleCWLTestJson, "--uuid", "potato", "--info" });
    }

    /**
     * Tests if debug message is displayed when UUID is specified but with an invalid webhook URL
     *
     * @throws IOException
     */
    @Test
    public void launchCWLToolWithNotificationsUUIDInvalidURL() throws IOException {
        Client.main(new String[] { "--config", TestUtility.getConfigFileLocationWithInvalidNotifications(true), "tool", "launch",
                "--local-entry", sampleCWLDescriptor, "--json", sampleCWLTestJson, "--uuid", "potato", "--info" });
        String log = systemOutRule.getLog();
        Assert.assertTrue(log, log.contains(SENDING_NOTIFICATION));
        Assert.assertFalse(log, log.contains(SLACK_DESTINATION));
    }

    /**
     * Tests if no debug message is displayed when UUID is specified with a valid webhook URL
     *
     * @throws IOException
     */
    @Test
    public void launchCWLToolWithNotificationsUUIDValidURL() throws IOException {
        Client.main(
                new String[] { "--config", TestUtility.getConfigFileLocationWithValidNotifications(true), "tool", "launch", "--local-entry",
                        sampleCWLDescriptor, "--json", sampleCWLTestJson, "--uuid", "potato", "--info" });
        String log = systemOutRule.getLog();
        Assert.assertTrue(log, log.contains(SENDING_NOTIFICATION));
        Assert.assertTrue(log, log.contains(SLACK_DESTINATION));
    }

    /**
     * Tests if nothing relevant is displayed when UUID is not specified but with a valid webhook URL
     *
     * @throws IOException
     */
    @Test
    public void launchCWLToolWithNotificationsNoUUIDValidURL() throws IOException {
        Client.main(
                new String[] { "--config", TestUtility.getConfigFileLocationWithValidNotifications(true), "tool", "launch", "--local-entry",
                        sampleCWLDescriptor, "--json", sampleCWLTestJson, "--info" });
        String log = systemOutRule.getLog();
        Assert.assertTrue(log, log.contains(SENDING_NOTIFICATION));
        Assert.assertTrue(log, log.contains(GENERATING_UUID));
        Assert.assertTrue(log, log.contains(SLACK_DESTINATION));
    }

    // WDL TESTS

    /**
     * Tests if an error is displayed when UUID is specified with no webhook URL
     *
     * @throws IOException
     */
    @Test
    public void launchWDLToolWithNotificationsUUIDNoURL() throws IOException {
        exit.expectSystemExitWithStatus(Client.CLIENT_ERROR);
        exit.checkAssertionAfterwards(() -> {
            String log = systemErrRule.getLog();
            Assert.assertTrue(log, log.contains("Aborting launch."));
        });
        Client.main(
                new String[] { "--config", TestUtility.getConfigFileLocation(true), "tool", "launch", "--local-entry", sampleWDLDescriptor,
                        "--json", sampleWDLTestJson, "--uuid", "potato" });


    }

    /**
     * Tests if debug message is displayed when UUID is specified but with an invalid webhook URL
     *
     * @throws IOException
     */
    @Test
    public void launchWDLToolWithNotificationsUUIDInvalidURL() throws IOException {
        Client.main(new String[] { "--config", TestUtility.getConfigFileLocationWithInvalidNotifications(true), "tool", "launch",
                "--local-entry", sampleWDLDescriptor, "--json", sampleWDLTestJson, "--uuid", "potato", "--info" });
        String log = systemOutRule.getLog();
        Assert.assertTrue(log, log.contains(SENDING_NOTIFICATION));
        Assert.assertFalse(log, log.contains(SLACK_DESTINATION));
    }

    /**
     * Tests if no debug message is displayed when UUID is specified with a valid webhook URL
     *
     * @throws IOException
     */
    @Test
    public void launchWDLToolWithNotificationsUUIDValidURL() throws IOException {
        Client.main(new String[] { "--config", TestUtility.getConfigFileLocationWithValidNotifications(true), "tool", "launch",
                "--local-entry", sampleWDLDescriptor, "--json", sampleWDLTestJson, "--uuid", "potato", "--info" });
        String log = systemOutRule.getLog();
        Assert.assertTrue(log, log.contains(SENDING_NOTIFICATION));
        Assert.assertTrue(log, log.contains(SLACK_DESTINATION));
    }

    /**
     * Tests if nothing relevant is displayed when UUID is not specified but with a valid webhook URL
     *
     * @throws IOException
     */
    @Test
    public void launchWDLToolWithNotificationsNoUUIDValidURL() throws IOException {
        Client.main(new String[] { "--config", TestUtility.getConfigFileLocationWithValidNotifications(true), "tool", "launch",
                "--local-entry", sampleWDLDescriptor, "--json", sampleWDLTestJson, "--info" });
        String log = systemOutRule.getLog();
        Assert.assertTrue(log, log.contains(GENERATING_UUID));
        Assert.assertTrue(log, log.contains(SENDING_NOTIFICATION));
        Assert.assertTrue(log, log.contains(SLACK_DESTINATION));
    }
}
