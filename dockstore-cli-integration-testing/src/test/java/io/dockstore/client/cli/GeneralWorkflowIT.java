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

import static io.dockstore.client.cli.ArgumentUtility.CONVERT;
import static io.dockstore.client.cli.Client.CONFIG;
import static io.dockstore.client.cli.Client.SCRIPT_FLAG;
import static io.dockstore.client.cli.Client.VERSION;
import static io.dockstore.client.cli.Client.WORKFLOW;
import static io.dockstore.client.cli.nested.AbstractEntryClient.ENTRY_2_JSON;
import static io.dockstore.client.cli.nested.AbstractEntryClient.INFO;
import static io.dockstore.client.cli.nested.AbstractEntryClient.PUBLISH;
import static io.dockstore.client.cli.nested.AbstractEntryClient.REFRESH;
import static io.dockstore.client.cli.nested.AbstractEntryClient.TEST_PARAMETER;
import static io.dockstore.client.cli.nested.ToolClient.VERSION_TAG;
import static io.dockstore.client.cli.nested.WesCommandParser.ENTRY;
import static io.dockstore.client.cli.nested.WorkflowClient.UPDATE_WORKFLOW;
import static io.dockstore.webservice.helpers.ZenodoHelper.PUBLISHED_ENTRY_REQUIRED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static uk.org.webcompere.systemstubs.SystemStubs.catchSystemExit;

import com.google.common.collect.Lists;
import io.dockstore.client.cli.nested.WorkflowClient;
import io.dockstore.common.CLICommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.ToilCompatibleTest;
import io.dockstore.common.WorkflowTest;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.ApiException;
import io.dockstore.openapi.client.api.HostedApi;
import io.dockstore.openapi.client.api.WorkflowsApi;
import io.dockstore.openapi.client.model.SourceFile;
import io.dockstore.openapi.client.model.Workflow;
import io.dockstore.openapi.client.model.Workflow.DescriptorTypeEnum;
import io.dockstore.openapi.client.model.Workflow.SourceControlEnum;
import io.dockstore.openapi.client.model.WorkflowVersion;
import io.dropwizard.testing.ResourceHelpers;
import java.util.List;
import org.elasticsearch.common.collect.Set;
import org.junit.experimental.categories.Category;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

/**
 * This test suite will have tests for the workflow mode of the Dockstore Client.
 * Created by aduncan on 05/04/16.
 */
@Tag(ConfidentialTest.NAME)
@Tag(WorkflowTest.NAME)
class GeneralWorkflowIT extends BaseIT {

    @SystemStub
    public final SystemOut systemOutRule = new SystemOut();

    @SystemStub
    public final SystemErr systemErrRule = new SystemErr();

    @BeforeEach
    @Override
    public void resetDBBetweenTests() throws Exception {
        CLICommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
    }

