package io.dockstore.client.cli;

import io.dockstore.common.CLICommonTestUtilities;
import io.dockstore.common.SourceControl;
import io.dropwizard.testing.ResourceHelpers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

import static io.dockstore.client.cli.Client.CONFIG;
import static io.dockstore.client.cli.Client.SCRIPT_FLAG;
import static io.dockstore.client.cli.Client.WORKFLOW;
import static io.dockstore.client.cli.nested.AbstractEntryClient.MANUAL_PUBLISH;
import static io.dockstore.client.cli.nested.AbstractEntryClient.PUBLISH;
import static io.dockstore.client.cli.nested.AbstractEntryClient.STAR;
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
            new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, MANUAL_PUBLISH, "--repository",
                "hello-dockstore-workflow", "--organization", "DockstoreTestUser2", "--git-version-control", "github", "--workflow-name",
                "testname", "--workflow-path", "/Dockstore.wdl", "--descriptor-type", "wdl", SCRIPT_FLAG });
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, PUBLISH, "--entry",
            SourceControl.GITHUB + "/DockstoreTestUser2/hello-dockstore-workflow/testname", "--pub", SCRIPT_FLAG });
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, STAR, "--entry",
            SourceControl.GITHUB + "/DockstoreTestUser2/hello-dockstore-workflow/testname", SCRIPT_FLAG });
        assertTrue(systemOutRule.getText().contains("Successfully starred"));
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, STAR, "--unstar", "--entry",
            SourceControl.GITHUB + "/DockstoreTestUser2/hello-dockstore-workflow/testname", SCRIPT_FLAG });
        assertTrue(systemOutRule.getText().contains("Successfully unstarred"));
    }
}
