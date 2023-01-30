package io.dockstore.client.cli;

import io.dockstore.client.cli.nested.ToolClient;
import io.dockstore.common.CLICommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.Registry;
import io.dockstore.common.SourceControl;
import io.dockstore.common.ToolTest;
import io.dropwizard.testing.ResourceHelpers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

import static io.dockstore.client.cli.Client.WORKFLOW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.org.webcompere.systemstubs.SystemStubs.catchSystemExit;

@Tag(ConfidentialTest.NAME)
@Tag(ToolTest.NAME)
class QuayGitHubBasicIT extends BaseIT {

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
     * Test Quay and Github -
     * These tests are focused on testing tools created from Quay and Github repositories
     */

    /**
     * Checks that the two Quay/Github tools were automatically found
     */
    @Test
    void testQuayGithubAutoRegistration() {

        final long count = testingPostgres.runSelectStatement(
            "select count(*) from tool where  registry = '" + Registry.QUAY_IO.getDockerPath() + "' and giturl like 'git@github.com%'",
            long.class);
        assertEquals(5, count, "there should be 5 registered from Quay and Github, there are " + count);
    }

    /**
     * Tests the case where a manually registered quay tool matching an automated build should be treated as a separate auto build (see issue 106)
     */
    @Test
    void testManualQuaySameAsAutoQuay() {
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.QUAY_IO.name(), "--namespace", "dockstoretestuser", "--name", "quayandgithub", "--git-url",
            "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "regular", "--script" });

        final long count = testingPostgres.runSelectStatement(
            "select count(*) from tool where mode != 'MANUAL_IMAGE_PATH' and registry = '" + Registry.QUAY_IO.getDockerPath()
                + "' and namespace = 'dockstoretestuser' and name = 'quayandgithub' and toolname = 'regular'", long.class);
        assertEquals(1, count, "the tool should be Auto");
    }

