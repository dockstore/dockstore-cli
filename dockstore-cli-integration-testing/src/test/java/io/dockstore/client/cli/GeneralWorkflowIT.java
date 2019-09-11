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

import java.util.List;

import com.google.common.collect.Lists;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.SlowTest;
import io.dockstore.common.SourceControl;
import io.dockstore.common.ToilCompatibleTest;
import io.dockstore.common.WorkflowTest;
import io.dropwizard.testing.ResourceHelpers;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.UsersApi;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.PublishRequest;
import io.swagger.client.model.Workflow;
import io.swagger.client.model.WorkflowVersion;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.experimental.categories.Category;

import static io.dockstore.client.cli.Client.API_ERROR;
import static io.dockstore.webservice.core.Version.CANNOT_FREEZE_VERSIONS_WITH_NO_FILES;
import static io.dockstore.webservice.helpers.EntryVersionHelper.CANNOT_MODIFY_FROZEN_VERSIONS_THIS_WAY;
import static io.dockstore.webservice.resources.WorkflowResource.FROZEN_VERSION_REQUIRED;
import static io.dockstore.webservice.resources.WorkflowResource.NO_ZENDO_USER_TOKEN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * This test suite will have tests for the workflow mode of the Dockstore Client.
 * Created by aduncan on 05/04/16.
 */
@Category({ConfidentialTest.class, WorkflowTest.class})
public class GeneralWorkflowIT extends BaseIT {

    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();

