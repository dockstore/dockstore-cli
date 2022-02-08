package io.dockstore.client.cli;

import io.dropwizard.testing.ResourceHelpers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@Category(WesToilIT.class)
public class WesToilIT {

    public static final String TOIL_CONFIG = ResourceHelpers.resourceFilePath("wesIt/config_toil");
    public static final int WAIT_COUNT = 10; // The number of times we will check on a workflow's run status before automatically failing
    public static final int WAIT_ITER_TIME_MILLI = 10000; // The time between each check of a workflow run status

    public static final String RUN_ID_PATTERN_PREFIX = "Launched WES run with id:";
    public static final String COMPLETED_STATE = "state: COMPLETE";

    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();
    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog();
    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog();

    @Test
    public void testBasicLaunch1() throws InterruptedException {
        String[] commandStatementRun = new String[]{ "workflow", "wes", "launch",
            "--config", TOIL_CONFIG,
            "--entry", "github.com/dockstore-testing/wes-testing/single-descriptor-with-input:main",
            "--json", ResourceHelpers.resourceFilePath("wesIt/w1_test.json"),
            "--inline-workflow"
        };
        Client.main(commandStatementRun);
        assertTrue("A helper command should be printed to stdout when a workflow is successfully started", systemOutRule.getLog().contains("dockstore workflow wes status --id"));

        // This is pretty ugly, but we don't present the workflow ID in an clean format.
        int runIdIndex = systemOutRule.getLog().indexOf(RUN_ID_PATTERN_PREFIX);
        int newlineFromRunId = systemOutRule.getLog().indexOf("\n", runIdIndex);
        String runId = systemOutRule.getLog().substring(runIdIndex + RUN_ID_PATTERN_PREFIX.length(), newlineFromRunId).trim();

        for (int i = 0; i < WAIT_COUNT; i++) {

            // Check on a workflow's run status
            String[] commandStatementStatus = new String[]{ "workflow", "wes", "status",
                "--config", TOIL_CONFIG,
                "--id", runId
            };
            Client.main(commandStatementStatus);

            // If the returned state is completed, we succeeded the test so return
            if (systemOutRule.getLog().contains(COMPLETED_STATE)) {
                return;
            }

            Thread.sleep(WAIT_ITER_TIME_MILLI);
        }

        fail("The workflow did not succeed in time.");
    }
}
