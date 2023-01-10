package io.dockstore.client.cli;

import io.dockstore.common.CommonTestUtilities;
import io.dockstore.openapi.client.model.WorkflowSubClass;
import io.dropwizard.testing.ResourceHelpers;
import io.openapi.model.DescriptorType;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.PublishRequest;
import io.swagger.client.model.Workflow;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.org.webcompere.systemstubs.SystemStubs.catchSystemExit;

/**
 * Tests every command in the workflow mode with a GitHub App Tool except manual_publish (because it's unrelated) and wes
 */
public class GitHubAppToolIT extends BaseIT {

    private static final String WORKFLOW_REPO = "DockstoreTestUser2/test-workflows-and-tools";
    private static final String ENTRY_PATH = String.format("github.com/%s/test-workflows-and-tools/md5sum", USER_2_USERNAME);
    private static final String VERSION = "main";
    private static final String ENTRY_PATH_WITH_VERSION = String.format("%s:%s", ENTRY_PATH, VERSION);

    @SystemStub
    public final SystemOut systemOutRule = new SystemOut();

    @SystemStub
    public final SystemErr systemErrRule = new SystemErr();


    @BeforeEach
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        final WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        workflowApi.handleGitHubRelease(WORKFLOW_REPO, USER_2_USERNAME, "refs/heads/main", INSTALLATION_ID);
        publishWorkflow();
    }

    @Test
    public void list() {
        systemOutRule.clear();
        Client.main(new String[]{"workflow", "list", "--config", ResourceHelpers.resourceFilePath("config_file2.txt")});
        assertTrue(systemOutRule.getText().contains(ENTRY_PATH));
    }

    @Disabled("Requires an endpoint that is able to search for published apptools with a pattern")
    @Test
    public void search() {
        systemOutRule.clear();
        Client.main(new String[]{"workflow", "search", "--pattern", "md5sum"});
        assertTrue(systemOutRule.getText().contains(ENTRY_PATH));
    }

    @Test
    public void publish() {
        Client.main(new String[]{"workflow", "publish", "--unpub", "--entry", ENTRY_PATH, "--config", ResourceHelpers.resourceFilePath("config_file2.txt")});
        assertTrue(
                systemOutRule.getText().contains("Successfully unpublished  github.com/DockstoreTestUser2/test-workflows-and-tools/md5sum"));
        systemOutRule.clear();
        Client.main(new String[]{"workflow", "publish", "--entry", ENTRY_PATH, "--config", ResourceHelpers.resourceFilePath("config_file2.txt")});
        assertTrue(
                systemOutRule.getText().contains("Successfully published  github.com/DockstoreTestUser2/test-workflows-and-tools/md5sum"));
    }

    @Test
    public void info() {
        Client.main(new String[]{"workflow", "info", "--entry", ENTRY_PATH, "--config", ResourceHelpers.resourceFilePath("config_file2.txt")});
        assertTrue(systemOutRule.getText().contains("git@github.com:DockstoreTestUser2/test-workflows-and-tools.git"));
        assertTrue(systemOutRule.getText().contains(ENTRY_PATH));
    }

    @Test
    public void cwl() {
        Client.main(new String[]{"workflow", DescriptorType.CWL.toString(), "--entry", ENTRY_PATH_WITH_VERSION, "--config", ResourceHelpers.resourceFilePath("config_file2.txt")});
        assertTrue(systemOutRule.getText().contains("label: Simple md5sum tool"));
    }

    @Test
    public void wdl() throws Exception {
        int exitCode = catchSystemExit(() -> Client.main(new String[]{"workflow", DescriptorType.WDL.toString(), "--entry", ENTRY_PATH_WITH_VERSION, "--config", ResourceHelpers.resourceFilePath("config_file2.txt")}));
        assertEquals(Client.API_ERROR, exitCode);
        assertTrue(systemOutRule.getText().contains("Invalid version"));
    }

    @Test
    public void refresh() throws Exception {
        int exitCode = catchSystemExit(() -> Client.main(new String[]{"workflow", "refresh", "--entry", ENTRY_PATH, "--config", ResourceHelpers.resourceFilePath("config_file2.txt")}));
        assertEquals(Client.COMMAND_ERROR, exitCode);
        assertTrue(systemOutRule.getText().contains("GitHub Apps entries cannot be refreshed"));
    }

    @Test
    public void label() {
        Client.main(new String[]{"workflow", "label", "--add", "potato", "--entry", ENTRY_PATH, "--config", ResourceHelpers.resourceFilePath("config_file2.txt")});
        assertTrue(systemOutRule.getText().contains("The workflow has the following labels:"));
        systemOutRule.clear();
        Client.main(new String[]{"workflow", "label", "--remove", "potato", "--entry", ENTRY_PATH, "--config", ResourceHelpers.resourceFilePath("config_file2.txt")});
        assertTrue(systemOutRule.getText().contains("The workflow has no labels"));
    }

    @Test
    public void star() {
        Client.main(new String[]{"workflow", "star", "--entry", ENTRY_PATH, "--config", ResourceHelpers.resourceFilePath("config_file2.txt")});
        assertTrue(systemOutRule.getText().contains("Successfully starred github.com/DockstoreTestUser2/test-workflows-and-tools/md5sum"));
        systemOutRule.clear();
        Client.main(new String[]{"workflow", "star", "--unstar", "--entry", ENTRY_PATH, "--config", ResourceHelpers.resourceFilePath("config_file2.txt")});
        assertTrue(
                systemOutRule.getText().contains("Successfully unstarred github.com/DockstoreTestUser2/test-workflows-and-tools/md5sum"));
    }

    @Test
    public void testParameter() throws Exception {
        int exitCode = catchSystemExit(() -> Client.main(new String[]{"workflow", "test_parameter", "--version", "main", "--add", "test.json", "--entry", ENTRY_PATH, "--config", ResourceHelpers.resourceFilePath("config_file2.txt")}));
        assertEquals(Client.COMMAND_ERROR, exitCode);
        assertTrue(systemOutRule.getText().contains("Cannot update test parameter files of GitHub App entries"));
    }

    @Test
    public void convert() {
        Client.main(new String[]{"workflow", "convert", "entry2json", "--entry", ENTRY_PATH_WITH_VERSION, "--config", ResourceHelpers.resourceFilePath("config_file2.txt")});
        assertTrue(systemOutRule.getText().contains("tmp/fill_me_in.txt"));
    }

    @Test
    public void launch() throws ApiException {
        Client.main(new String[]{"workflow", "launch", "--entry", ENTRY_PATH_WITH_VERSION, "--json", ResourceHelpers.resourceFilePath("md5sum_cwl.json"), "--script", "--config",
            ResourceHelpers.resourceFilePath("config_file2.txt")});
        assertTrue(systemOutRule.getText().contains("Final process status is success"));
    }

    @Test
    public void download() {
        Client.main(new String[]{"workflow", "download", "--entry", ENTRY_PATH_WITH_VERSION, "--config", ResourceHelpers.resourceFilePath("config_file2.txt")});
        assertTrue(systemOutRule.getText().contains("GET /workflows/1001/zip/1001 HTTP/1.1"));
        Client.main(new String[]{"workflow", "download", "--entry", ENTRY_PATH_WITH_VERSION, "--zip", "--config", ResourceHelpers.resourceFilePath("config_file2.txt")});
        assertEquals(2, StringUtils.countMatches(systemOutRule.getText(), "GET /workflows/1001/zip/1001 HTTP/1.1"));
    }

    @Test
    public void updateWorkflow() throws Exception {
        int exitCode = catchSystemExit(() ->  Client.main(new String[]{"workflow", "update_workflow", "--entry", ENTRY_PATH, "--config", ResourceHelpers.resourceFilePath("config_file2.txt")}));
        assertEquals(Client.COMMAND_ERROR, exitCode);
        assertTrue(systemOutRule.getText().contains("Command not supported for GitHub App entries"));
    }

    @Test
    public void versionTag() throws Exception {
        int exitCode = catchSystemExit(() -> Client.main(new String[]{"workflow", "version_tag", "--name", "main", "--entry", ENTRY_PATH, "--config", ResourceHelpers.resourceFilePath("config_file2.txt")}));
        assertEquals(Client.COMMAND_ERROR, exitCode);
        assertTrue(systemOutRule.getText().contains("Command not supported for GitHub App entries"));
    }

    @Test
    public void restub() throws Exception {
        int exitCode = catchSystemExit(() ->  Client.main(new String[]{"workflow", "restub", "--entry", ENTRY_PATH, "--config", ResourceHelpers.resourceFilePath("config_file2.txt")}));
        assertEquals(Client.COMMAND_ERROR, exitCode);
        assertTrue(systemOutRule.getText().contains("Command not supported for GitHub App entries"));
    }

    private void publishWorkflow() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        final WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        Workflow workflowByPath = workflowApi.getWorkflowByPath(ENTRY_PATH, WorkflowSubClass.APPTOOL.toString(), null);
        final PublishRequest publishRequest = SwaggerUtility.createPublishRequest(true);
        workflowApi.publish(workflowByPath.getId(), publishRequest);
    }

}