    @Before
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false);
    }

    /**
     * This test checks that refresh all workflows (with a mix of stub and full) and refresh individual.  It then tries to publish them
     */
    @Test
    public void testRefreshAndPublish() {
        // Set up DB


        // refresh all
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--script" });

        // refresh individual that is valid
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--entry",
                SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow", "--script" });

        // refresh all
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--script" });

        // check that valid is valid and full
        final long count = testingPostgres.runSelectStatement("select count(*) from workflow where ispublished='t'", long.class);
        assertEquals("there should be 0 published entries, there are " + count, 0, count);
        final long count2 = testingPostgres
                .runSelectStatement("select count(*) from workflowversion where valid='t'", long.class);
        assertEquals("there should be 2 valid versions, there are " + count2, 2, count2);
        final long count3 = testingPostgres.runSelectStatement("select count(*) from workflow where mode='FULL'", long.class);
        assertEquals("there should be 1 full workflows, there are " + count3, 1, count3);
        final long count4 = testingPostgres.runSelectStatement("select count(*) from workflowversion", long.class);
        assertEquals("there should be 4 versions, there are " + count4, 4, count4);

        // attempt to publish it
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--entry",
                SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow", "--script" });

        final long count5 = testingPostgres
                .runSelectStatement("select count(*) from workflow where ispublished='t'", long.class);
        assertEquals("there should be 1 published entry, there are " + count5, 1, count5);

        // unpublish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--entry",
                SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow", "--unpub", "--script" });

        final long count6 = testingPostgres
                .runSelectStatement("select count(*) from workflow where ispublished='t'", long.class);
        assertEquals("there should be 0 published entries, there are " + count6, 0, count6);

        testPublishList();
    }

    /**
     * Test the "dockstore workflow publish" command
     */
    private void testPublishList() {
        systemOutRule.clearLog();
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--script" });
        assertTrue("Should contain a FULL workflow belonging to the user", systemOutRule.getLog().contains("github.com/DockstoreTestUser2/hello-dockstore-workflow"));
        assertFalse("Should not contain a STUB workflow belonging to the user", systemOutRule.getLog().contains("gitlab.com/dockstore.test.user2/dockstore-workflow-md5sum-unified "));
    }

    /**
     * This tests that the information for a workflow can only be seen if it is published
     */
    @Test
    public void testInfo() {
        // manual publish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "manual_publish",
                "--repository", "hello-dockstore-workflow", "--organization", "DockstoreTestUser2", "--git-version-control", "github",
                "--workflow-name", "testname", "--workflow-path", "/Dockstore.wdl", "--descriptor-type", "wdl", "--script" });

        // info
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "info", "--entry",
                SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow/testname", "--script" });

        // unpublish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--entry",
                SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow/testname", "--unpub", "--script" });

        // info
        systemExit.expectSystemExitWithStatus(Client.API_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "info", "--entry",
                SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow/testname", "--script" });
    }

    /**
     * This test manually publishing a workflow and grabbing valid descriptor
     */
    @Test
    public void testManualPublishAndGrabWDL() {
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "manual_publish",
                "--repository", "hello-dockstore-workflow", "--organization", "DockstoreTestUser2", "--git-version-control", "github",
                "--workflow-name", "testname", "--workflow-path", "/Dockstore.wdl", "--descriptor-type", "wdl", "--script" });
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "wdl", "--entry",
                SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow/testname:testBoth", "--script" });
    }

    /**
     * This tests attempting to publish a workflow with no valid versions
     */
    @Test
    public void testRefreshAndPublishInvalid() {
        // Set up DB


        // refresh all
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--script" });

        // refresh individual
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--entry",
                SourceControl.GITHUB.toString() + "/DockstoreTestUser2/dockstore_empty_repo", "--script" });

        // check that no valid versions
        final long count = testingPostgres
                .runSelectStatement("select count(*) from workflowversion where valid='t'", long.class);
        assertEquals("there should be 0 valid versions, there are " + count, 0, count);

        // try and publish
        systemExit.expectSystemExitWithStatus(Client.API_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--entry",
                SourceControl.GITHUB.toString() + "/DockstoreTestUser2/dockstore_empty_repo", "--script" });
    }

    /**
     * This tests attempting to manually publish a workflow with no valid versions
     */
    @Test
    public void testManualPublishInvalid() {
        systemExit.expectSystemExitWithStatus(Client.API_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "manual_publish",
                "--repository", "dockstore_empty_repo", "--organization", "DockstoreTestUser2", "--git-version-control", "github",
                "--workflow-name", "testname", "--workflow-path", "/Dockstore.wdl", "--descriptor-type", "wdl", "--script" });
    }

    /**
     * This tests adding and removing labels from a workflow
     */
    @Test
    public void testLabelEditing() {
        // Set up DB


        // Set up workflow
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "manual_publish",
                "--repository", "hello-dockstore-workflow", "--organization", "DockstoreTestUser2", "--git-version-control", "github",
                "--workflow-path", "/Dockstore.wdl", "--descriptor-type", "wdl", "--script" });

        // add labels
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "label", "--entry",
                SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow", "--add", "test1", "--add", "test2", "--script" });

        final long count = testingPostgres.runSelectStatement("select count(*) from entry_label", long.class);
        assertEquals("there should be 2 labels, there are " + count, 2, count);

        // remove labels
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "label", "--entry",
                SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow", "--remove", "test1", "--add", "test3", "--script" });

        final long count2 = testingPostgres.runSelectStatement("select count(*) from entry_label", long.class);
        assertEquals("there should be 2 labels, there are " + count2, 2, count2);
    }

    /**
     * This tests manually publishing a workflow and grabbing invalid descriptor (should fail)
     */
    @Test
    public void testGetInvalidDescriptor() {
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "manual_publish",
                "--repository", "hello-dockstore-workflow", "--organization", "DockstoreTestUser2", "--git-version-control", "github",
                "--workflow-name", "testname", "--workflow-path", "/Dockstore.wdl", "--descriptor-type", "wdl", "--script" });

        systemExit.expectSystemExitWithStatus(Client.API_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "cwl", "--entry",
                SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow/testname:testBoth", "--script" });
    }

    /**
     * This tests manually publishing a duplicate workflow (should fail)
     */
    @Test
    public void testManualPublishDuplicate() {
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "manual_publish",
                "--repository", "hello-dockstore-workflow", "--organization", "DockstoreTestUser2", "--git-version-control", "github",
                "--workflow-name", "testname", "--workflow-path", "/Dockstore.wdl", "--descriptor-type", "wdl", "--script" });

        systemExit.expectSystemExitWithStatus(Client.API_ERROR);

        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "manual_publish",
                "--repository", "hello-dockstore-workflow", "--organization", "DockstoreTestUser2", "--git-version-control", "github",
                "--workflow-name", "testname", "--workflow-path", "/Dockstore.wdl", "--descriptor-type", "wdl", "--script" });
    }

    /**
     * This tests that a user can update a workflow version
     */
    @Test
    public void testUpdateWorkflowVersion() {
        // Set up DB


        // Update workflow
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "manual_publish",
                "--repository", "hello-dockstore-workflow", "--organization", "DockstoreTestUser2", "--git-version-control", "github",
                "--workflow-name", "testname", "--workflow-path", "/Dockstore.wdl", "--descriptor-type", "wdl", "--script" });
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "version_tag", "--entry",
                SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow/testname", "--name", "master", "--workflow-path", "/Dockstore2.wdl",
                "--hidden", "true", "--script" });

        final long count = testingPostgres.runSelectStatement(
                "select count(*) from workflowversion wv, version_metadata vm where wv.name = 'master' and vm.hidden = 't' and wv.workflowpath = '/Dockstore2.wdl' and wv.id = vm.id",
                long.class);
        assertEquals("there should be 1 matching workflow version, there is " + count, 1, count);
    }

    /**
     * This tests that a restub will work on an unpublished, full workflow
     */
    @Test
    public void testRestub() {
        // Set up DB


        // Refresh and then restub
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--script" });
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--entry",
                SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow", "--script" });
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "restub", "--entry",
                SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow", "--script" });

        final long count = testingPostgres.runSelectStatement("select count(*) from workflowversion", long.class);
        assertEquals("there should be 0 workflow versions, there are " + count, 0, count);
    }

    /**
     * This tests that a restub will not work on an published, full workflow
     */
    @Test
    public void testRestubError() {
        // Refresh and then restub
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--script" });
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--entry",
                SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow", "--script" });
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--entry",
                SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow", "--script" });

        systemExit.expectSystemExitWithStatus(Client.CLIENT_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "restub", "--entry",
                SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow", "--script" });
    }

    /**
     * Tests updating workflow descriptor type when a workflow is FULL and when it is a STUB
     */
    @Test
    public void testDescriptorTypes() {
        // Set up DB


        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--script" });
        Client.main(
                new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "update_workflow", "--entry",
                        SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow", "--descriptor-type", "wdl", "--script" });

        final long count = testingPostgres
                .runSelectStatement("select count(*) from workflow where descriptortype = 'wdl'", long.class);
        assertEquals("there should be 1 wdl workflow, there are " + count, 1, count);

        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--entry",
                SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow", "--script" });
        systemExit.expectSystemExitWithStatus(Client.CLIENT_ERROR);
        Client.main(
                new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "update_workflow", "--entry",
                        SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow", "--descriptor-type", "cwl", "--script" });
    }

    /**
     * Tests updating a workflow tag with invalid workflow descriptor path
     */
    @Test
    public void testWorkflowVersionIncorrectPath() {
        // Set up DB


        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--script" });
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--entry",
                SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow", "--script" });
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "version_tag", "--entry",
                SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow", "--name", "master", "--workflow-path", "/newdescriptor.cwl", "--script" });

        final long count = testingPostgres
                .runSelectStatement("select count(*) from workflowversion where name = 'master' and workflowpath = '/newdescriptor.cwl'",
                        long.class);
        assertEquals("the workflow version should now have a new descriptor path", 1, count);

        systemExit.expectSystemExitWithStatus(Client.CLIENT_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "version_tag", "--entry",
                SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow", "--name", "master", "--workflow-path", "/Dockstore.wdl", "--script" });

    }

    /**
     * Tests that convert with valid imports will work, but convert without valid imports will throw an error (for CWL)
     */
    @Test
    @Category(ToilCompatibleTest.class)
    public void testRefreshAndConvertWithImportsCWL() {
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--script" });
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--entry",
                SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow", "--script" });
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--entry",
                SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow", "--script" });
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "convert", "entry2json",
                "--entry", SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow:testBoth", "--script" });

        systemExit.expectSystemExitWithStatus(Client.GENERIC_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "convert", "entry2json",
                "--entry", SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow:testCWL", "--script" });

    }

    /**
     * Tests that convert with valid imports will work (for WDL)
     */
    @Test
    public void testRefreshAndConvertWithImportsWDL() {

        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--script" });
        Client.main(
                new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "update_workflow", "--entry",
                        SourceControl.BITBUCKET.toString() + "/dockstore_testuser2/dockstore-workflow", "--descriptor-type", "wdl", "--workflow-path", "/Dockstore.wdl",
                        "--script" });

        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--entry",
                SourceControl.BITBUCKET.toString() + "/dockstore_testuser2/dockstore-workflow", "--script" });
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--entry",
                SourceControl.BITBUCKET.toString() + "/dockstore_testuser2/dockstore-workflow", "--script" });

        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "convert", "entry2json",
                "--entry", SourceControl.BITBUCKET.toString() + "/dockstore_testuser2/dockstore-workflow:wdl_import","--script" });
        assertTrue(systemOutRule.getLog().contains("\"three_step.cgrep.pattern\": \"String\""));

    }

    /**
     * Tests that a developer can launch a CWL workflow locally, instead of getting files from Dockstore
     * Todo: Works locally but not on Travis.  This is due the the relative position of the file paths in the input JSON
     */
    @Ignore
    public void testLocalLaunchCWL() {
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "launch", "--local-entry",
                ResourceHelpers.resourceFilePath("filtercount.cwl.yaml"), "--json",
                ResourceHelpers.resourceFilePath("filtercount-job.json"), "--script" });
    }

    /**
     * This tests that attempting to launch a workflow locally, where no file exists, an IOError will occur
     */
    @Test
    public void testLocalLaunchCWLNoFile() {
        systemExit.expectSystemExitWithStatus(Client.ENTRY_NOT_FOUND);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "launch", "--local-entry",
                "imnotreal.cwl", "--json", "imnotreal-job.json", "--script" });
    }

    /**
     * This tests that attempting to launch a WDL workflow locally, where no file exists, an IOError will occur
     */
    @Test
    public void testLocalLaunchWDLNoFile() {
        systemExit.expectSystemExitWithStatus(Client.ENTRY_NOT_FOUND);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "launch", "--local-entry",
                "imnotreal.wdl", "--json", "imnotreal-job.json", "--script" });
    }

    /**
     * This tests that attempting to launch a workflow remotely, where no file exists, an APIError will occur
     */
    @Test
    @Category(ToilCompatibleTest.class)
    public void testRemoteLaunchCWLNoFile() {
        systemExit.expectSystemExitWithStatus(Client.ENTRY_NOT_FOUND);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "launch", "--entry",
                "imnotreal.cwl", "--json", "imnotreal-job.json", "--script" });
    }

    /**
     * This tests that attempting to launch a WDL workflow remotely, where no file exists, an APIError will occur
     */
    @Test
    public void testRemoteLaunchWDLNoFile() {
        systemExit.expectSystemExitWithStatus(Client.ENTRY_NOT_FOUND);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "launch", "--entry",
                "imnotreal.wdl", "--json", "imnotreal-job.json", "--script" });
    }

    /**
     * Tests that a developer can launch a WDL workflow locally, instead of getting files from Dockstore
     */
    @Test
    public void testLocalLaunchWDL() {
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "launch", "--local-entry",
                ResourceHelpers.resourceFilePath("wdl.wdl"), "--json", ResourceHelpers.resourceFilePath("wdl.json"),
                "--script" });
    }

    /**
     * Tests that a developer can launch a WDL workflow with a File input being a directory
     */
    @Test
    public void testLocalLaunchWDLWithDir() {
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "launch", "--local-entry",
                ResourceHelpers.resourceFilePath("directorytest.wdl"), "--json", ResourceHelpers.resourceFilePath("directorytest.json"), "--script" });
    }

    /**
     * Tests that a developer can launch a WDL workflow locally, with an HTTP/HTTPS URL
     * TODO: cromwell needs to support HTTP/HTTPS file prov
     */
    @Ignore
    public void testLocalLaunchWDLImportHTTP() {
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "launch", "--local-entry",
                ResourceHelpers.resourceFilePath("wdlhttpimport.wdl"), "--json", ResourceHelpers.resourceFilePath("wdlhttp.json"), "--script" });
    }

    /**
     * Tests that a only Github, Gitlab and bitbucket http/https imports are valid
     */
    @Test
    public void testLocalLaunchWDLImportIncorrectHTTP() {
        systemExit.expectSystemExitWithStatus(API_ERROR);
        // TODO: looking at the system log doesn't seem to work deterministically with checkAssertionAfterwards
        // re-enable and test with versions of system rules newer than 1.17.1
//        systemExit.checkAssertionAfterwards(
//            () -> assertTrue("Output should indicate issues with WDL imports and exit", systemOutRule.getLog().contains("Could not get WDL imports")));
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "launch", "--local-entry",
                ResourceHelpers.resourceFilePath("wdlincorrecthttp.wdl"), "--json", ResourceHelpers.resourceFilePath("wdl.json"), "--script" });
    }

    @Test
    public void testUpdateWorkflowPath() throws ApiException {
        // Set up webservice
        ApiClient webClient = WorkflowIT.getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        UsersApi usersApi = new UsersApi(webClient);
        final Long userId = usersApi.getUser().getId();

        // Make publish request (true)
        final PublishRequest publishRequest = SwaggerUtility.createPublishRequest(true);

        // Get workflows
        usersApi.refreshWorkflows(userId);

        Workflow githubWorkflow = workflowApi
                .manualRegister("github", "DockstoreTestUser2/test_lastmodified", "/Dockstore.cwl", "test-update-workflow", "cwl", "/test.json");

        // Publish github workflow
        Workflow workflow = workflowApi.refresh(githubWorkflow.getId());

        //update the default workflow path to be hello.cwl , the workflow path in workflow versions should also be changes
        workflow.setWorkflowPath("/hello.cwl");
        workflowApi.updateWorkflowPath(githubWorkflow.getId(), workflow);
        workflowApi.refresh(githubWorkflow.getId());

        // Set up DB


        //check if the workflow versions have the same workflow path or not in the database
        final String masterpath = testingPostgres
                .runSelectStatement("select workflowpath from workflowversion where name = 'testWorkflowPath'", String.class);
        final String testpath = testingPostgres
                .runSelectStatement("select workflowpath from workflowversion where name = 'testWorkflowPath'", String.class);
        assertEquals("master workflow path should be the same as default workflow path, it is " + masterpath, "/Dockstore.cwl",
            masterpath);
        assertEquals("test workflow path should be the same as default workflow path, it is " + testpath, "/Dockstore.cwl", testpath);
    }

    @Test
    public void testWorkflowFreezingWithNoFiles(){
        ApiClient webClient = WorkflowIT.getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        UsersApi usersApi = new UsersApi(webClient);
        final Long userId = usersApi.getUser().getId();

        // Get workflows
        usersApi.refreshWorkflows(userId);

        Workflow githubWorkflow = workflowApi
            .manualRegister("github", "DockstoreTestUser2/test_lastmodified", "/wrongpath.wdl", "test-update-workflow", "wdl", "/wrong-test.json");

        Workflow workflowBeforeFreezing = workflowApi.refresh(githubWorkflow.getId());
        WorkflowVersion master = workflowBeforeFreezing.getWorkflowVersions().stream().filter(v -> v.getName().equals("master")).findFirst().get();
        master.setFrozen(true);
        try {
            List<WorkflowVersion> workflowVersions = workflowApi.updateWorkflowVersion(workflowBeforeFreezing.getId(), Lists.newArrayList(master));
        } catch (ApiException e) {
            // should exception
            assertTrue("missing error message", e.getMessage().contains(CANNOT_FREEZE_VERSIONS_WITH_NO_FILES));
            return;
        }
        fail("should be unreachable");
    }

    @Test
    public void testWorkflowFreezing() throws ApiException {
        // Set up webservice
        ApiClient webClient = WorkflowIT.getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        UsersApi usersApi = new UsersApi(webClient);
        final Long userId = usersApi.getUser().getId();

        // Get workflows
        usersApi.refreshWorkflows(userId);

        Workflow githubWorkflow = workflowApi
            .manualRegister("github", "DockstoreTestUser2/test_lastmodified", "/hello.wdl", "test-update-workflow", "wdl", "/test.json");

        // Publish github workflow
        Workflow workflowBeforeFreezing = workflowApi.refresh(githubWorkflow.getId());
        WorkflowVersion master = workflowBeforeFreezing.getWorkflowVersions().stream().filter(v -> v.getName().equals("master")).findFirst().get();
        master.setFrozen(true);
        final List<WorkflowVersion> workflowVersions1 = workflowApi
            .updateWorkflowVersion(workflowBeforeFreezing.getId(), Lists.newArrayList(master));
        master = workflowVersions1.stream().filter(v -> v.getName().equals("master")).findFirst().get();
        assertTrue(master.isFrozen());

        // try various operations that should be disallowed

        // cannot modify version properties, like unfreezing for now
        workflowBeforeFreezing = workflowApi.refresh(githubWorkflow.getId());
        master = workflowBeforeFreezing.getWorkflowVersions().stream().filter(v -> v.getName().equals("master")).findFirst().get();
        master.setFrozen(false);
        List<WorkflowVersion> workflowVersions = workflowApi.updateWorkflowVersion(workflowBeforeFreezing.getId(), Lists.newArrayList(master));
        master = workflowVersions.stream().filter(v -> v.getName().equals("master")).findFirst().get();
        assertTrue(master.isFrozen());

        // but should be able to change doi stuff
        master.setFrozen(true);
        master.setDoiStatus(WorkflowVersion.DoiStatusEnum.REQUESTED);
        master.setDoiURL("foo");
        workflowVersions = workflowApi.updateWorkflowVersion(workflowBeforeFreezing.getId(), Lists.newArrayList(master));
        master = workflowVersions.stream().filter(v -> v.getName().equals("master")).findFirst().get();
        assertEquals("foo", master.getDoiURL());
        assertEquals(WorkflowVersion.DoiStatusEnum.REQUESTED, master.getDoiStatus());

        // refresh should skip over the frozen version
        final Workflow refresh = workflowApi.refresh(githubWorkflow.getId());
        master = refresh.getWorkflowVersions().stream().filter(v -> v.getName().equals("master")).findFirst().get();


        // cannot modify sourcefiles for a frozen version
        assertFalse(master.getSourceFiles().isEmpty());
        master.getSourceFiles().forEach(s -> {
            assertTrue(s.isFrozen());
            testingPostgres.runUpdateStatement("update sourcefile set content = 'foo' where id = " + s.getId());
            final String content = testingPostgres
                .runSelectStatement("select content from sourcefile where id = " + s.getId(), String.class);
            assertNotEquals("foo", content);
        });

        // cannot add or delete test files for frozen versions
        try {
            workflowApi.deleteTestParameterFiles(githubWorkflow.getId(), Lists.newArrayList("foo"), "master");
            fail("could delete test parameter file");
        } catch (ApiException e) {
            assertTrue(e.getMessage().contains(CANNOT_MODIFY_FROZEN_VERSIONS_THIS_WAY));
        }
        try {
            workflowApi.addTestParameterFiles(githubWorkflow.getId(), Lists.newArrayList("foo"), "", "master");
            fail("could add test parameter file");
        } catch(ApiException e) {
            assertTrue(e.getMessage().contains(CANNOT_MODIFY_FROZEN_VERSIONS_THIS_WAY));
        }
    }

    /**
     * This tests that a workflow can be updated to have default version, and that metadata is set related to the default version
     */
    @Test
    public void testUpdateWorkflowDefaultVersion() {
        // Set up DB


        // Setup workflow
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "manual_publish",
                "--repository", "hello-dockstore-workflow", "--organization", "DockstoreTestUser2", "--git-version-control", "github",
                "--script" });

        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--entry",
                SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow", "--script" });

        // Update workflow with version with no metadata
        Client.main(
                new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "update_workflow", "--entry",
                        SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow", "--default-version", "testWDL", "--script" });

        // Assert default version is updated and no author or email is found
        final long count = testingPostgres
                .runSelectStatement("select count(*) from workflow where defaultversion = 'testWDL'", long.class);
        assertEquals("there should be 1 matching workflow, there is " + count, 1, count);

        final long count2 = testingPostgres
                .runSelectStatement("select count(*) from workflow where defaultversion = 'testWDL' and author is null and email is null",
                        long.class);
        assertEquals("The given workflow shouldn't have any contact info", 1, count2);

        // Update workflow with version with metadata
        Client.main(
                new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "update_workflow", "--entry",
                        SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow", "--default-version", "testBoth", "--script" });

        // Assert default version is updated and author and email are set
        final long count3 = testingPostgres
                .runSelectStatement("select count(*) from workflow where defaultversion = 'testBoth'", long.class);
        assertEquals("there should be 1 matching workflow, there is " + count3, 1, count3);

        final long count4 = testingPostgres.runSelectStatement(
                "select count(*) from workflow where defaultversion = 'testBoth' and author = 'testAuthor' and email = 'testEmail'",
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
     * This test tests a bunch of different assumptions for how refresh should work for workflows
     */
    @Test
    public void testRefreshRelatedConcepts() {
        // Set up DB


        // refresh all
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--script" });

        // refresh individual that is valid
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--entry",
                SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow", "--script" });

        // check that workflow is valid and full
        final long count2 = testingPostgres
                .runSelectStatement("select count(*) from workflowversion where valid='t'", long.class);
        assertEquals("there should be 2 valid versions, there are " + count2, 2, count2);
        final long count3 = testingPostgres.runSelectStatement("select count(*) from workflow where mode='FULL'", long.class);
        assertEquals("there should be 1 full workflows, there are " + count3, 1, count3);

        // Change path for each version so that it is invalid
        testingPostgres.runUpdateStatement("UPDATE workflowversion SET workflowpath='thisisnotarealpath.cwl', dirtybit=true");
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--entry",
                SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow", "--script" });

        // Workflow has no valid versions so you cannot publish

        // check that invalid
        final long count4 = testingPostgres
                .runSelectStatement("select count(*) from workflowversion where valid='f'", long.class);
        assertEquals("there should be 4 invalid versions, there are " + count4, 4, count4);

        // Restub
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "restub", "--entry",
                SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow", "--script" });

        // Update workflow to WDL
        Client.main(
                new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "update_workflow", "--entry",
                        SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow", "--workflow-path", "Dockstore.wdl", "--descriptor-type", "wdl",
                        "--script" });

        // Can now publish workflow
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--entry",
                SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow", "--script" });

        // unpublish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--entry",
                SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow", "--unpub", "--script" });

        // Set paths to invalid
        testingPostgres.runUpdateStatement("UPDATE workflowversion SET workflowpath='thisisnotarealpath.wdl', dirtybit=true");
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--entry",
                SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow", "--script" });

        // Check that versions are invalid
        final long count5 = testingPostgres
                .runSelectStatement("select count(*) from workflowversion where valid='f'", long.class);
        assertEquals("there should be 4 invalid versions, there are " + count5, 4, count5);

        // should now not be able to publish
        systemExit.expectSystemExitWithStatus(Client.API_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--entry",
                SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow", "--script" });
    }

    /**
     * This tests the dirty bit attribute for workflow versions with github
     */
    @Test
    public void testGithubDirtyBit() {
        // Setup DB


        // refresh all
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--script" });

        // refresh individual that is valid
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--entry",
                SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow", "--script" });

        // Check that no versions have a true dirty bit
        final long count = testingPostgres
                .runSelectStatement("select count(*) from workflowversion where dirtybit = true", long.class);
        assertEquals("there should be no versions with dirty bit, there are " + count, 0, count);

        // Edit workflow path for a version
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "version_tag", "--entry",
                SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow", "--name", "master", "--workflow-path", "/Dockstoredirty.cwl", "--script" });

        // There should be on dirty bit
        final long count1 = testingPostgres
                .runSelectStatement("select count(*) from workflowversion where dirtybit = true", long.class);
        assertEquals("there should be 1 versions with dirty bit, there are " + count1, 1, count1);

        // Update default cwl
        Client.main(
                new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "update_workflow", "--entry",
                        SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow", "--workflow-path", "/Dockstoreclean.cwl", "--script" });

        // There should be 3 versions with new cwl
        final long count2 = testingPostgres
                .runSelectStatement("select count(*) from workflowversion where workflowpath = '/Dockstoreclean.cwl'",
                        long.class);
        assertEquals("there should be 3 versions with workflow path /Dockstoreclean.cwl, there are " + count2, 3, count2);

    }

    /**
     * This tests the dirty bit attribute for workflow versions with bitbucket
     */
    @Test
    public void testBitbucketDirtyBit() {
        // Setup DB


        // refresh all
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--script" });

        // refresh individual that is valid
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--entry",
                SourceControl.BITBUCKET.toString() + "/dockstore_testuser2/dockstore-workflow", "--script" });
        final long nullLastModifiedWorkflowVersions = testingPostgres.runSelectStatement("select count(*) from workflowversion where lastmodified is null", long.class);
        assertEquals("All Bitbucket workflow versions should have last modified populated after refreshing", 0, nullLastModifiedWorkflowVersions);
        // Check that no versions have a true dirty bit
        final long count = testingPostgres
                .runSelectStatement("select count(*) from workflowversion where dirtybit = true", long.class);
        assertEquals("there should be no versions with dirty bit, there are " + count, 0, count);

        // Edit workflow path for a version
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "version_tag", "--entry",
                SourceControl.BITBUCKET.toString() + "/dockstore_testuser2/dockstore-workflow", "--name", "master", "--workflow-path", "/Dockstoredirty.cwl", "--script" });

        // There should be on dirty bit
        final long count1 = testingPostgres
                .runSelectStatement("select count(*) from workflowversion where dirtybit = true", long.class);
        assertEquals("there should be 1 versions with dirty bit, there are " + count1, 1, count1);

        // Update default cwl
        Client.main(
                new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "update_workflow", "--entry",
                        SourceControl.BITBUCKET.toString() + "/dockstore_testuser2/dockstore-workflow", "--workflow-path", "/Dockstoreclean.cwl", "--script" });

        // There should be 3 versions with new cwl
        final long count2 = testingPostgres
                .runSelectStatement("select count(*) from workflowversion where workflowpath = '/Dockstoreclean.cwl'",
                        long.class);
        assertEquals("there should be 4 versions with workflow path /Dockstoreclean.cwl, there are " + count2, 4, count2);

    }

    /**
     * This is a high level test to ensure that gitlab basics are working for gitlab as a workflow repo
     */
    @Test
    public void testGitlab() {
        // Setup DB


        // Refresh workflow
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--script" });
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--entry",
                SourceControl.GITLAB.toString() + "/dockstore.test.user2/dockstore-workflow-example", "--script" });
        final long nullLastModifiedWorkflowVersions = testingPostgres.runSelectStatement("select count(*) from workflowversion where lastmodified is null", long.class);
        assertEquals("All GitLab workflow versions should have last modified populated after refreshing", 0, nullLastModifiedWorkflowVersions);

        // Check a few things
        final long count = testingPostgres.runSelectStatement(
                "select count(*) from workflow where mode='FULL' and sourcecontrol = '" + SourceControl.GITLAB.toString() + "' and organization = 'dockstore.test.user2' and repository = 'dockstore-workflow-example'",
                long.class);
        assertEquals("there should be 1 workflow, there are " + count, 1, count);

        final long count2 = testingPostgres
                .runSelectStatement("select count(*) from workflowversion where valid='t'", long.class);
        assertEquals("there should be 2 valid version, there are " + count2, 2, count2);

        final long count3 = testingPostgres.runSelectStatement(
                "select count(*) from workflow where mode='FULL' and sourcecontrol = '" + SourceControl.GITLAB.toString() + "' and organization = 'dockstore.test.user2' and repository = 'dockstore-workflow-example'",
                long.class);
        assertEquals("there should be 1 workflow, there are " + count3, 1, count3);

        // publish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--entry",
                SourceControl.GITLAB.toString() + "/dockstore.test.user2/dockstore-workflow-example", "--script" });
        final long count4 = testingPostgres.runSelectStatement(
                "select count(*) from workflow where mode='FULL' and sourcecontrol = '" + SourceControl.GITLAB.toString() + "' and organization = 'dockstore.test.user2' and repository = 'dockstore-workflow-example' and ispublished='t'",
                long.class);
        assertEquals("there should be 1 published workflow, there are " + count4, 1, count4);

        // Should be able to get info since it is published
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "info", "--entry",
                SourceControl.GITLAB.toString() + "/dockstore.test.user2/dockstore-workflow-example", "--script" });

        // Should be able to grab descriptor
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "cwl", "--entry",
                SourceControl.GITLAB.toString() + "/dockstore.test.user2/dockstore-workflow-example:master", "--script" });

        // unpublish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--entry",
                SourceControl.GITLAB.toString() + "/dockstore.test.user2/dockstore-workflow-example", "--unpub", "--script" });
        final long count5 = testingPostgres.runSelectStatement(
                "select count(*) from workflow where mode='FULL' and sourcecontrol = '" + SourceControl.GITLAB.toString() + "' and organization = 'dockstore.test.user2' and repository = 'dockstore-workflow-example' and ispublished='t'",
                long.class);
        assertEquals("there should be 0 published workflows, there are " + count5, 0, count5);

        // change default branch
        final long count6 = testingPostgres.runSelectStatement(
                "select count(*) from workflow where sourcecontrol = '" + SourceControl.GITLAB.toString() + "' and organization = 'dockstore.test.user2' and repository = 'dockstore-workflow-example' and author is null and email is null and description is null",
                long.class);
        assertEquals("The given workflow shouldn't have any contact info", 1, count6);

        Client.main(
                new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "update_workflow", "--entry",
                        SourceControl.GITLAB.toString() + "/dockstore.test.user2/dockstore-workflow-example", "--default-version", "test", "--script" });

        final long count7 = testingPostgres.runSelectStatement(
                "select count(*) from workflow where defaultversion = 'test' and author is null and email is null and description is null",
                long.class);
        assertEquals("The given workflow should now have contact info and description", 0, count7);

        // restub
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "restub", "--entry",
                SourceControl.GITLAB.toString() + "/dockstore.test.user2/dockstore-workflow-example", "--script" });
        final long count8 = testingPostgres.runSelectStatement(
                "select count(*) from workflow where mode='STUB' and sourcecontrol = '" + SourceControl.GITLAB.toString() + "' and organization = 'dockstore.test.user2' and repository = 'dockstore-workflow-example'",
                long.class);
        assertEquals("The workflow should now be a stub", 1, count8);

        // Convert to WDL workflow
        Client.main(
                new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "update_workflow", "--entry",
                        SourceControl.GITLAB.toString() + "/dockstore.test.user2/dockstore-workflow-example", "--descriptor-type", "wdl", "--script" });

        // Should now be a WDL workflow
        final long count9 = testingPostgres
                .runSelectStatement("select count(*) from workflow where descriptortype='wdl'", long.class);
        assertEquals("there should be no 1 wdl workflow" + count9, 1, count9);

    }

    /**
     * This tests manually publishing a Bitbucket workflow
     */
    @Test
    public void testManualPublishBitbucket() {
        // Setup DB


        // manual publish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "manual_publish",
                "--repository", "dockstore-workflow", "--organization", "dockstore_testuser2", "--git-version-control", "bitbucket",
                "--workflow-name", "testname", "--workflow-path", "/Dockstore.wdl", "--descriptor-type", "wdl", "--script" });

        // Check for two valid versions (wdl_import and surprisingly, cwl_import)
        final long count = testingPostgres
                .runSelectStatement("select count(*) from workflowversion where valid='t' and (name='wdl_import' OR name='cwl_import')", long.class);
        assertEquals("There should be a valid 'wdl_import' version and a valid 'cwl_import' version", 2, count);

        final long count2 = testingPostgres.runSelectStatement("select count(*) from workflowversion where lastmodified is null", long.class);
        assertEquals("All Bitbucket workflow versions should have last modified populated when manual published", 0, count2);

        // grab wdl file
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "wdl", "--entry",
                SourceControl.BITBUCKET.toString() + "/dockstore_testuser2/dockstore-workflow/testname:wdl_import", "--script" });
    }


    /**
     * This tests manually publishing a gitlab workflow
     */
    @Test
    public void testManualPublishGitlab() {
        // Setup DB


        // manual publish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "manual_publish",
                "--repository", "dockstore-workflow-example", "--organization", "dockstore.test.user2", "--git-version-control", "gitlab",
                "--workflow-name", "testname", "--workflow-path", "/Dockstore.wdl", "--descriptor-type", "wdl", "--script" });

        // Check for one valid version
        final long count = testingPostgres
                .runSelectStatement("select count(*) from workflowversion where valid='t'", long.class);
        assertEquals("there should be 1 valid version, there are " + count, 1, count);

        final long count2 = testingPostgres.runSelectStatement("select count(*) from workflowversion where lastmodified is null", long.class);
        assertEquals("All GitLab workflow versions should have last modified populated when manual published", 0, count2);

        // grab wdl file
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "wdl", "--entry",
                SourceControl.GITLAB.toString() + "/dockstore.test.user2/dockstore-workflow-example/testname:master", "--script" });
    }

    /**
     * This tests getting branches and tags from gitlab repositories
     */
    @Test
    @Category(SlowTest.class)
    public void testGitLabTagAndBranchTracking() {
        // Setup DB


        // manual publish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "manual_publish",
            "--repository", "dockstore-workflow-md5sum-unified", "--organization", "dockstore.test.user2", "--git-version-control", "gitlab",
            "--workflow-name", "testname", "--workflow-path", "/checker.wdl", "--descriptor-type", "wdl", "--script" });

        final long count = testingPostgres
            .runSelectStatement("select count(*) from workflowversion", long.class);
        assertTrue("there should be at least 5 versions, there are " + count, count >= 5);
        final long branchCount = testingPostgres
            .runSelectStatement("select count(*) from workflowversion where referencetype = 'BRANCH'", long.class);
        assertTrue("there should be at least 2 branches, there are " + count, branchCount >= 2);
        final long tagCount = testingPostgres
            .runSelectStatement("select count(*) from workflowversion where referencetype = 'TAG'", long.class);
        assertTrue("there should be at least 3 tags, there are " + count, tagCount >= 3);
    }

    /**
     * This tests that WDL files are properly parsed for secondary WDL files
     */
    @Test
    public void testWDLWithImports() {
        // Setup DB


        // Refresh all
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--script" });

        // Update workflow to be WDL with correct path
        Client.main(
                new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "update_workflow", "--entry",
                        SourceControl.GITHUB.toString() + "/DockstoreTestUser2/test_workflow_wdl", "--descriptor-type", "wdl", "--workflow-path", "/hello.wdl", "--script" });

        // Check for WDL files
        final long count = testingPostgres
                .runSelectStatement("select count(*) from sourcefile where path='helper.wdl'", long.class);
        assertEquals("there should be 1 secondary file named helper.wdl, there are " + count, 1, count);

    }

    /**
     * This tests basic concepts with workflow test parameter files
     */
    @Test
    public void testTestParameterFile() {
        // Setup DB


        // Refresh all
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--script" });

        // Refresh specific
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--entry",
                SourceControl.GITHUB.toString() + "/DockstoreTestUser2/parameter_test_workflow", "--script" });

        // There should be no sourcefiles
        final long count = testingPostgres
                .runSelectStatement("select count(*) from sourcefile where type like '%_TEST_JSON'", long.class);
        assertEquals("there should be no source files that are test parameter files, there are " + count, 0, count);

        // Update version master with test parameters
        Client.main(
                new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "test_parameter", "--entry",
                        SourceControl.GITHUB.toString() + "/DockstoreTestUser2/parameter_test_workflow", "--version", "master", "--add", "test.cwl.json", "--add",
                        "test2.cwl.json", "--add", "fake.cwl.json", "--remove", "notreal.cwl.json", "--script" });
        final long count2 = testingPostgres
                .runSelectStatement("select count(*) from sourcefile where type like '%_TEST_JSON'", long.class);
        assertEquals("there should be two sourcefiles that are test parameter files, there are " + count2, 2, count2);

        // Update version with test parameters
        Client.main(
                new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "test_parameter", "--entry",
                        SourceControl.GITHUB.toString() + "/DockstoreTestUser2/parameter_test_workflow", "--version", "master", "--add", "test.cwl.json", "--remove",
                        "test2.cwl.json", "--script" });
        final long count3 = testingPostgres
                .runSelectStatement("select count(*) from sourcefile where type like '%_TEST_JSON'", long.class);
        assertEquals("there should be one sourcefile that is a test parameter file, there are " + count3, 1, count3);

        // Update other version with test parameters
        Client.main(
                new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "test_parameter", "--entry",
                        SourceControl.GITHUB.toString() + "/DockstoreTestUser2/parameter_test_workflow", "--version", "wdltest", "--add", "test.wdl.json", "--script" });
        final long count4 = testingPostgres
                .runSelectStatement("select count(*) from sourcefile where type='CWL_TEST_JSON'", long.class);
        assertEquals("there should be two sourcefiles that are cwl test parameter files, there are " + count4, 2, count4);

        // Restub
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "restub", "--entry",
                SourceControl.GITHUB.toString() + "/DockstoreTestUser2/parameter_test_workflow", "--script" });

        // Change to WDL
        Client.main(
                new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "update_workflow", "--entry",
                        SourceControl.GITHUB.toString() + "/DockstoreTestUser2/parameter_test_workflow", "--descriptor-type", "wdl", "--workflow-path", "Dockstore.wdl",
                        "--script" });

        // Should be no sourcefiles
        final long count5 = testingPostgres
                .runSelectStatement("select count(*) from sourcefile where type like '%_TEST_JSON'", long.class);
        assertEquals("there should be no source files that are test parameter files, there are " + count5, 0, count5);

        // Update version wdltest with test parameters
        Client.main(
                new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "test_parameter", "--entry",
                        SourceControl.GITHUB.toString() + "/DockstoreTestUser2/parameter_test_workflow", "--version", "wdltest", "--add", "test.wdl.json", "--script" });
        final long count6 = testingPostgres
                .runSelectStatement("select count(*) from sourcefile where type='WDL_TEST_JSON'", long.class);
        assertEquals("there should be one sourcefile that is a wdl test parameter file, there are " + count6, 1, count6);
    }

    /**
     * This tests that you can verify and unverify a workflow
     */
    @Ignore("Deprecated. CLI needs to be changed to use new Extended TRS verification endpoint")
    @Test
    public void testVerify() {
        // Setup DB


        // Versions should be unverified
        final long count = testingPostgres
                .runSelectStatement("select count(*) from workflowversion wv, version_metadata vm where vm.verified='true' and wv.id = vm.id", long.class);

        assertEquals("there should be no verified workflowversions, there are " + count, 0, count);

        // Refresh workflows
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--script" });

        // Refresh workflow
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--entry",
                SourceControl.GITHUB.toString() + "/DockstoreTestUser2/parameter_test_workflow", "--script" });

        // Verify workflowversion
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "verify", "--entry",
                SourceControl.GITHUB.toString() + "/DockstoreTestUser2/parameter_test_workflow", "--verified-source", "Docker testing group", "--version", "master",
                "--script" });

        // Version should be verified
        final long count2 = testingPostgres
                .runSelectStatement("select count(*) from workflowversion wv, version_metadata vm where vm.verified='true' and vm.verifiedSource='Docker testing group' and wv.id = vm.id",
                        long.class);
        assertEquals("there should be one verified workflowversion, there are " + count2, 1, count2);

        // Update workflowversion to have new verified source
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "verify", "--entry",
                SourceControl.GITHUB.toString() + "/DockstoreTestUser2/parameter_test_workflow", "--verified-source", "Docker testing group2", "--version", "master",
                "--script" });

        // Version should have new verified source
        final long count3 = testingPostgres
                .runSelectStatement("select count(*) from workflowversion wv, version_metadata vm where vm.verified='true' and vm.verifiedSource='Docker testing group2' and wv.id = vm.id",
                        long.class);
        assertEquals("there should be one verified workflowversion, there are " + count3, 1, count3);

        // Verify another version
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "verify", "--entry",
                SourceControl.GITHUB.toString() + "/DockstoreTestUser2/parameter_test_workflow", "--verified-source", "Docker testing group", "--version", "wdltest",
                "--script" });

        // Version should be verified
        final long count4 = testingPostgres
                .runSelectStatement("select count(*) from workflowversion wv, version_metadata vm where vm.verified='true' and wv.id = vm.id", long.class);
        assertEquals("there should be two verified workflowversions, there are " + count4, 2, count4);

        // Unverify workflowversion
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "verify", "--entry",
                SourceControl.GITHUB.toString() + "/DockstoreTestUser2/parameter_test_workflow", "--unverify", "--version", "master", "--script" });

        // Workflowversion should be unverified
        final long count5 = testingPostgres
                .runSelectStatement("select count(*) from workflowversion wv, version_metadata vm where vm.verified='true' and wv.id = vm.id", long.class);
        assertEquals("there should be one verified workflowversion, there are " + count5, 1, count5);
    }

    /**
     * This tests that you can refresh user data by refreshing a workflow
     * ONLY WORKS if the current user in the database dump has no metadata, and on Github there is metadata (bio, location)
     * If the user has metadata, test will pass as long as the user's metadata isn't the same as Github already
     */
    @Test
    public void testRefreshingUserMetadata() {
        // Setup database


        // Refresh all workflows
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh",  "--script" });

        // Check that user has been updated
        // TODO: bizarrely, the new GitHub Java API library doesn't seem to handle bio
        // final long count = testingPostgres.runSelectStatement("select count(*) from enduser where location='Toronto' and bio='I am a test user'", long.class);
        final long count = testingPostgres.runSelectStatement("select count(*) from user_profile where location='Toronto'", long.class);
        assertEquals("One user should have this info now, there are  " + count, 1, count);
    }
    @Test
    public void testGenerateDOIFrozenVersion() throws ApiException {
        // Set up webservice
        ApiClient webClient = WorkflowIT.getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        //register workflow
        Workflow githubWorkflow = workflowApi
                .manualRegister("github", "DockstoreTestUser2/test_lastmodified", "/hello.wdl", "test-update-workflow", "wdl", "/test.json");

        Workflow workflowBeforeFreezing = workflowApi.refresh(githubWorkflow.getId());
        WorkflowVersion master = workflowBeforeFreezing.getWorkflowVersions().stream().filter(v -> v.getName().equals("master")).findFirst().get();

        //try issuing DOI for workflow version that is not frozen.
        try {
            workflowApi.requestDOIForWorkflowVersion(workflowBeforeFreezing.getId(), master.getId(), "");
            fail("This line should never execute if version is mutable. DOI should only be generated for frozen versions of workflows.");
        } catch (ApiException ex) {
            assertTrue(ex.getResponseBody().contains(FROZEN_VERSION_REQUIRED));
        }

        //freeze version 'master'
        master.setFrozen(true);
        final List<WorkflowVersion> workflowVersions1 = workflowApi
                .updateWorkflowVersion(workflowBeforeFreezing.getId(), Lists.newArrayList(master));
        master = workflowVersions1.stream().filter(v -> v.getName().equals("master")).findFirst().get();
        assertTrue(master.isFrozen());

        //TODO: For now just checking for next failure (no Zenodo token), but should replace with when DOI registration tests are written
        try {
            workflowApi.requestDOIForWorkflowVersion(workflowBeforeFreezing.getId(), master.getId(), "");
            fail("This line should never execute without valid Zenodo token");
        } catch (ApiException ex) {
            assertTrue(ex.getResponseBody().contains(NO_ZENDO_USER_TOKEN));

        }

    }
}
