package io.dockstore.client.cli;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import io.dockstore.client.cli.nested.WesLauncher;
import io.dockstore.client.cli.nested.WesRequestData;
import io.dockstore.client.cli.nested.WorkflowClient;
import io.dockstore.common.MuteForSuccessfulTests;
import io.dockstore.openapi.client.api.WorkflowsApi;
import io.dockstore.openapi.client.model.Workflow;
import io.dockstore.openapi.client.model.WorkflowVersion;
import io.dropwizard.testing.ResourceHelpers;
import io.openapi.wes.client.ApiException;
import io.openapi.wes.client.api.WorkflowExecutionServiceApi;
import io.openapi.wes.client.model.RunId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.org.webcompere.systemstubs.SystemStubs.catchSystemExit;

@ExtendWith(SystemStubsExtension.class)
@ExtendWith(MuteForSuccessfulTests.class)
class WesLauncherTest {

    public static final String RUN_ID = "123456-098765-123123-04983782";
    public static final String WORKFLOW_PATH = "github.com/org/repo";
    public static final String DESCRIPTOR_PATH = "rightHere.wdl";

    @SystemStub
    public final SystemOut systemOutRule = new SystemOut();

    @SystemStub
    public final SystemErr systemErrRule = new SystemErr();

    public Workflow buildFakeWorkflow() {
        Workflow workflow = new Workflow();

        // Set workflow versions
        WorkflowVersion version = new WorkflowVersion();
        version.setName("v1");
        version.setWorkflowPath(DESCRIPTOR_PATH);
        workflow.setWorkflowVersions(List.of(version));

        workflow.setDefaultVersion("v1");
        workflow.setDescriptorType(Workflow.DescriptorTypeEnum.WDL);
        workflow.setPath(WORKFLOW_PATH);
        workflow.setWorkflowPath(DESCRIPTOR_PATH);

        return workflow;
    }

    public WorkflowClient mockWorkflowClient(String configPath) {

        // Create the CLI client
        Client client = new Client();
        if (configPath != null) {
            client.setConfigFile(ResourceHelpers.resourceFilePath(configPath));
        }
        client.setupClientEnvironment(new ArrayList<>());

        Workflow fakeWorkflow = buildFakeWorkflow();

        // Mock the workflows API, and return a prebuilt workflow
        WorkflowsApi workflowApi = mock(WorkflowsApi.class);
        WorkflowClient workflowClient = mock(WorkflowClient.class);

        // Set request credentials object
        WesRequestData wrd = new WesRequestData("myUrl", "myBearerToken");
        workflowClient.setWesRequestData(wrd);

        // WorkflowsApi Function mocks
        when(workflowApi.getPublishedWorkflowByPath(
            any(String.class),
            any(io.dockstore.openapi.client.model.WorkflowSubClass.class),
            ArgumentMatchers.isNull(),
            any(String.class)
        )).thenReturn(fakeWorkflow);

        // WorkflowClient function mocks
        when(workflowClient.getClient()).thenReturn(client);
        when(workflowClient.getVersionID(any(String.class))).thenReturn(fakeWorkflow.getDefaultVersion());
        when(workflowClient.getTrsId(any(String.class))).thenReturn("#workflow/" + WORKFLOW_PATH);
        when(workflowClient.getWorkflowsApi()).thenReturn(workflowApi);

        return workflowClient;
    }

    public WorkflowExecutionServiceApi mockWesApi() throws ApiException {
        WorkflowExecutionServiceApi fakeApi = mock(WorkflowExecutionServiceApi.class);

        // Mock requests to the GA4GH WES API, returning a fake run ID
        RunId runId = new RunId();
        runId.setRunId(RUN_ID);
        when(fakeApi.runWorkflow(
            any(File.class),
            any(String.class),
            any(String.class),
            any(String.class),
            any(String.class),
            any(String.class),
            any(List.class)
        )).thenReturn(runId);

        when(fakeApi.runWorkflow(
            ArgumentMatchers.isNull(),
            any(String.class),
            any(String.class),
            any(String.class),
            any(String.class),
            any(String.class),
            any(List.class)
        )).thenReturn(runId);

        return fakeApi;
    }

    @Test
    public void testLaunchWithNoFiles() throws ApiException {
        WorkflowClient workflowClient = mockWorkflowClient("configNoContent");
        String workflowEntry = "my/entry/path";
        WorkflowExecutionServiceApi clientWorkflowExecutionServiceApi = mockWesApi();

        WesLauncher.launchWesCommand(clientWorkflowExecutionServiceApi, workflowClient, workflowEntry, false, null, null, false);
        assertTrue(systemOutRule.getText().contains(RUN_ID), "The runId should be printed out");
    }

    @Test
    public void testLaunchWithExistingFile() throws ApiException {
        WorkflowClient workflowClient = mockWorkflowClient("configNoContent");
        String workflowEntry = "my/entry/path";
        String workflowParamPath = ResourceHelpers.resourceFilePath("helloSpaces.json");
        List<String> attachments = new ArrayList<>();
        WorkflowExecutionServiceApi clientWorkflowExecutionServiceApi = mockWesApi();

        WesLauncher.launchWesCommand(clientWorkflowExecutionServiceApi, workflowClient, workflowEntry, false, workflowParamPath, attachments, false);
        assertTrue(systemOutRule.getText().contains(RUN_ID), "The runId should be printed out");
    }

