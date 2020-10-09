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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.ws.rs.core.GenericType;

import com.google.common.collect.Lists;
import io.dockstore.client.cli.nested.WorkflowClient;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.SourceControl;
import io.dockstore.common.WorkflowTest;
import io.dropwizard.testing.ResourceHelpers;
import io.swagger.client.ApiClient;
import io.swagger.client.api.HostedApi;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.SourceFile;
import io.swagger.client.model.Workflow;
import io.swagger.client.model.WorkflowVersion;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;

import static io.swagger.client.model.ToolDescriptor.TypeEnum.CWL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Extra confidential integration tests, focus on testing workflow interactions
 * {@link io.dockstore.client.cli.BaseIT}
 *
 * @author dyuen
 */
@Category({ ConfidentialTest.class, WorkflowTest.class })
public class WorkflowIT extends BaseIT {

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();
    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();
    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();
    @Rule
    public final ExpectedException thrown = ExpectedException.none();
    private final String clientConfig = ResourceHelpers.resourceFilePath("clientConfig");
    private final String jsonFilePath = ResourceHelpers.resourceFilePath("wc-job.json");

    @Before
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false);
    }

    @Test
    public void testWorkflowLaunchOrNotLaunchBasedOnCredentials() throws IOException {
        String toolpath = SourceControl.GITHUB.toString() + "/DockstoreTestUser2/md5sum-checker/test";

        testingPostgres.runUpdateStatement("update enduser set isadmin = 't' where username = 'DockstoreTestUser2';");
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        Workflow workflow = workflowApi.manualRegister(SourceControl.GITHUB.getFriendlyName(), "DockstoreTestUser2/md5sum-checker",
            "/checker-workflow-wrapping-workflow.cwl", "test", "cwl", null);
        assertEquals("There should be one user of the workflow after manually registering it.", 1, workflow.getUsers().size());
        Workflow refresh = workflowApi.refresh(workflow.getId());

        assertFalse(refresh.isIsPublished());

        // should be able to launch properly with correct credentials even though the workflow is not published
        FileUtils.writeStringToFile(new File("md5sum.input"), "foo", StandardCharsets.UTF_8);
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "launch", "--entry", toolpath,
                "--json", ResourceHelpers.resourceFilePath("md5sum_cwl.json"), "--script" });

        // should not be able to launch properly with incorrect credentials
        systemExit.expectSystemExitWithStatus(Client.ENTRY_NOT_FOUND);
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "workflow", "launch", "--entry", toolpath,
                "--json", ResourceHelpers.resourceFilePath("md5sum_cwl.json"), "--script" });
    }

    /**
     * This tests that you are able to download zip files for versions of a workflow
     */
    @Test
    public void downloadZipFile() throws IOException {
        String toolpath = SourceControl.GITHUB.toString() + "/DockstoreTestUser2/md5sum-checker/test";
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        // Register and refresh workflow
        Workflow workflow = workflowApi
            .manualRegister(SourceControl.GITHUB.getFriendlyName(), "DockstoreTestUser2/md5sum-checker", "/md5sum/md5sum-workflow.cwl",
                "test", "cwl", null);
        Workflow refresh = workflowApi.refresh(workflow.getId());
        Long workflowId = refresh.getId();
        WorkflowVersion workflowVersion = refresh.getWorkflowVersions().get(0);
        Long versionId = workflowVersion.getId();

        // Download unpublished workflow version
        workflowApi.getWorkflowZip(workflowId, versionId);
        byte[] arbitraryURL = SwaggerUtility.getArbitraryURL("/workflows/" + workflowId + "/zip/" + versionId, new GenericType<byte[]>() {
        }, webClient);
        File tempZip = File.createTempFile("temp", "zip");
        Path write = Files.write(tempZip.toPath(), arbitraryURL);
        ZipFile zipFile = new ZipFile(write.toFile());
        assertTrue("zip file seems incorrect",
            zipFile.stream().map(ZipEntry::getName).collect(Collectors.toList()).contains("md5sum/md5sum-workflow.cwl"));

        // should not be able to get zip anonymously before publication
        boolean thrownException = false;
        try {
            SwaggerUtility.getArbitraryURL("/workflows/" + workflowId + "/zip/" + versionId, new GenericType<byte[]>() {
            }, CommonTestUtilities.getWebClient(false, null, testingPostgres));
        } catch (Exception e) {
            thrownException = true;
        }
        assertTrue(thrownException);
        tempZip.deleteOnExit();

        // Download published workflow version
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--entry", toolpath,
                "--script" });
        arbitraryURL = SwaggerUtility.getArbitraryURL("/workflows/" + workflowId + "/zip/" + versionId, new GenericType<byte[]>() {
        }, CommonTestUtilities.getWebClient(false, null, testingPostgres));
        File tempZip2 = File.createTempFile("temp", "zip");
        write = Files.write(tempZip2.toPath(), arbitraryURL);
        zipFile = new ZipFile(write.toFile());
        assertTrue("zip file seems incorrect",
            zipFile.stream().map(ZipEntry::getName).collect(Collectors.toList()).contains("md5sum/md5sum-workflow.cwl"));

        tempZip2.deleteOnExit();

        // download and unzip via CLI
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "download", "--entry",
            toolpath + ":" + workflowVersion.getName(), "--script" });
        zipFile.stream().forEach((ZipEntry entry) -> {
            if (!(entry).isDirectory()) {
                File innerFile = new File(System.getProperty("user.dir"), entry.getName());
                assert (innerFile.exists());
                assert (innerFile.delete());
            }
        });

        // download zip via CLI
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "download", "--entry",
            toolpath + ":" + workflowVersion.getName(), "--zip", "--script" });
        File downloadedZip = new File(new WorkflowClient(null, null, null, false).zipFilename(workflow));
        assert (downloadedZip.exists());
        assert (downloadedZip.delete());
    }

    @Test
    public void testCheckerWorkflowDownloadBasedOnCredentials() throws IOException {
        String toolpath = SourceControl.GITHUB.toString() + "/DockstoreTestUser2/md5sum-checker/test";

        testingPostgres.runUpdateStatement("update enduser set isadmin = 't' where username = 'DockstoreTestUser2';");

        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        Workflow workflow = workflowApi
            .manualRegister(SourceControl.GITHUB.getFriendlyName(), "DockstoreTestUser2/md5sum-checker", "/md5sum/md5sum-workflow.cwl",
                "test", "cwl", null);
        Workflow refresh = workflowApi.refresh(workflow.getId());
        assertFalse(refresh.isIsPublished());
        workflowApi.registerCheckerWorkflow("checker-workflow-wrapping-workflow.cwl", workflow.getId(), "cwl", "checker-input-cwl.json");
        workflowApi.refresh(workflow.getId());

        final String fileWithIncorrectCredentials = ResourceHelpers.resourceFilePath("config_file.txt");
        final String fileWithCorrectCredentials = ResourceHelpers.resourceFilePath("config_file2.txt");

        // should be able to download properly with correct credentials even though the workflow is not published
        FileUtils.writeStringToFile(new File("md5sum.input"), "foo", StandardCharsets.UTF_8);
        Client.main(
            new String[] { "--config", fileWithCorrectCredentials, "checker", "download", "--entry", toolpath, "--version", "master",
                "--script" });

        // Publish the workflow
        Client.main(new String[] { "--config", fileWithCorrectCredentials, "workflow", "publish", "--entry", toolpath, "--script" });

        // should be able to download properly with incorrect credentials because the entry is published
        Client.main(
            new String[] { "--config", fileWithIncorrectCredentials, "checker", "download", "--entry", toolpath, "--version", "master",
                "--script" });

        // Unpublish the workflow
        Client.main(
            new String[] { "--config", fileWithCorrectCredentials, "workflow", "publish", "--entry", toolpath, "--unpub", "--script" });

        // should not be able to download properly with incorrect credentials because the entry is not published
        systemExit.expectSystemExitWithStatus(Client.ENTRY_NOT_FOUND);
        Client.main(
            new String[] { "--config", fileWithIncorrectCredentials, "checker", "download", "--entry", toolpath, "--version", "master",
                "--script" });
    }

    @Test
    public void testCheckerWorkflowLaunchBasedOnCredentials() throws IOException {
        String toolpath = SourceControl.GITHUB.toString() + "/DockstoreTestUser2/md5sum-checker/test";

        testingPostgres.runUpdateStatement("update enduser set isadmin = 't' where username = 'DockstoreTestUser2';");
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        Workflow workflow = workflowApi
            .manualRegister(SourceControl.GITHUB.getFriendlyName(), "DockstoreTestUser2/md5sum-checker", "/md5sum/md5sum-workflow.cwl",
                "test", "cwl", null);
        Workflow refresh = workflowApi.refresh(workflow.getId());
        Assert.assertFalse(refresh.isIsPublished());

        workflowApi.registerCheckerWorkflow("/checker-workflow-wrapping-workflow.cwl", workflow.getId(), "cwl", "checker-input-cwl.json");

        workflowApi.refresh(workflow.getId());

        // should be able to launch properly with correct credentials even though the workflow is not published
        FileUtils.writeStringToFile(new File("md5sum.input"), "foo", StandardCharsets.UTF_8);
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "checker", "launch", "--entry", toolpath,
                "--json", ResourceHelpers.resourceFilePath("md5sum_cwl.json"), "--script" });

        // should be able to launch properly with incorrect credentials but the entry is published
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--entry", toolpath,
                "--script" });
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "checker", "launch", "--entry", toolpath,
                "--json", ResourceHelpers.resourceFilePath("md5sum_cwl.json"), "--script" });

        // should not be able to launch properly with incorrect credentials
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--entry", toolpath,
                "--unpub", "--script" });
        systemExit.expectSystemExitWithStatus(Client.ENTRY_NOT_FOUND);
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "checker", "launch", "--entry", toolpath,
                "--json", ResourceHelpers.resourceFilePath("md5sum_cwl.json"), "--script" });
    }

    @Test
    public void testHostedWorkflowMetadataAndLaunch() throws IOException {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        HostedApi hostedApi = new HostedApi(webClient);
        Workflow hostedWorkflow = hostedApi.createHostedWorkflow("name", null, CWL.toString(), null, null);
        assertNotNull(hostedWorkflow.getLastModifiedDate());
        assertNotNull(hostedWorkflow.getLastUpdated());

        // make a couple garbage edits
        SourceFile source = new SourceFile();
        source.setPath("/Dockstore.cwl");
        source.setAbsolutePath("/Dockstore.cwl");
        source.setContent("cwlVersion: v1.0\nclass: Workflow");
        source.setType(SourceFile.TypeEnum.DOCKSTORE_CWL);
        SourceFile source1 = new SourceFile();
        source1.setPath("sorttool.cwl");
        source1.setContent("foo");
        source1.setAbsolutePath("/sorttool.cwl");
        source1.setType(SourceFile.TypeEnum.DOCKSTORE_CWL);
        SourceFile source2 = new SourceFile();
        source2.setPath("revtool.cwl");
        source2.setContent("foo");
        source2.setAbsolutePath("/revtool.cwl");
        source2.setType(SourceFile.TypeEnum.DOCKSTORE_CWL);
        hostedApi.editHostedWorkflow(hostedWorkflow.getId(), Lists.newArrayList(source, source1, source2));

        source.setContent("cwlVersion: v1.0\nclass: Workflow");
        source1.setContent("food");
        source2.setContent("food");
        final Workflow updatedHostedWorkflow = hostedApi
            .editHostedWorkflow(hostedWorkflow.getId(), Lists.newArrayList(source, source1, source2));
        assertNotNull(updatedHostedWorkflow.getLastModifiedDate());
        assertNotNull(updatedHostedWorkflow.getLastUpdated());

        // note that this workflow contains metadata defined on the inputs to the workflow in the old (pre-map) CWL way that is still valid v1.0 CWL
        source.setContent(FileUtils
            .readFileToString(new File(ResourceHelpers.resourceFilePath("hosted_metadata/Dockstore.cwl")), StandardCharsets.UTF_8));
        source1.setContent(
            FileUtils.readFileToString(new File(ResourceHelpers.resourceFilePath("hosted_metadata/sorttool.cwl")), StandardCharsets.UTF_8));
        source2.setContent(
            FileUtils.readFileToString(new File(ResourceHelpers.resourceFilePath("hosted_metadata/revtool.cwl")), StandardCharsets.UTF_8));
        Workflow workflow = hostedApi.editHostedWorkflow(hostedWorkflow.getId(), Lists.newArrayList(source, source1, source2));
        assertFalse(workflow.getInputFileFormats().isEmpty());
        assertFalse(workflow.getOutputFileFormats().isEmpty());

        // launch the workflow, note that the latest version of the workflow should launch (i.e. the working one)
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "launch", "--entry",
            workflow.getFullWorkflowPath(), "--json", ResourceHelpers.resourceFilePath("revsort-job.json"), "--script" });

        WorkflowsApi workflowsApi = new WorkflowsApi(webClient);
        workflowsApi.publish(workflow.getId(), SwaggerUtility.createPublishRequest(true));
        // should also launch successfully with the wrong credentials when published
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "workflow", "launch", "--entry",
            workflow.getFullWorkflowPath(), "--json", ResourceHelpers.resourceFilePath("revsort-job.json"), "--script" });
    }

    /**
     * Test for cwl1.1
     * Of the languages support features, this tests:
     * Workflow Registration
     * Metadata Display
     * Validation
     * Launch remote workflow
     */
    @Test
    public void cwlVersion11() {
        final ApiClient userApiClient = CommonTestUtilities.getWebClient(true, USER_2_USERNAME, testingPostgres);
        WorkflowsApi userWorkflowsApi = new WorkflowsApi(userApiClient);
        userWorkflowsApi.manualRegister("github", "dockstore-testing/Workflows-For-CI", "/cwl/v1.1/metadata.cwl", "metadata", "cwl",
            "/cwl/v1.1/cat-job.json");
        final Workflow workflowByPathGithub = userWorkflowsApi
            .getWorkflowByPath("github.com/dockstore-testing/Workflows-For-CI/metadata", null, false);
        final Workflow workflow = userWorkflowsApi.refresh(workflowByPathGithub.getId());
        Assert.assertEquals("Print the contents of a file to stdout using 'cat' running in a docker container.", workflow.getDescription());
        Assert.assertEquals("Peter Amstutz", workflow.getAuthor());
        Assert.assertEquals("peter.amstutz@curoverse.com", workflow.getEmail());
        Assert.assertTrue(workflow.getWorkflowVersions().stream().anyMatch(versions -> "master".equals(versions.getName())));
        Optional<WorkflowVersion> optionalWorkflowVersion = workflow.getWorkflowVersions().stream()
            .filter(version -> "master".equalsIgnoreCase(version.getName())).findFirst();
        assertTrue(optionalWorkflowVersion.isPresent());
        WorkflowVersion workflowVersion = optionalWorkflowVersion.get();

        // verify sourcefiles
        final io.dockstore.openapi.client.ApiClient userOpenApiClient = CommonTestUtilities.getOpenApiWebClient(true, USER_2_USERNAME, testingPostgres);
        io.dockstore.openapi.client.api.WorkflowsApi openApiWorkflowApi = new io.dockstore.openapi.client.api.WorkflowsApi(userOpenApiClient);
        List<io.dockstore.openapi.client.model.SourceFile> sourceFileList = openApiWorkflowApi.getWorkflowVersionsSourcefiles(workflow.getId(), workflowVersion.getId(), null);

        Assert.assertEquals(2, sourceFileList.size());
        Assert.assertTrue(sourceFileList.stream().anyMatch(sourceFile -> sourceFile.getPath().equals("/cwl/v1.1/cat-job.json")));
        Assert.assertTrue(sourceFileList.stream().anyMatch(sourceFile -> sourceFile.getPath().equals("/cwl/v1.1/metadata.cwl")));

        // Check validation works.  It is invalid because this is a tool and not a workflow.
        Assert.assertFalse(workflowVersion.isValid());

        userWorkflowsApi
            .manualRegister("github", "dockstore-testing/Workflows-For-CI", "/cwl/v1.1/count-lines1-wf.cwl", "count-lines1-wf", "cwl",
                "/cwl/v1.1/wc-job.json");
        final Workflow workflowByPathGithub2 = userWorkflowsApi
            .getWorkflowByPath("github.com/dockstore-testing/Workflows-For-CI/count-lines1-wf", null, false);
        final Workflow workflow2 = userWorkflowsApi.refresh(workflowByPathGithub2.getId());
        Assert.assertTrue(workflow.getWorkflowVersions().stream().anyMatch(versions -> "master".equals(versions.getName())));
        Optional<WorkflowVersion> optionalWorkflowVersion2 = workflow2.getWorkflowVersions().stream()
            .filter(version -> "master".equalsIgnoreCase(version.getName())).findFirst();
        assertTrue(optionalWorkflowVersion2.isPresent());
        WorkflowVersion workflowVersion2 = optionalWorkflowVersion2.get();
        // Check validation works.  It should be valid
        Assert.assertTrue(workflowVersion2.isValid());
        userWorkflowsApi.publish(workflowByPathGithub2.getId(), SwaggerUtility.createPublishRequest(true));
        List<String> args = new ArrayList<>();
        args.add("workflow");
        args.add("launch");
        args.add("--entry");
        args.add("github.com/dockstore-testing/Workflows-For-CI/count-lines1-wf");
        args.add("--yaml");
        args.add(jsonFilePath);
        args.add("--config");
        args.add(clientConfig);
        args.add("--script");

        Client.main(args.toArray(new String[0]));
        Assert.assertTrue(systemOutRule.getLog().contains("Final process status is success"));
    }
}
