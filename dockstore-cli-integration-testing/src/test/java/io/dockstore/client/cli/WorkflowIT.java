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
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.ws.rs.core.GenericType;

import com.google.common.collect.Lists;
import io.dockstore.client.cli.nested.WorkflowClient;
import io.dockstore.common.CLICommonTestUtilities;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

import static io.dockstore.client.cli.ArgumentUtility.DOWNLOAD;
import static io.dockstore.client.cli.ArgumentUtility.LAUNCH;
import static io.dockstore.client.cli.Client.CHECKER;
import static io.dockstore.client.cli.Client.CONFIG;
import static io.dockstore.client.cli.Client.SCRIPT_FLAG;
import static io.dockstore.client.cli.Client.VERSION;
import static io.dockstore.client.cli.Client.WORKFLOW;
import static io.dockstore.client.cli.nested.AbstractEntryClient.PUBLISH;
import static io.dockstore.client.cli.nested.WesCommandParser.ENTRY;
import static io.dockstore.client.cli.nested.WesCommandParser.JSON;
import static io.swagger.client.model.ToolDescriptor.TypeEnum.CWL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.org.webcompere.systemstubs.SystemStubs.catchSystemExit;

/**
 * Extra confidential integration tests, focus on testing workflow interactions
 * {@link io.dockstore.client.cli.BaseIT}
 *
 * @author dyuen
 */
@Tag(ConfidentialTest.NAME)
@Tag(WorkflowTest.NAME)
class WorkflowIT extends BaseIT {

    @SystemStub
    public final SystemOut systemOutRule = new SystemOut();

    @SystemStub
    public final SystemErr systemErrRule = new SystemErr();

    private final String clientConfig = ResourceHelpers.resourceFilePath("clientConfig");
    private final String jsonFilePath = ResourceHelpers.resourceFilePath("wc-job.json");

    @BeforeEach
    @Override
    public void resetDBBetweenTests() throws Exception {
        CLICommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
    }

    @Test
    void testWorkflowLaunchOrNotLaunchBasedOnCredentials() throws Exception {
        String toolpath = SourceControl.GITHUB.toString() + "/DockstoreTestUser2/md5sum-checker/test";

        testingPostgres.runUpdateStatement("update enduser set isadmin = 't' where username = 'DockstoreTestUser2';");
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        Workflow workflow = workflowApi.manualRegister(SourceControl.GITHUB.getFriendlyName(), "DockstoreTestUser2/md5sum-checker",
            "/checker-workflow-wrapping-workflow.cwl", "test", CWL.toString(), null);
        assertEquals(1, workflow.getUsers().size(), "There should be one user of the workflow after manually registering it.");
        Workflow refresh = workflowApi.refresh(workflow.getId(), true);

        assertFalse(refresh.isIsPublished());

        // should be able to launch properly with correct credentials even though the workflow is not published
        FileUtils.writeStringToFile(new File("md5sum.input"), "foo", StandardCharsets.UTF_8);
        Client.main(
            new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, LAUNCH, ENTRY, toolpath,
                JSON, ResourceHelpers.resourceFilePath("md5sum_cwl.json"), SCRIPT_FLAG });

