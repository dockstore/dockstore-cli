package io.dockstore.client.cli;

import io.dockstore.common.CLICommonTestUtilities;
import io.dockstore.common.SourceControl;
import io.dropwizard.testing.ResourceHelpers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

import static io.dockstore.client.cli.Client.WORKFLOW;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author gluu
 * @since 2020-02-28
 */
class StarIT extends BaseIT {

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
    void starWorkflow() {
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, "manual_publish", "--repository",
                "hello-dockstore-workflow", "--organization", "DockstoreTestUser2", "--git-version-control", "github", "--workflow-name",
                "testname", "--workflow-path", "/Dockstore.wdl", "--descriptor-type", "wdl", "--script" });
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, "publish", "--entry",
            SourceControl.GITHUB + "/DockstoreTestUser2/hello-dockstore-workflow/testname", "--pub", "--script" });
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, "star", "--entry",
            SourceControl.GITHUB + "/DockstoreTestUser2/hello-dockstore-workflow/testname", "--script" });
        assertTrue(systemOutRule.getText().contains("Successfully starred"));
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, "star", "--unstar", "--entry",
            SourceControl.GITHUB + "/DockstoreTestUser2/hello-dockstore-workflow/testname", "--script" });
        assertTrue(systemOutRule.getText().contains("Successfully unstarred"));
    }
}