    @Test
    public void testLaunchWithFakeFile() throws Exception {
        WorkflowClient workflowClient = mockWorkflowClient("configNoContent");
        String workflowEntry = "my/entry/path";
        String workflowParamPath = "this/file/doesnt/exist";
        List<String> attachments = new ArrayList<>();
        WorkflowExecutionServiceApi clientWorkflowExecutionServiceApi = mockWesApi();

        // Expect an error to be thrown when a file cant be found
        catchSystemExit(() -> WesLauncher.launchWesCommand(clientWorkflowExecutionServiceApi, workflowClient, workflowEntry, false, workflowParamPath,
                attachments, false));
    }

    @Test
    public void testLaunchWithSomeRealAttachments() throws ApiException {
        WorkflowClient workflowClient = mockWorkflowClient("configNoContent");
        String workflowEntry = "my/entry/path";
        String workflowParamPath = ResourceHelpers.resourceFilePath("helloSpaces.json");
        List<String> attachments = new ArrayList<>();
        attachments.add(ResourceHelpers.resourceFilePath("helloSpaces.json"));
        attachments.add(ResourceHelpers.resourceFilePath("helloSpaces.wdl"));
        attachments.add(ResourceHelpers.resourceFilePath("idNonWord.cwl"));
        WorkflowExecutionServiceApi clientWorkflowExecutionServiceApi = mockWesApi();

        // Expect an error to be thrown when a file cant be found
        WesLauncher.launchWesCommand(clientWorkflowExecutionServiceApi, workflowClient, workflowEntry, false, workflowParamPath, attachments, false);
        assertTrue(systemOutRule.getText().contains(RUN_ID), "The runId should be printed out");

    }

    @Test
    public void testLaunchWithFakeAttachments() throws Exception {
        WorkflowClient workflowClient = mockWorkflowClient("configNoContent");
        String workflowEntry = "my/entry/path";
        String workflowParamPath = ResourceHelpers.resourceFilePath("helloSpaces.json");
        List<String> attachments = new ArrayList<>();
        attachments.add("this/file/doesnt/exist");
        WorkflowExecutionServiceApi clientWorkflowExecutionServiceApi = mockWesApi();

        // Expect an error to be thrown when a file cant be found
        catchSystemExit(() -> WesLauncher.launchWesCommand(clientWorkflowExecutionServiceApi, workflowClient, workflowEntry, false, workflowParamPath, attachments, false));
    }

    @Test
    public void testLaunchWithSomeFakeAttachments() throws Exception {
        WorkflowClient workflowClient = mockWorkflowClient("configNoContent");
        String workflowEntry = "my/entry/path";
        String workflowParamPath = ResourceHelpers.resourceFilePath("helloSpaces.json");
        List<String> attachments = new ArrayList<>();
        attachments.add(ResourceHelpers.resourceFilePath("helloSpaces.json"));
        attachments.add("uh/oh/this/is/a/typo");
        attachments.add(ResourceHelpers.resourceFilePath("helloSpaces.wdl"));
        WorkflowExecutionServiceApi clientWorkflowExecutionServiceApi = mockWesApi();

        // Expect an error to be thrown when a file cant be found
        catchSystemExit(() ->  WesLauncher.launchWesCommand(clientWorkflowExecutionServiceApi, workflowClient, workflowEntry, false, workflowParamPath, attachments, false));
    }

    @Test
    public void testLaunchWithDirectoryNotFile() throws ApiException {
        WorkflowClient workflowClient = mockWorkflowClient("configNoContent");
        String workflowEntry = "my/entry/path";
        String workflowParamPath = ResourceHelpers.resourceFilePath("");
        WorkflowExecutionServiceApi clientWorkflowExecutionServiceApi = mockWesApi();

        // Directories should be ignored, no error should be thrown
        WesLauncher.launchWesCommand(clientWorkflowExecutionServiceApi, workflowClient, workflowEntry, false, workflowParamPath, null, false);
    }

    @Test
    public void testTrsUrlCreation() throws ApiException {

        // Create client + launcher
        WorkflowClient aec = mockWorkflowClient("configNoContent");
        WesRequestData wrd = new WesRequestData("myWesUrl");
        aec.setWesRequestData(wrd);

        Workflow workflow = new Workflow();
        workflow.setWorkflowPath("rightHere.wdl");
        workflow.setDefaultVersion("v1");
        workflow.setDescriptorType(Workflow.DescriptorTypeEnum.WDL);
        workflow.setRepository("repo");
        workflow.setOrganization("org");
        workflow.setRepository("github.com");

        WorkflowVersion workflowVersion = new WorkflowVersion();
        workflowVersion.setWorkflowPath("rightHere.wdl");

        // Ensure the #workflow prefix is correctly encoded
        final String trsWorkflowUrl = WesLauncher.combineTrsUrlComponents(aec, "github.com/org/repo:master", workflow, workflowVersion);

        final String expectedWorkflowResult = "https://dockstore.org/api/ga4gh/trs/v2/tools/%23workflow%2Fgithub.com%2Forg%2Frepo/versions/v1/PLAIN_WDL/descriptor/rightHere.wdl";
        assertEquals(expectedWorkflowResult, trsWorkflowUrl, "Checking the entire URL");

    }
}
