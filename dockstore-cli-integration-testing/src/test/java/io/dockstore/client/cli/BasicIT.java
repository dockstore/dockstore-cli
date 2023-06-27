/*
 *    Copyright 2017 OICR
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

import java.util.Collections;
import java.util.List;

import io.dockstore.client.cli.nested.ToolClient;
import io.dockstore.common.CLICommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.Registry;
import io.dockstore.common.SlowTest;
import io.dockstore.common.ToolTest;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.api.ContainersApi;
import io.dockstore.openapi.client.api.HostedApi;
import io.dockstore.openapi.client.model.DockstoreTool;
import io.dropwizard.testing.ResourceHelpers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

import static io.dockstore.client.cli.ArgumentUtility.LAUNCH;
import static io.dockstore.client.cli.CheckerClient.ADD;
import static io.dockstore.client.cli.CheckerClient.UPDATE;
import static io.dockstore.client.cli.Client.CONFIG;
import static io.dockstore.client.cli.Client.SCRIPT_FLAG;
import static io.dockstore.client.cli.Client.TOOL;
import static io.dockstore.client.cli.Client.VERSION;
import static io.dockstore.client.cli.nested.AbstractEntryClient.CHECKSUM_NULL_MESSAGE;
import static io.dockstore.client.cli.nested.AbstractEntryClient.CHECKSUM_VALIDATED_MESSAGE;
import static io.dockstore.client.cli.nested.AbstractEntryClient.MANUAL_PUBLISH;
import static io.dockstore.client.cli.nested.AbstractEntryClient.PUBLISH;
import static io.dockstore.client.cli.nested.AbstractEntryClient.REFRESH;
import static io.dockstore.client.cli.nested.AbstractEntryClient.TEST_PARAMETER;
import static io.dockstore.client.cli.nested.ToolClient.UPDATE_TOOL;
import static io.dockstore.client.cli.nested.ToolClient.VERSION_TAG;
import static io.dockstore.client.cli.nested.WesCommandParser.ENTRY;
import static io.dockstore.client.cli.nested.WesCommandParser.JSON;
import static io.dockstore.common.DescriptorLanguage.CWL;
import static io.dockstore.common.DescriptorLanguage.WDL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.org.webcompere.systemstubs.SystemStubs.catchSystemExit;

/**
 * Basic confidential integration tests, focusing on publishing/unpublishing both automatic and manually added tools
 * This is important as it tests the web service with real data instead of dummy data, using actual services like Github and Quay
 *
 * @author aduncan
 */
@Tag(ConfidentialTest.NAME)
@Tag(ToolTest.NAME)
class BasicIT extends BaseIT {

    @SystemStub
    public final SystemOut systemOutRule = new SystemOut();

    @SystemStub
    public final SystemErr systemErrRule = new SystemErr();

    @BeforeEach
    @Override
    public void resetDBBetweenTests() throws Exception {
        CLICommonTestUtilities.cleanStatePrivate1(SUPPORT, testingPostgres);
    }


    /*
     * General-ish tests
     */