    @Test
    @Disabled("Ignoring for 1.14.0, refresh less important and this test is probably not scaling well as number of testing workflow increases")
    void refreshAll() {
        // refresh all
        // refreshByOrganizationReplacement(USER_2_USERNAME);

        // get userid
        final long userid = testingPostgres.runSelectStatement(String.format("SELECT id FROM user_profile WHERE username='%s';", USER_2_USERNAME), long.class);

        // Delete all entries associated with the userid
        testingPostgres.runDeleteStatement(String.format("DELETE FROM user_entry ue WHERE ue.userid = %d", userid));

        // Count number of entries after running the delete statement
        final long entryCountAfterDelete = testingPostgres.runSelectStatement(String.format("SELECT COUNT(*) FROM user_entry WHERE userid = %d;", userid), long.class);
        assertEquals(0, entryCountAfterDelete, "After deletion, there should be 0 entries remaining associated with this user");

        // run CLI refresh command to refresh all workflows
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, REFRESH});

        // final count of workflows associated with this user
        final long entryCountAfterRefresh = testingPostgres.runSelectStatement(String.format("SELECT COUNT(*) FROM user_entry WHERE userid = %d;", userid), long.class);
        assertTrue(entryCountAfterRefresh >= 40, "User should be associated with >= 40 workflows");
    }

    /**
     * This test checks that refresh all workflows (with a mix of stub and full) and refresh individual.  It then tries to publish them
     */
    @Test
    void testRefreshAndPublish() {
        // refresh all
        refreshByOrganizationReplacement(USER_2_USERNAME, Set.of(SourceControlEnum.GITHUB_COM + "/DockstoreTestUser2/hello-dockstore-workflow"));

        // refresh individual that is valid
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, REFRESH, ENTRY,
            SourceControlEnum.GITHUB_COM + "/DockstoreTestUser2/hello-dockstore-workflow", SCRIPT_FLAG });

        // check that valid is valid and full
        final long count = testingPostgres.runSelectStatement("select count(*) from workflow where ispublished='t'", long.class);
        assertEquals(0, count, "there should be 0 published entries, there are " + count);
        final long count2 = testingPostgres.runSelectStatement("select count(*) from workflowversion where valid='t'", long.class);
        assertEquals(2, count2, "there should be 2 valid versions, there are " + count2);
        final long count3 = testingPostgres.runSelectStatement("select count(*) from workflow where mode='FULL'", long.class);
        assertEquals(1, count3, "there should be 1 full workflows, there are " + count3);
        final long count4 = testingPostgres.runSelectStatement("select count(*) from workflowversion", long.class);
        assertTrue(4 <= count4, "there should be at least 4 versions, there are " + count4);

        // attempt to publish it
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, PUBLISH, ENTRY,
            SourceControlEnum.GITHUB_COM + "/DockstoreTestUser2/hello-dockstore-workflow", SCRIPT_FLAG });

        final long count5 = testingPostgres.runSelectStatement("select count(*) from workflow where ispublished='t'", long.class);
        assertEquals(1, count5, "there should be 1 published entry, there are " + count5);

        // unpublish
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, PUBLISH, ENTRY,
            SourceControlEnum.GITHUB_COM + "/DockstoreTestUser2/hello-dockstore-workflow", "--unpub", SCRIPT_FLAG });

        final long count6 = testingPostgres.runSelectStatement("select count(*) from workflow where ispublished='t'", long.class);
        assertEquals(0, count6, "there should be 0 published entries, there are " + count6);

        testPublishList();
    }

    /**
     * Test the "dockstore workflow publish" command
     */
    private void testPublishList() {
        systemOutRule.clear();
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, PUBLISH, SCRIPT_FLAG });
        assertTrue(systemOutRule.getText().contains("github.com/DockstoreTestUser2/hello-dockstore-workflow"),
                "Should contain a FULL workflow belonging to the user");
        assertFalse(systemOutRule.getText().contains("gitlab.com/dockstore.test.user2/dockstore-workflow-md5sum-unified "),
                "Should not contain a STUB workflow belonging to the user");
    }


    /**
     * This tests attempting to publish a workflow with no valid versions
     */
    @Test
    void testRefreshAndPublishInvalid() throws Exception {
        // refresh all
        refreshByOrganizationReplacement(USER_2_USERNAME, Set.of(SourceControlEnum.GITHUB_COM + "/DockstoreTestUser2/dockstore_empty_repo"));

        // refresh individual
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, REFRESH, ENTRY,
            SourceControlEnum.GITHUB_COM + "/DockstoreTestUser2/dockstore_empty_repo", SCRIPT_FLAG });

        // check that no valid versions
        final long count = testingPostgres.runSelectStatement("select count(*) from workflowversion where valid='t'", long.class);
        assertEquals(0, count, "there should be 0 valid versions, there are " + count);

        // try and publish
        int exitCode = catchSystemExit(() -> Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, PUBLISH, ENTRY,
                SourceControlEnum.GITHUB_COM + "/DockstoreTestUser2/dockstore_empty_repo", SCRIPT_FLAG }));
        assertEquals(Client.API_ERROR, exitCode);
    }







    /**
     * Tests updating workflow descriptor type when a workflow is FULL and when it is a STUB
     */
    @Test
    void testDescriptorTypes() throws Exception {
        refreshByOrganizationReplacement(USER_2_USERNAME, Set.of(SourceControlEnum.GITHUB_COM + "/DockstoreTestUser2/hello-dockstore-workflow"));
        Client.main(
            new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, UPDATE_WORKFLOW, ENTRY,
                SourceControlEnum.GITHUB_COM + "/DockstoreTestUser2/hello-dockstore-workflow", "--descriptor-type", DescriptorTypeEnum.WDL.toString(), SCRIPT_FLAG });

        final long count = testingPostgres.runSelectStatement("select count(*) from workflow where descriptortype = 'wdl'", long.class);
        assertEquals(1, count, "there should be 1 wdl workflow, there are " + count);

        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, REFRESH, ENTRY,
            SourceControlEnum.GITHUB_COM + "/DockstoreTestUser2/hello-dockstore-workflow", SCRIPT_FLAG });
        int exitCode = catchSystemExit(() -> Client.main(
                new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, UPDATE_WORKFLOW, ENTRY,
                        SourceControlEnum.GITHUB_COM + "/DockstoreTestUser2/hello-dockstore-workflow", "--descriptor-type", DescriptorTypeEnum.CWL.toString(), SCRIPT_FLAG }));
        assertEquals(Client.CLIENT_ERROR, exitCode);
    }

    /**
     * Tests updating a workflow tag with invalid workflow descriptor path
     */
    @Test
    void testWorkflowVersionIncorrectPath() throws Exception {
        refreshByOrganizationReplacement(USER_2_USERNAME, Set.of(SourceControlEnum.GITHUB_COM + "/DockstoreTestUser2/hello-dockstore-workflow"));
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, REFRESH, ENTRY,
            SourceControlEnum.GITHUB_COM + "/DockstoreTestUser2/hello-dockstore-workflow", SCRIPT_FLAG });
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, VERSION_TAG, ENTRY,
            SourceControlEnum.GITHUB_COM + "/DockstoreTestUser2/hello-dockstore-workflow", "--name", "master", "--workflow-path",
            "/newdescriptor.cwl", SCRIPT_FLAG });

        final long count = testingPostgres
            .runSelectStatement("select count(*) from workflowversion where name = 'master' and workflowpath = '/newdescriptor.cwl'",
                long.class);
        assertEquals(1, count, "the workflow version should now have a new descriptor path");

        int exitCode = catchSystemExit(() -> Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, VERSION_TAG, ENTRY,
                SourceControlEnum.GITHUB_COM + "/DockstoreTestUser2/hello-dockstore-workflow", "--name", "master", "--workflow-path",
                "/Dockstore.wdl", SCRIPT_FLAG }));
        assertEquals(Client.CLIENT_ERROR, exitCode);
    }

    /**
     * Tests that convert with valid imports will work, but convert without valid imports will throw an error (for CWL)
     */
    @Test
    @Category(ToilCompatibleTest.class)
    void testRefreshAndConvertWithImportsCWL() throws Exception {
        refreshByOrganizationReplacement(USER_2_USERNAME, Set.of(SourceControlEnum.GITHUB_COM + "/DockstoreTestUser2/hello-dockstore-workflow"));
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, REFRESH, ENTRY,
            SourceControlEnum.GITHUB_COM + "/DockstoreTestUser2/hello-dockstore-workflow", SCRIPT_FLAG });
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, PUBLISH, ENTRY,
            SourceControlEnum.GITHUB_COM + "/DockstoreTestUser2/hello-dockstore-workflow", SCRIPT_FLAG });
        Client.main(
            new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, CONVERT, ENTRY_2_JSON, ENTRY,
                SourceControlEnum.GITHUB_COM + "/DockstoreTestUser2/hello-dockstore-workflow:testBoth", SCRIPT_FLAG });

        int exitCode = catchSystemExit(() -> Client.main(
                new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, CONVERT, ENTRY_2_JSON, ENTRY,
                        SourceControlEnum.GITHUB_COM + "/DockstoreTestUser2/hello-dockstore-workflow:testCWL", SCRIPT_FLAG }));
        assertEquals(Client.GENERIC_ERROR, exitCode);
    }




    /**
     * This test tests a bunch of different assumptions for how refresh should work for workflows
     */
    @Test
    void testRefreshRelatedConcepts() throws Exception {
        // refresh all
        refreshByOrganizationReplacement(USER_2_USERNAME, Set.of(SourceControlEnum.GITHUB_COM + "/DockstoreTestUser2/hello-dockstore-workflow"));

        // refresh individual that is valid
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, REFRESH, ENTRY,
            SourceControlEnum.GITHUB_COM + "/DockstoreTestUser2/hello-dockstore-workflow", SCRIPT_FLAG });

        // check that workflow is valid and full
        final long count2 = testingPostgres.runSelectStatement("select count(*) from workflowversion where valid='t'", long.class);
        assertEquals(2, count2, "there should be 2 valid versions, there are " + count2);
        final long count3 = testingPostgres.runSelectStatement("select count(*) from workflow where mode='FULL'", long.class);
        assertEquals(1, count3, "there should be 1 full workflows, there are " + count3);

        // Change path for each version so that it is invalid
        testingPostgres.runUpdateStatement("UPDATE workflowversion SET workflowpath='thisisnotarealpath.cwl', dirtybit=true");
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, REFRESH, ENTRY,
            SourceControlEnum.GITHUB_COM + "/DockstoreTestUser2/hello-dockstore-workflow", SCRIPT_FLAG });

        // Workflow has no valid versions so you cannot publish

        // check that invalid
        final long count4 = testingPostgres.runSelectStatement("select count(*) from workflowversion where valid='f'", long.class);
        assertTrue(4 <= count4, "there should be at least 4 invalid versions, there are " + count4);
    }

    /**
     * This test tests a bunch of different assumptions for how refresh should work for workflows
     */
    @Test
    void testRefreshRelatedConceptsWdl() throws Exception {
        // refresh all
        refreshByOrganizationReplacement(USER_2_USERNAME, Set.of(SourceControlEnum.GITHUB_COM + "/DockstoreTestUser2/hello-dockstore-workflow"));


        // Update workflow to WDL
        Client.main(
            new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, UPDATE_WORKFLOW, ENTRY,
                SourceControlEnum.GITHUB_COM + "/DockstoreTestUser2/hello-dockstore-workflow", "--workflow-path", "Dockstore.wdl",
                "--descriptor-type", DescriptorTypeEnum.WDL.toString(), SCRIPT_FLAG });

        // Can now publish workflow
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, PUBLISH, ENTRY,
            SourceControlEnum.GITHUB_COM + "/DockstoreTestUser2/hello-dockstore-workflow", SCRIPT_FLAG });

        // unpublish
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, PUBLISH, ENTRY,
            SourceControlEnum.GITHUB_COM + "/DockstoreTestUser2/hello-dockstore-workflow", "--unpub", SCRIPT_FLAG });

        // Set paths to invalid
        testingPostgres.runUpdateStatement("UPDATE workflowversion SET workflowpath='thisisnotarealpath.wdl', dirtybit=true");
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, REFRESH, ENTRY,
            SourceControlEnum.GITHUB_COM + "/DockstoreTestUser2/hello-dockstore-workflow", SCRIPT_FLAG });

        // Check that versions are invalid
        final long count5 = testingPostgres.runSelectStatement("select count(*) from workflowversion where valid='f'", long.class);
        assertTrue(4 <= count5, "there should be at least 4 invalid versions, there are " + count5);

        // should now not be able to publish
        int exitCode = catchSystemExit(() -> Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, PUBLISH, ENTRY,
            SourceControlEnum.GITHUB_COM + "/DockstoreTestUser2/hello-dockstore-workflow", SCRIPT_FLAG }));
        assertEquals(Client.API_ERROR, exitCode);
    }

    /**
     * This tests the dirty bit attribute for workflow versions with github
     */
    @Test
    void testGithubDirtyBit() {
        // refresh all
        refreshByOrganizationReplacement(USER_2_USERNAME, Set.of(SourceControlEnum.GITHUB_COM + "/DockstoreTestUser2/hello-dockstore-workflow"));

        // refresh individual that is valid
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, REFRESH, ENTRY,
            SourceControlEnum.GITHUB_COM + "/DockstoreTestUser2/hello-dockstore-workflow", SCRIPT_FLAG });

        // Check that no versions have a true dirty bit
        final long count = testingPostgres.runSelectStatement("select count(*) from workflowversion where dirtybit = true", long.class);
        assertEquals(0, count, "there should be no versions with dirty bit, there are " + count);

        // Edit workflow path for a version
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, VERSION_TAG, ENTRY,
            SourceControlEnum.GITHUB_COM + "/DockstoreTestUser2/hello-dockstore-workflow", "--name", "master", "--workflow-path",
            "/Dockstoredirty.cwl", SCRIPT_FLAG });

        // There should be on dirty bit
        final long count1 = testingPostgres.runSelectStatement("select count(*) from workflowversion where dirtybit = true", long.class);
        assertEquals(1, count1, "there should be 1 versions with dirty bit, there are " + count1);

        // Update default cwl
        Client.main(
            new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, UPDATE_WORKFLOW, ENTRY,
                SourceControlEnum.GITHUB_COM + "/DockstoreTestUser2/hello-dockstore-workflow", "--workflow-path", "/Dockstoreclean.cwl",
                SCRIPT_FLAG });

        // There should be 3 versions with new cwl
        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from workflowversion where workflowpath = '/Dockstoreclean.cwl'", long.class);
        assertTrue(3 <= count2,
                "there should be at least 3 versions with workflow path /Dockstoreclean.cwl, there are " + count2);

    }

    /**
     * This is a high level test to ensure that gitlab basics are working for gitlab as a workflow repo
     */
    @Test
    @Disabled("Ignoring for 1.8.6, enable for 1.9.0")
    void testGitLab() {
        // Refresh workflow
        refreshByOrganizationReplacement(USER_2_USERNAME, Set.of(SourceControlEnum.GITLAB_COM + "/dockstore.test.user2/dockstore-workflow-example"));
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, REFRESH, ENTRY,
            SourceControlEnum.GITLAB_COM + "/dockstore.test.user2/dockstore-workflow-example", SCRIPT_FLAG });
        final long nullLastModifiedWorkflowVersions = testingPostgres
            .runSelectStatement("select count(*) from workflowversion where lastmodified is null", long.class);
        assertEquals(0, nullLastModifiedWorkflowVersions,
                "All GitLab workflow versions should have last modified populated after refreshing");

        // Check a few things
        final long count = testingPostgres.runSelectStatement(
            "select count(*) from workflow where mode='FULL' and sourcecontrol = '" + SourceControlEnum.GITLAB_COM
                + "' and organization = 'dockstore.test.user2' and repository = 'dockstore-workflow-example'", long.class);
        assertEquals(1, count, "there should be 1 workflow, there are " + count);

        final long count2 = testingPostgres.runSelectStatement("select count(*) from workflowversion where valid='t'", long.class);
        assertEquals(2, count2, "there should be 2 valid version, there are " + count2);

        final long count3 = testingPostgres.runSelectStatement(
            "select count(*) from workflow where mode='FULL' and sourcecontrol = '" + SourceControlEnum.GITLAB_COM
                + "' and organization = 'dockstore.test.user2' and repository = 'dockstore-workflow-example'", long.class);
        assertEquals(1, count3, "there should be 1 workflow, there are " + count3);

        // publish
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, PUBLISH, ENTRY,
            SourceControlEnum.GITLAB_COM + "/dockstore.test.user2/dockstore-workflow-example", SCRIPT_FLAG });
        final long count4 = testingPostgres.runSelectStatement(
            "select count(*) from workflow where mode='FULL' and sourcecontrol = '" + SourceControlEnum.GITLAB_COM
                + "' and organization = 'dockstore.test.user2' and repository = 'dockstore-workflow-example' and ispublished='t'",
            long.class);
        assertEquals(1, count4, "there should be 1 published workflow, there are " + count4);

        // Should be able to get info since it is published
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, INFO, ENTRY,
            SourceControlEnum.GITLAB_COM + "/dockstore.test.user2/dockstore-workflow-example", SCRIPT_FLAG });

        // Should be able to grab descriptor
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, DescriptorTypeEnum.CWL.toString(), ENTRY,
            SourceControlEnum.GITLAB_COM + "/dockstore.test.user2/dockstore-workflow-example:master", SCRIPT_FLAG });

        // unpublish
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, PUBLISH, ENTRY,
            SourceControlEnum.GITLAB_COM + "/dockstore.test.user2/dockstore-workflow-example", "--unpub", SCRIPT_FLAG });
        final long count5 = testingPostgres.runSelectStatement(
            "select count(*) from workflow where mode='FULL' and sourcecontrol = '" + SourceControlEnum.GITLAB_COM
                + "' and organization = 'dockstore.test.user2' and repository = 'dockstore-workflow-example' and ispublished='t'",
            long.class);
        assertEquals(0, count5, "there should be 0 published workflows, there are " + count5);

        // change default branch
        final long count6 = testingPostgres.runSelectStatement(
            "select count(*) from workflow where sourcecontrol = '" + SourceControlEnum.GITLAB_COM
                + "' and organization = 'dockstore.test.user2' and repository = 'dockstore-workflow-example' and author is null and email is null and description is null",
            long.class);
        assertEquals(1, count6, "The given workflow shouldn't have any contact info");

        Client.main(
            new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, UPDATE_WORKFLOW, ENTRY,
                SourceControlEnum.GITLAB_COM + "/dockstore.test.user2/dockstore-workflow-example", "--default-version", "test",
                SCRIPT_FLAG });

        final long count7 = testingPostgres.runSelectStatement(
            "select count(*) from workflow where actualdefaultversion = 'test' and author is null and email is null and description is null",
            long.class);
        assertEquals(0, count7, "The given workflow should now have contact info and description");
    }




    /**
     * This tests that WDL files are properly parsed for secondary WDL files
     */
    @Test
    void testWDLWithImports() {
        // Refresh all
        refreshByOrganizationReplacement(USER_2_USERNAME, Set.of(SourceControlEnum.GITHUB_COM + "/DockstoreTestUser2/test_workflow_wdl"));

        // Update workflow to be WDL with correct path
        Client.main(
            new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, UPDATE_WORKFLOW, ENTRY,
                SourceControlEnum.GITHUB_COM + "/DockstoreTestUser2/test_workflow_wdl", "--descriptor-type", DescriptorTypeEnum.WDL.toString(), "--workflow-path",
                "/hello.wdl", SCRIPT_FLAG });

        // Check for WDL files
        final long count = testingPostgres.runSelectStatement("select count(*) from sourcefile where path='helper.wdl'", long.class);
        assertEquals(1, count, "there should be 1 secondary file named helper.wdl, there are " + count);

    }

    /**
     * This tests basic concepts with workflow test parameter files
     */
    @Test
    void testTestParameterFile() {
        // Refresh all
        refreshByOrganizationReplacement(USER_2_USERNAME, Set.of(SourceControlEnum.GITHUB_COM + "/DockstoreTestUser2/parameter_test_workflow"));

        // Refresh specific
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, REFRESH, ENTRY,
            SourceControlEnum.GITHUB_COM + "/DockstoreTestUser2/parameter_test_workflow", SCRIPT_FLAG });

        // There should be no sourcefiles
        final long count = testingPostgres.runSelectStatement("select count(*) from sourcefile where type like '%_TEST_JSON'", long.class);
        assertEquals(0, count, "there should be no source files that are test parameter files, there are " + count);

        // Update version master with test parameters
        Client.main(
            new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, TEST_PARAMETER, ENTRY,
                SourceControlEnum.GITHUB_COM + "/DockstoreTestUser2/parameter_test_workflow", VERSION, "master", "--add",
                // Trying to remove a non-existent test parameter file now causes an error. It didn't use to and test was relying
                // on that behavior.
                "test.cwl.json", "--add", "test2.cwl.json", "--add", "fake.cwl.json", /*"--remove", "notreal.cwl.json",*/ SCRIPT_FLAG });
        final long count2 = testingPostgres.runSelectStatement("select count(*) from sourcefile where type like '%_TEST_JSON'", long.class);
        assertEquals(2, count2, "there should be two sourcefiles that are test parameter files, there are " + count2);

        // Update version with test parameters
        Client.main(
            new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, TEST_PARAMETER, ENTRY,
                SourceControlEnum.GITHUB_COM + "/DockstoreTestUser2/parameter_test_workflow", VERSION, "master", "--add",
                "test.cwl.json", "--remove", "test2.cwl.json", SCRIPT_FLAG });
        final long count3 = testingPostgres.runSelectStatement("select count(*) from sourcefile where type like '%_TEST_JSON'", long.class);
        assertEquals(1, count3, "there should be one sourcefile that is a test parameter file, there are " + count3);

        // Update other version with test parameters
        Client.main(
            new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, TEST_PARAMETER, ENTRY,
                SourceControlEnum.GITHUB_COM + "/DockstoreTestUser2/parameter_test_workflow", VERSION, "wdltest", "--add",
                "test.wdl.json", SCRIPT_FLAG });
        final long count4 = testingPostgres.runSelectStatement("select count(*) from sourcefile where type='CWL_TEST_JSON'", long.class);
        assertEquals(2, count4, "there should be two sourcefiles that are cwl test parameter files, there are " + count4);
    }


    /**
     * This tests that you can refresh user data by refreshing a workflow
     * ONLY WORKS if the current user in the database dump has no metadata, and on Github there is metadata (bio, location)
     * If the user has metadata, test will pass as long as the user's metadata isn't the same as Github already
     * <p>
     * Ignoring this one for 1.9, since we don't have the refresh endpoint any more
     */
    @Disabled("refresh has changed")
    @Test
    void testRefreshingUserMetadata() {
        // Refresh all workflows
        refreshByOrganizationReplacement(USER_2_USERNAME, Set.of(SourceControlEnum.GITHUB_COM + "/DockstoreTestUser2/parameter_test_workflow"));

        // Check that user has been updated
        // TODO: bizarrely, the new GitHub Java API library doesn't seem to handle bio
        // final long count = testingPostgres.runSelectStatement("select count(*) from enduser where location='Toronto' and bio='I am a test user'", long.class);
        final long count = testingPostgres.runSelectStatement("select count(*) from user_profile where location='Toronto'", long.class);
        assertEquals(1, count, "One user should have this info now, there are  " + count);
    }

    @Test
    void testGenerateDOIFrozenVersion() throws ApiException {
        // Set up webservice
        ApiClient webClient = WorkflowIT.getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        //register workflow
        Workflow githubWorkflow = workflowApi
            .manualRegister("github", "DockstoreTestUser2/test_lastmodified", "/hello.wdl", "test-update-workflow", DescriptorTypeEnum.WDL.toString(), "/test.json");

        Workflow workflowBeforeFreezing = workflowApi.refresh1(githubWorkflow.getId(), true);
        WorkflowVersion master = workflowBeforeFreezing.getWorkflowVersions().stream().filter(v -> v.getName().equals("master")).findFirst()
            .get();

        //try issuing DOI for workflow version that is not frozen.
        try {
            workflowApi.requestDOIForWorkflowVersion(workflowBeforeFreezing.getId(), master.getId(), "");
            fail(
                    "This line should never execute if version is mutable. DOI should only be generated for frozen versions of workflows.");
        } catch (ApiException ex) {
            assertTrue(ex.getResponseBody().contains(PUBLISHED_ENTRY_REQUIRED));
        }

        //freeze version 'master'
        master.setFrozen(true);
        final List<WorkflowVersion> workflowVersions1 = workflowApi
            .updateWorkflowVersion(workflowBeforeFreezing.getId(), Lists.newArrayList(master));
        master = workflowVersions1.stream().filter(v -> v.getName().equals("master")).findFirst().get();
        assertTrue(master.isFrozen());

        //TODO: For now just checking for next failure (no Zenodo token), but should replace with when DOI registration tests are written
        //TODO: this was supposed to fail without a token, but it fails without publishing
        try {
            workflowApi.requestDOIForWorkflowVersion(workflowBeforeFreezing.getId(), master.getId(), "");
            fail("This line should never execute without valid Zenodo token");
        } catch (ApiException ex) {
            assertTrue(ex.getResponseBody().contains(PUBLISHED_ENTRY_REQUIRED));

        }
    }

    @Test
    void testPublishUnpublishHostedWorkflow() {

        final String publishNameParameter = "--new-entry-name";

        // Create a hosted workflow
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        HostedApi hostedApi = new HostedApi(webClient);
        Workflow hostedWorkflow = hostedApi.createHostedWorkflow(null, "testHosted", DescriptorTypeEnum.CWL.toString(), null, null);
        SourceFile source = new SourceFile();
        source.setPath("/Dockstore.cwl");
        source.setAbsolutePath("/Dockstore.cwl");
        source.setContent("cwlVersion: v1.0\nclass: Workflow");
        source.setType(SourceFile.TypeEnum.DOCKSTORE_CWL);
        hostedApi.editHostedWorkflow(Lists.newArrayList(source), hostedWorkflow.getId());

        // Verify the user has 1 unpublished, hosted workflow
        final long initialUnpublishedHostedCount = testingPostgres
            .runSelectStatement("SELECT COUNT(*) FROM workflow WHERE organization='" + USER_2_USERNAME + "' "
                + "AND repository='testHosted' AND mode='HOSTED' AND ispublished='f';", long.class);
        assertEquals(1, initialUnpublishedHostedCount, "There should be 1 unpublished hosted workflow");

        // Call publish with an entryname parameter specified
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish",
                "--entry", "dockstore.org/DockstoreTestUser2/testHosted", publishNameParameter, "fakeName", "--script"});
        assertTrue(systemErrRule.getText().contains(WorkflowClient.INVALID_WORKFLOW_MODE_PUBLISH), "User should be notified that the command is invalid");

        // Check that there are no published workflows for this user using the HOSTED name/custom names
        final long countTotalPublishedWorkflows = testingPostgres
            .runSelectStatement("SELECT COUNT(*) FROM workflow WHERE organization='" + USER_2_USERNAME + "' "
                + "AND (repository='testHosted' OR repository='fakeName') AND ispublished='t';", long.class);
        assertEquals(0, countTotalPublishedWorkflows, "Should contain no published workflows");

        // attempt to call publish normally, it should succeed
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish",
                "--entry", "dockstore.org/DockstoreTestUser2/testHosted", "--script"});

        // verify the workflow is published
        final long finalPublishedCount = testingPostgres
            .runSelectStatement("SELECT COUNT(*) FROM workflow WHERE organization='" + USER_2_USERNAME + "' "
                + "AND repository='testHosted' AND ispublished='t';", long.class);
        assertEquals(1, finalPublishedCount, "Should contain 1 published workflow");
    }

    @Test
    void testPublishUnpublishedDockstoreYMLWorkflow() {

        final String publishNameParameter = "--new-entry-name";

        // publish a repo
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "manual_publish",
                "--repository", "workflow-dockstore-yml", "--organization", USER_2_USERNAME, "--git-version-control", "github", "--script" });

        // force the workflow into DOCKSTORE_YML mode.
        testingPostgres.runUpdateStatement(
                "UPDATE workflow SET mode = 'DOCKSTORE_YML' WHERE repository='workflow-dockstore-yml' AND organization='" + USER_2_USERNAME
                        + "';");

        // Verify the user has 1 published, apps workflow
        final long initialPublishedYMLCount = testingPostgres.runSelectStatement(
                "SELECT COUNT(*) FROM workflow WHERE organization='" + USER_2_USERNAME + "' " + "AND repository='workflow-dockstore-yml' AND mode='DOCKSTORE_YML' AND ispublished='t';", long.class);
        assertEquals(1, initialPublishedYMLCount, "There should be 1 published DOCKSTORE_YML workflow");

        // Call publish with an entryname parameter specified
        systemErrRule.clear();
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--entry",
                "github.com/" + USER_2_USERNAME + "/workflow-dockstore-yml", publishNameParameter, "fakeName", "--script" });
        assertTrue(systemErrRule.getText()
                .contains(WorkflowClient.INVALID_WORKFLOW_MODE_PUBLISH), "User should be notified that the command is invalid");

        // Verify only 1 workflow is published
        final long countTotalPublishedWorkflows = testingPostgres.runSelectStatement(
                "SELECT COUNT(*) FROM workflow WHERE organization='" + USER_2_USERNAME + "' " + "AND (repository='workflow-dockstore-yml' OR repository='fakeName') AND ispublished='t';", long.class);
        assertEquals(1, countTotalPublishedWorkflows, "User should have only 1 published workflow");
    }
}
