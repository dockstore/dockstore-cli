package io.dockstore.client.cli;

import io.dockstore.common.CLICommonTestUtilities;
import io.dockstore.common.FlushingSystemErr;
import io.dockstore.common.FlushingSystemOut;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.ApiException;
import io.dockstore.openapi.client.api.WorkflowsApi;
import io.dockstore.openapi.client.model.PublishRequest;
import io.dockstore.openapi.client.model.Workflow;
import io.dockstore.openapi.client.model.WorkflowSubClass;
import io.dropwizard.testing.ResourceHelpers;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

import static io.dockstore.client.cli.ArgumentUtility.CONVERT;
import static io.dockstore.client.cli.ArgumentUtility.DOWNLOAD;
import static io.dockstore.client.cli.ArgumentUtility.LAUNCH;
import static io.dockstore.client.cli.Client.CONFIG;
import static io.dockstore.client.cli.Client.SCRIPT_FLAG;
import static io.dockstore.client.cli.Client.WORKFLOW;
import static io.dockstore.client.cli.nested.AbstractEntryClient.ENTRY_2_JSON;
import static io.dockstore.client.cli.nested.AbstractEntryClient.INFO;
import static io.dockstore.client.cli.nested.AbstractEntryClient.LABEL;
import static io.dockstore.client.cli.nested.AbstractEntryClient.LIST;
import static io.dockstore.client.cli.nested.AbstractEntryClient.PUBLISH;
import static io.dockstore.client.cli.nested.AbstractEntryClient.REFRESH;
import static io.dockstore.client.cli.nested.AbstractEntryClient.SEARCH;
import static io.dockstore.client.cli.nested.AbstractEntryClient.STAR;
import static io.dockstore.client.cli.nested.AbstractEntryClient.TEST_PARAMETER;
import static io.dockstore.client.cli.nested.ToolClient.VERSION_TAG;
import static io.dockstore.client.cli.nested.WesCommandParser.ENTRY;
import static io.dockstore.client.cli.nested.WesCommandParser.JSON;
import static io.dockstore.client.cli.nested.WorkflowClient.UPDATE_WORKFLOW;
import static io.dockstore.common.DescriptorLanguage.CWL;
import static io.dockstore.common.DescriptorLanguage.WDL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.org.webcompere.systemstubs.SystemStubs.catchSystemExit;

/**
 * Tests every command in the workflow mode with a GitHub App Tool except manual_publish (because it's unrelated) and wes
 */
class GitHubAppToolIT extends BaseIT {

    private static final String WORKFLOW_REPO = "DockstoreTestUser2/test-workflows-and-tools";
    private static final String ENTRY_PATH = String.format("github.com/%s/test-workflows-and-tools/md5sum", USER_2_USERNAME);
    private static final String VERSION = "main";
    private static final String ENTRY_PATH_WITH_VERSION = String.format("%s:%s", ENTRY_PATH, VERSION);

    @SystemStub
    public final SystemOut systemOutRule = new FlushingSystemOut();

    @SystemStub
    public final SystemErr systemErrRule = new FlushingSystemErr();


    @BeforeEach
    @Override
    public void resetDBBetweenTests() throws Exception {
        CLICommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        final WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        systemErrRule.clear();
        systemOutRule.clear();

        // the following change the DB state
        workflowApi.handleGitHubRelease("refs/heads/main", INSTALLATION_ID, WORKFLOW_REPO, USER_2_USERNAME);
        publishWorkflow();
    }