    /**
     * Tests manually adding, updating, and removing a dockerhub tool
     */
    @Test
    void testVersionTagDockerhub() {
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, MANUAL_PUBLISH, "--registry",
            Registry.DOCKER_HUB.name(), Registry.DOCKER_HUB.toString(), "--namespace", "dockstoretestuser", "--name", "dockerhubandgithub",
            "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "regular",
            SCRIPT_FLAG });

        // Add a tag
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, VERSION_TAG, ADD, ENTRY,
            "registry.hub.docker.com/dockstoretestuser/dockerhubandgithub/regular", "--name", "masterTest", "--image-id",
            "4728f8f5ce1709ec8b8a5282e274e63de3c67b95f03a519191e6ea675c5d34e8", "--git-reference", "master", SCRIPT_FLAG });

        final long count = testingPostgres.runSelectStatement("select count(*) from tag where name = 'masterTest'", long.class);
        assertEquals(1, count, "there should be one tag");

        // Update tag
        Client.main(
            new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, VERSION_TAG, UPDATE, ENTRY,
                "registry.hub.docker.com/dockstoretestuser/dockerhubandgithub/regular", "--name", "masterTest", "--hidden", "true",
                SCRIPT_FLAG });

        final long count2 = testingPostgres.runSelectStatement(
            "select count(*) from tag t, version_metadata vm where name = 'masterTest' and vm.hidden='t' and t.id = vm.id", long.class);
        assertEquals(1, count2, "there should be one tag");

        // Remove tag
        Client.main(
            new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, VERSION_TAG, "remove", ENTRY,
                "registry.hub.docker.com/dockstoretestuser/dockerhubandgithub/regular", "--name", "masterTest", SCRIPT_FLAG });

        final long count3 = testingPostgres.runSelectStatement("select count(*) from tag where name = 'masterTest'", long.class);
        assertEquals(0, count3, "there should be no tags");

    }


    /**
     * Tests that a git reference for a tool can include branches named like feature/...
     */
    @Test
    void testGitReferenceFeatureBranch() {

        final long count = testingPostgres.runSelectStatement("select count(*) from tag where reference = 'feature/test'", long.class);
        assertEquals(2, count, "there should be 2 tags with the reference feature/test");
    }




    /*
     * Test dockerhub and github -
     * These tests are focused on testing entrys created from Dockerhub and Github repositories
     */

    /**
     * Tests manual registration and unpublishing of a Dockerhub/Github entry
     */
    @Test
    void testDockerhubGithubManualRegistration() {
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, MANUAL_PUBLISH, "--registry",
            Registry.DOCKER_HUB.name(), Registry.DOCKER_HUB.toString(), "--namespace", "dockstoretestuser", "--name", "dockerhubandgithub",
            "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "regular",
            SCRIPT_FLAG });

        final long count = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'regular' and ispublished='t'", long.class);

        assertEquals(1, count, "there should be 1 entries");

        // Unpublish
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, PUBLISH, "--unpub", ENTRY,
            "registry.hub.docker.com/dockstoretestuser/dockerhubandgithub/regular", SCRIPT_FLAG });
        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'regular' and ispublished='t'", long.class);

        assertEquals(0, count2, "there should be 0 entries");

    }

    /**
     * Will test manually publishing and unpublishing a Dockerhub/Github entry with an alternate structure
     */
    @Test
    void testDockerhubGithubAlternateStructure() {
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, MANUAL_PUBLISH, "--registry",
            Registry.DOCKER_HUB.name(), Registry.DOCKER_HUB.toString(), "--namespace", "dockstoretestuser", "--name", "dockerhubandgithub",
            "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay-alternate.git", "--git-reference", "master", "--toolname",
            "alternate", "--cwl-path", "/testDir/Dockstore.cwl", "--dockerfile-path", "/testDir/Dockerfile", SCRIPT_FLAG });

        final long count = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'alternate' and ispublished='t'", long.class);

        assertEquals(1, count, "there should be 1 entry");

        // Unpublish
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, PUBLISH, "--unpub", ENTRY,
            "registry.hub.docker.com/dockstoretestuser/dockerhubandgithub/alternate", SCRIPT_FLAG });
        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'alternate' and ispublished='f'", long.class);

        assertEquals(1, count2, "there should be 1 entry");
    }

    /**
     * Will test attempting to manually publish a Dockerhub/Github entry using incorrect CWL and/or dockerfile locations
     */
    @Disabled("probably broken with changes to manual publish")
    void testDockerhubGithubWrongStructure() throws Exception {
        // Todo : Manual publish entry with wrong cwl and dockerfile locations, should not be able to manual publish
        int exitCode = catchSystemExit(() -> Client.main(
                new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, MANUAL_PUBLISH, "--registry",
                        Registry.DOCKER_HUB.name(), Registry.DOCKER_HUB.toString(), "--namespace", "dockstoretestuser", "--name",
                        "dockerhubandgithubalternate", "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay-alternate.git",
                        "--git-reference", "master", "--toolname", "regular", "--cwl-path", "/Dockstore.cwl", "--dockerfile-path",
                        "/Dockerfile", SCRIPT_FLAG }));
        assertEquals(Client.GENERIC_ERROR, exitCode);
    }

    /**
     * Checks that you can manually publish and unpublish a Dockerhub/Github duplicate if different toolnames are set (but same Path)
     */
    @Test
    void testDockerhubGithubManualRegistrationDuplicates() {
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, MANUAL_PUBLISH, "--registry",
            Registry.DOCKER_HUB.name(), Registry.DOCKER_HUB.toString(), "--namespace", "dockstoretestuser", "--name", "dockerhubandgithub",
            "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "regular",
            SCRIPT_FLAG });

        final long count = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'regular' and ispublished='t'", long.class);

        assertEquals(1, count, "there should be 1 entry");

        // Add duplicate entry with different toolname
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, MANUAL_PUBLISH, "--registry",
            Registry.DOCKER_HUB.name(), Registry.DOCKER_HUB.toString(), "--namespace", "dockstoretestuser", "--name", "dockerhubandgithub",
            "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "regular2",
            SCRIPT_FLAG });

        // Unpublish the duplicate entrys
        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname like 'regular%' and ispublished='t'", long.class);
        assertEquals(2, count2, "there should be 2 entries");

        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, PUBLISH, "--unpub", ENTRY,
            "registry.hub.docker.com/dockstoretestuser/dockerhubandgithub/regular", SCRIPT_FLAG });
        final long count3 = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'regular2' and ispublished='t'", long.class);

        assertEquals(1, count3, "there should be 1 entry");

        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, PUBLISH, "--unpub", ENTRY,
            "registry.hub.docker.com/dockstoretestuser/dockerhubandgithub/regular2", SCRIPT_FLAG });
        final long count4 = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname like 'regular%' and ispublished='t'", long.class);

        assertEquals(0, count4, "there should be 0 entries");

    }



    /*
     * Test dockerhub and gitlab -
     * These tests are focused on testing entries created from Dockerhub and Gitlab repositories
     */

    /**
     * Tests manual registration and unpublishing of a Dockerhub/Gitlab entry
     */
    @Test
    @Tag(SlowTest.NAME)
    void testDockerhubGitlabManualRegistration() {
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, MANUAL_PUBLISH, "--registry",
            Registry.DOCKER_HUB.name(), Registry.DOCKER_HUB.toString(), "--namespace", "dockstoretestuser", "--name", "dockerhubandgitlab",
            "--git-url", "git@gitlab.com:dockstore.test.user/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "regular",
            SCRIPT_FLAG });

        final long count = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'regular' and ispublished='t'", long.class);

        assertEquals(1, count, "there should be 1 entries, there are " + count);

        // Unpublish
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, PUBLISH, "--unpub", ENTRY,
            "registry.hub.docker.com/dockstoretestuser/dockerhubandgitlab/regular", SCRIPT_FLAG });
        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'regular' and ispublished='t'", long.class);

        assertEquals(0, count2, "there should be 0 entries, there are " + count2);
    }

    /**
     * Will test manually publishing and unpublishing a Dockerhub/Gitlab entry with an alternate structure
     */
    @Test
    @Tag(SlowTest.NAME)
    void testDockerhubGitlabAlternateStructure() {
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, MANUAL_PUBLISH, "--registry",
            Registry.DOCKER_HUB.name(), Registry.DOCKER_HUB.toString(), "--namespace", "dockstoretestuser", "--name", "dockerhubandgitlab",
            "--git-url", "git@gitlab.com:dockstore.test.user/quayandgitlabalternate.git", "--git-reference", "master", "--toolname",
            "alternate", "--cwl-path", "/testDir/Dockstore.cwl", "--dockerfile-path", "/testDir/Dockerfile", SCRIPT_FLAG });

        final long count = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'alternate' and ispublished='t'", long.class);
        assertEquals(1, count, "there should be 1 entry");

        // Unpublish
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, PUBLISH, "--unpub", ENTRY,
            "registry.hub.docker.com/dockstoretestuser/dockerhubandgitlab/alternate", SCRIPT_FLAG });

        final long count3 = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'alternate' and ispublished='f'", long.class);
        assertEquals(1, count3, "there should be 1 entry");

    }

    /**
     * Checks that you can manually publish and unpublish a Dockerhub/Gitlab duplicate if different toolnames are set (but same Path)
     */
    @Test
    @Tag(SlowTest.NAME)
    void testDockerhubGitlabManualRegistrationDuplicates() {
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, MANUAL_PUBLISH, "--registry",
            Registry.DOCKER_HUB.name(), Registry.DOCKER_HUB.toString(), "--namespace", "dockstoretestuser", "--name", "dockerhubandgitlab",
            "--git-url", "git@gitlab.com:dockstore.test.user/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "regular",
            SCRIPT_FLAG });

        final long count = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'regular' and ispublished='t'", long.class);

        assertEquals(1, count, "there should be 1 entry");

        // Add duplicate entry with different toolname
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, MANUAL_PUBLISH, "--registry",
            Registry.DOCKER_HUB.name(), Registry.DOCKER_HUB.toString(), "--namespace", "dockstoretestuser", "--name", "dockerhubandgitlab",
            "--git-url", "git@gitlab.com:dockstore.test.user/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "regular2",
            SCRIPT_FLAG });

        // Unpublish the duplicate entries
        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname like 'regular%' and ispublished='t'", long.class);
        assertEquals(2, count2, "there should be 2 entries");

        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, PUBLISH, "--unpub", ENTRY,
            "registry.hub.docker.com/dockstoretestuser/dockerhubandgitlab/regular", SCRIPT_FLAG });
        final long count3 = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'regular2' and ispublished='t'", long.class);

        assertEquals(1, count3, "there should be 1 entry");

        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, PUBLISH, "--unpub", ENTRY,
            "registry.hub.docker.com/dockstoretestuser/dockerhubandgitlab/regular2", SCRIPT_FLAG });
        final long count4 = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname like 'regular%' and ispublished='t'", long.class);

        assertEquals(0, count4, "there should be 0 entries");

    }



    /**
     * This tests basic concepts with tool test parameter files
     */
    @Test
    void testTestJson() {
        // Refresh
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, REFRESH, ENTRY,
            "quay.io/dockstoretestuser/test_input_json" });

        // Check that no WDL or CWL test files
        final long count = testingPostgres.runSelectStatement("select count(*) from sourcefile where type like '%_TEST_JSON'", long.class);
        assertEquals(0, count, "there should be no sourcefiles that are test parameter files, there are " + count);

        // Update tag with test parameters
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, TEST_PARAMETER, ENTRY,
            "quay.io/dockstoretestuser/test_input_json", VERSION, "master", "--descriptor-type", CWL.toString(), "--add", "test.cwl.json",
            // Trying to remove a non-existent parameter file now fails
            "--add", "test2.cwl.json", "--add", "fake.cwl.json", /*"--remove", "notreal.cwl.json",*/ SCRIPT_FLAG });
        final long count2 = testingPostgres.runSelectStatement("select count(*) from sourcefile where type like '%_TEST_JSON'", long.class);
        assertEquals(2, count2, "there should be two sourcefiles that are test parameter files, there are " + count2);

        // Update tag with test parameters
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, TEST_PARAMETER, ENTRY,
            "quay.io/dockstoretestuser/test_input_json", VERSION, "master", "--descriptor-type", CWL.toString(), "--add", "test.cwl.json",
            "--remove", "test2.cwl.json", SCRIPT_FLAG });
        final long count3 = testingPostgres.runSelectStatement("select count(*) from sourcefile where type like '%_TEST_JSON'", long.class);
        assertEquals(1, count3, "there should be one sourcefile that is a test parameter file, there are " + count3);

        // Update tag wdltest with test parameters
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, TEST_PARAMETER, ENTRY,
            "quay.io/dockstoretestuser/test_input_json", VERSION, "wdltest", "--descriptor-type", WDL.toString(), "--add", "test.wdl.json",
            SCRIPT_FLAG });
        final long count4 = testingPostgres.runSelectStatement("select count(*) from sourcefile where type='WDL_TEST_JSON'", long.class);
        assertEquals(1, count4, "there should be one sourcefile that is a wdl test parameter file, there are " + count4);

        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, TEST_PARAMETER, ENTRY,
            "quay.io/dockstoretestuser/test_input_json", VERSION, "wdltest", "--descriptor-type", CWL.toString(), "--add", "test.cwl.json",
            SCRIPT_FLAG });
        final long count5 = testingPostgres.runSelectStatement("select count(*) from sourcefile where type='CWL_TEST_JSON'", long.class);
        assertEquals(2, count5, "there should be two sourcefiles that are test parameter files, there are " + count5);

        // refreshing again with the default paths set should not create extra redundant test parameter files
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, UPDATE_TOOL, ENTRY,
            "quay.io/dockstoretestuser/test_input_json", "--test-cwl-path", "test.cwl.json" });
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, UPDATE_TOOL, ENTRY,
            "quay.io/dockstoretestuser/test_input_json", "--test-wdl-path", "test.wdl.json" });
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, REFRESH, ENTRY,
            "quay.io/dockstoretestuser/test_input_json" });
        final List<Long> testJsonCounts = testingPostgres.runSelectListStatement(
            "select count(*) from sourcefile s, version_sourcefile vs where (s.type = 'CWL_TEST_JSON' or s.type = 'WDL_TEST_JSON') and s.id = vs.sourcefileid group by vs.versionid",
            long.class);
        assertTrue(testJsonCounts.size() >= 3,
                "there should be at least three sets of test json sourcefiles " + testJsonCounts.size());
        for (Long testJsonCount : testJsonCounts) {
            assertTrue(testJsonCount <= 2, "there should be at most two test json for each version");
        }
    }

    /**
     * This test is the same testTestJson, but CWL and WDL are in lowercase. This ensures that a user can use either
     * upper or lower case for CWL and WDL.
     */
    @Test
    void testTestJsonWithLowerCaseWDLandCWL() {
        // Refresh
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, REFRESH, ENTRY,
                "quay.io/dockstoretestuser/test_input_json" });

        // Check that no WDL or CWL test files
        final long count = testingPostgres.runSelectStatement("select count(*) from sourcefile where type like '%_TEST_JSON'", long.class);
        assertEquals(0, count, "there should be no sourcefiles that are test parameter files, there are " + count);

        // Update tag with test parameters
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, TEST_PARAMETER, ENTRY,
                "quay.io/dockstoretestuser/test_input_json", VERSION, "master", "--descriptor-type", CWL.toString().toLowerCase(), "--add", "test.cwl.json",
                // Trying to remove a non-existent parameter file now fails
                "--add", "test2.cwl.json", "--add", "fake.cwl.json", /*"--remove", "notreal.cwl.json",*/ SCRIPT_FLAG });
        final long count2 = testingPostgres.runSelectStatement("select count(*) from sourcefile where type like '%_TEST_JSON'", long.class);
        assertEquals(2, count2, "there should be two sourcefiles that are test parameter files, there are " + count2);

        // Update tag with test parameters
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, TEST_PARAMETER, ENTRY,
                "quay.io/dockstoretestuser/test_input_json", VERSION, "master", "--descriptor-type", CWL.toString().toLowerCase(), "--add", "test.cwl.json",
                "--remove", "test2.cwl.json", SCRIPT_FLAG });
        final long count3 = testingPostgres.runSelectStatement("select count(*) from sourcefile where type like '%_TEST_JSON'", long.class);
        assertEquals(1, count3, "there should be one sourcefile that is a test parameter file, there are " + count3);

        // Update tag wdltest with test parameters
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, TEST_PARAMETER, ENTRY,
                "quay.io/dockstoretestuser/test_input_json", VERSION, "wdltest", "--descriptor-type", WDL.toString().toLowerCase(), "--add", "test.wdl.json",
                SCRIPT_FLAG });
        final long count4 = testingPostgres.runSelectStatement("select count(*) from sourcefile where type='WDL_TEST_JSON'", long.class);
        assertEquals(1, count4, "there should be one sourcefile that is a wdl test parameter file, there are " + count4);

        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, TEST_PARAMETER, ENTRY,
                "quay.io/dockstoretestuser/test_input_json", VERSION, "wdltest", "--descriptor-type", CWL.toString().toLowerCase(), "--add", "test.cwl.json",
                SCRIPT_FLAG });
        final long count5 = testingPostgres.runSelectStatement("select count(*) from sourcefile where type='CWL_TEST_JSON'", long.class);
        assertEquals(2, count5, "there should be two sourcefiles that are test parameter files, there are " + count5);

        // refreshing again with the default paths set should not create extra redundant test parameter files
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, UPDATE_TOOL, ENTRY,
                "quay.io/dockstoretestuser/test_input_json", "--test-cwl-path", "test.cwl.json" });
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, UPDATE_TOOL, ENTRY,
                "quay.io/dockstoretestuser/test_input_json", "--test-wdl-path", "test.wdl.json" });
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, REFRESH, ENTRY,
                "quay.io/dockstoretestuser/test_input_json" });
        final List<Long> testJsonCounts = testingPostgres.runSelectListStatement(
                "select count(*) from sourcefile s, version_sourcefile vs where (s.type = 'CWL_TEST_JSON' or s.type = 'WDL_TEST_JSON') and s.id = vs.sourcefileid group by vs.versionid",
                long.class);
        assertTrue(testJsonCounts.size() >= 3,
                "there should be at least three sets of test json sourcefiles " + testJsonCounts.size());
        for (Long testJsonCount : testJsonCounts) {
            assertTrue(testJsonCount <= 2, "there should be at most two test json for each version");
        }
    }

    @Test
    void testTestParameterOtherUsers() {
        final ApiClient correctWebClient = getWebClient(BaseIT.USER_1_USERNAME, testingPostgres);
        final ApiClient otherWebClient = getWebClient(BaseIT.OTHER_USERNAME, testingPostgres);

        ContainersApi containersApi = new ContainersApi(correctWebClient);
        final DockstoreTool containerByToolPath = containersApi.getContainerByToolPath("quay.io/dockstoretestuser/test_input_json", null);
        containersApi.refresh(containerByToolPath.getId());

        // Check that no WDL or CWL test files
        final long count = testingPostgres.runSelectStatement("select count(*) from sourcefile where type like '%_TEST_JSON'", long.class);
        assertEquals(0, count, "there should be no sourcefiles that are test parameter files, there are " + count);

        containersApi
            .addTestParameterFiles(containerByToolPath.getId(), "", Collections.singletonList("/test.json"),
                "master", CWL.toString());

        boolean shouldFail = false;
        try {
            final ContainersApi containersApi1 = new ContainersApi(otherWebClient);
            containersApi1.addTestParameterFiles(containerByToolPath.getId(), "", Collections.singletonList("/test2.cwl.json"),
                CWL.toString(), "master");
        } catch (Exception e) {
            shouldFail = true;
        }
        assertTrue(shouldFail);

        containersApi
            .addTestParameterFiles(containerByToolPath.getId(), "", Collections.singletonList("/test2.cwl.json"),
                 "master",  CWL.toString());

        final long count3 = testingPostgres.runSelectStatement("select count(*) from sourcefile where type like '%_TEST_JSON'", long.class);
        assertEquals(2, count3, "there should be one sourcefile that is a test parameter file, there are " + count3);

        // start testing deletion
        shouldFail = false;
        try {
            final ContainersApi containersApi1 = new ContainersApi(otherWebClient);
            containersApi1.deleteTestParameterFiles(containerByToolPath.getId(), Collections.singletonList("/test2.cwl.json"),
                CWL.toString(), "master");
        } catch (Exception e) {
            shouldFail = true;
        }
        assertTrue(shouldFail);
        containersApi
            .deleteTestParameterFiles(containerByToolPath.getId(), Collections.singletonList("/test.json"),
                "master", CWL.toString());
        containersApi.deleteTestParameterFiles(containerByToolPath.getId(), Collections.singletonList("/test2.cwl.json"),
            "master", CWL.toString());

        final long count4 = testingPostgres.runSelectStatement("select count(*) from sourcefile where type like '%_TEST_JSON'", long.class);
        assertEquals(0, count4, "there should be one sourcefile that is a test parameter file, there are " + count4);
    }

    /**
     * This tests some cases for private tools
     */
    @Test
    void testPrivateManualPublish() throws Exception {
        // Setup DB

        // Manual publish private repo with tool maintainer email
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, MANUAL_PUBLISH, "--registry",
            Registry.DOCKER_HUB.name(), "--namespace", "dockstoretestuser", "--name", "private_test_repo", "--git-url",
            "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "tool1",
            "--tool-maintainer-email", "testemail@domain.com", "--private", "true", SCRIPT_FLAG });

        // The tool should be private, published and have the correct email
        final long count = testingPostgres.runSelectStatement(
            "select count(*) from tool where ispublished='true' and privateaccess='true' and toolmaintaineremail='testemail@domain.com'",
            long.class);
        assertEquals(1, count, "one tool should be private and published, there are " + count);

        // Manual publish public repo
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, MANUAL_PUBLISH, "--registry",
            Registry.DOCKER_HUB.name(), "--namespace", "dockstoretestuser", "--name", "private_test_repo", "--git-url",
            "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "tool2", SCRIPT_FLAG });

        // NOTE: The tool should not have an associated email

        // Should not be able to convert to a private repo since it is published and has no email
        int exitCode = catchSystemExit(() -> Client.main(
                new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, UPDATE_TOOL, ENTRY,
                        "registry.hub.docker.com/dockstoretestuser/private_test_repo/tool2", "--private", "true", SCRIPT_FLAG }));
        assertEquals(Client.CLIENT_ERROR, exitCode);

        // Give the tool a tool maintainer email
        Client.main(
            new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, UPDATE_TOOL, ENTRY,
                "registry.hub.docker.com/dockstoretestuser/private_test_repo/tool2", "--private", "true", "--tool-maintainer-email",
                "testemail@domain.com", SCRIPT_FLAG });
    }

    /**
     * This tests that you can convert a published public tool to private if it has a tool maintainer email set
     */
    @Test
    void testPublicToPrivateToPublicTool() {
        // Setup DB

        // Manual publish public repo
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, MANUAL_PUBLISH, "--registry",
            Registry.DOCKER_HUB.name(), Registry.DOCKER_HUB.toString(), "--namespace", "dockstoretestuser", "--name", "private_test_repo",
            "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "tool1",
            SCRIPT_FLAG });

        // NOTE: The tool should not have an associated email

        // Give the tool a tool maintainer email and make private
        Client.main(
            new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, UPDATE_TOOL, ENTRY,
                "registry.hub.docker.com/dockstoretestuser/private_test_repo/tool1", "--private", "true", "--tool-maintainer-email",
                "testemail@domain.com", SCRIPT_FLAG });

        // The tool should be private, published and have the correct email
        final long count = testingPostgres.runSelectStatement(
            "select count(*) from tool where ispublished='true' and privateaccess='true' and toolmaintaineremail='testemail@domain.com'",
            long.class);
        assertEquals(1, count, "one tool should be private and published, there are " + count);

        // Convert the tool back to public
        Client.main(
            new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, UPDATE_TOOL, ENTRY,
                "registry.hub.docker.com/dockstoretestuser/private_test_repo/tool1", "--private", "false", SCRIPT_FLAG });

        // Check that the tool is no longer private
        final long count2 = testingPostgres.runSelectStatement(
            "select count(*) from tool where ispublished='true' and privateaccess='true' and toolmaintaineremail='testemail@domain.com'",
            long.class);
        assertEquals(0, count2, "no tool should be private, but there are " + count2);

    }

    /**
     * This tests that you can change a tool from public to private without a tool maintainer email, as long as an email is found in the descriptor
     */
    @Test
    void testDefaultToEmailInDescriptorForPrivateRepos() {
        // Setup DB

        // Manual publish public repo
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, MANUAL_PUBLISH, "--registry",
            Registry.DOCKER_HUB.name(), Registry.DOCKER_HUB.toString(), "--namespace", "dockstoretestuser", "--name", "private_test_repo",
            "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay-2.git", "--git-reference", "master", "--toolname", "tool1",
            SCRIPT_FLAG });

        // NOTE: The tool should have an associated email

        // Make the tool private
        Client.main(
            new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, UPDATE_TOOL, ENTRY,
                "registry.hub.docker.com/dockstoretestuser/private_test_repo/tool1", "--private", "true", SCRIPT_FLAG });

        // The tool should be private, published and not have a maintainer email
        final long count = testingPostgres
            .runSelectStatement("select count(*) from tool where ispublished='true' and privateaccess='true' and toolmaintaineremail=''",
                long.class);
        assertEquals(1, count, "one tool should be private and published, there are " + count);

        // Convert the tool back to public
        Client.main(
            new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, UPDATE_TOOL, ENTRY,
                "registry.hub.docker.com/dockstoretestuser/private_test_repo/tool1", "--private", "false", SCRIPT_FLAG });

        // Check that the tool is no longer private
        final long count2 = testingPostgres.runSelectStatement(
            "select count(*) from tool where ispublished='true' and privateaccess='true' and toolmaintaineremail='testemail@domain.com'",
            long.class);
        assertEquals(0, count2, "no tool should be private, but there are " + count2);

        // Make the tool private but this time define a tool maintainer
        Client.main(
            new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, UPDATE_TOOL, ENTRY,
                "registry.hub.docker.com/dockstoretestuser/private_test_repo/tool1", "--private", "true", "--tool-maintainer-email",
                "testemail2@domain.com", SCRIPT_FLAG });

        // Check that the tool is no longer private
        final long count3 = testingPostgres.runSelectStatement(
            "select count(*) from tool where ispublished='true' and privateaccess='true' and toolmaintaineremail='testemail2@domain.com'",
            long.class);
        assertEquals(1, count3, "one tool should be private and published, there are " + count3);
    }

    /**
     * This tests that you cannot manually publish a private tool unless it has a tool maintainer email
     */
    @Test
    void testPrivateManualPublishNoToolMaintainerEmail() throws Exception {
        // Manual publish private repo without tool maintainer email
        int exitCode = catchSystemExit(() -> Client.main(
                new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, MANUAL_PUBLISH, "--registry",
                        Registry.DOCKER_HUB.name(), Registry.DOCKER_HUB.toString(), "--namespace", "dockstoretestuser", "--name",
                        "private_test_repo", "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference",
                        "master", "--private", "true", SCRIPT_FLAG }));
        assertEquals(Client.CLIENT_ERROR, exitCode);
    }

    /**
     * This tests that you can manually publish a gitlab registry image
     */
    @Test
    @Tag(SlowTest.NAME)
    void testManualPublishGitlabDocker() throws Exception {
        // Setup database

        // Manual publish
        catchSystemExit(() -> Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, MANUAL_PUBLISH, "--registry",
            Registry.GITLAB.name(), "--namespace", "dockstore.test.user", "--name", "dockstore-whalesay",
            "--git-url", "git@gitlab.com:dockstore.test.user/dockstore-whalesay.git", "--git-reference", "master", "--toolname",
            "alternate", "--private", "true", "--tool-maintainer-email", "duncan.andrew.g@gmail.com", SCRIPT_FLAG }));

        // Check that tool exists and is published
        final long count = testingPostgres
            .runSelectStatement("select count(*) from tool where ispublished='true' and privateaccess='true'", long.class);
        assertEquals(1, count, "one tool should be private and published, there are " + count);
    }

    /**
     * This tests that you can manually publish a private only registry (Amazon ECR), but you can't change the tool to public
     */
    @Test
    void testManualPublishPrivateOnlyRegistry() throws Exception {
        // Setup database

        // Manual publish
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, MANUAL_PUBLISH, "--registry",
            Registry.AMAZON_ECR.name(), "--namespace", "notarealnamespace", "--name", "notarealname", "--git-url",
            "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "alternate", "--private",
            "true", "--tool-maintainer-email", "duncan.andrew.g@gmail.com", "--custom-docker-path", "test.dkr.ecr.test.amazonaws.com",
            SCRIPT_FLAG });

        // Check that tool is published and has correct values
        final long count = testingPostgres.runSelectStatement(
            "select count(*) from tool where ispublished='true' and privateaccess='true' and registry='test.dkr.ecr.test.amazonaws.com' and namespace = 'notarealnamespace' and name = 'notarealname'",
            long.class);
        assertEquals(1, count, "one tool should be private, published and from amazon, there are " + count);

        // Update tool to public (shouldn't work)
        int exitCode = catchSystemExit(() -> Client.main(
                new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, UPDATE_TOOL, ENTRY,
                        "test.dkr.ecr.test.amazonaws.com/notarealnamespace/notarealname/alternate", "--private", "false", SCRIPT_FLAG }));
        assertEquals(Client.API_ERROR, exitCode);
    }

    /**
     * This tests that you can manually publish a private only registry (Seven Bridges), but you can't change the tool to public
     */
    @Test
    void testManualPublishSevenBridgesTool() throws Exception {
        // Setup database

        // Manual publish
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, MANUAL_PUBLISH, "--registry",
            Registry.SEVEN_BRIDGES.name(), "--namespace", "notarealnamespace", "--name", "notarealname", "--git-url",
            "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "alternate", "--private",
            "true", "--tool-maintainer-email", "duncan.andrew.g@gmail.com", "--custom-docker-path", "images.sbgenomics.com", SCRIPT_FLAG });

        // Check that tool is published and has correct values
        final long count = testingPostgres.runSelectStatement(
            "select count(*) from tool where ispublished='true' and privateaccess='true' and registry='images.sbgenomics.com' and namespace = 'notarealnamespace' and name = 'notarealname'",
            long.class);
        assertEquals(1, count, "one tool should be private, published and from seven bridges, there are " + count);

        // Update tool to public (shouldn't work)
        int exitCode = catchSystemExit(() -> Client.main(
                new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, UPDATE_TOOL, ENTRY,
                        "images.sbgenomics.com/notarealnamespace/notarealname/alternate", "--private", "false", SCRIPT_FLAG }));
        assertEquals(Client.CLIENT_ERROR, exitCode);
    }

    /**
     * This tests that you can't manually publish a private only registry (Seven Bridges) with an incorrect registry path
     */
    @Test
    void testManualPublishSevenBridgesToolIncorrectRegistryPath() throws Exception {
        // Setup database

        // Manual publish correct path
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, MANUAL_PUBLISH, "--registry",
            Registry.SEVEN_BRIDGES.name(), "--namespace", "notarealnamespace", "--name", "notarealname", "--git-url",
            "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "alternate", "--private",
            "true", "--tool-maintainer-email", "duncan.andrew.g@gmail.com", "--custom-docker-path", "test-images.sbgenomics.com",
            SCRIPT_FLAG });

        // Check that tool is published and has correct values
        final long count = testingPostgres.runSelectStatement(
            "select count(*) from tool where ispublished='true' and privateaccess='true' and registry='test-images.sbgenomics.com' and namespace = 'notarealnamespace' and name = 'notarealname'",
            long.class);
        assertEquals(1, count, "one tool should be private, published and from seven bridges, there are " + count);

        // Manual publish incorrect path
        int exitCode = catchSystemExit(() -> Client.main(
                new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, MANUAL_PUBLISH, "--registry",
                        Registry.SEVEN_BRIDGES.name(), "--namespace", "notarealnamespace", "--name", "notarealname", "--git-url",
                        "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "alternate",
                        "--private", "true", "--tool-maintainer-email", "duncan.andrew.g@gmail.com", "--custom-docker-path",
                        "testimages.sbgenomics.com", SCRIPT_FLAG }));
        assertEquals(Client.CLIENT_ERROR, exitCode);
    }

    /**
     * This tests that you can't manually publish a private only registry as public
     */
    @Test
    void testManualPublishPrivateOnlyRegistryAsPublic() throws Exception {
        // Manual publish
        int exitCode = catchSystemExit(() -> Client.main(
                new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, MANUAL_PUBLISH, "--registry",
                        Registry.AMAZON_ECR.name(), "--namespace", "notarealnamespace", "--name", "notarealname", "--git-url",
                        "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "alternate",
                        "--tool-maintainer-email", "duncan.andrew.g@gmail.com", "--custom-docker-path", "amazon.registry", SCRIPT_FLAG }));
        assertEquals(Client.CLIENT_ERROR, exitCode);
    }

    /**
     * This tests that you can't manually publish a tool from a registry that requires a custom docker path without specifying the path
     */
    @Test
    void testManualPublishCustomDockerPathRegistry() throws Exception {
        // Manual publish
        int exitCode = catchSystemExit(() -> Client.main(
                new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, MANUAL_PUBLISH, "--registry",
                        Registry.AMAZON_ECR.name(), "--namespace", "notarealnamespace", "--name", "notarealname", "--git-url",
                        "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "alternate",
                        "--private", "true", "--tool-maintainer-email", "duncan.andrew.g@gmail.com", SCRIPT_FLAG }));
        assertEquals(Client.CLIENT_ERROR, exitCode);
    }

    /**
     * Test the "dockstore tool publish" command
     */
    @Test
    void testPublishList() {
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, PUBLISH, SCRIPT_FLAG });
        assertTrue(systemOutRule.getText().contains("quay.io/dockstoretestuser/noautobuild"),
                "Should have contained the unpublished tool belonging to the user");
        assertFalse(systemOutRule.getText().contains("quay.io/test_org/test1"),
                "Should not have contained the unpublished tool belonging to another user");
    }



    @Test
    void launchToolChecksumValidation() {

        // manual publish the tool
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, PUBLISH, ENTRY,
            "quay.io/dockstoretestuser/test_input_json", SCRIPT_FLAG });

        // refresh the tool
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, REFRESH, ENTRY,
            "quay.io/dockstoretestuser/test_input_json", SCRIPT_FLAG });

        // launch the tool
        systemOutRule.clear();
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, LAUNCH, ENTRY,
            "quay.io/dockstoretestuser/test_input_json", JSON, ResourceHelpers.resourceFilePath("tool_hello_world.json"), SCRIPT_FLAG });
        assertTrue(
                systemOutRule.getText().contains(CHECKSUM_VALIDATED_MESSAGE) && !systemOutRule.getText().contains(CHECKSUM_NULL_MESSAGE),
                "Output should indicate that checksums have been validated");

        // unpublish the tool
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, PUBLISH, "--unpub", ENTRY,
            "quay.io/dockstoretestuser/test_input_json", SCRIPT_FLAG });

        // launch the unpublished tool
        systemOutRule.clear();
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, LAUNCH, ENTRY,
            "quay.io/dockstoretestuser/test_input_json", JSON, ResourceHelpers.resourceFilePath("tool_hello_world.json"), SCRIPT_FLAG });
        assertTrue(
                systemOutRule.getText().contains(CHECKSUM_VALIDATED_MESSAGE) && !systemOutRule.getText().contains(CHECKSUM_NULL_MESSAGE),
                "Output should indicate that checksums have been validated");
    }

    @Test
    void testPublishUnpublishHostedTool() {

        final String publishNameParameter = "--new-entry-name";

        // Create a hosted tool
        final ApiClient webClient = getWebClient(BaseIT.USER_1_USERNAME, testingPostgres);
        HostedApi hostedApi = new HostedApi(webClient);
        hostedApi.createHostedTool(Registry.QUAY_IO.getDockerPath().toLowerCase(), "testHosted",
                CWL.getShortName(), "hostedToolNamespace", null);

        // verify there is an unpublished hosted tool
        final long initialUnpublishedHostedCount = testingPostgres.runSelectStatement(
                "SELECT COUNT(*) FROM tool WHERE namespace='hostedToolNamespace' " + "AND name='testHosted' AND mode='HOSTED' AND ispublished='f';", long.class);
        assertEquals(1, initialUnpublishedHostedCount, "There should be 1 unpublished hosted tool");

        // attempt to publish the tool with a custom name, should fail
        systemErrRule.clear();
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--entry",
                "quay.io/hostedToolNamespace/testHosted", publishNameParameter, "fakeName", "--script" });
        assertTrue(systemErrRule.getText().contains(ToolClient.INVALID_TOOL_MODE_PUBLISH), "User should be notified that the command is invalid");

        // verify there are no new published tools with the above/original name
        final long initialPublishedHostedCount = testingPostgres.runSelectStatement(
                "SELECT COUNT(*) FROM tool WHERE namespace='hostedToolNamespace' " + "AND mode='HOSTED' AND ispublished='t';", long.class);
        assertEquals(0, initialPublishedHostedCount, "There should be 0 published hosted tools for this namespace");

        // call publish normally
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--entry",
                "quay.io/hostedToolNamespace/testHosted", "--script" });

        // verify the tool is published
        final long finalPublishedHostedCount = testingPostgres.runSelectStatement(
                "SELECT COUNT(*) FROM tool WHERE namespace='hostedToolNamespace' " + "AND mode='HOSTED' AND ispublished='t';", long.class);
        assertEquals(1, finalPublishedHostedCount, "There should be 1 published hosted tool");
    }
}