        // should not be able to launch properly with incorrect credentials
        int exitCode = catchSystemExit(() -> Client.main(
                new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), WORKFLOW, LAUNCH, ENTRY, toolpath,
                        JSON, ResourceHelpers.resourceFilePath("md5sum_cwl.json"), SCRIPT_FLAG }));
        assertEquals(Client.ENTRY_NOT_FOUND, exitCode);
    }

    /**
     * This tests that you are able to download zip files for versions of a workflow
     */
    @Test
    void downloadZipFile() throws IOException {
        String toolpath = SourceControl.GITHUB.toString() + "/DockstoreTestUser2/md5sum-checker/test";
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        // Register and refresh workflow
        Workflow workflow = workflowApi
            .manualRegister(SourceControl.GITHUB.getFriendlyName(), "DockstoreTestUser2/md5sum-checker", "/md5sum/md5sum-workflow.cwl",
                "test", CWL.toString(), null);
        Workflow refresh = workflowApi.refresh(workflow.getId(), true);
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
        assertTrue(zipFile.stream().map(ZipEntry::getName).toList().contains("md5sum/md5sum-workflow.cwl"),
                "zip file seems incorrect");

        // should not be able to get zip anonymously before publication
        boolean thrownException = false;
        try {
            SwaggerUtility.getArbitraryURL("/workflows/" + workflowId + "/zip/" + versionId, new GenericType<byte[]>() {
            }, CLICommonTestUtilities.getWebClient(false, null, testingPostgres));
        } catch (Exception e) {
            thrownException = true;
        }
        assertTrue(thrownException);
        tempZip.deleteOnExit();

        // Download published workflow version
        Client.main(
            new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, PUBLISH, ENTRY, toolpath,
                SCRIPT_FLAG });
        arbitraryURL = SwaggerUtility.getArbitraryURL("/workflows/" + workflowId + "/zip/" + versionId, new GenericType<byte[]>() {
        }, CLICommonTestUtilities.getWebClient(false, null, testingPostgres));
        File tempZip2 = File.createTempFile("temp", "zip");
        write = Files.write(tempZip2.toPath(), arbitraryURL);
        zipFile = new ZipFile(write.toFile());
        assertTrue(zipFile.stream().map(ZipEntry::getName).toList().contains("md5sum/md5sum-workflow.cwl"),
                "zip file seems incorrect");

        tempZip2.deleteOnExit();

        // download and unzip via CLI
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, DOWNLOAD, ENTRY,
            toolpath + ":" + workflowVersion.getName(), SCRIPT_FLAG });
        zipFile.stream().forEach((ZipEntry entry) -> {
            if (!(entry).isDirectory()) {
                File innerFile = new File(System.getProperty("user.dir"), entry.getName());
                assert (innerFile.exists());
                assert (innerFile.delete());
            }
        });

        // download zip via CLI
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, DOWNLOAD, ENTRY,
            toolpath + ":" + workflowVersion.getName(), "--zip", SCRIPT_FLAG });
        File downloadedZip = new File(new WorkflowClient(null, null, null, false).zipFilename(workflow));
        assert (downloadedZip.exists());
        assert (downloadedZip.delete());
    }

    @Test
    void testCheckerWorkflowDownloadBasedOnCredentials() throws Exception {
        String toolpath = SourceControl.GITHUB.toString() + "/DockstoreTestUser2/md5sum-checker/test";

        testingPostgres.runUpdateStatement("update enduser set isadmin = 't' where username = 'DockstoreTestUser2';");

        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        Workflow workflow = workflowApi
            .manualRegister(SourceControl.GITHUB.getFriendlyName(), "DockstoreTestUser2/md5sum-checker", "/md5sum/md5sum-workflow.cwl",
                "test", CWL.toString(), null);
        Workflow refresh = workflowApi.refresh(workflow.getId(), true);
        assertFalse(refresh.isIsPublished());
        workflowApi.registerCheckerWorkflow("checker-workflow-wrapping-workflow.cwl", workflow.getId(), CWL.toString(), "checker-input-cwl.json");
        workflowApi.refresh(workflow.getId(), true);

        final String fileWithIncorrectCredentials = ResourceHelpers.resourceFilePath("config_file.txt");
        final String fileWithCorrectCredentials = ResourceHelpers.resourceFilePath("config_file2.txt");

        // should be able to download properly with correct credentials even though the workflow is not published
        FileUtils.writeStringToFile(new File("md5sum.input"), "foo", StandardCharsets.UTF_8);
        Client.main(
            new String[] { CONFIG, fileWithCorrectCredentials, CHECKER, DOWNLOAD, ENTRY, toolpath, VERSION, "master",
                SCRIPT_FLAG });

        // Publish the workflow
        Client.main(new String[] { CONFIG, fileWithCorrectCredentials, WORKFLOW, PUBLISH, ENTRY, toolpath, SCRIPT_FLAG });

        // should be able to download properly with incorrect credentials because the entry is published
        Client.main(
            new String[] { CONFIG, fileWithIncorrectCredentials, CHECKER, DOWNLOAD, ENTRY, toolpath, VERSION, "master",
                SCRIPT_FLAG });

        // Unpublish the workflow
        Client.main(
            new String[] { CONFIG, fileWithCorrectCredentials, WORKFLOW, PUBLISH, ENTRY, toolpath, "--unpub", SCRIPT_FLAG });

        // should not be able to download properly with incorrect credentials because the entry is not published
        int exitCode = catchSystemExit(() ->  Client.main(
                new String[] { CONFIG, fileWithIncorrectCredentials, CHECKER, DOWNLOAD, ENTRY, toolpath, VERSION, "master",
                        SCRIPT_FLAG }));
        assertEquals(Client.ENTRY_NOT_FOUND, exitCode);
    }

    @Test
    void testCheckerWorkflowLaunchBasedOnCredentials() throws Exception {
        String toolpath = SourceControl.GITHUB.toString() + "/DockstoreTestUser2/md5sum-checker/test";

        testingPostgres.runUpdateStatement("update enduser set isadmin = 't' where username = 'DockstoreTestUser2';");
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        Workflow workflow = workflowApi
            .manualRegister(SourceControl.GITHUB.getFriendlyName(), "DockstoreTestUser2/md5sum-checker", "/md5sum/md5sum-workflow.cwl",
                "test", CWL.toString(), null);
        Workflow refresh = workflowApi.refresh(workflow.getId(), true);
        assertFalse(refresh.isIsPublished());

        workflowApi.registerCheckerWorkflow("/checker-workflow-wrapping-workflow.cwl", workflow.getId(), CWL.toString(), "checker-input-cwl.json");

        workflowApi.refresh(workflow.getId(), true);

        // should be able to launch properly with correct credentials even though the workflow is not published
        FileUtils.writeStringToFile(new File("md5sum.input"), "foo", StandardCharsets.UTF_8);
        Client.main(
            new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), CHECKER, LAUNCH, ENTRY, toolpath,
                JSON, ResourceHelpers.resourceFilePath("md5sum_cwl.json"), SCRIPT_FLAG });

        // should be able to launch properly with incorrect credentials but the entry is published
        Client.main(
            new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, PUBLISH, ENTRY, toolpath,
                SCRIPT_FLAG });
        Client.main(
            new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), CHECKER, LAUNCH, ENTRY, toolpath,
                JSON, ResourceHelpers.resourceFilePath("md5sum_cwl.json"), SCRIPT_FLAG });

        // should not be able to launch properly with incorrect credentials
        Client.main(
            new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, PUBLISH, ENTRY, toolpath,
                "--unpub", SCRIPT_FLAG });
        int exitCode = catchSystemExit(() ->  Client.main(
                new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), CHECKER, LAUNCH, ENTRY, toolpath,
                        JSON, ResourceHelpers.resourceFilePath("md5sum_cwl.json"), SCRIPT_FLAG }));
        assertEquals(Client.ENTRY_NOT_FOUND, exitCode);
    }

    @Test
    void testHostedWorkflowMetadataAndLaunch() throws IOException {
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
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, LAUNCH, ENTRY,
            workflow.getFullWorkflowPath(), JSON, ResourceHelpers.resourceFilePath("revsort-job.json"), SCRIPT_FLAG });

        WorkflowsApi workflowsApi = new WorkflowsApi(webClient);
        workflowsApi.publish(workflow.getId(), SwaggerUtility.createPublishRequest(true));
        // should also launch successfully with the wrong credentials when published
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), WORKFLOW, LAUNCH, ENTRY,
            workflow.getFullWorkflowPath(), JSON, ResourceHelpers.resourceFilePath("revsort-job.json"), SCRIPT_FLAG });
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
    void cwlVersion11() {
        final ApiClient userApiClient = CLICommonTestUtilities.getWebClient(true, USER_2_USERNAME, testingPostgres);
        WorkflowsApi userWorkflowsApi = new WorkflowsApi(userApiClient);
        userWorkflowsApi.manualRegister("github", "dockstore-testing/Workflows-For-CI", "/cwl/v1.1/metadata.cwl", "metadata", CWL.toString(),
            "/cwl/v1.1/cat-job.json");
        final Workflow workflowByPathGithub = userWorkflowsApi
            .getWorkflowByPath("github.com/dockstore-testing/Workflows-For-CI/metadata", WorkflowClient.BIOWORKFLOW, null);
        final Workflow workflow = userWorkflowsApi.refresh(workflowByPathGithub.getId(), true);
        assertEquals("Print the contents of a file to stdout using 'cat' running in a docker container.",
                workflow.getDescription());
        assertEquals("Peter Amstutz", workflow.getAuthor());
        assertEquals("peter.amstutz@curoverse.com", workflow.getEmail());
        assertTrue(workflow.getWorkflowVersions().stream().anyMatch(versions -> "master".equals(versions.getName())));
        Optional<WorkflowVersion> optionalWorkflowVersion = workflow.getWorkflowVersions().stream()
            .filter(version -> "master".equalsIgnoreCase(version.getName())).findFirst();
        assertTrue(optionalWorkflowVersion.isPresent());
        WorkflowVersion workflowVersion = optionalWorkflowVersion.get();

        // verify sourcefiles
        final io.dockstore.openapi.client.ApiClient userOpenApiClient = CLICommonTestUtilities.getOpenApiWebClient(true, USER_2_USERNAME, testingPostgres);
        io.dockstore.openapi.client.api.WorkflowsApi openApiWorkflowApi = new io.dockstore.openapi.client.api.WorkflowsApi(userOpenApiClient);
        List<io.dockstore.openapi.client.model.SourceFile> sourceFileList = openApiWorkflowApi.getWorkflowVersionsSourcefiles(workflow.getId(), workflowVersion.getId(), null);

        assertEquals(2, sourceFileList.size());
        assertTrue(sourceFileList.stream().anyMatch(sourceFile -> sourceFile.getPath().equals("/cwl/v1.1/cat-job.json")));
        assertTrue(sourceFileList.stream().anyMatch(sourceFile -> sourceFile.getPath().equals("/cwl/v1.1/metadata.cwl")));

        // Check validation works.  It is invalid because this is a tool and not a workflow.
        assertFalse(workflowVersion.isValid());

        userWorkflowsApi
            .manualRegister("github", "dockstore-testing/Workflows-For-CI", "/cwl/v1.1/count-lines1-wf.cwl", "count-lines1-wf", CWL.toString(),
                "/cwl/v1.1/wc-job.json");
        final Workflow workflowByPathGithub2 = userWorkflowsApi
            .getWorkflowByPath("github.com/dockstore-testing/Workflows-For-CI/count-lines1-wf", WorkflowClient.BIOWORKFLOW, null);
        final Workflow workflow2 = userWorkflowsApi.refresh(workflowByPathGithub2.getId(), true);
        assertTrue(workflow.getWorkflowVersions().stream().anyMatch(versions -> "master".equals(versions.getName())));
        Optional<WorkflowVersion> optionalWorkflowVersion2 = workflow2.getWorkflowVersions().stream()
            .filter(version -> "master".equalsIgnoreCase(version.getName())).findFirst();
        assertTrue(optionalWorkflowVersion2.isPresent());
        WorkflowVersion workflowVersion2 = optionalWorkflowVersion2.get();
        // Check validation works.  It should be valid
        assertTrue(workflowVersion2.isValid());
        userWorkflowsApi.publish(workflowByPathGithub2.getId(), SwaggerUtility.createPublishRequest(true));
        List<String> args = new ArrayList<>();
        args.add(WORKFLOW);
        args.add(LAUNCH);
        args.add(ENTRY);
        args.add("github.com/dockstore-testing/Workflows-For-CI/count-lines1-wf");
        args.add("--yaml");
        args.add(jsonFilePath);
        args.add(CONFIG);
        args.add(clientConfig);
        args.add(SCRIPT_FLAG);

        Client.main(args.toArray(new String[0]));
        assertTrue(systemOutRule.getText().contains("Final process status is success"));
    }
}