    @Test
    void list() {
        systemOutRule.clear();
        Client.main(new String[]{WORKFLOW, LIST, CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt")});
        assertTrue(systemOutRule.getText().contains(ENTRY_PATH));
    }

    @Disabled("Requires an endpoint that is able to search for published apptools with a pattern")
    @Test
    void search() {
        systemOutRule.clear();
        Client.main(new String[]{WORKFLOW, SEARCH, "--pattern", "md5sum"});
        assertTrue(systemOutRule.getText().contains(ENTRY_PATH));
    }

    @Test
    void publish() {
        Client.main(new String[]{WORKFLOW, PUBLISH, "--unpub", ENTRY, ENTRY_PATH, CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt")});
        assertTrue(
                systemOutRule.getText().contains("Successfully unpublished  github.com/DockstoreTestUser2/test-workflows-and-tools/md5sum"));
        systemOutRule.clear();
        Client.main(new String[]{WORKFLOW, PUBLISH, ENTRY, ENTRY_PATH, CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt")});
        assertTrue(
                systemOutRule.getText().contains("Successfully published  github.com/DockstoreTestUser2/test-workflows-and-tools/md5sum"));
    }

    @Test
    void info() {
        Client.main(new String[]{WORKFLOW, INFO, ENTRY, ENTRY_PATH, CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt")});
        assertTrue(systemOutRule.getText().contains("git@github.com:DockstoreTestUser2/test-workflows-and-tools.git"));
        assertTrue(systemOutRule.getText().contains(ENTRY_PATH));
    }

    @Test
    void cwl() {
        Client.main(new String[]{WORKFLOW, CWL.toString(), ENTRY, ENTRY_PATH_WITH_VERSION, CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt")});
        assertTrue(systemOutRule.getText().contains("label: Simple md5sum tool"));
    }

    @Test
    void wdl() throws Exception {
        int exitCode = catchSystemExit(() -> Client.main(new String[]{WORKFLOW, WDL.toString(), ENTRY, ENTRY_PATH_WITH_VERSION, CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt")}));
        assertEquals(Client.API_ERROR, exitCode);
        assertTrue(systemErrRule.getOutput().getText().contains("Invalid version"), "stderr missing invalid version message, looked like " + systemErrRule.getOutput().getText());
    }

    @Test
    void refresh() throws Exception {
        int exitCode = catchSystemExit(() -> Client.main(new String[]{WORKFLOW, REFRESH, ENTRY, ENTRY_PATH, CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt")}));
        assertEquals(Client.COMMAND_ERROR, exitCode);
        assertTrue(systemErrRule.getText().contains("GitHub Apps entries cannot be refreshed"), "stderr missing error message, looked like: " + systemErrRule.getText());
    }

    @Test
    void label() {
        Client.main(new String[]{WORKFLOW, LABEL, "--add", "potato", ENTRY, ENTRY_PATH, CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt")});
        assertTrue(systemOutRule.getText().contains("The workflow has the following labels:"));
        systemOutRule.clear();
        Client.main(new String[]{WORKFLOW, LABEL, "--remove", "potato", ENTRY, ENTRY_PATH, CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt")});
        assertTrue(systemOutRule.getText().contains("The workflow has no labels"));
    }

    @Test
    void star() {
        Client.main(new String[]{WORKFLOW, STAR, ENTRY, ENTRY_PATH, CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt")});
        assertTrue(systemOutRule.getText().contains("Successfully starred github.com/DockstoreTestUser2/test-workflows-and-tools/md5sum"));
        systemOutRule.clear();
        Client.main(new String[]{WORKFLOW, STAR, "--unstar", ENTRY, ENTRY_PATH, CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt")});
        assertTrue(
                systemOutRule.getText().contains("Successfully unstarred github.com/DockstoreTestUser2/test-workflows-and-tools/md5sum"));
    }

    @Test
    void testParameter() throws Exception {
        int exitCode = catchSystemExit(() -> Client.main(new String[]{WORKFLOW, TEST_PARAMETER, "--version", "main", "--add", "test.json", ENTRY, ENTRY_PATH, CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt")}));
        assertEquals(Client.COMMAND_ERROR, exitCode);
        assertTrue(systemErrRule.getText().contains("Cannot update test parameter files of GitHub App entries"));
    }


    @Test
    void convert() {
        Client.main(new String[]{WORKFLOW, CONVERT, ENTRY_2_JSON, ENTRY, ENTRY_PATH_WITH_VERSION, CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt")});
        assertTrue(systemOutRule.getText().contains("tmp/fill_me_in.txt"));
    }

    @Test
    void launch() throws ApiException {
        Client.main(new String[]{WORKFLOW, LAUNCH, ENTRY, ENTRY_PATH_WITH_VERSION, JSON, ResourceHelpers.resourceFilePath("md5sum_cwl.json"), SCRIPT_FLAG, CONFIG,
            ResourceHelpers.resourceFilePath("config_file2.txt")});
        assertTrue(systemOutRule.getText().contains("Final process status is success"));
    }

    @Test
    void download() {
        Client.main(new String[]{WORKFLOW, DOWNLOAD, ENTRY, ENTRY_PATH_WITH_VERSION, CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt")});
        assertTrue(systemOutRule.getText().contains("GET /workflows/1001/zip/1001 HTTP/1.1"));
        systemOutRule.clear();
        Client.main(new String[]{WORKFLOW, DOWNLOAD, ENTRY, ENTRY_PATH_WITH_VERSION, "--zip", CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt")});
        assertEquals(1, StringUtils.countMatches(systemOutRule.getOutput().getText(), "GET /workflows/1001/zip/1001 HTTP/1.1"), "not matching, output looked like " + systemOutRule.getOutput().getText());
    }

    @Test
    void updateWorkflow() throws Exception {
        int exitCode = catchSystemExit(() ->  Client.main(new String[]{WORKFLOW, UPDATE_WORKFLOW, ENTRY, ENTRY_PATH, CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt")}));
        assertEquals(Client.COMMAND_ERROR, exitCode);
        assertTrue(systemErrRule.getText().contains("Command not supported for GitHub App entries"));
    }

    @Test
    void versionTag() throws Exception {
        int exitCode = catchSystemExit(() -> Client.main(new String[]{WORKFLOW, VERSION_TAG, "--name", "main", ENTRY, ENTRY_PATH, CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt")}));
        assertEquals(Client.COMMAND_ERROR, exitCode);
        assertTrue(systemErrRule.getText().contains("Command not supported for GitHub App entries"));
    }

    @Test
    void restub() throws Exception {
        int exitCode = catchSystemExit(() ->  Client.main(new String[]{WORKFLOW, "restub", ENTRY, ENTRY_PATH, CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt")}));
        assertEquals(Client.COMMAND_ERROR, exitCode);
        assertTrue(systemErrRule.getOutput().getText().contains("Command not supported for GitHub App entries"), "looked like: " + systemErrRule.getOutput().getText());
    }

    private void publishWorkflow() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        final WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        Workflow workflowByPath = workflowApi.getWorkflowByPath(ENTRY_PATH, WorkflowSubClass.APPTOOL, null);
        final PublishRequest publishRequest = SwaggerUtility.createPublishRequest(true);
        workflowApi.publish1(workflowByPath.getId(), publishRequest);
    }

}
