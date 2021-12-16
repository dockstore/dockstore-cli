package io.dockstore.client.cli;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import io.dockstore.client.cli.nested.WesLauncher;
import io.dockstore.client.cli.nested.WesRequestData;
import io.dockstore.client.cli.nested.WorkflowClient;
import io.dropwizard.testing.ResourceHelpers;
import io.openapi.wes.client.ApiException;
import io.openapi.wes.client.api.WorkflowExecutionServiceApi;
import io.openapi.wes.client.model.RunId;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.Workflow;
import io.swagger.client.model.WorkflowVersion;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.mockito.ArgumentMatchers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class WesLauncherIT {

    public static final String RUN_ID = "123456-098765-123123-04983782";
    public static final String WORKFLOW_PATH = "github.com/org/repo";
    public static final String DESCRIPTOR_PATH = "rightHere.wdl";

    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();
    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog();
    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog();

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

    public WorkflowClient mockWorkflowClient(String configPath) throws ApiException {

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
        WorkflowExecutionServiceApi fakeApi = mock(WorkflowExecutionServiceApi.class);

        // Set request credentials object
        WesRequestData wrd = new WesRequestData("myUrl", "myBearerToken");
        workflowClient.setWesRequestData(wrd);

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

        // WorkflowsApi Function mocks
        when(workflowApi.getPublishedWorkflowByPath(
            any(String.class),
            ArgumentMatchers.isNull(),
            ArgumentMatchers.isNull(),
            any(String.class)
        )).thenReturn(fakeWorkflow);

        // WorkflowClient function mocks
        when(workflowClient.getClient()).thenReturn(client);
        when(workflowClient.getVersionID(any(String.class))).thenReturn(fakeWorkflow.getDefaultVersion());
        when(workflowClient.getTrsId(any(String.class))).thenReturn("#workflow/" + WORKFLOW_PATH);
        when(workflowClient.getWorkflowsApi()).thenReturn(workflowApi);
        when(workflowClient.getWorkflowExecutionServiceApi()).thenReturn(fakeApi);

        return workflowClient;
    }

    @Test
    public void testLaunchWithNoFiles() throws ApiException {
        WorkflowClient workflowClient = mockWorkflowClient("configNoContent");
        String workflowEntry = "my/entry/path";

        WesLauncher.launchWesCommand(workflowClient, workflowEntry, null, null);
        assertTrue("The runId should be printed out", systemOutRule.getLog().contains(RUN_ID));
    }

    @Test
    public void testLaunchWithExistingFile() throws ApiException {
        WorkflowClient workflowClient = mockWorkflowClient("configNoContent");
        String workflowEntry = "my/entry/path";
        String workflowParamPath = ResourceHelpers.resourceFilePath("helloSpaces.json");
        List<String> attachments = new ArrayList<>();

        WesLauncher.launchWesCommand(workflowClient, workflowEntry, workflowParamPath, attachments);
        assertTrue("The runId should be printed out", systemOutRule.getLog().contains(RUN_ID));
    }

    @Test
    public void testLaunchWithFakeFile() throws ApiException {
        WorkflowClient workflowClient = mockWorkflowClient("configNoContent");
        String workflowEntry = "my/entry/path";
        String workflowParamPath = "this/file/doesnt/exist";
        List<String> attachments = new ArrayList<>();

        // Expect an error to be thrown when a file cant be found
        systemExit.expectSystemExit();
        WesLauncher.launchWesCommand(workflowClient, workflowEntry, workflowParamPath, attachments);
        assertTrue("The file doesn't exist, so an error should be thrown",
            systemErrRule.getLog().contains("Unable to locate file: this/file/doesnt/exist"));
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

        // Expect an error to be thrown when a file cant be found
        WesLauncher.launchWesCommand(workflowClient, workflowEntry, workflowParamPath, attachments);
        assertTrue("The runId should be printed out", systemOutRule.getLog().contains(RUN_ID));

    }

    @Test
    public void testLaunchWithFakeAttachments() throws ApiException {
        WorkflowClient workflowClient = mockWorkflowClient("configNoContent");
        String workflowEntry = "my/entry/path";
        String workflowParamPath = ResourceHelpers.resourceFilePath("helloSpaces.json");
        List<String> attachments = new ArrayList<>();
        attachments.add("this/file/doesnt/exist");

        // Expect an error to be thrown when a file cant be found
        systemExit.expectSystemExit();
        WesLauncher.launchWesCommand(workflowClient, workflowEntry, workflowParamPath, attachments);
        assertTrue("The file doesn't exist, so an error should be thrown",
            systemErrRule.getLog().contains("Unable to locate file: this/file/doesnt/exist"));
    }

    @Test
    public void testLaunchWithSomeFakeAttachments() throws ApiException {
        WorkflowClient workflowClient = mockWorkflowClient("configNoContent");
        String workflowEntry = "my/entry/path";
        String workflowParamPath = ResourceHelpers.resourceFilePath("helloSpaces.json");
        List<String> attachments = new ArrayList<>();
        attachments.add(ResourceHelpers.resourceFilePath("helloSpaces.json"));
        attachments.add("uh/oh/this/is/a/typo");
        attachments.add(ResourceHelpers.resourceFilePath("helloSpaces.wdl"));

        // Expect an error to be thrown when a file cant be found
        systemExit.expectSystemExit();
        WesLauncher.launchWesCommand(workflowClient, workflowEntry, workflowParamPath, attachments);
        assertTrue("The file doesn't exist, so an error should be thrown",
            systemErrRule.getLog().contains("Unable to locate file: uh/oh/this/is/a/typo"));
    }

    @Test
    public void testLaunchWithDirectoryNotFile() throws ApiException {
        WorkflowClient workflowClient = mockWorkflowClient("configNoContent");
        String workflowEntry = "my/entry/path";
        String workflowParamPath = ResourceHelpers.resourceFilePath("");

        // Expect an error to be thrown when a file cant be found
        systemExit.expectSystemExit();
        WesLauncher.launchWesCommand(workflowClient, workflowEntry, workflowParamPath, null);
        assertTrue("A directory is passed, not a file. An error should be thrown.",
            systemErrRule.getLog().contains("Unable to locate file: " + workflowParamPath));
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

        // Ensure the #workflow prefix is correctly encoded
        final String trsWorkflowUrl = WesLauncher.combineTrsUrlComponents(aec, "github.com/org/repo:master", workflow);

        final String expectedWorkflowResult = "https://dockstore.org/api/ga4gh/trs/v2/tools/%23workflow%2Fgithub.com%2Forg%2Frepo/versions/v1/PLAIN_WDL/descriptor/rightHere.wdl";
        assertEquals("Checking the entire URL", expectedWorkflowResult, trsWorkflowUrl);

    }
}
