package io.dockstore.client.cli;

import io.dockstore.client.cli.nested.ToolClient;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.FlushingSystemErrRule;
import io.dockstore.common.FlushingSystemOutRule;
import io.dockstore.common.Registry;
import io.dockstore.common.SourceControl;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@Category({ ConfidentialTest.class, ToolTest.class })
public class QuayGitHubBasicIT extends BaseIT {
    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();

    @Rule
    public final SystemOutRule systemOutRule = new FlushingSystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new FlushingSystemErrRule().enableLog().muteForSuccessfulTests();

    @Before
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate1(SUPPORT, testingPostgres);
    }

    /*
     * Test Quay and Github -
     * These tests are focused on testing tools created from Quay and Github repositories
     */

    /**
     * Checks that the two Quay/Github tools were automatically found
     */
    @Test
    public void testQuayGithubAutoRegistration() {

        final long count = testingPostgres.runSelectStatement(
            "select count(*) from tool where  registry = '" + Registry.QUAY_IO.getDockerPath() + "' and giturl like 'git@github.com%'",
            long.class);
        Assert.assertEquals("there should be 5 registered from Quay and Github, there are " + count, 5, count);
    }

    /**
     * Tests the case where a manually registered quay tool matching an automated build should be treated as a separate auto build (see issue 106)
     */
    @Test
    public void testManualQuaySameAsAutoQuay() {
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.QUAY_IO.name(), "--namespace", "dockstoretestuser", "--name", "quayandgithub", "--git-url",
            "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "regular", "--script" });

        final long count = testingPostgres.runSelectStatement(
            "select count(*) from tool where mode != 'MANUAL_IMAGE_PATH' and registry = '" + Registry.QUAY_IO.getDockerPath()
                + "' and namespace = 'dockstoretestuser' and name = 'quayandgithub' and toolname = 'regular'", long.class);
        Assert.assertEquals("the tool should be Auto", 1, count);
    }

    /**
     * Tests the case where a manually registered quay tool has the same path as an auto build but different git repo
     */
    @Test
    public void testManualQuayToAutoSamePathDifferentGitRepo() {
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.QUAY_IO.name(), "--namespace", "dockstoretestuser", "--name", "quayandgithub", "--git-url",
            "git@github.com:DockstoreTestUser/dockstore-whalesay-alternate.git", "--git-reference", "master", "--toolname", "alternate",
            "--cwl-path", "/testDir/Dockstore.cwl", "--dockerfile-path", "/testDir/Dockerfile", "--script" });

        final long count = testingPostgres.runSelectStatement(
            "select count(*) from tool where mode = 'MANUAL_IMAGE_PATH' and registry = '" + Registry.QUAY_IO.getDockerPath()
                + "' and namespace = 'dockstoretestuser' and name = 'quayandgithub' and toolname = 'alternate'", long.class);
        Assert.assertEquals("the tool should be Manual still", 1, count);
    }

    /**
     * Tests that a manually published tool still becomes manual even after the existing similar auto tools all have toolnames (see issue 120)
     */
    @Test
    public void testManualQuayToAutoNoAutoWithoutToolname() {
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", ToolClient.UPDATE_TOOL, "--entry",
                "quay.io/dockstoretestuser/quayandgithub", "--toolname", "testToolname", "--script" });

        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.QUAY_IO.name(), "--namespace", "dockstoretestuser", "--name", "quayandgithub", "--git-url",
            "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "testtool", "--script" });

        final long count = testingPostgres.runSelectStatement(
            "select count(*) from tool where mode != 'MANUAL_IMAGE_PATH' and registry = '" + Registry.QUAY_IO.getDockerPath()
                + "' and namespace = 'dockstoretestuser' and name = 'quayandgithub' and toolname = 'testtool'", long.class);
        Assert.assertEquals("the tool should be Auto", 1, count);
    }

    /**
     * TODO: Don't use SQL statements here
     * The testing database originally has tools with tags.  This test:
     * - Deletes a tag from a certain tool from db
     * - Refreshes the tool
     * - Checks if the tag is back
     */
    @Test
    public void testRefreshAfterDeletingAVersion() {
        // Get the tool id of the entry whose path is quay.io/dockstoretestuser/quayandgithub
        final long id = testingPostgres
            .runSelectStatement("select id from tool where name = 'quayandgithub' and namespace='dockstoretestuser' and registry='quay.io'",
                long.class);

        // Check how many versions the entry has
        final long currentNumberOfTags = testingPostgres
            .runSelectStatement("select count(*) from tag where parentid = '" + id + "'", long.class);
        assertTrue("There are no tags for this tool", currentNumberOfTags > 0);

        // This grabs the first tag that belongs to the tool
        final long firstTag = testingPostgres.runSelectStatement("select id from tag where parentid = '" + id + "'", long.class);

        // Delete the version that is known
        testingPostgres.runUpdateStatement("delete from tag where parentid = '" + id + "' and id='" + firstTag + "'");
        testingPostgres.runUpdateStatement("delete from tag where id = '" + firstTag + "'");

        // Double check that there is one less tag
        final long afterDeletionTags = testingPostgres
            .runSelectStatement("select count(*) from tag where parentId = '" + id + "'", long.class);
        Assert.assertEquals(currentNumberOfTags - 1, afterDeletionTags);

        // Refresh the tool
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "refresh", "--entry",
            "quay.io/dockstoretestuser/quayandgithub", "--script" });

        // Check how many tags there are after the refresh
        final long afterRefreshTags = testingPostgres
            .runSelectStatement("select count(*) from tag where parentid = '" + id + "'", long.class);
        Assert.assertEquals(currentNumberOfTags, afterRefreshTags);
    }

    /**
     * Tests a user trying to add a quay tool that they do not own and are not in the owning organization
     */
    @Test
    public void testAddQuayRepoOfNonOwnedOrg() {
        // Repo user isn't part of org
        systemExit.expectSystemExitWithStatus(Client.API_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.QUAY_IO.name(), Registry.QUAY_IO.toString(), "--namespace", "dockstore2", "--name", "testrepo2", "--git-url",
            "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "testOrg", "--cwl-path",
            "/Dockstore.cwl", "--dockerfile-path", "/Dockerfile", "--script" });

    }

    /**
     * Tests that refresh workflows works, also that refreshing without a github token should not destroy workflows or their existing versions
     */
    @Test
    public void testRefreshWorkflow() {
        refreshByOrganizationReplacement(USER_1_USERNAME);
        // should have a certain number of workflows based on github contents
        final long secondWorkflowCount = testingPostgres.runSelectStatement("select count(*) from workflow", long.class);
        assertTrue("should find non-zero number of workflows", secondWorkflowCount > 0);

        // refresh a specific workflow
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "workflow", "refresh", "--entry",
            SourceControl.GITHUB.toString() + "/DockstoreTestUser/dockstore-whalesay-wdl" });

        // artificially create an invalid version
        testingPostgres.runUpdateStatement("update workflowversion set name = 'test'");
        testingPostgres.runUpdateStatement("update workflowversion set reference = 'test'");

        // refresh
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "workflow", "refresh", "--entry",
            SourceControl.GITHUB.toString() + "/DockstoreTestUser/dockstore-whalesay-wdl" });

        // check that the version was deleted
        final long updatedWorkflowVersionCount = testingPostgres.runSelectStatement("select count(*) from workflowversion", long.class);
        final long updatedWorkflowVersionName = testingPostgres
            .runSelectStatement("select count(*) from workflowversion where name='master'", long.class);
        assertTrue("there should be only one version", updatedWorkflowVersionCount == 1 && updatedWorkflowVersionName == 1);

        // delete quay.io token
        testingPostgres.runUpdateStatement("delete from token where tokensource = 'github.com'");

        systemExit.checkAssertionAfterwards(() -> {
            // should not delete workflows
            final long thirdWorkflowCount = testingPostgres.runSelectStatement("select count(*) from workflow", long.class);
            Assert.assertEquals("there should be no change in count of workflows", secondWorkflowCount, thirdWorkflowCount);
        });

        // should include nextflow example workflow stub
        final long nfWorkflowCount = testingPostgres
            .runSelectStatement("select count(*) from workflow where giturl like '%ampa-nf%'", long.class);
        assertTrue("should find non-zero number of next flow workflows", nfWorkflowCount > 0);

        // refresh
        systemExit.expectSystemExit();
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "workflow", "refresh", "--entry",
            SourceControl.GITHUB.toString() + "/DockstoreTestUser/dockstore-whalesay-wdl" });
    }

    /**
     * Tests the case where a manually registered quay tool does not have any automated builds set up, though a manual build was run (see issue 107)
     * UPDATE: Should fail because you can't publish a tool with no valid tags
     */
    @Test
    public void testManualQuayManualBuild() {
        systemExit.expectSystemExitWithStatus(Client.API_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.QUAY_IO.name(), Registry.QUAY_IO.toString(), "--namespace", "dockstoretestuser", "--name", "noautobuild", "--git-url",
            "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "alternate",
            "--script" });

    }

    /**
     * Tests the case where a manually registered quay tool does not have any tags
     */
    @Test
    public void testManualQuayNoTags() {
        systemExit.expectSystemExitWithStatus(Client.API_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.QUAY_IO.name(), Registry.QUAY_IO.toString(), "--namespace", "dockstoretestuser", "--name", "nobuildsatall",
            "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "alternate",
            "--script" });
    }

    /**
     * Tests that a quick registered quay tool with no autobuild can be updated to have a manually set CWL file from git (see issue 19)
     */
    @Test
    public void testQuayNoAutobuild() {
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", ToolClient.UPDATE_TOOL, "--entry",
                "quay.io/dockstoretestuser/noautobuild", "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay.git",
                "--script" });

        final long count = testingPostgres.runSelectStatement("select count(*) from tool where registry = '" + Registry.QUAY_IO.getDockerPath()
                + "' and namespace = 'dockstoretestuser' and name = 'noautobuild' and giturl = 'git@github.com:DockstoreTestUser/dockstore-whalesay.git'",
            long.class);
        assertEquals("the tool should now have an associated git repo", 1, count);

        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", ToolClient.UPDATE_TOOL, "--entry",
                "quay.io/dockstoretestuser/nobuildsatall", "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay.git",
                "--script" });

        final long count2 = testingPostgres.runSelectStatement("select count(*) from tool where registry = '" + Registry.QUAY_IO.getDockerPath()
                + "' and namespace = 'dockstoretestuser' and name = 'nobuildsatall' and giturl = 'git@github.com:DockstoreTestUser/dockstore-whalesay.git'",
            long.class);
        assertEquals("the tool should now have an associated git repo", 1, count2);

    }


    /**
     * Tests that refresh all works, also that refreshing without a quay.io token should not destroy tools
     */
    @Test
    public void testRefresh() {
        final long startToolCount = testingPostgres.runSelectStatement("select count(*) from tool", long.class);
        // should have 0 tools to start with
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "refresh", "--script" });
        // should have a certain number of tools based on github contents
        final long secondToolCount = testingPostgres.runSelectStatement("select count(*) from tool", long.class);
        assertTrue(startToolCount <= secondToolCount && secondToolCount > 1);

        // delete quay.io token
        testingPostgres.runUpdateStatement("delete from token where tokensource = 'quay.io'");
        // CLI now does not fail if unable to refresh, commenting out the check for system exit
        // refresh
        //        systemExit.expectSystemExitWithStatus(6);
        //        systemExit.checkAssertionAfterwards(() -> {
        //            // should not delete tools
        //            final long thirdToolCount = testingPostgres.runSelectStatement("select count(*) from tool", long.class);
        //            Assert.assertEquals("there should be no change in count of tools", secondToolCount, thirdToolCount);
        //        });
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "refresh", "--script" });
        final long thirdToolCount = testingPostgres.runSelectStatement("select count(*) from tool", long.class);
        assertEquals("there should be no change in count of tools", secondToolCount, thirdToolCount);
    }

    /**
     * Ensures that you can't publish an automatically added Quay/Github tool with an alternate structure unless you change the Dockerfile and Dockstore.cwl locations
     */
    @Test
    public void testQuayGithubPublishAlternateStructure() {
        systemExit.expectSystemExitWithStatus(Client.API_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--entry",
            "quay.io/dockstoretestuser/quayandgithubalternate", "--script" });

        // TODO: change the tag tag locations of Dockerfile and Dockstore.cwl, now should be able to publish
    }

    /**
     * Checks that you can properly publish and unpublish a Quay/Github tool
     */
    @Test
    public void testQuayGithubPublishAndUnpublishATool() {
        // Publish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--entry",
            "quay.io/dockstoretestuser/quayandgithub", "--script" });

        final long count = testingPostgres
            .runSelectStatement("select count(*) from tool where name = 'quayandgithub' and ispublished='t'", long.class);
        Assert.assertEquals("there should be 1 registered", 1, count);

        // Unpublish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry",
            "quay.io/dockstoretestuser/quayandgithub", "--script" });

        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from tool where name = 'quayandgithub' and ispublished='t'", long.class);
        Assert.assertEquals("there should be 0 registered", 0, count2);
    }

    /**
     * Checks that you can manually publish and unpublish a Quay/Github tool with an alternate structure, if the CWL and Dockerfile paths are defined properly
     */
    @Test
    public void testQuayGithubManualPublishAndUnpublishAlternateStructure() {
        // Manual publish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.QUAY_IO.name(), Registry.QUAY_IO.toString(), "--namespace", "dockstoretestuser", "--name", "quayandgithubalternate",
            "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay-alternate.git", "--git-reference", "master", "--toolname",
            "alternate", "--cwl-path", "/testDir/Dockstore.cwl", "--dockerfile-path", "/testDir/Dockerfile", "--script" });

        final long count = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'alternate' and ispublished='t'", long.class);

        Assert.assertEquals("there should be 1 entries, there are " + count, 1, count);

        // Unpublish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry",
            "quay.io/dockstoretestuser/quayandgithubalternate/alternate", "--script" });
        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'alternate' and ispublished='t'", long.class);

        Assert.assertEquals("there should be 0 entries, there are " + count2, 0, count2);
    }

    /**
     * Ensures that one cannot register an existing Quay/Github entry if you don't give it an alternate toolname
     */
    @Test
    public void testQuayGithubManuallyRegisterDuplicate() {
        systemExit.expectSystemExitWithStatus(Client.API_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.QUAY_IO.name(), Registry.QUAY_IO.toString(), "--namespace", "dockstoretestuser", "--name", "quayandgithub",
            "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--script" });
    }

    /**
     * Tests that a WDL file is supported
     */
    @Test
    public void testQuayGithubQuickRegisterWithWDL() {
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "refresh", "--entry",
            "quay.io/dockstoretestuser/quayandgithub", "--script" });
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--entry",
            "quay.io/dockstoretestuser/quayandgithub", "--script" });

        final long count = testingPostgres.runSelectStatement("select count(*) from tool where registry = '" + Registry.QUAY_IO.getDockerPath()
            + "' and namespace = 'dockstoretestuser' and name = 'quayandgithub' and ispublished = 't'", long.class);
        Assert.assertEquals("the given entry should be published", 1, count);
    }

    /**
     * This tests that a tool can be updated to have default version, and that metadata is set related to the default version
     */
    @Test
    public void testSetDefaultTag() {
        // Set up DB

        // Update tool with default version that has metadata
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", ToolClient.UPDATE_TOOL, "--entry",
                "quay.io/dockstoretestuser/quayandgithub", "--default-version", "master", "--script" });

        final long count = testingPostgres.runSelectStatement("select count(*) from tool where registry = '" + Registry.QUAY_IO.getDockerPath()
            + "' and namespace = 'dockstoretestuser' and name = 'quayandgithub' and actualdefaultversion is not null", long.class);
        Assert.assertEquals("the tool should have a default version set", 1, count);

        final long count2 = testingPostgres.runSelectStatement("select count(*) from tool where registry = '" + Registry.QUAY_IO.getDockerPath()
                + "' and namespace = 'dockstoretestuser' and name = 'quayandgithub' and actualdefaultversion is not null and author = 'Dockstore Test User'",
            long.class);
        Assert.assertEquals("the tool should have any metadata set (author)", 1, count2);

        // Invalidate tags
        testingPostgres.runUpdateStatement("UPDATE tag SET valid='f'");

        // Shouldn't be able to publish
        systemExit.expectSystemExitWithStatus(Client.API_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--entry",
            "quay.io/dockstoretestuser/quayandgithub", "--script" });

    }

    /**
     * This tests that a tool can not be updated to have no default descriptor paths
     */
    @Test
    public void testToolNoDefaultDescriptors() {
        // Update tool with empty WDL, shouldn't fail
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", ToolClient.UPDATE_TOOL, "--entry",
                "quay.io/dockstoretestuser/quayandgithub", "--wdl-path", "", "--script" });

        // Update tool with empty CWL, should now fail
        systemExit.expectSystemExitWithStatus(Client.CLIENT_ERROR);
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", ToolClient.UPDATE_TOOL, "--entry",
                "quay.io/dockstoretestuser/quayandgithub", "--cwl-path", "", "--script" });
    }

    /**
     * This tests that a tool cannot be manually published if it has no default descriptor paths
     */
    @Test
    public void testManualPublishToolNoDescriptorPaths() {
        // Manual publish, should fail
        systemExit.expectSystemExitWithStatus(Client.CLIENT_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.QUAY_IO.name(), Registry.QUAY_IO.toString(), "--namespace", "dockstoretestuser", "--name", "quayandgithubalternate",
            "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay-alternate.git", "--git-reference", "master", "--toolname",
            "alternate", "--cwl-path", "", "--wdl-path", "", "--dockerfile-path", "/testDir/Dockerfile", "--script" });
    }

    /**
     * This tests that a tool cannot be manually published if it has an incorrect registry
     */
    @Test
    public void testManualPublishToolIncorrectRegistry() {
        // Manual publish, should fail
        systemExit.expectSystemExitWithStatus(Client.CLIENT_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            "thisisafakeregistry", "--namespace", "dockstoretestuser", "--name", "quayandgithubalternate", "--git-url",
            "git@github.com:DockstoreTestUser/dockstore-whalesay-alternate.git", "--git-reference", "master", "--toolname", "alternate",
            "--script" });
    }

    /**
     * This tests the dirty bit attribute for tool tags with quay
     */
    @Test
    public void testQuayDirtyBit() {
        // Setup db

        // Check that no tags have a true dirty bit
        final long count = testingPostgres.runSelectStatement("select count(*) from tag where dirtybit = true", long.class);
        Assert.assertEquals("there should be no tags with dirty bit, there are " + count, 0, count);

        // Edit tag cwl
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "version_tag", "update", "--entry",
                "quay.io/dockstoretestuser/quayandgithub", "--name", "master", "--cwl-path", "/Dockstoredirty.cwl", "--script" });

        // Edit another tag wdl
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "version_tag", "update", "--entry",
                "quay.io/dockstoretestuser/quayandgithub", "--name", "latest", "--wdl-path", "/Dockstoredirty.wdl", "--script" });

        // There should now be two true dirty bits
        final long count1 = testingPostgres.runSelectStatement("select count(*) from tag where dirtybit = true", long.class);
        Assert.assertEquals("there should be two tags with dirty bit, there are " + count1, 2, count1);

        // Update default cwl to /Dockstoreclean.cwl
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", ToolClient.UPDATE_TOOL, "--entry",
                "quay.io/dockstoretestuser/quayandgithub", "--cwl-path", "/Dockstoreclean.cwl", "--script" });

        // There should only be one tag with /Dockstoreclean.cwl (both tag with new cwl and new wdl should be dirty and not changed)
        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from tag where cwlpath = '/Dockstoreclean.cwl'", long.class);
        Assert.assertEquals("there should be only one tag with the cwl path /Dockstoreclean.cwl, there are " + count2, 1, count2);
    }

    /**
     * Checks that you can properly publish and unpublish a Quay/Github tool using the --new-entry-name parameter
     */
    @Test
    public void testQuayGithubPublishAndUnpublishAToolnewEntryName() {

        final String publishNameParameter = "--new-entry-name";

        // Publish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--entry",
            "quay.io/dockstoretestuser/quayandgithub", publishNameParameter, "fake_tool_name", "--script" });

        // Publish a second time, should fail
        systemOutRule.clearLog();
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--entry",
            "quay.io/dockstoretestuser/quayandgithub", publishNameParameter, "fake_tool_name", "--script" });
        assertTrue("Attempting to publish a registered tool should notify the user",
            systemOutRule.getLog().contains("The following tool is already registered: quay.io/dockstoretestuser/quayandgithub/fake_tool_name"));

        final long countPublish = testingPostgres
            .runSelectStatement("SELECT COUNT(*) FROM tool WHERE toolname = 'fake_tool_name' AND ispublished='t'", long.class);
        Assert.assertEquals("there should be 1 registered tool", 1, countPublish);

        // Unpublish incorrectly
        systemExit.expectSystemExitWithStatus(Client.COMMAND_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry",
            "quay.io/dockstoretestuser/quayandgithub", publishNameParameter, "fake_tool_name", "--script" });

        final long countBadUnpublish = testingPostgres
            .runSelectStatement("SELECT COUNT(*) FROM tool WHERE toolname = 'fake_tool_name' AND ispublished='t'", long.class);
        Assert.assertEquals("there should be 1 registered tool after invalid unpublish request", 1, countBadUnpublish);

        // unpublish correctly
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry",
            "quay.io/dockstoretestuser/quayandgithub/fake_tool_name", "--script" });

        final long countGoodUnpublish = testingPostgres
            .runSelectStatement("SELECT COUNT(*) FROM tool WHERE toolname = 'fake_tool_name' AND ispublished='t'", long.class);
        Assert.assertEquals("there should be 0 registered", 0, countGoodUnpublish);

        // try to unpublish the unpublished tool
        systemOutRule.clearLog();
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry",
            "quay.io/dockstoretestuser/quayandgithub/fake_tool_name", "--script" });
        assertTrue("Attempting to publish a registered tool should notify the user",
            systemOutRule.getLog().contains("The following tool is already unpublished: quay.io/dockstoretestuser/quayandgithub/fake_tool_name"));
    }

    /**
     * Checks that you can properly publish and unpublish a Quay/Github tool using the --entryname parameter. --entryname is deprecated,
     * verifying backwards compatibility
     */
    @Test
    public void testQuayGithubPublishAndUnpublishAToolEntryName() {

        final String publishNameParameter = "--entryname";

        // Publish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--entry",
            "quay.io/dockstoretestuser/quayandgithub", publishNameParameter, "fake_tool_name", "--script" });

        // Publish a second time, should fail
        systemOutRule.clearLog();
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--entry",
            "quay.io/dockstoretestuser/quayandgithub", publishNameParameter, "fake_tool_name", "--script" });
        assertTrue("Attempting to publish a registered tool should notify the user",
            systemOutRule.getLog().contains("The following tool is already registered: quay.io/dockstoretestuser/quayandgithub/fake_tool_name"));

        final long countPublish = testingPostgres
            .runSelectStatement("SELECT COUNT(*) FROM tool WHERE toolname = 'fake_tool_name' AND ispublished='t'", long.class);
        Assert.assertEquals("there should be 1 registered tool", 1, countPublish);

        // Unpublish incorrectly
        systemExit.expectSystemExitWithStatus(Client.COMMAND_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry",
            "quay.io/dockstoretestuser/quayandgithub", publishNameParameter, "fake_tool_name", "--script" });

        final long countBadUnpublish = testingPostgres
            .runSelectStatement("SELECT COUNT(*) FROM tool WHERE toolname = 'fake_tool_name' AND ispublished='t'", long.class);
        Assert.assertEquals("there should be 1 registered tool after invalid unpublish request", 1, countBadUnpublish);

        // unpublish correctly
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry",
            "quay.io/dockstoretestuser/quayandgithub/fake_tool_name", "--script" });

        final long countGoodUnpublish = testingPostgres
            .runSelectStatement("SELECT COUNT(*) FROM tool WHERE toolname = 'fake_tool_name' AND ispublished='t'", long.class);
        Assert.assertEquals("there should be 0 registered", 0, countGoodUnpublish);

        // try to unpublish the unpublished tool
        systemOutRule.clearLog();
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry",
            "quay.io/dockstoretestuser/quayandgithub/fake_tool_name", "--script" });
        assertTrue("Attempting to publish a registered tool should notify the user",
            systemOutRule.getLog().contains("The following tool is already unpublished: quay.io/dockstoretestuser/quayandgithub/fake_tool_name"));
    }


}
