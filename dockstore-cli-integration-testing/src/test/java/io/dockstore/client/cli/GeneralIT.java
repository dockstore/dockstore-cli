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

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import io.dockstore.client.cli.nested.ToolClient;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.Registry;
import io.dockstore.common.ToilCompatibleTest;
import io.dockstore.common.ToolTest;
import io.dropwizard.testing.ResourceHelpers;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.ContainersApi;
import io.swagger.client.model.DockstoreTool;
import io.swagger.client.model.PublishRequest;
import io.swagger.client.model.Tag;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
     * Checks that auto upgrade works and that the dockstore CLI is updated to the latest tag
     * Must be run after class since upgrading before tests may cause them to fail
     */
    @Ignore
    public static void testAutoUpgrade() {
        String installLocation = Client.getInstallLocation();
        String latestVersion = Client.getLatestVersion();

        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "--upgrade", "--script" });
        String currentVersion = Client.getCurrentVersion();

        if (installLocation != null && latestVersion != null && currentVersion != null) {
            Assert.assertEquals("Dockstore CLI should now be up to date with the latest stable tag.", currentVersion, latestVersion);
        }
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

    /**
     * Checks that you can't add/remove labels unless they all are of proper format
     */
    @Test
    public void testLabelIncorrectInput() {
        systemExit.expectSystemExitWithStatus(Client.CLIENT_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "label", "--entry",
            "quay.io/dockstoretestuser2/quayandgithub", "--add", "docker-hub", "--add", "quay.io", "--script" });
    }

    /**
     * Tests adding/editing/deleting container related labels (for search)
     */
    @Test
    public void testAddEditRemoveLabel() {
        // Test adding/removing labels for different containers
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "label", "--entry",
            "quay.io/dockstoretestuser2/quayandgithub", "--add", "quay", "--add", "github", "--remove", "dockerhub", "--script" });
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "label", "--entry",
            "quay.io/dockstoretestuser2/quayandgithub", "--add", "github", "--add", "dockerhub", "--remove", "quay", "--script" });

        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "label", "--entry",
            "quay.io/dockstoretestuser2/quayandgithubalternate", "--add", "alternate", "--add", "github", "--script" });
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "label", "--entry",
            "quay.io/dockstoretestuser2/quayandgithubalternate", "--remove", "github", "--script" });

        final long count = testingPostgres.runSelectStatement("select count(*) from entry_label where entryid = '2'", long.class);
        assertEquals("there should be 2 labels for the given container, there are " + count, 2, count);

        final long count2 = testingPostgres.runSelectStatement(
            "select count(*) from label where value = 'quay' or value = 'github' or value = 'dockerhub' or value = 'alternate'",
            long.class);
        assertEquals("there should be 4 labels in the database (No Duplicates), there are " + count2, 4, count2);
    }

    /**
     * Tests altering the cwl and dockerfile paths to invalid locations (quick registered)
     */
    @Test
    public void testVersionTagWDLCWLAndDockerfilePathsAlteration() {
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "version_tag", "update", "--entry",
                "quay.io/dockstoretestuser2/quayandgithub", "--name", "master", "--cwl-path", "/testDir/Dockstore.cwl", "--wdl-path",
                "/testDir/Dockstore.wdl", "--dockerfile-path", "/testDir/Dockerfile", "--script" });

        final long count = testingPostgres.runSelectStatement(
            "select count(*) from tag,tool where tool.registry = '" + Registry.QUAY_IO.getDockerPath()
                + "' and tool.namespace = 'dockstoretestuser2' and tool.name = 'quayandgithub' and tool.toolname IS NULL and tool.id=tag.parentid and valid = 'f'",
            long.class);
        assertEquals("there should now be an invalid tag, found " + count, 1, count);

        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "version_tag", "update", "--entry",
                "quay.io/dockstoretestuser2/quayandgithub", "--name", "master", "--cwl-path", "/Dockstore.cwl", "--wdl-path",
                "/Dockstore.wdl", "--dockerfile-path", "/Dockerfile", "--script" });

        final long count2 = testingPostgres.runSelectStatement(
            "select count(*) from tag,tool where tool.registry = '" + Registry.QUAY_IO.getDockerPath()
                + "' and tool.namespace = 'dockstoretestuser2' and tool.name = 'quayandgithub' and tool.toolname IS NULL and tool.id=tag.parentid and valid = 'f'",
            long.class);
        assertEquals("the invalid tag should now be valid, found " + count2, 0, count2);
    }

    /**
     * Test trying to remove a tag for auto build
     */
    @Test
    public void testVersionTagRemoveAutoContainer() {
        systemExit.expectSystemExitWithStatus(Client.CLIENT_ERROR);
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "version_tag", "remove", "--entry",
                "quay.io/dockstoretestuser2/quayandgithub", "--name", "master", "--script" });
    }

    /**
     * Test trying to add a tag for auto build
     */
    @Test
    public void testVersionTagAddAutoContainer() {
        systemExit.expectSystemExitWithStatus(Client.CLIENT_ERROR);
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "version_tag", "add", "--entry",
                "quay.io/dockstoretestuser2/quayandgithub", "--name", "masterTest", "--image-id",
                "4728f8f5ce1709ec8b8a5282e274e63de3c67b95f03a519191e6ea675c5d34e8", "--git-reference", "master", "--script" });
    }

    /**
     * Tests adding tags to a manually registered container
     */
    @Test
    public void testAddVersionTagManualContainer() {
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "manual_publish", "--registry",
            Registry.QUAY_IO.name(), "--namespace", "dockstoretestuser2", "--name", "quayandgithub", "--git-url",
            "git@github.com:dockstoretestuser2/quayandgithubalternate.git", "--git-reference", "master", "--toolname", "alternate",
            "--cwl-path", "/testDir/Dockstore.cwl", "--dockerfile-path", "/testDir/Dockerfile", "--script" });

        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "version_tag", "add", "--entry",
                "quay.io/dockstoretestuser2/quayandgithub/alternate", "--name", "masterTest", "--image-id",
                "4728f8f5ce1709ec8b8a5282e274e63de3c67b95f03a519191e6ea675c5d34e8", "--git-reference", "master", "--script" });

        final long count = testingPostgres.runSelectStatement(
            " select count(*) from  tag, tool where tag.parentid = tool.id and giturl ='git@github.com:dockstoretestuser2/quayandgithubalternate.git' and toolname = 'alternate'",
            long.class);
        assertEquals(
            "there should be 3 tags, 2  that are autogenerated (master and latest) and the newly added masterTest tag, found " + count, 3,
            count);

    }

    /**
     * Tests hiding and unhiding different versions of a container (quick registered)
     */
    @Test
    public void testVersionTagHide() {
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "version_tag", "update", "--entry",
                "quay.io/dockstoretestuser2/quayandgithub", "--name", "master", "--hidden", "true", "--script" });
        final long count = testingPostgres
            .runSelectStatement("select count(*) from tag t, version_metadata vm where vm.hidden = 't' and t.id = vm.id", long.class);
        assertEquals("there should be 1 hidden tag", 1, count);

        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "version_tag", "update", "--entry",
                "quay.io/dockstoretestuser2/quayandgithub", "--name", "master", "--hidden", "false", "--script" });
        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from tag t, version_metadata vm where vm.hidden = 't' and t.id = vm.id", long.class);
        assertEquals("there should be 0 hidden tag", 0, count2);
    }

    /**
     * Will test deleting a tag from a manually registered container
     */
    @Test
    public void testVersionTagDelete() {
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "manual_publish", "--registry",
            Registry.QUAY_IO.name(), "--namespace", "dockstoretestuser2", "--name", "quayandgithub", "--git-url",
            "git@github.com:dockstoretestuser2/quayandgithubalternate.git", "--git-reference", "master", "--toolname", "alternate",
            "--cwl-path", "/testDir/Dockstore.cwl", "--wdl-path", "/testDir/Dockstore.wdl", "--dockerfile-path", "/testDir/Dockerfile",
            "--script" });

        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "version_tag", "add", "--entry",
                "quay.io/dockstoretestuser2/quayandgithub/alternate", "--name", "masterTest", "--image-id",
                "4728f8f5ce1709ec8b8a5282e274e63de3c67b95f03a519191e6ea675c5d34e8", "--git-reference", "master", "--script" });

        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "version_tag", "remove", "--entry",
                "quay.io/dockstoretestuser2/quayandgithub/alternate", "--name", "masterTest", "--script" });

        final long count = testingPostgres.runSelectStatement("select count(*) from tag where name = 'masterTest'", long.class);
        assertEquals("there should be no tags with the name masterTest", 0, count);
    }

    /**
     * Check that refreshing an incorrect individual container won't work
     */
    @Test
    public void testRefreshIncorrectContainer() {
        systemExit.expectSystemExitWithStatus(Client.API_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "refresh", "--entry",
            "quay.io/dockstoretestuser2/unknowncontainer", "--script" });
    }

    /**
     * Tests that tool2JSON works for entries on Dockstore
     */
    @Test
    public void testTool2JSONWDL() {
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "publish", "--entry",
            "quay.io/dockstoretestuser2/quayandgithubwdl" });
        // need to publish before converting
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "convert", "entry2json", "--entry",
                "quay.io/dockstoretestuser2/quayandgithubwdl", "--descriptor", "wdl", "--script" });
        // TODO: Test that output is the expected WDL file
        Assert.assertTrue(systemOutRule.getLog().contains("\"test.hello.name\": \"String\""));
    }

    @Test
    public void registerUnregisterAndCopy() {
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "publish", "--entry",
            "quay.io/dockstoretestuser2/quayandgithubwdl" });

        boolean published = testingPostgres.runSelectStatement(
            "select ispublished from tool where registry = '" + Registry.QUAY_IO.getDockerPath()
                + "' and namespace = 'dockstoretestuser2' and name = 'quayandgithubwdl';", boolean.class);
        assertTrue("tool not published", published);

        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "publish", "--entry",
            "quay.io/dockstoretestuser2/quayandgithubwdl", "--entryname", "foo" });

        long count = testingPostgres.runSelectStatement("select count(*) from tool where registry = '" + Registry.QUAY_IO.getDockerPath()
            + "' and namespace = 'dockstoretestuser2' and name = 'quayandgithubwdl';", long.class);
        assertEquals("should be two after republishing", 2, count);

        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "publish", "--unpub", "--entry",
                "quay.io/dockstoretestuser2/quayandgithubwdl" });

        published = testingPostgres.runSelectStatement("select ispublished from tool where registry = '" + Registry.QUAY_IO.getDockerPath()
            + "' and namespace = 'dockstoretestuser2' and name = 'quayandgithubwdl' and toolname IS NULL;", boolean.class);
        assertFalse(published);
    }

    /**
     * Tests that WDL2JSON works for local file
     */
    @Test
    public void testWDL2JSON() {
        File sourceFile = new File(ResourceHelpers.resourceFilePath("wdl.wdl"));
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "convert", "wdl2json", "--wdl",
            sourceFile.getAbsolutePath(), "--script" });
        // TODO: Test that output is the expected WDL file
    }

    @Test
    @Category(ToilCompatibleTest.class)
    public void testCWL2JSON() {
        File sourceFile = new File(ResourceHelpers.resourceFilePath("dockstore-tool-bamstats.cwl"));
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "convert", "cwl2json", "--cwl",
            sourceFile.getAbsolutePath(), "--script" });
        // TODO: Test that output is the expected JSON file
    }

    @Test
    @Category(ToilCompatibleTest.class)
    public void testCWL2YAML() {
        File sourceFile = new File(ResourceHelpers.resourceFilePath("dockstore-tool-bamstats.cwl"));
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "convert", "cwl2yaml", "--cwl",
            sourceFile.getAbsolutePath(), "--script" });
        // TODO: Test that output is the expected yaml file
    }

    /**
     * Check that a user can't refresh another users container
     */
    @Test
    public void testRefreshOtherUsersContainer() {
        systemExit.expectSystemExitWithStatus(Client.API_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "refresh", "--entry",
            "quay.io/test_org/test1", "--script" });
    }

    /**
     * Tests that WDL and CWL files can be grabbed from the command line
     */
    @Test
    public void testGetWdlAndCwl() {
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "publish", "--entry",
            "quay.io/dockstoretestuser2/quayandgithubwdl" });
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "wdl", "--entry",
            "quay.io/dockstoretestuser2/quayandgithubwdl", "--script" });
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "publish", "--entry",
            "quay.io/dockstoretestuser2/quayandgithub" });
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "cwl", "--entry",
            "quay.io/dockstoretestuser2/quayandgithub", "--script" });
    }

    /**
     * Tests that attempting to get a WDL file when none exists won't work
     */
    @Test
    public void testGetWdlFailure() {
        systemExit.expectSystemExitWithStatus(Client.API_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "wdl", "--entry",
            "quay.io/dockstoretestuser2/quayandgithub", "--script" });
    }

    /**
     * Tests that a user can only add Quay containers that they own directly or through an organization
     */
    @Test
    public void testUserPrivilege() {
        // Repo user has access to
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "manual_publish", "--registry",
            Registry.QUAY_IO.name(), "--namespace", "dockstoretestuser2", "--name", "quayandgithub", "--git-url",
            "git@github.com:dockstoretestuser2/quayandgithubalternate.git", "--git-reference", "master", "--toolname", "testTool",
            "--cwl-path", "/testDir/Dockstore.cwl", "--dockerfile-path", "/testDir/Dockerfile", "--script" });
        final long count = testingPostgres.runSelectStatement("select count(*) from tool where registry = '" + Registry.QUAY_IO.getDockerPath()
            + "' and namespace = 'dockstoretestuser2' and name = 'quayandgithub' and toolname = 'testTool'", long.class);
        assertEquals("the container should exist", 1, count);

        // Repo user is part of org
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "manual_publish", "--registry",
            Registry.QUAY_IO.name(), "--namespace", "dockstore2", "--name", "testrepo2", "--git-url",
            "git@github.com:dockstoretestuser2/quayandgithub.git", "--git-reference", "master", "--toolname", "testOrg", "--cwl-path",
            "/Dockstore.cwl", "--dockerfile-path", "/Dockerfile", "--script" });
        final long count2 = testingPostgres.runSelectStatement("select count(*) from tool where registry = '" + Registry.QUAY_IO.getDockerPath()
            + "' and namespace = 'dockstore2' and name = 'testrepo2' and toolname = 'testOrg'", long.class);
        assertEquals("the container should exist", 1, count2);

        // Repo user doesn't own
        systemExit.expectSystemExitWithStatus(Client.API_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "manual_publish", "--registry",
            Registry.QUAY_IO.name(), "--namespace", "dockstoretestuser", "--name", "testrepo", "--git-url",
            "git@github.com:dockstoretestuser/quayandgithub.git", "--git-reference", "master", "--toolname", "testTool", "--cwl-path",
            "/Dockstore.cwl", "--dockerfile-path", "/Dockerfile", "--script" });
    }

    /**
     * This tests that zip file can be downloaded or not based on published state and auth.
     */
    @Test
    public void downloadZipFileTestAuth() throws IOException {
        final ApiClient ownerWebClient = getWebClient(USER_2_USERNAME, testingPostgres);
        ContainersApi ownerContainersApi = new ContainersApi(ownerWebClient);

        final ApiClient anonWebClient = CommonTestUtilities.getWebClient(false, null, testingPostgres);
        ContainersApi anonContainersApi = new ContainersApi(anonWebClient);

        final ApiClient otherUserWebClient = CommonTestUtilities.getWebClient(true, OTHER_USERNAME, testingPostgres);
        ContainersApi otherUserContainersApi = new ContainersApi(otherUserWebClient);

        // Register and refresh tool
        DockstoreTool tool = ownerContainersApi.registerManual(CommonTestUtilities.getContainer());
        DockstoreTool refresh = ownerContainersApi.refresh(tool.getId());
        Long toolId = refresh.getId();
        Tag tag = refresh.getWorkflowVersions().get(0);
        Long versionId = tag.getId();

        // Try downloading unpublished
        // Owner: Should pass
        ownerContainersApi.getToolZip(toolId, versionId);
        // Anon: Should fail
        boolean success = true;
        try {
            anonContainersApi.getToolZip(toolId, versionId);
        } catch (ApiException ex) {
            success = false;
        } finally {
            assertFalse("User does not have access to tool.", success);
        }
        // Other user: Should fail
        success = true;
        try {
            otherUserContainersApi.getToolZip(toolId, versionId);
        } catch (ApiException ex) {
            success = false;
        } finally {
            assertFalse("User does not have access to tool.", success);
        }

        // Publish
        PublishRequest publishRequest = SwaggerUtility.createPublishRequest(true);
        ownerContainersApi.publish(toolId, publishRequest);

        // Try downloading published
        // Owner: Should pass
        ownerContainersApi.getToolZip(toolId, versionId);
        // Anon: Should pass
        anonContainersApi.getToolZip(toolId, versionId);
        // Other user: Should pass
        otherUserContainersApi.getToolZip(toolId, versionId);

        // test that these zips can be downloaded via CLI

        // download zip via CLI
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "download", "--entry",
            refresh.getToolPath() + ":" + tag.getName(), "--zip", "--script" });
        File downloadedZip = new File(new ToolClient(null, false).zipFilename(refresh));
        // record entries
        List<String> collect = new ZipFile(downloadedZip).stream().map(ZipEntry::getName).collect(Collectors.toList());
        assert (downloadedZip.exists());
        assert (downloadedZip.delete());

        // download and unzip via CLI while at it
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "download", "--entry",
            refresh.getToolPath() + ":" + tag.getName(), "--script" });
        collect.forEach(entry -> {
            File innerFile = new File(System.getProperty("user.dir"), entry);
            assert (innerFile.exists());
            assert (innerFile.delete());
        });
    }

}
