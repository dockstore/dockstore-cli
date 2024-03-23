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

import static io.dockstore.client.cli.ArgumentUtility.LAUNCH;
import static io.dockstore.client.cli.Client.CONFIG;
import static io.dockstore.client.cli.Client.TOOL;
import static io.dockstore.client.cli.nested.WesCommandParser.JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.org.webcompere.systemstubs.SystemStubs.catchSystemExit;

import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.TestUtility;
import io.dockstore.common.ToolTest;
import io.dropwizard.testing.ResourceHelpers;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.security.SystemExit;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

/**
 * @author gluu
 * @since 16/01/18
 */
@Tag(ConfidentialTest.NAME)
@Tag(ToolTest.NAME)
class NotificationsIT extends BaseIT {
    private static final String SAMPLE_CWL_DESCRIPTOR = ResourceHelpers.resourceFilePath("dockstore-tool-helloworld.cwl");
    private static final String SAMPLE_WDL_DESCRIPTOR = ResourceHelpers.resourceFilePath("wdl.wdl");
    private static final String SAMPLE_CWL_TEST_JSON = "https://raw.githubusercontent.com/dockstore/dockstore/f343bcd6e4465a8ef790208f87740bd4d5a9a4da/dockstore-client/src/test/resources/test.cwl.json";
    private static final String SAMPLE_WDL_TEST_JSON = ResourceHelpers.resourceFilePath("wdl.json");
    private static final String SLACK_DESTINATION = "Destination is Slack. Message is not 100% compatible.";
    private static final String SENDING_NOTIFICATION = "Sending notifications message.";
    private static final String GENERATING_UUID = "The UUID generated for this specific execution is ";

    @SystemStub
    public final SystemOut systemOutRule = new SystemOut();

    @SystemStub
    public final SystemErr systemErrRule = new SystemErr();

    @Override
    @BeforeEach
    public void resetDBBetweenTests() {
        systemOutRule.clear();
        systemErrRule.clear();
    }

    /**
     * Tests if an error is displayed when UUID is specified with no webhook URL
     *
     * @throws IOException
     */
    @Test
    void launchCWLToolWithNotificationsUUIDNoURL() throws Exception {
        int exitCode = catchSystemExit(() ->  Client.main(
                new String[] { CONFIG, TestUtility.getConfigFileLocation(true), TOOL, LAUNCH, "--local-entry", SAMPLE_CWL_DESCRIPTOR,
                        JSON, SAMPLE_CWL_TEST_JSON, "--uuid", "potato", "--info" }));
        assertEquals(Client.CLIENT_ERROR, exitCode);
        String log = systemErrRule.getText();
        assertTrue(log.contains("Aborting launch."), log);
    }

    /**
     * Tests if debug message is displayed when UUID is specified but with an invalid webhook URL
     *
     * @throws IOException
     */
    @Test
    void launchCWLToolWithNotificationsUUIDInvalidURL() throws Exception {
        new SystemExit().execute(() -> Client.main(
                new String[] { CONFIG, TestUtility.getConfigFileLocationWithInvalidNotifications(true), TOOL, LAUNCH, "--local-entry",
                        SAMPLE_CWL_DESCRIPTOR, JSON, SAMPLE_CWL_TEST_JSON, "--uuid", "potato", "--info" }));
        String log = systemOutRule.getText();
        assertTrue(log.contains(SENDING_NOTIFICATION), log);
        assertFalse(log.contains(SLACK_DESTINATION), log);
    }

    /**
     * Tests if no debug message is displayed when UUID is specified with a valid webhook URL
     *
     * @throws IOException
     */
    @Test
    void launchCWLToolWithNotificationsUUIDValidURL() throws Exception {
        new SystemExit().execute(() -> Client.main(
                new String[] { CONFIG, TestUtility.getConfigFileLocationWithValidNotifications(true), TOOL, LAUNCH, "--local-entry",
                        SAMPLE_CWL_DESCRIPTOR, JSON, SAMPLE_CWL_TEST_JSON, "--uuid", "potato", "--info" }));
        String log = systemOutRule.getText();
        assertTrue(log.contains(SENDING_NOTIFICATION), log);
        assertTrue(log.contains(SLACK_DESTINATION), log);
    }