    /**
     * Tests the case where a manually registered quay tool has the same path as an auto build but different git repo
     */
    @Test
    void testManualQuayToAutoSamePathDifferentGitRepo() {
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.QUAY_IO.name(), "--namespace", "dockstoretestuser", "--name", "quayandgithub", "--git-url",
            "git@github.com:DockstoreTestUser/dockstore-whalesay-alternate.git", "--git-reference", "master", "--toolname", "alternate",
            "--cwl-path", "/testDir/Dockstore.cwl", "--dockerfile-path", "/testDir/Dockerfile", "--script" });

        final long count = testingPostgres.runSelectStatement(
            "select count(*) from tool where mode = 'MANUAL_IMAGE_PATH' and registry = '" + Registry.QUAY_IO.getDockerPath()
                + "' and namespace = 'dockstoretestuser' and name = 'quayandgithub' and toolname = 'alternate'", long.class);
        assertEquals(1, count, "the tool should be Manual still");
    }

    /**
     * Tests that a manually published tool still becomes manual even after the existing similar auto tools all have toolnames (see issue 120)
     */
    @Test
    void testManualQuayToAutoNoAutoWithoutToolname() {
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", ToolClient.UPDATE_TOOL, "--entry",
                "quay.io/dockstoretestuser/quayandgithub", "--toolname", "testToolname", "--script" });

        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.QUAY_IO.name(), "--namespace", "dockstoretestuser", "--name", "quayandgithub", "--git-url",
            "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "testtool", "--script" });

        final long count = testingPostgres.runSelectStatement(
            "select count(*) from tool where mode != 'MANUAL_IMAGE_PATH' and registry = '" + Registry.QUAY_IO.getDockerPath()
                + "' and namespace = 'dockstoretestuser' and name = 'quayandgithub' and toolname = 'testtool'", long.class);
        assertEquals(1, count, "the tool should be Auto");
    }

    /**
     * TODO: Don't use SQL statements here
     * The testing database originally has tools with tags.  This test:
     * - Deletes a tag from a certain tool from db
     * - Refreshes the tool
     * - Checks if the tag is back
     */
    @Test
    void testRefreshAfterDeletingAVersion() {
        // Get the tool id of the entry whose path is quay.io/dockstoretestuser/quayandgithub
        final long id = testingPostgres
            .runSelectStatement("select id from tool where name = 'quayandgithub' and namespace='dockstoretestuser' and registry='quay.io'",
                long.class);

        // Check how many versions the entry has
        final long currentNumberOfTags = testingPostgres
            .runSelectStatement("select count(*) from tag where parentid = '" + id + "'", long.class);
        assertTrue(currentNumberOfTags > 0, "There are no tags for this tool");

        // This grabs the first tag that belongs to the tool
        final long firstTag = testingPostgres.runSelectStatement("select id from tag where parentid = '" + id + "'", long.class);

        // Delete the version that is known
        testingPostgres.runUpdateStatement("delete from tag where parentid = '" + id + "' and id='" + firstTag + "'");
        testingPostgres.runUpdateStatement("delete from tag where id = '" + firstTag + "'");

        // Double check that there is one less tag
        final long afterDeletionTags = testingPostgres
            .runSelectStatement("select count(*) from tag where parentId = '" + id + "'", long.class);
        assertEquals(currentNumberOfTags - 1, afterDeletionTags);

        // Refresh the tool
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "refresh", "--entry",
            "quay.io/dockstoretestuser/quayandgithub", "--script" });

        // Check how many tags there are after the refresh
        final long afterRefreshTags = testingPostgres
            .runSelectStatement("select count(*) from tag where parentid = '" + id + "'", long.class);
        assertEquals(currentNumberOfTags, afterRefreshTags);
    }

    /**
     * Tests a user trying to add a quay tool that they do not own and are not in the owning organization
     */
    @Test
    void testAddQuayRepoOfNonOwnedOrg() throws Exception {
        // Repo user isn't part of org
        int exitCode = catchSystemExit(() -> Client.main(
                new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
                        Registry.QUAY_IO.name(), Registry.QUAY_IO.toString(), "--namespace", "dockstore2", "--name", "testrepo2",
                        "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--toolname",
                        "testOrg", "--cwl-path", "/Dockstore.cwl", "--dockerfile-path", "/Dockerfile", "--script" }));
        assertEquals(Client.API_ERROR, exitCode);
    }

    /**
     * Tests that refresh workflows works, also that refreshing without a github token should not destroy workflows or their existing versions
     */
    @Test
    void testRefreshWorkflow() throws Exception {
        refreshByOrganizationReplacement(USER_1_USERNAME);
        // should have a certain number of workflows based on github contents
        final long secondWorkflowCount = testingPostgres.runSelectStatement("select count(*) from workflow", long.class);
        assertTrue(secondWorkflowCount > 0, "should find non-zero number of workflows");

        // refresh a specific workflow
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), WORKFLOW, "refresh", "--entry",
            SourceControl.GITHUB.toString() + "/DockstoreTestUser/dockstore-whalesay-wdl" });

        // artificially create an invalid version
        testingPostgres.runUpdateStatement("update workflowversion set name = 'test'");
        testingPostgres.runUpdateStatement("update workflowversion set reference = 'test'");

        // refresh
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), WORKFLOW, "refresh", "--entry",
            SourceControl.GITHUB.toString() + "/DockstoreTestUser/dockstore-whalesay-wdl" });

        // check that the version was deleted
        final long updatedWorkflowVersionCount = testingPostgres.runSelectStatement("select count(*) from workflowversion", long.class);
        final long updatedWorkflowVersionName = testingPostgres
            .runSelectStatement("select count(*) from workflowversion where name='master'", long.class);
        assertTrue(updatedWorkflowVersionCount == 1 && updatedWorkflowVersionName == 1, "there should be only one version");

        // delete quay.io token
        testingPostgres.runUpdateStatement("delete from token where tokensource = 'github.com'");

        // should include nextflow example workflow stub
        final long nfWorkflowCount = testingPostgres
            .runSelectStatement("select count(*) from workflow where giturl like '%ampa-nf%'", long.class);
        assertTrue(nfWorkflowCount > 0, "should find non-zero number of next flow workflows");

        // refresh
        catchSystemExit(() -> Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), WORKFLOW, "refresh", "--entry",
                SourceControl.GITHUB.toString() + "/DockstoreTestUser/dockstore-whalesay-wdl" }));
        final long thirdWorkflowCount = testingPostgres.runSelectStatement("select count(*) from workflow", long.class);
        assertEquals(secondWorkflowCount, thirdWorkflowCount, "there should be no change in count of workflows");
    }

    /**
     * Tests the case where a manually registered quay tool does not have any automated builds set up, though a manual build was run (see issue 107)
     * UPDATE: Should fail because you can't publish a tool with no valid tags
     */
    @Test
    void testManualQuayManualBuild() throws Exception {
        int exitCode = catchSystemExit(() -> Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
                Registry.QUAY_IO.name(), Registry.QUAY_IO.toString(), "--namespace", "dockstoretestuser", "--name", "noautobuild", "--git-url",
                "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "alternate",
                "--script" }));
        assertEquals(Client.API_ERROR, exitCode);
    }

    /**
     * Tests the case where a manually registered quay tool does not have any tags
     */
    @Test
    void testManualQuayNoTags() throws Exception {
        int exitCode = catchSystemExit(() -> Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
                Registry.QUAY_IO.name(), Registry.QUAY_IO.toString(), "--namespace", "dockstoretestuser", "--name", "nobuildsatall",
                "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "alternate",
                "--script" }));
        assertEquals(Client.API_ERROR, exitCode);
    }

    /**
     * Tests that a quick registered quay tool with no autobuild can be updated to have a manually set CWL file from git (see issue 19)
     */
    @Test
    void testQuayNoAutobuild() {
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", ToolClient.UPDATE_TOOL, "--entry",
                "quay.io/dockstoretestuser/noautobuild", "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay.git",
                "--script" });

        final long count = testingPostgres.runSelectStatement("select count(*) from tool where registry = '" + Registry.QUAY_IO.getDockerPath()
                + "' and namespace = 'dockstoretestuser' and name = 'noautobuild' and giturl = 'git@github.com:DockstoreTestUser/dockstore-whalesay.git'",
            long.class);
        assertEquals(1, count, "the tool should now have an associated git repo");

        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", ToolClient.UPDATE_TOOL, "--entry",
                "quay.io/dockstoretestuser/nobuildsatall", "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay.git",
                "--script" });

        final long count2 = testingPostgres.runSelectStatement("select count(*) from tool where registry = '" + Registry.QUAY_IO.getDockerPath()
                + "' and namespace = 'dockstoretestuser' and name = 'nobuildsatall' and giturl = 'git@github.com:DockstoreTestUser/dockstore-whalesay.git'",
            long.class);
        assertEquals(1, count2, "the tool should now have an associated git repo");

    }


    /**
     * Tests that refresh all works, also that refreshing without a quay.io token should not destroy tools
     */
    @Test
    void testRefresh() {
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
        assertEquals(secondToolCount, thirdToolCount, "there should be no change in count of tools");
    }

    /**
     * Ensures that you can't publish an automatically added Quay/Github tool with an alternate structure unless you change the Dockerfile and Dockstore.cwl locations
     */
    @Test
    void testQuayGithubPublishAlternateStructure() throws Exception {
        int exitCode = catchSystemExit(() ->  Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--entry",
                "quay.io/dockstoretestuser/quayandgithubalternate", "--script" }));
        assertEquals(Client.API_ERROR, exitCode);
        // TODO: change the tag tag locations of Dockerfile and Dockstore.cwl, now should be able to publish
    }

    /**
     * Checks that you can properly publish and unpublish a Quay/Github tool
     */
    @Test
    void testQuayGithubPublishAndUnpublishATool() {
        // Publish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--entry",
            "quay.io/dockstoretestuser/quayandgithub", "--script" });

        final long count = testingPostgres
            .runSelectStatement("select count(*) from tool where name = 'quayandgithub' and ispublished='t'", long.class);
        assertEquals(1, count, "there should be 1 registered");

        // Unpublish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry",
            "quay.io/dockstoretestuser/quayandgithub", "--script" });

        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from tool where name = 'quayandgithub' and ispublished='t'", long.class);
        assertEquals(0, count2, "there should be 0 registered");
    }

    /**
     * Checks that you can manually publish and unpublish a Quay/Github tool with an alternate structure, if the CWL and Dockerfile paths are defined properly
     */
    @Test
    void testQuayGithubManualPublishAndUnpublishAlternateStructure() {
        // Manual publish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.QUAY_IO.name(), Registry.QUAY_IO.toString(), "--namespace", "dockstoretestuser", "--name", "quayandgithubalternate",
            "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay-alternate.git", "--git-reference", "master", "--toolname",
            "alternate", "--cwl-path", "/testDir/Dockstore.cwl", "--dockerfile-path", "/testDir/Dockerfile", "--script" });

        final long count = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'alternate' and ispublished='t'", long.class);

        assertEquals(1, count, "there should be 1 entries, there are " + count);

        // Unpublish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry",
            "quay.io/dockstoretestuser/quayandgithubalternate/alternate", "--script" });
        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'alternate' and ispublished='t'", long.class);

        assertEquals(0, count2, "there should be 0 entries, there are " + count2);
    }

    /**
     * Ensures that one cannot register an existing Quay/Github entry if you don't give it an alternate toolname
     */
    @Test
    void testQuayGithubManuallyRegisterDuplicate() throws Exception {
        int exitCode = catchSystemExit(() ->  Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
                Registry.QUAY_IO.name(), Registry.QUAY_IO.toString(), "--namespace", "dockstoretestuser", "--name", "quayandgithub",
                "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--script" }));
        assertEquals(Client.API_ERROR, exitCode);
    }

    /**
     * Tests that a WDL file is supported
     */
    @Test
    void testQuayGithubQuickRegisterWithWDL() {
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "refresh", "--entry",
            "quay.io/dockstoretestuser/quayandgithub", "--script" });
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--entry",
            "quay.io/dockstoretestuser/quayandgithub", "--script" });

        final long count = testingPostgres.runSelectStatement("select count(*) from tool where registry = '" + Registry.QUAY_IO.getDockerPath()
            + "' and namespace = 'dockstoretestuser' and name = 'quayandgithub' and ispublished = 't'", long.class);
        assertEquals(1, count, "the given entry should be published");
    }

    /**
     * This tests that a tool can be updated to have default version, and that metadata is set related to the default version
     */
    @Test
    void testSetDefaultTag() throws Exception {
        // Set up DB

        // Update tool with default version that has metadata
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", ToolClient.UPDATE_TOOL, "--entry",
                "quay.io/dockstoretestuser/quayandgithub", "--default-version", "master", "--script" });

        final long count = testingPostgres.runSelectStatement("select count(*) from tool where registry = '" + Registry.QUAY_IO.getDockerPath()
            + "' and namespace = 'dockstoretestuser' and name = 'quayandgithub' and actualdefaultversion is not null", long.class);
        assertEquals(1, count, "the tool should have a default version set");

        final long count2 = testingPostgres.runSelectStatement("select count(*) from tool where registry = '" + Registry.QUAY_IO.getDockerPath()
                + "' and namespace = 'dockstoretestuser' and name = 'quayandgithub' and actualdefaultversion is not null and author = 'Dockstore Test User'",
            long.class);
        assertEquals(1, count2, "the tool should have any metadata set (author)");

        // Invalidate tags
        testingPostgres.runUpdateStatement("UPDATE tag SET valid='f'");

        // Shouldn't be able to publish
        int exitCode = catchSystemExit(() ->   Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--entry",
                "quay.io/dockstoretestuser/quayandgithub", "--script" }));
        assertEquals(Client.API_ERROR, exitCode);
    }

    /**
     * This tests that a tool can not be updated to have no default descriptor paths
     */
    @Test
    void testToolNoDefaultDescriptors() throws Exception {
        // Update tool with empty WDL, shouldn't fail
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", ToolClient.UPDATE_TOOL, "--entry",
                "quay.io/dockstoretestuser/quayandgithub", "--wdl-path", "", "--script" });

        // Update tool with empty CWL, should now fail
        int exitCode = catchSystemExit(() -> Client.main(
                new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", ToolClient.UPDATE_TOOL, "--entry",
                        "quay.io/dockstoretestuser/quayandgithub", "--cwl-path", "", "--script" }));
        assertEquals(Client.CLIENT_ERROR, exitCode);
    }

    /**
     * This tests that a tool cannot be manually published if it has no default descriptor paths
     */
    @Test
    void testManualPublishToolNoDescriptorPaths() throws Exception {
        // Manual publish, should fail
        int exitCode = catchSystemExit(() -> Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
                Registry.QUAY_IO.name(), Registry.QUAY_IO.toString(), "--namespace", "dockstoretestuser", "--name", "quayandgithubalternate",
                "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay-alternate.git", "--git-reference", "master", "--toolname",
                "alternate", "--cwl-path", "", "--wdl-path", "", "--dockerfile-path", "/testDir/Dockerfile", "--script" }));
        assertEquals(Client.CLIENT_ERROR, exitCode);
    }

    /**
     * This tests that a tool cannot be manually published if it has an incorrect registry
     */
    @Test
    void testManualPublishToolIncorrectRegistry() throws Exception {
        // Manual publish, should fail
        int exitCode = catchSystemExit(() -> Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
                "thisisafakeregistry", "--namespace", "dockstoretestuser", "--name", "quayandgithubalternate", "--git-url",
                "git@github.com:DockstoreTestUser/dockstore-whalesay-alternate.git", "--git-reference", "master", "--toolname", "alternate",
                "--script" }));
        assertEquals(Client.CLIENT_ERROR, exitCode);
    }

    /**
     * This tests the dirty bit attribute for tool tags with quay
     */
    @Test
    void testQuayDirtyBit() {
        // Setup db

        // Check that no tags have a true dirty bit
        final long count = testingPostgres.runSelectStatement("select count(*) from tag where dirtybit = true", long.class);
        assertEquals(0, count, "there should be no tags with dirty bit, there are " + count);

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
        assertEquals(2, count1, "there should be two tags with dirty bit, there are " + count1);

        // Update default cwl to /Dockstoreclean.cwl
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", ToolClient.UPDATE_TOOL, "--entry",
                "quay.io/dockstoretestuser/quayandgithub", "--cwl-path", "/Dockstoreclean.cwl", "--script" });

        // There should only be one tag with /Dockstoreclean.cwl (both tag with new cwl and new wdl should be dirty and not changed)
        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from tag where cwlpath = '/Dockstoreclean.cwl'", long.class);
        assertEquals(1, count2, "there should be only one tag with the cwl path /Dockstoreclean.cwl, there are " + count2);
    }

    /**
     * Checks that you can properly publish and unpublish a Quay/Github tool using the --new-entry-name parameter
     */
    @Test
    void testQuayGithubPublishAndUnpublishAToolnewEntryName() throws Exception {

        final String publishNameParameter = "--new-entry-name";

        // Publish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--entry",
            "quay.io/dockstoretestuser/quayandgithub", publishNameParameter, "fake_tool_name", "--script" });

        // Publish a second time, should fail
        systemOutRule.clear();
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--entry",
            "quay.io/dockstoretestuser/quayandgithub", publishNameParameter, "fake_tool_name", "--script" });
        assertTrue(
                systemOutRule.getText().contains("The following tool is already registered: quay.io/dockstoretestuser/quayandgithub/fake_tool_name"),
                "Attempting to publish a registered tool should notify the user");

        final long countPublish = testingPostgres
            .runSelectStatement("SELECT COUNT(*) FROM tool WHERE toolname = 'fake_tool_name' AND ispublished='t'", long.class);
        assertEquals(1, countPublish, "there should be 1 registered tool");

        // Unpublish incorrectly
        int exitCode = catchSystemExit(() -> Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry",
                "quay.io/dockstoretestuser/quayandgithub", publishNameParameter, "fake_tool_name", "--script" }));
        assertEquals(Client.COMMAND_ERROR, exitCode);

        final long countBadUnpublish = testingPostgres
            .runSelectStatement("SELECT COUNT(*) FROM tool WHERE toolname = 'fake_tool_name' AND ispublished='t'", long.class);
        assertEquals(1, countBadUnpublish, "there should be 1 registered tool after invalid unpublish request");

        // unpublish correctly
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry",
            "quay.io/dockstoretestuser/quayandgithub/fake_tool_name", "--script" });

        final long countGoodUnpublish = testingPostgres
            .runSelectStatement("SELECT COUNT(*) FROM tool WHERE toolname = 'fake_tool_name' AND ispublished='t'", long.class);
        assertEquals(0, countGoodUnpublish, "there should be 0 registered");

        // try to unpublish the unpublished tool
        systemOutRule.clear();
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry",
            "quay.io/dockstoretestuser/quayandgithub/fake_tool_name", "--script" });
        assertTrue(
                systemOutRule.getText().contains("The following tool is already unpublished: quay.io/dockstoretestuser/quayandgithub/fake_tool_name"),
                "Attempting to publish a registered tool should notify the user");
    }

    /**
     * Checks that you can properly publish and unpublish a Quay/Github tool using the --entryname parameter. --entryname is deprecated,
     * verifying backwards compatibility
     */
    @Test
    void testQuayGithubPublishAndUnpublishAToolEntryName() throws Exception {

        final String publishNameParameter = "--entryname";

        // Publish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--entry",
            "quay.io/dockstoretestuser/quayandgithub", publishNameParameter, "fake_tool_name", "--script" });

        // Publish a second time, should fail
        systemOutRule.clear();
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--entry",
            "quay.io/dockstoretestuser/quayandgithub", publishNameParameter, "fake_tool_name", "--script" });
        assertTrue(
                systemOutRule.getText().contains("The following tool is already registered: quay.io/dockstoretestuser/quayandgithub/fake_tool_name"),
                "Attempting to publish a registered tool should notify the user");

        final long countPublish = testingPostgres
            .runSelectStatement("SELECT COUNT(*) FROM tool WHERE toolname = 'fake_tool_name' AND ispublished='t'", long.class);
        assertEquals(1, countPublish, "there should be 1 registered tool");

        // Unpublish incorrectly
        int exitCode = catchSystemExit(() -> Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry",
                "quay.io/dockstoretestuser/quayandgithub", publishNameParameter, "fake_tool_name", "--script" }));
        assertEquals(Client.COMMAND_ERROR, exitCode);

        final long countBadUnpublish = testingPostgres
            .runSelectStatement("SELECT COUNT(*) FROM tool WHERE toolname = 'fake_tool_name' AND ispublished='t'", long.class);
        assertEquals(1, countBadUnpublish, "there should be 1 registered tool after invalid unpublish request");

        // unpublish correctly
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry",
            "quay.io/dockstoretestuser/quayandgithub/fake_tool_name", "--script" });

        final long countGoodUnpublish = testingPostgres
            .runSelectStatement("SELECT COUNT(*) FROM tool WHERE toolname = 'fake_tool_name' AND ispublished='t'", long.class);
        assertEquals(0, countGoodUnpublish, "there should be 0 registered");

        // try to unpublish the unpublished tool
        systemOutRule.clear();
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry",
            "quay.io/dockstoretestuser/quayandgithub/fake_tool_name", "--script" });
        assertTrue(
                systemOutRule.getText().contains("The following tool is already unpublished: quay.io/dockstoretestuser/quayandgithub/fake_tool_name"),
                "Attempting to publish a registered tool should notify the user");
    }


}
