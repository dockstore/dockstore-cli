package io.dockstore.client.cli;

import io.dockstore.common.CLICommonTestUtilities;
import io.dockstore.common.SourceControl;
import io.dropwizard.testing.ResourceHelpers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author gluu
 * @since 2020-02-28
 */
public class StarIT extends BaseIT {

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
    public void starWorkflow() {
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "manual_publish", "--repository",
                "hello-dockstore-workflow", "--organization", "DockstoreTestUser2", "--git-version-control", "github", "--workflow-name",
                "testname", "--workflow-path", "/Dockstore.wdl", "--descriptor-type", "wdl", "--script" });
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--entry",
            SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow/testname", "--pub", "--script" });
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "star", "--entry",
            SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow/testname", "--script" });
        assertTrue(systemOutRule.getText().contains("Successfully starred"));
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "star", "--unstar", "--entry",
            SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow/testname", "--script" });
        assertTrue(systemOutRule.getText().contains("Successfully unstarred"));
    }
}
