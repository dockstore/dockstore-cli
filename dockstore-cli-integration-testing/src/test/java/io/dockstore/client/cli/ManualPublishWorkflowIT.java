package io.dockstore.client.cli;

import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.FlushingSystemErrRule;
import io.dockstore.common.FlushingSystemOutRule;
import io.dockstore.common.SlowTest;
import io.dockstore.common.SourceControl;
import io.dockstore.common.WorkflowTest;
import io.dropwizard.testing.ResourceHelpers;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@Category({ ConfidentialTest.class, WorkflowTest.class })
public class ManualPublishWorkflowIT extends BaseIT {

    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();

    @Rule
    public final SystemOutRule systemOutRule = new FlushingSystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new FlushingSystemErrRule().enableLog().muteForSuccessfulTests();

    @Before
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
    }


    /**
     * This tests manually publishing a duplicate workflow (should fail)
     */
    @Test
    public void testManualPublishDuplicate() {
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "manual_publish", "--repository",
                "hello-dockstore-workflow", "--organization", "DockstoreTestUser2", "--git-version-control", "github", "--workflow-name",
                "testname", "--workflow-path", "/Dockstore.wdl", "--descriptor-type", "wdl", "--script" });

        systemExit.expectSystemExitWithStatus(Client.API_ERROR);

        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "manual_publish", "--repository",
                "hello-dockstore-workflow", "--organization", "DockstoreTestUser2", "--git-version-control", "github", "--workflow-name",
                "testname", "--workflow-path", "/Dockstore.wdl", "--descriptor-type", "wdl", "--script" });
    }


    /**
     * This tests that a user can update a workflow version
     */
    @Test
    public void testUpdateWorkflowVersion() {
        // Update workflow
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "manual_publish", "--repository",
                "hello-dockstore-workflow", "--organization", "DockstoreTestUser2", "--git-version-control", "github", "--workflow-name",
                "testname", "--workflow-path", "/Dockstore.wdl", "--descriptor-type", "wdl", "--script" });
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "version_tag", "--entry",
            SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow/testname", "--name", "master",
            "--workflow-path", "/Dockstore2.wdl", "--script" });

        final long count = testingPostgres.runSelectStatement(
            "select count(*) from workflowversion wv, version_metadata vm where wv.name = 'master' and wv.workflowpath = '/Dockstore2.wdl' and wv.id = vm.id",
            long.class);
        assertEquals("there should be 1 matching workflow version, there is " + count, 1, count);
    }


    /**
     * This tests that a workflow can be updated to have default version, and that metadata is set related to the default version
     */
    @Test
    public void testUpdateWorkflowDefaultVersion() {
        // Setup workflow
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "manual_publish", "--repository",
                "hello-dockstore-workflow", "--organization", "DockstoreTestUser2", "--git-version-control", "github", "--script" });

        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--entry",
            SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow", "--script" });

        // Update workflow with version with no metadata
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "update_workflow", "--entry",
                SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow", "--default-version", "testWDL",
                "--script" });

        // Assert default version is updated and no author or email is found
        final long count = testingPostgres.runSelectStatement("select count(*) from workflow w, workflowversion wv where wv.name = 'testWDL' and wv.id = w.actualdefaultversion", long.class);
        assertEquals("there should be 1 matching workflow, there is " + count, 1, count);

        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from workflow w, workflowversion wv where wv.id = w.actualdefaultversion and wv.name = 'testWDL' and w.author is null and w.email is null",
                long.class);
        assertEquals("The given workflow shouldn't have any contact info", 1, count2);

        // Update workflow with version with metadata
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "update_workflow", "--entry",
                SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow", "--default-version", "testBoth",
                "--script" });

        // Assert default version is updated and author and email are set
        final long count3 = testingPostgres
            .runSelectStatement("select count(*) from workflow w, workflowversion wv where wv.name = 'testBoth' and w.id=wv.parentid", long.class);
        assertEquals("there should be 1 matching workflow, there is " + count3, 1, count3);

        final long count4 = testingPostgres.runSelectStatement(
            "select count(*) from workflow w, workflowversion wv where w.actualdefaultversion = wv.id and wv.name = 'testBoth' and w.author = 'testAuthor' and w.email = 'testEmail'",
            long.class);
        assertEquals("The given workflow should have contact info", 1, count4);

        // Unpublish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--entry",
            SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow", "--unpub", "--script" });

        // Alter workflow so that it has no valid tags
        testingPostgres.runUpdateStatement("UPDATE workflowversion SET valid='f'");

        // Now you shouldn't be able to publish the workflow
        systemExit.expectSystemExitWithStatus(Client.API_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--entry",
            SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow", "--script" });
    }

    /**
     * This tests manually publishing a gitlab workflow
     */
    @Test
    @Ignore("Ignoring for 1.8.6, enable for 1.9.0")
    public void testManualPublishGitlab() {
        // manual publish
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "manual_publish", "--repository",
                "dockstore-workflow-example", "--organization", "dockstore.test.user2", "--git-version-control", "gitlab",
                "--workflow-name", "testname", "--workflow-path", "/Dockstore.wdl", "--descriptor-type", "wdl", "--script" });

        // Check for one valid version
        final long count = testingPostgres.runSelectStatement("select count(*) from workflowversion where valid='t'", long.class);
        assertEquals("there should be 1 valid version, there are " + count, 1, count);

        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from workflowversion where lastmodified is null", long.class);
        assertEquals("All GitLab workflow versions should have last modified populated when manual published", 0, count2);

        // grab wdl file
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "wdl", "--entry",
            SourceControl.GITLAB.toString() + "/dockstore.test.user2/dockstore-workflow-example/testname:master", "--script" });
    }

    /**
     * This tests getting branches and tags from gitlab repositories
     */
    @Ignore
    @Test
    @Category(SlowTest.class)
    public void testGitLabTagAndBranchTracking() {
        // manual publish
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "manual_publish", "--repository",
                "dockstore-workflow-md5sum-unified", "--organization", "dockstore.test.user2", "--git-version-control", "gitlab",
                "--workflow-name", "testname", "--workflow-path", "/checker.wdl", "--descriptor-type", "wdl", "--script" });

        final long count = testingPostgres.runSelectStatement("select count(*) from workflowversion", long.class);
        assertTrue("there should be at least 5 versions, there are " + count, count >= 5);
        final long branchCount = testingPostgres
            .runSelectStatement("select count(*) from workflowversion where referencetype = 'BRANCH'", long.class);
        assertTrue("there should be at least 2 branches, there are " + count, branchCount >= 2);
        final long tagCount = testingPostgres
            .runSelectStatement("select count(*) from workflowversion where referencetype = 'TAG'", long.class);
        assertTrue("there should be at least 3 tags, there are " + count, tagCount >= 3);
    }

    /**
     * This tests that you can verify and unverify a workflow
     */
    @Test
    public void testVerify() {
        // Versions should be unverified
        final long count = testingPostgres
            .runSelectStatement("select count(*) from workflowversion wv, version_metadata vm where vm.verified='true' and wv.id = vm.id",
                long.class);

        assertEquals("there should be no verified workflowversions, there are " + count, 0, count);

        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "manual_publish", "--repository",
                "parameter_test_workflow", "--organization", "DockstoreTestUser2", "--git-version-control", "github", "--workflow-path",
                "/Dockstore.cwl", "--descriptor-type", "cwl", "--script" });

        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "test_parameter", "--add",
            "/test.wdl.json", "--entry", SourceControl.GITHUB.toString() + "/DockstoreTestUser2/parameter_test_workflow", "--version",
            "wdltest", "--script" });

        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "test_parameter", "--add",
            "/test.cwl.json", "--entry", SourceControl.GITHUB.toString() + "/DockstoreTestUser2/parameter_test_workflow", "--version",
            "master", "--script" });

        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--entry",
            SourceControl.GITHUB.toString() + "/DockstoreTestUser2/parameter_test_workflow", "--script" });

        // Verify workflowversion
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "verify", "--trs-id",
            "#workflow/github.com/DockstoreTestUser2/parameter_test_workflow", "--version-id", "wdltest", "--file-path", "test.wdl.json",
            "--descriptor-type", "cwl", "--platform", "Cromwell", "--platform-version", "thing", "--metadata", "Docker testing group",
            "--script" });

        // Version should be verified
        final long count2 = testingPostgres.runSelectStatement(
            "select count(*) from workflowversion wv, version_metadata vm where vm.verified='true' and vm.verifiedSource='[\"Docker testing group\"]' and wv.id = vm.id",
            long.class);
        assertEquals("there should be one verified workflowversion, there are " + count2, 1, count2);

        // Update workflowversion to have another verified source
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "verify", "--trs-id",
            "#workflow/github.com/DockstoreTestUser2/parameter_test_workflow", "--version-id", "wdltest", "--file-path", "test.wdl.json",
            "--descriptor-type", "cwl", "--platform", "Cromwell", "--platform-version", "thing", "--metadata", "Docker testing group2",
            "--script" });

        // Version should have new verified source
        final long count3 = testingPostgres.runSelectStatement(
            "select count(*) from workflowversion wv, version_metadata vm where vm.verified='true' and vm.verifiedSource='[\"Docker testing group2\"]' and wv.id = vm.id",
            long.class);
        assertEquals("there should be one verified workflowversion, there are " + count3, 1, count3);

        // Verify another version
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "verify", "--trs-id",
            "#workflow/github.com/DockstoreTestUser2/parameter_test_workflow", "--version-id", "master", "--file-path", "test.cwl.json",
            "--descriptor-type", "cwl", "--platform", "Cromwell", "--platform-version", "thing", "--metadata", "Docker testing group",
            "--script" });

        // Version should be verified
        final long count4 = testingPostgres
            .runSelectStatement("select count(*) from workflowversion wv, version_metadata vm where vm.verified='true' and wv.id = vm.id",
                long.class);
        assertEquals("there should be two verified workflowversions, there are " + count4, 2, count4);

        // Unverify workflowversion
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "verify", "--trs-id",
            "#workflow/github.com/DockstoreTestUser2/parameter_test_workflow", "--version-id", "master", "--file-path", "test.cwl.json",
            "--descriptor-type", "cwl", "--platform", "Cromwell", "--platform-version", "thing", "--unverify", "--metadata",
            "Docker testing group", "--script" });

        // Workflowversion should be unverified
        final long count5 = testingPostgres
            .runSelectStatement("select count(*) from workflowversion wv, version_metadata vm where vm.verified='true' and wv.id = vm.id",
                long.class);
        assertEquals("there should be one verified workflowversion, there are " + count5, 1, count5);
    }

    /**
     * This tests that the information for a workflow can only be seen if it is published
     */
    @Test
    public void testInfo() {
        // manual publish
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "manual_publish", "--repository",
                "hello-dockstore-workflow", "--organization", "DockstoreTestUser2", "--git-version-control", "github", "--workflow-name",
                "testname", "--workflow-path", "/Dockstore.wdl", "--descriptor-type", "wdl", "--script" });

        // info (no version)
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "info", "--entry",
            SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow/testname", "--script" });

        // info (with version)
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "info", "--entry",
            SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow/testname:master", "--script" });

        // unpublish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--entry",
            SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow/testname", "--unpub", "--script" });

        // info
        systemExit.expectSystemExitWithStatus(Client.COMMAND_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "info", "--entry",
            SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow/testname", "--script" });
    }

    /**
     * This test manually publishing a workflow and grabbing valid descriptor
     */
    @Test
    public void testManualPublishAndGrabWDL() {
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "manual_publish", "--repository",
                "hello-dockstore-workflow", "--organization", "DockstoreTestUser2", "--git-version-control", "github", "--workflow-name",
                "testname", "--workflow-path", "/Dockstore.wdl", "--descriptor-type", "wdl", "--script" });
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "wdl", "--entry",
            SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow/testname:testBoth", "--script" });
    }

    /**
     * This tests attempting to manually publish a workflow with no valid versions
     */
    @Test
    public void testManualPublishInvalid() {
        systemExit.expectSystemExitWithStatus(Client.API_ERROR);
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "manual_publish", "--repository",
                "dockstore_empty_repo", "--organization", "DockstoreTestUser2", "--git-version-control", "github", "--workflow-name",
                "testname", "--workflow-path", "/Dockstore.wdl", "--descriptor-type", "wdl", "--script" });
    }

    /**
     * This tests adding and removing labels from a workflow
     */
    @Test
    public void testLabelEditing() {
        // Set up workflow
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "manual_publish", "--repository",
                "hello-dockstore-workflow", "--organization", "DockstoreTestUser2", "--git-version-control", "github", "--workflow-path",
                "/Dockstore.wdl", "--descriptor-type", "wdl", "--script" });

        // add labels
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "label", "--entry",
            SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow", "--add", "test1", "--add", "test2",
            "--script" });

        final long count = testingPostgres.runSelectStatement("select count(*) from entry_label", long.class);
        assertEquals("there should be 2 labels, there are " + count, 2, count);

        // remove labels
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "label", "--entry",
            SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow", "--remove", "test1", "--add", "test3",
            "--script" });

        final long count2 = testingPostgres.runSelectStatement("select count(*) from entry_label", long.class);
        assertEquals("there should be 2 labels, there are " + count2, 2, count2);
    }

    /**
     * This tests manually publishing a workflow and grabbing invalid descriptor (should fail)
     */
    @Test
    public void testGetInvalidDescriptor() {
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "manual_publish", "--repository",
                "hello-dockstore-workflow", "--organization", "DockstoreTestUser2", "--git-version-control", "github", "--workflow-name",
                "testname", "--workflow-path", "/Dockstore.wdl", "--descriptor-type", "wdl", "--script" });

        systemExit.expectSystemExitWithStatus(Client.API_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "cwl", "--entry",
            SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow/testname:testBoth", "--script" });
    }

    /**
     * Tests publishing/unpublishing workflows with the --new-entry-name parameter
     */
    @Test
    public void testPublishWithNewEntryName() {

        final String publishNameParameter = "--new-entry-name";

        // register workflow
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "manual_publish", "--repository",
            "parameter_test_workflow", "--organization", "DockstoreTestUser2", "--git-version-control", "github", "--script"});

        // count number of workflows for this user with the workflowname 'test_entryname'
        final long countInitialWorkflowPublish = testingPostgres
            .runSelectStatement("SELECT COUNT(*) FROM workflow WHERE organization='DockstoreTestUser2' "
                + "AND repository='parameter_test_workflow' AND workflowname IS NULL;", long.class);
        assertEquals("The initial workflow should be published without a workflow name", 1, countInitialWorkflowPublish);

        // publish workflow with name 'test_entryname'
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish",
                "--entry", "github.com/DockstoreTestUser2/parameter_test_workflow", publishNameParameter, "test_entryname", "--script"});

        // publish workflow with name 'test_entryname' a second time, shouldn't work
        systemOutRule.clearLog();
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish",
                "--entry", "github.com/DockstoreTestUser2/parameter_test_workflow", publishNameParameter, "test_entryname", "--script"});
        assertTrue("Attempting to publish a registered workflow should notify the user",
            systemOutRule.getLog().contains("The following workflow is already registered: github.com/DockstoreTestUser2/parameter_test_workflow"));

        // verify there are 2 workflows associated with the user
        final long countTotalPublishedWorkflows = testingPostgres
            .runSelectStatement("SELECT COUNT(*) FROM workflow WHERE organization='DockstoreTestUser2' "
                + "AND repository='parameter_test_workflow' AND ispublished='t';", long.class);
        assertEquals("Ensure there are 2 published workflows", 2, countTotalPublishedWorkflows);

        // verify count of number of published workflows, with the desired name, is 1
        final long countPublishedWorkflowWithCustomName = testingPostgres
            .runSelectStatement("SELECT COUNT(*) FROM workflow WHERE organization='DockstoreTestUser2' "
                + "AND repository='parameter_test_workflow' AND workflowname='test_entryname' AND ispublished='t';", long.class);
        assertEquals("Ensure there is a published workflow with the expected workflow name", 1, countPublishedWorkflowWithCustomName);

        // Try unpublishing with both --unpub and --entryname specified, should fail
        systemExit.expectSystemExitWithStatus(Client.COMMAND_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--unpub",
            "--entry", "github.com/DockstoreTestUser2/parameter_test_workflow", publishNameParameter, "test_entryname", "--script"});

        // unpublish workflow with name 'test_entryname'
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--unpub",
            "--entry", "github.com/DockstoreTestUser2/parameter_test_workflow/test_entryname", "--script"});

        // verify count of number of unpublish workflows with the desired name is 1
        final long countUnpublishedWorkflowWithCustomName = testingPostgres.runSelectStatement(
            "SELECT COUNT(*) FROM workflow WHERE organization='DockstoreTestUser2' AND repository='parameter_test_workflow' AND workflowname='test_entryname' AND ispublished='f';", long.class);
        assertEquals("The workflow should exist and be unpublished", 1, countUnpublishedWorkflowWithCustomName);

        systemOutRule.clearLog();
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--unpub",
            "--entry", "github.com/DockstoreTestUser2/parameter_test_workflow/test_entryname", "--script"});
        assertTrue("Attempting to publish a registered workflow should notify the user",
            systemOutRule.getLog().contains("The following workflow is already unpublished: github.com/DockstoreTestUser2/parameter_test_workflow"));
    }

    /**
     * Tests publishing/unpublishing workflows with the original --entryname parameter to ensure backwards compatibility
     */
    @Test
    public void testPublishWithEntryName() {

        final String publishNameParameter = "--entryname";

        // register workflow
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "manual_publish", "--repository",
            "parameter_test_workflow", "--organization", "DockstoreTestUser2", "--git-version-control", "github", "--script"});

        // count number of workflows for this user with the workflowname 'test_entryname'
        final long countInitialWorkflowPublish = testingPostgres
            .runSelectStatement("SELECT COUNT(*) FROM workflow WHERE organization='DockstoreTestUser2' "
                + "AND repository='parameter_test_workflow' AND workflowname IS NULL;", long.class);
        assertEquals("The initial workflow should be published without a workflow name", 1, countInitialWorkflowPublish);

        // publish workflow with name 'test_entryname'
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish",
                "--entry", "github.com/DockstoreTestUser2/parameter_test_workflow", publishNameParameter, "test_entryname", "--script"});

        // publish workflow with name 'test_entryname' a second time, shouldn't work
        systemOutRule.clearLog();
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish",
                "--entry", "github.com/DockstoreTestUser2/parameter_test_workflow", publishNameParameter, "test_entryname", "--script"});
        assertTrue("Attempting to publish a registered workflow should notify the user",
            systemOutRule.getLog().contains("The following workflow is already registered: github.com/DockstoreTestUser2/parameter_test_workflow"));

        // verify there are 2 workflows associated with the user
        final long countTotalPublishedWorkflows = testingPostgres
            .runSelectStatement("SELECT COUNT(*) FROM workflow WHERE organization='DockstoreTestUser2' "
                + "AND repository='parameter_test_workflow' AND ispublished='t';", long.class);
        assertEquals("Ensure there are 2 published workflows", 2, countTotalPublishedWorkflows);

        // verify count of number of published workflows, with the desired name, is 1
        final long countPublishedWorkflowWithCustomName = testingPostgres
            .runSelectStatement("SELECT COUNT(*) FROM workflow WHERE organization='DockstoreTestUser2' "
                + "AND repository='parameter_test_workflow' AND workflowname='test_entryname' AND ispublished='t';", long.class);
        assertEquals("Ensure there is a published workflow with the expected workflow name", 1, countPublishedWorkflowWithCustomName);

        // Try unpublishing with both --unpub and --entryname specified, should fail
        systemExit.expectSystemExitWithStatus(Client.COMMAND_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--unpub",
            "--entry", "github.com/DockstoreTestUser2/parameter_test_workflow", publishNameParameter, "test_entryname", "--script"});

        // unpublish workflow with name 'test_entryname'
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--unpub",
            "--entry", "github.com/DockstoreTestUser2/parameter_test_workflow/test_entryname", "--script"});

        // verify count of number of unpublish workflows with the desired name is 1
        final long countUnpublishedWorkflowWithCustomName = testingPostgres.runSelectStatement(
            "SELECT COUNT(*) FROM workflow WHERE organization='DockstoreTestUser2' AND repository='parameter_test_workflow' AND workflowname='test_entryname' AND ispublished='f';", long.class);
        assertEquals("The workflow should exist and be unpublished", 1, countUnpublishedWorkflowWithCustomName);

        systemOutRule.clearLog();
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--unpub",
            "--entry", "github.com/DockstoreTestUser2/parameter_test_workflow/test_entryname", "--script"});
        assertTrue("Attempting to publish a registered workflow should notify the user",
            systemOutRule.getLog().contains("The following workflow is already unpublished: github.com/DockstoreTestUser2/parameter_test_workflow"));

    }




}
