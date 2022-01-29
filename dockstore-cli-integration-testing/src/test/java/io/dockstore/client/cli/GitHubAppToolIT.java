package io.dockstore.client.cli;

import static io.dockstore.client.cli.Client.API_ERROR;
import static io.dockstore.client.cli.Client.COMMAND_ERROR;

import io.dockstore.common.CommonTestUtilities;
import io.dockstore.openapi.client.model.WorkflowSubClass;
import io.dropwizard.testing.ResourceHelpers;
import io.openapi.model.DescriptorType;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.PublishRequest;
import io.swagger.client.model.Workflow;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;

public class GitHubAppToolIT extends BaseIT {
    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog();

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog();

    private static final String workflowRepo = "DockstoreTestUser2/test-workflows-and-tools";
    private static final String entryPath = String.format("github.com/%s/test-workflows-and-tools/md5sum", USER_2_USERNAME);
    private static final String version = "main";
    private static final String entryPathWithVersion = String.format("%s:%s", entryPath, version);


    @Before
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false);
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        final WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        workflowApi.handleGitHubRelease(workflowRepo, USER_2_USERNAME, "refs/heads/main", installationId);
        publishWorkflow();
    }

    @Test
    public void list() {
        systemOutRule.clearLog();
        Client.main(new String[]{"workflow", "list", "--config", ResourceHelpers.resourceFilePath("config_file2.txt")});
        Assert.assertTrue(systemOutRule.getLog().contains(entryPath));
    }

    @Ignore("Requires an endpoint that is able to search for published apptools with a pattern")
    @Test
    public void search() {
        systemOutRule.clearLog();
        Client.main(new String[]{"workflow", "search", "--pattern", "md5sum"});
        Assert.assertTrue(systemOutRule.getLog().contains(entryPath));
    }

    @Test
    public void publish() {
        Client.main(new String[]{"workflow", "publish", "--unpub", "--entry", entryPath, "--config", ResourceHelpers.resourceFilePath("config_file2.txt")});
        Assert.assertTrue(systemOutRule.getLog().contains("Successfully unpublished  github.com/DockstoreTestUser2/test-workflows-and-tools/md5sum"));
        systemOutRule.clearLog();
        Client.main(new String[]{"workflow", "publish", "--entry", entryPath, "--config", ResourceHelpers.resourceFilePath("config_file2.txt")});
        Assert.assertTrue(systemOutRule.getLog().contains("Successfully published  github.com/DockstoreTestUser2/test-workflows-and-tools/md5sum"));
    }

    @Test
    public void info() {
        Client.main(new String[]{"workflow", "info", "--entry", entryPath, "--config", ResourceHelpers.resourceFilePath("config_file2.txt")});
        Assert.assertTrue(systemOutRule.getLog().contains("git@github.com:DockstoreTestUser2/test-workflows-and-tools.git"));
        Assert.assertTrue(systemOutRule.getLog().contains(entryPath));
    }

    @Test
    public void CWL() {
        Client.main(new String[]{"workflow", DescriptorType.CWL.toString(), "--entry", entryPathWithVersion, "--config", ResourceHelpers.resourceFilePath("config_file2.txt")});
        Assert.assertTrue(systemOutRule.getLog().contains("label: Simple md5sum tool"));
    }

    @Test
    public void WDL() {
        systemExit.expectSystemExitWithStatus(API_ERROR);
        systemExit.checkAssertionAfterwards(() -> Assert.assertTrue(systemOutRule.getLog().contains("Invalid version")));
        Client.main(new String[]{"workflow", DescriptorType.WDL.toString(), "--entry", entryPathWithVersion, "--config", ResourceHelpers.resourceFilePath("config_file2.txt")});
    }

    @Test
    public void refresh() {
        systemExit.expectSystemExitWithStatus(COMMAND_ERROR);
        systemExit.checkAssertionAfterwards(() -> Assert.assertTrue(systemOutRule.getLog().contains("GitHub Apps entries cannot be refreshed")));
        Client.main(new String[]{"workflow", "refresh", "--entry", entryPath, "--config", ResourceHelpers.resourceFilePath("config_file2.txt")});
    }

    @Test
    public void label() {
        Client.main(new String[]{"workflow", "label", "--add", "potato", "--entry", entryPath, "--config", ResourceHelpers.resourceFilePath("config_file2.txt")});
        Assert.assertTrue(systemOutRule.getLog().contains("The workflow has the following labels:"));
        systemOutRule.clearLog();
        Client.main(new String[]{"workflow", "label", "--remove", "potato", "--entry", entryPath, "--config", ResourceHelpers.resourceFilePath("config_file2.txt")});
        Assert.assertTrue(systemOutRule.getLog().contains("The workflow has no labels"));
    }

    @Test
    public void star() {
        Client.main(new String[]{"workflow", "star", "--entry", entryPath, "--config", ResourceHelpers.resourceFilePath("config_file2.txt")});
        Assert.assertTrue(systemOutRule.getLog().contains("Successfully starred github.com/DockstoreTestUser2/test-workflows-and-tools/md5sum"));
        systemOutRule.clearLog();
        Client.main(new String[]{"workflow", "star", "--unstar", "--entry", entryPath, "--config", ResourceHelpers.resourceFilePath("config_file2.txt")});
        Assert.assertTrue(systemOutRule.getLog().contains("Successfully unstarred github.com/DockstoreTestUser2/test-workflows-and-tools/md5sum"));
    }

    @Test
    public void testParameter() {
        systemExit.expectSystemExitWithStatus(COMMAND_ERROR);
        systemExit.checkAssertionAfterwards(() -> Assert.assertTrue(systemOutRule.getLog().contains("Cannot update test parameter files of GitHub App entries")));
        Client.main(new String[]{"workflow", "test_parameter", "--version", "main", "--add", "test.json", "--entry", entryPath, "--config", ResourceHelpers.resourceFilePath("config_file2.txt")});
    }

    // TODO: add assertions
    @Test
    public void convert() {
        Client.main(new String[]{"workflow", "convert", "entry2json", "--entry", entryPathWithVersion, "--config", ResourceHelpers.resourceFilePath("config_file2.txt")});
    }

    // TODO: add assertions
    @Test
    public void launch() throws ApiException {
        Client.main(new String[]{"workflow", "launch", "--entry", entryPathWithVersion, "--json", ResourceHelpers.resourceFilePath("md5sum_cwl.json"), "--script", "--config", ResourceHelpers.resourceFilePath("config_file2.txt")});
    }

    // TODO: add assertions
    @Test
    public void download() {
        Client.main(new String[]{"workflow", "download", "--entry", entryPathWithVersion, "--config", ResourceHelpers.resourceFilePath("config_file2.txt")});
        Client.main(new String[]{"workflow", "download", "--entry", entryPathWithVersion, "--zip", "--config", ResourceHelpers.resourceFilePath("config_file2.txt")});
    }

    @Ignore("TODO: Possibly covered by other tests")
    @Test
    public void wes() {
        Client.main(new String[]{"workflow", "wes"});
    }

    @Ignore("Unrelated")
    @Test
    public void manual_publish() {
        Client.main(new String[]{"workflow", "search"});
    }

    @Test
    public void update_workflow() {
        systemExit.expectSystemExitWithStatus(COMMAND_ERROR);
        systemExit.checkAssertionAfterwards(() -> Assert.assertTrue(systemOutRule.getLog().contains("Command not supported for GitHub App entries")));
        Client.main(new String[]{"workflow", "update_workflow", "--entry", entryPath, "--config", ResourceHelpers.resourceFilePath("config_file2.txt")});
    }

    @Test
    public void version_tag() {
        systemExit.expectSystemExitWithStatus(COMMAND_ERROR);
        systemExit.checkAssertionAfterwards(() -> Assert.assertTrue(systemOutRule.getLog().contains("Command not supported for GitHub App entries")));
        Client.main(new String[]{"workflow", "version_tag", "--name", "main", "--entry", entryPath, "--config", ResourceHelpers.resourceFilePath("config_file2.txt")});
    }

    @Test
    public void restub() {
        systemExit.expectSystemExitWithStatus(COMMAND_ERROR);
        systemExit.checkAssertionAfterwards(() -> Assert.assertTrue(systemOutRule.getLog().contains("Command not supported for GitHub App entries")));
        Client.main(new String[]{"workflow", "restub", "--entry", entryPath, "--config", ResourceHelpers.resourceFilePath("config_file2.txt")});
    }

    private void publishWorkflow() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        final WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        Workflow workflowByPath = workflowApi.getWorkflowByPath(entryPath, WorkflowSubClass.APPTOOL.toString(), null);
        final PublishRequest publishRequest = SwaggerUtility.createPublishRequest(true);
        workflowApi.publish(workflowByPath.getId(), publishRequest);
    }

}