    /**
     * Tests if nothing relevant is displayed when UUID is not specified but with a valid webhook URL
     *
     * @throws IOException
     */
    @Test
    void launchCWLToolWithNotificationsNoUUIDValidURL() throws Exception {
        new SystemExit().execute(() -> Client.main(
                new String[] { CONFIG, TestUtility.getConfigFileLocationWithValidNotifications(true), TOOL, LAUNCH, "--local-entry",
                        SAMPLE_CWL_DESCRIPTOR, JSON, SAMPLE_CWL_TEST_JSON, "--info" }));
        String log = systemOutRule.getText();
        assertTrue(log.contains(SENDING_NOTIFICATION), log);
        assertTrue(log.contains(GENERATING_UUID), log);
        assertTrue(log.contains(SLACK_DESTINATION), log);
    }

    // WDL TESTS

    /**
     * Tests if an error is displayed when UUID is specified with no webhook URL
     *
     */
    @Test
    void launchWDLToolWithNotificationsUUIDNoURL() throws Exception {
        int exitCode = catchSystemExit(() ->   Client.main(
                new String[] { CONFIG, TestUtility.getConfigFileLocation(true), TOOL, LAUNCH, "--local-entry", SAMPLE_WDL_DESCRIPTOR,
                        JSON, SAMPLE_WDL_TEST_JSON, "--uuid", "potato" }));
        String log = systemErrRule.getText();
        assertEquals(Client.CLIENT_ERROR, exitCode);
        assertTrue(log.contains("Aborting launch."), log);
    }

    /**
     * Tests if debug message is displayed when UUID is specified but with an invalid webhook URL
     *
     * @throws IOException
     */
    @Test
    void launchWDLToolWithNotificationsUUIDInvalidURL() throws Exception {
        new SystemExit().execute(() -> Client.main(
                new String[] { CONFIG, TestUtility.getConfigFileLocationWithInvalidNotifications(true), TOOL, LAUNCH, "--local-entry",
                        SAMPLE_WDL_DESCRIPTOR, JSON, SAMPLE_WDL_TEST_JSON, "--uuid", "potato", "--info" }));
        String log = systemOutRule.getText();
        assertTrue(log.contains(SENDING_NOTIFICATION), log);
        assertFalse(log.contains(SLACK_DESTINATION), log);
    }

    /**
     * Tests if no debug message is displayed when UUID is specified with a valid webhook URL
     *
     * @throws IOException
     */
    @Test
    void launchWDLToolWithNotificationsUUIDValidURL() throws Exception {
        new SystemExit().execute(() -> Client.main(
                new String[] { CONFIG, TestUtility.getConfigFileLocationWithValidNotifications(true), TOOL, LAUNCH, "--local-entry",
                        SAMPLE_WDL_DESCRIPTOR, JSON, SAMPLE_WDL_TEST_JSON, "--uuid", "potato", "--info" }));
        String log = systemOutRule.getText();
        assertTrue(log.contains(SENDING_NOTIFICATION), log);
        assertTrue(log.contains(SLACK_DESTINATION), log);
    }

    /**
     * Tests if nothing relevant is displayed when UUID is not specified but with a valid webhook URL
     *
     * @throws IOException
     */
    @Test
    void launchWDLToolWithNotificationsNoUUIDValidURL() throws Exception {
        new SystemExit().execute(() -> Client.main(
                new String[] { CONFIG, TestUtility.getConfigFileLocationWithValidNotifications(true), TOOL, LAUNCH, "--local-entry",
                        SAMPLE_WDL_DESCRIPTOR, JSON, SAMPLE_WDL_TEST_JSON, "--info" }));
        String log = systemOutRule.getText();
        assertTrue(log.contains(GENERATING_UUID), log);
        assertTrue(log.contains(SENDING_NOTIFICATION), log);
        assertTrue(log.contains(SLACK_DESTINATION), log);
    }
}
