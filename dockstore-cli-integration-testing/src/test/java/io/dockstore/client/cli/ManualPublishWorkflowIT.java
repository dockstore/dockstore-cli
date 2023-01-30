package io.dockstore.client.cli;

import io.dockstore.common.CLICommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.SlowTest;
import io.dockstore.common.SourceControl;
import io.dockstore.common.WorkflowTest;
import io.dropwizard.testing.ResourceHelpers;
import org.junit.experimental.categories.Category;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

import static io.dockstore.client.cli.Client.CONFIG;
import static io.dockstore.client.cli.Client.VERSION;
import static io.dockstore.client.cli.Client.WORKFLOW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.org.webcompere.systemstubs.SystemStubs.catchSystemExit;

@Tag(ConfidentialTest.NAME)
@Tag(WorkflowTest.NAME)
class ManualPublishWorkflowIT extends BaseIT {

    @SystemStub
    public final SystemOut systemOutRule = new SystemOut();

    @SystemStub
    public final SystemErr systemErrRule = new SystemErr();

    @BeforeEach
    @Override
    public void resetDBBetweenTests() throws Exception {
        CLICommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
    }


    /**
     * This tests manually publishing a duplicate workflow (should fail)
     */
    @Test
    void testManualPublishDuplicate() throws Exception {
        Client.main(
            new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, "manual_publish", "--repository",
                "hello-dockstore-workflow", "--organization", "DockstoreTestUser2", "--git-version-control", "github", "--workflow-name",
                "testname", "--workflow-path", "/Dockstore.wdl", "--descriptor-type", "wdl", "--script" });

        int exitCode = catchSystemExit(() ->   Client.main(
                new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, "manual_publish", "--repository",
                        "hello-dockstore-workflow", "--organization", "DockstoreTestUser2", "--git-version-control", "github", "--workflow-name",
                        "testname", "--workflow-path", "/Dockstore.wdl", "--descriptor-type", "wdl", "--script" }));
        assertEquals(Client.API_ERROR, exitCode);
    }


    /**
     * This tests that a user can update a workflow version
     */
    @Test
    void testUpdateWorkflowVersion() {
        // Update workflow
        Client.main(
            new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, "manual_publish", "--repository",
                "hello-dockstore-workflow", "--organization", "DockstoreTestUser2", "--git-version-control", "github", "--workflow-name",
                "testname", "--workflow-path", "/Dockstore.wdl", "--descriptor-type", "wdl", "--script" });
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, "version_tag", "--entry",
            SourceControl.GITHUB + "/DockstoreTestUser2/hello-dockstore-workflow/testname", "--name", "master",
            "--workflow-path", "/Dockstore2.wdl", "--script" });

        final long count = testingPostgres.runSelectStatement(
            "select count(*) from workflowversion wv, version_metadata vm where wv.name = 'master' and wv.workflowpath = '/Dockstore2.wdl' and wv.id = vm.id",
            long.class);
        assertEquals(1, count, "there should be 1 matching workflow version, there is " + count);
    }


    /**
     * This tests that a workflow can be updated to have default version, and that metadata is set related to the default version
     */
    @Test
    void testUpdateWorkflowDefaultVersion() throws Exception {
        // Setup workflow
        Client.main(
            new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, "manual_publish", "--repository",
                "hello-dockstore-workflow", "--organization", "DockstoreTestUser2", "--git-version-control", "github", "--script" });

        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, "refresh", "--entry",
            SourceControl.GITHUB + "/DockstoreTestUser2/hello-dockstore-workflow", "--script" });

        // Update workflow with version with no metadata
        Client.main(
            new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, "update_workflow", "--entry",
                SourceControl.GITHUB + "/DockstoreTestUser2/hello-dockstore-workflow", "--default-version", "testWDL",
                "--script" });

        // Assert default version is updated and no author or email is found
        final long count = testingPostgres.runSelectStatement("select count(*) from workflow w, workflowversion wv where wv.name = 'testWDL' and wv.id = w.actualdefaultversion", long.class);
        assertEquals(1, count, "there should be 1 matching workflow, there is " + count);

        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from workflow w, workflowversion wv where wv.id = w.actualdefaultversion and wv.name = 'testWDL' and w.author is null and w.email is null",
                long.class);
        assertEquals(1, count2, "The given workflow shouldn't have any contact info");

        // Update workflow with version with metadata
        Client.main(
            new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, "update_workflow", "--entry",
                SourceControl.GITHUB + "/DockstoreTestUser2/hello-dockstore-workflow", "--default-version", "testBoth",
                "--script" });

        // Assert default version is updated and author and email are set
        final long count3 = testingPostgres
            .runSelectStatement("select count(*) from workflow w, workflowversion wv where wv.name = 'testBoth' and w.id=wv.parentid", long.class);
        assertEquals(1, count3, "there should be 1 matching workflow, there is " + count3);

        final long count4 = testingPostgres.runSelectStatement(
            "select count(*) from workflow w, workflowversion wv where w.actualdefaultversion = wv.id and wv.name = 'testBoth' and w.author = 'testAuthor' and w.email = 'testEmail'",
            long.class);
        assertEquals(1, count4, "The given workflow should have contact info");

        // Unpublish
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, "publish", "--entry",
            SourceControl.GITHUB + "/DockstoreTestUser2/hello-dockstore-workflow", "--unpub", "--script" });

        // Alter workflow so that it has no valid tags
        testingPostgres.runUpdateStatement("UPDATE workflowversion SET valid='f'");

        // Now you shouldn't be able to publish the workflow
        int exitCode = catchSystemExit(() ->  Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, "publish", "--entry",
                SourceControl.GITHUB + "/DockstoreTestUser2/hello-dockstore-workflow", "--script" }));
        assertEquals(Client.API_ERROR, exitCode);
    }

    /**
     * This tests manually publishing a gitlab workflow
     */
    @Test
    @Disabled("Ignoring for 1.8.6, enable for 1.9.0")
    void testManualPublishGitlab() {
        // manual publish
        Client.main(
            new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, "manual_publish", "--repository",
                "dockstore-workflow-example", "--organization", "dockstore.test.user2", "--git-version-control", "gitlab",
                "--workflow-name", "testname", "--workflow-path", "/Dockstore.wdl", "--descriptor-type", "wdl", "--script" });

        // Check for one valid version
        final long count = testingPostgres.runSelectStatement("select count(*) from workflowversion where valid='t'", long.class);
        assertEquals(1, count, "there should be 1 valid version, there are " + count);

        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from workflowversion where lastmodified is null", long.class);
        assertEquals(0, count2, "All GitLab workflow versions should have last modified populated when manual published");

        // grab wdl file
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, "wdl", "--entry",
            SourceControl.GITLAB + "/dockstore.test.user2/dockstore-workflow-example/testname:master", "--script" });
    }

    /**
     * This tests getting branches and tags from gitlab repositories
     */
    @Disabled("probably rate-limited on gitlab")
    @Test
    @Category(SlowTest.class)
    void testGitLabTagAndBranchTracking() {
        // manual publish
        Client.main(
            new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "manual_publish", "--repository",
                "dockstore-workflow-md5sum-unified", "--organization", "dockstore.test.user2", "--git-version-control", "gitlab",
                "--workflow-name", "testname", "--workflow-path", "/checker.wdl", "--descriptor-type", "wdl", "--script" });

        final long count = testingPostgres.runSelectStatement("select count(*) from workflowversion", long.class);
        assertTrue(count >= 5, "there should be at least 5 versions, there are " + count);
        final long branchCount = testingPostgres
            .runSelectStatement("select count(*) from workflowversion where referencetype = 'BRANCH'", long.class);
        assertTrue(branchCount >= 2, "there should be at least 2 branches, there are " + count);
        final long tagCount = testingPostgres
            .runSelectStatement("select count(*) from workflowversion where referencetype = 'TAG'", long.class);
        assertTrue(tagCount >= 3, "there should be at least 3 tags, there are " + count);
    }

    /**
     * This tests that you can verify and unverify a workflow
     */
    @Test
    void testVerify() {
        // Versions should be unverified
        final long count = testingPostgres
            .runSelectStatement("select count(*) from workflowversion wv, version_metadata vm where vm.verified='true' and wv.id = vm.id",
                long.class);

        assertEquals(0, count, "there should be no verified workflowversions, there are " + count);

        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "manual_publish", "--repository",
                "parameter_test_workflow", "--organization", "DockstoreTestUser2", "--git-version-control", "github", "--workflow-path",
                "/Dockstore.cwl", "--descriptor-type", "cwl", "--script" });

        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "test_parameter", "--add",
            "/test.wdl.json", "--entry", SourceControl.GITHUB + "/DockstoreTestUser2/parameter_test_workflow", VERSION,
            "wdltest", "--script" });

        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "test_parameter", "--add",
            "/test.cwl.json", "--entry", SourceControl.GITHUB + "/DockstoreTestUser2/parameter_test_workflow", VERSION,
            "master", "--script" });

        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--entry",
            SourceControl.GITHUB + "/DockstoreTestUser2/parameter_test_workflow", "--script" });

        // Verify workflowversion
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "verify", "--trs-id",
            "#workflow/github.com/DockstoreTestUser2/parameter_test_workflow", "--version-id", "wdltest", "--file-path", "test.wdl.json",
            "--descriptor-type", "cwl", "--platform", "Cromwell", "--platform-version", "thing", "--metadata", "Docker testing group",
            "--script" });

        // Version should be verified
        final long count2 = testingPostgres.runSelectStatement(
            "select count(*) from workflowversion wv, version_metadata vm where vm.verified='true' and vm.verifiedSource='[\"Docker testing group\"]' and wv.id = vm.id",
            long.class);
        assertEquals(1, count2, "there should be one verified workflowversion, there are " + count2);

        // Update workflowversion to have another verified source
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "verify", "--trs-id",
            "#workflow/github.com/DockstoreTestUser2/parameter_test_workflow", "--version-id", "wdltest", "--file-path", "test.wdl.json",
            "--descriptor-type", "cwl", "--platform", "Cromwell", "--platform-version", "thing", "--metadata", "Docker testing group2",
            "--script" });

        // Version should have new verified source
        final long count3 = testingPostgres.runSelectStatement(
            "select count(*) from workflowversion wv, version_metadata vm where vm.verified='true' and vm.verifiedSource='[\"Docker testing group2\"]' and wv.id = vm.id",
            long.class);
        assertEquals(1, count3, "there should be one verified workflowversion, there are " + count3);

        // Verify another version
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "verify", "--trs-id",
            "#workflow/github.com/DockstoreTestUser2/parameter_test_workflow", "--version-id", "master", "--file-path", "test.cwl.json",
            "--descriptor-type", "cwl", "--platform", "Cromwell", "--platform-version", "thing", "--metadata", "Docker testing group",
            "--script" });

        // Version should be verified
        final long count4 = testingPostgres
            .runSelectStatement("select count(*) from workflowversion wv, version_metadata vm where vm.verified='true' and wv.id = vm.id",
                long.class);
        assertEquals(2, count4, "there should be two verified workflowversions, there are " + count4);

        // Unverify workflowversion
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "verify", "--trs-id",
            "#workflow/github.com/DockstoreTestUser2/parameter_test_workflow", "--version-id", "master", "--file-path", "test.cwl.json",
            "--descriptor-type", "cwl", "--platform", "Cromwell", "--platform-version", "thing", "--unverify", "--metadata",
            "Docker testing group", "--script" });

        // Workflowversion should be unverified
        final long count5 = testingPostgres
            .runSelectStatement("select count(*) from workflowversion wv, version_metadata vm where vm.verified='true' and wv.id = vm.id",
                long.class);
        assertEquals(1, count5, "there should be one verified workflowversion, there are " + count5);
    }

    /**
     * This tests that the information for a workflow can only be seen if it is published
     */
    @Test
    void testInfo() throws Exception {
        // manual publish
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "manual_publish", "--repository",
                "hello-dockstore-workflow", "--organization", "DockstoreTestUser2", "--git-version-control", "github", "--workflow-name",
                "testname", "--workflow-path", "/Dockstore.wdl", "--descriptor-type", "wdl", "--script" });

        // info (no version)
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "info", "--entry",
            SourceControl.GITHUB + "/DockstoreTestUser2/hello-dockstore-workflow/testname", "--script" });

        // info (with version)
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "info", "--entry",
            SourceControl.GITHUB + "/DockstoreTestUser2/hello-dockstore-workflow/testname:master", "--script" });

        // unpublish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--entry",
            SourceControl.GITHUB + "/DockstoreTestUser2/hello-dockstore-workflow/testname", "--unpub", "--script" });

        // info
        int exitCode = catchSystemExit(() ->  Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "info", "--entry",
                SourceControl.GITHUB + "/DockstoreTestUser2/hello-dockstore-workflow/testname", "--script" }));
        assertEquals(Client.COMMAND_ERROR, exitCode);
    }

    /**
     * This test manually publishing a workflow and grabbing valid descriptor
     */
    @Test
    void testManualPublishAndGrabWDL() {
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "manual_publish", "--repository",
                "hello-dockstore-workflow", "--organization", "DockstoreTestUser2", "--git-version-control", "github", "--workflow-name",
                "testname", "--workflow-path", "/Dockstore.wdl", "--descriptor-type", "wdl", "--script" });
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "wdl", "--entry",
            SourceControl.GITHUB + "/DockstoreTestUser2/hello-dockstore-workflow/testname:testBoth", "--script" });
    }

    /**
     * This tests attempting to manually publish a workflow with no valid versions
     */
    @Test
    void testManualPublishInvalid() throws Exception {
        int exitCode = catchSystemExit(() ->  Client.main(
                new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "manual_publish", "--repository",
                        "dockstore_empty_repo", "--organization", "DockstoreTestUser2", "--git-version-control", "github", "--workflow-name",
                        "testname", "--workflow-path", "/Dockstore.wdl", "--descriptor-type", "wdl", "--script" }));
        assertEquals(Client.API_ERROR, exitCode);
    }

    /**
     * This tests adding and removing labels from a workflow
     */
    @Test
    void testLabelEditing() {
        // Set up workflow
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "manual_publish", "--repository",
                "hello-dockstore-workflow", "--organization", "DockstoreTestUser2", "--git-version-control", "github", "--workflow-path",
                "/Dockstore.wdl", "--descriptor-type", "wdl", "--script" });

        // add labels
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "label", "--entry",
            SourceControl.GITHUB + "/DockstoreTestUser2/hello-dockstore-workflow", "--add", "test1", "--add", "test2",
            "--script" });

        final long count = testingPostgres.runSelectStatement("select count(*) from entry_label", long.class);
        assertEquals(2, count, "there should be 2 labels, there are " + count);

        // remove labels
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "label", "--entry",
            SourceControl.GITHUB + "/DockstoreTestUser2/hello-dockstore-workflow", "--remove", "test1", "--add", "test3",
            "--script" });

        final long count2 = testingPostgres.runSelectStatement("select count(*) from entry_label", long.class);
        assertEquals(2, count2, "there should be 2 labels, there are " + count2);
    }

    /**
     * This tests manually publishing a workflow and grabbing invalid descriptor (should fail)
     */
    @Test
    void testGetInvalidDescriptor() throws Exception {
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "manual_publish", "--repository",
                "hello-dockstore-workflow", "--organization", "DockstoreTestUser2", "--git-version-control", "github", "--workflow-name",
                "testname", "--workflow-path", "/Dockstore.wdl", "--descriptor-type", "wdl", "--script" });

        int exitCode = catchSystemExit(() ->  Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "cwl", "--entry",
                SourceControl.GITHUB + "/DockstoreTestUser2/hello-dockstore-workflow/testname:testBoth", "--script" }));
        assertEquals(Client.API_ERROR, exitCode);
    }

    /**
     * Tests publishing/unpublishing workflows with the --new-entry-name parameter
     */
    @Test
    void testPublishWithNewEntryName() throws Exception {

        final String publishNameParameter = "--new-entry-name";

        // register workflow
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "manual_publish", "--repository",
            "parameter_test_workflow", "--organization", "DockstoreTestUser2", "--git-version-control", "github", "--script"});

        // count number of workflows for this user with the workflowname 'test_entryname'
        final long countInitialWorkflowPublish = testingPostgres
            .runSelectStatement("SELECT COUNT(*) FROM workflow WHERE organization='DockstoreTestUser2' "
                + "AND repository='parameter_test_workflow' AND workflowname IS NULL;", long.class);
        assertEquals(1, countInitialWorkflowPublish, "The initial workflow should be published without a workflow name");

        // publish workflow with name 'test_entryname'
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish",
                "--entry", "github.com/DockstoreTestUser2/parameter_test_workflow", publishNameParameter, "test_entryname", "--script"});

        // publish workflow with name 'test_entryname' a second time, shouldn't work
        systemOutRule.clear();
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish",
                "--entry", "github.com/DockstoreTestUser2/parameter_test_workflow", publishNameParameter, "test_entryname", "--script"});
        assertTrue(
                systemOutRule.getText().contains("The following workflow is already registered: github.com/DockstoreTestUser2/parameter_test_workflow"),
                "Attempting to publish a registered workflow should notify the user");

        // verify there are 2 workflows associated with the user
        final long countTotalPublishedWorkflows = testingPostgres
            .runSelectStatement("SELECT COUNT(*) FROM workflow WHERE organization='DockstoreTestUser2' "
                + "AND repository='parameter_test_workflow' AND ispublished='t';", long.class);
        assertEquals(2, countTotalPublishedWorkflows, "Ensure there are 2 published workflows");

        // verify count of number of published workflows, with the desired name, is 1
        final long countPublishedWorkflowWithCustomName = testingPostgres
            .runSelectStatement("SELECT COUNT(*) FROM workflow WHERE organization='DockstoreTestUser2' "
                + "AND repository='parameter_test_workflow' AND workflowname='test_entryname' AND ispublished='t';", long.class);
        assertEquals(1, countPublishedWorkflowWithCustomName,
                "Ensure there is a published workflow with the expected workflow name");

        // Try unpublishing with both --unpub and --entryname specified, should fail
        int exitCode = catchSystemExit(() ->   Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--unpub",
                "--entry", "github.com/DockstoreTestUser2/parameter_test_workflow", publishNameParameter, "test_entryname", "--script"}));
        assertEquals(Client.COMMAND_ERROR, exitCode);

        // unpublish workflow with name 'test_entryname'
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--unpub",
            "--entry", "github.com/DockstoreTestUser2/parameter_test_workflow/test_entryname", "--script"});

        // verify count of number of unpublish workflows with the desired name is 1
        final long countUnpublishedWorkflowWithCustomName = testingPostgres.runSelectStatement(
            "SELECT COUNT(*) FROM workflow WHERE organization='DockstoreTestUser2' AND repository='parameter_test_workflow' AND workflowname='test_entryname' AND ispublished='f';", long.class);
        assertEquals(1, countUnpublishedWorkflowWithCustomName, "The workflow should exist and be unpublished");

        systemOutRule.clear();
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--unpub",
            "--entry", "github.com/DockstoreTestUser2/parameter_test_workflow/test_entryname", "--script"});
        assertTrue(
                systemOutRule.getText().contains("The following workflow is already unpublished: github.com/DockstoreTestUser2/parameter_test_workflow"),
                "Attempting to publish a registered workflow should notify the user");
    }

    /**
     * Tests publishing/unpublishing workflows with the original --entryname parameter to ensure backwards compatibility
     */
    @Test
    void testPublishWithEntryName() throws Exception {

        final String publishNameParameter = "--entryname";

        // register workflow
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "manual_publish", "--repository",
            "parameter_test_workflow", "--organization", "DockstoreTestUser2", "--git-version-control", "github", "--script"});

        // count number of workflows for this user with the workflowname 'test_entryname'
        final long countInitialWorkflowPublish = testingPostgres
            .runSelectStatement("SELECT COUNT(*) FROM workflow WHERE organization='DockstoreTestUser2' "
                + "AND repository='parameter_test_workflow' AND workflowname IS NULL;", long.class);
        assertEquals(1, countInitialWorkflowPublish, "The initial workflow should be published without a workflow name");

        // publish workflow with name 'test_entryname'
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish",
                "--entry", "github.com/DockstoreTestUser2/parameter_test_workflow", publishNameParameter, "test_entryname", "--script"});

        // publish workflow with name 'test_entryname' a second time, shouldn't work
        systemOutRule.clear();
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish",
                "--entry", "github.com/DockstoreTestUser2/parameter_test_workflow", publishNameParameter, "test_entryname", "--script"});
        assertTrue(
                systemOutRule.getText().contains("The following workflow is already registered: github.com/DockstoreTestUser2/parameter_test_workflow"),
                "Attempting to publish a registered workflow should notify the user");

        // verify there are 2 workflows associated with the user
        final long countTotalPublishedWorkflows = testingPostgres
            .runSelectStatement("SELECT COUNT(*) FROM workflow WHERE organization='DockstoreTestUser2' "
                + "AND repository='parameter_test_workflow' AND ispublished='t';", long.class);
        assertEquals(2, countTotalPublishedWorkflows, "Ensure there are 2 published workflows");

        // verify count of number of published workflows, with the desired name, is 1
        final long countPublishedWorkflowWithCustomName = testingPostgres
            .runSelectStatement("SELECT COUNT(*) FROM workflow WHERE organization='DockstoreTestUser2' "
                + "AND repository='parameter_test_workflow' AND workflowname='test_entryname' AND ispublished='t';", long.class);
        assertEquals(1, countPublishedWorkflowWithCustomName,
                "Ensure there is a published workflow with the expected workflow name");

        // Try unpublishing with both --unpub and --entryname specified, should fail
        int exitCode = catchSystemExit(() ->   Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--unpub",
                "--entry", "github.com/DockstoreTestUser2/parameter_test_workflow", publishNameParameter, "test_entryname", "--script"}));
        assertEquals(Client.COMMAND_ERROR, exitCode);

        catchSystemExit(() -> Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--unpub",
            "--entry", "github.com/DockstoreTestUser2/parameter_test_workflow", publishNameParameter, "test_entryname", "--script"}));

        // unpublish workflow with name 'test_entryname'
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--unpub",
            "--entry", "github.com/DockstoreTestUser2/parameter_test_workflow/test_entryname", "--script"});

        // verify count of number of unpublish workflows with the desired name is 1
        final long countUnpublishedWorkflowWithCustomName = testingPostgres.runSelectStatement(
            "SELECT COUNT(*) FROM workflow WHERE organization='DockstoreTestUser2' AND repository='parameter_test_workflow' AND workflowname='test_entryname' AND ispublished='f';", long.class);
        assertEquals(1, countUnpublishedWorkflowWithCustomName, "The workflow should exist and be unpublished");

        systemOutRule.clear();
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--unpub",
            "--entry", "github.com/DockstoreTestUser2/parameter_test_workflow/test_entryname", "--script"});
        assertTrue(
                systemOutRule.getText().contains("The following workflow is already unpublished: github.com/DockstoreTestUser2/parameter_test_workflow"),
                "Attempting to publish a registered workflow should notify the user");
    }
}
