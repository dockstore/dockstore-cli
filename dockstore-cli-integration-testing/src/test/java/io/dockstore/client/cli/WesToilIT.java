package io.dockstore.client.cli;

import io.dockstore.client.cli.nested.WesTests;
import io.dockstore.common.FlushingSystemErrRule;
import io.dockstore.common.FlushingSystemOutRule;
import io.dropwizard.testing.ResourceHelpers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.assertTrue;

@Category(WesTests.class)
class WesToilIT {
    public static final String TOIL_CONFIG = ResourceHelpers.resourceFilePath("wesIt/config_toil");
    public static final int WAIT_COUNT = 10; // The number of times we will check on a workflow's run status before automatically failing
    public static final int WAIT_ITER_TIME_MILLI = 10000; // The time between each check of a workflow run status

    public static final String RUN_ID_PATTERN_PREFIX = "Launched WES run with id:";
    public static final String COMPLETED_STATE = "\"state\" : \"COMPLETE\"";
    public static final String EXECUTOR_ERROR_STATE = "\"state\" : \"EXECUTOR_ERROR\"";
    public static final String CANCELED_STATE = "\"state\" : \"CANCELED\"";

    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();
    @Rule
    public final SystemErrRule systemErrRule = new FlushingSystemErrRule().enableLog().muteForSuccessfulTests();
    @Rule
    public final SystemOutRule systemOutRule = new FlushingSystemOutRule().enableLog().muteForSuccessfulTests();

    /**
     * Searches for a runId in the provided string using a pattern printed by the CLI during a launch. Only needed for verbose outputs.
     * @param runLog The String to search for the runId in
     * @return String run ID
     */
    private String findWorkflowIdFromVerboseLaunch(String runLog) {
        // This is pretty ugly, but we don't present the workflow ID in an clean format.
        int runIdIndex = runLog.indexOf(RUN_ID_PATTERN_PREFIX);
        int newlineFromRunId = runLog.indexOf("\n", runIdIndex);
        return runLog.substring(runIdIndex + RUN_ID_PATTERN_PREFIX.length(), newlineFromRunId).trim();
    }

    /**
     * When not using verbose logging, the runId should just be "{ID}\n"
     * @param runLog The String to search for the runId in
     * @return String run ID
     */
    private String findWorkflowIdFromDefaultLaunch(String runLog) {
        return runLog.trim();
    }

    /**
     * Waits for the workflow corresponding to the runId parameter to reach the 'COMPLETE' state.
     * @param runId ID of a workflow
     * @return true if the workflow reaches the 'COMPLETE' state in time, false otherwise
     * @throws InterruptedException
     */
    private boolean waitForWorkflowState(String runId, String state) throws InterruptedException {
        for (int i = 0; i < WAIT_COUNT; i++) {

            // Check on a workflow's run status
            String[] commandStatementStatus = new String[]{ "workflow", "wes", "status",
                "--config", TOIL_CONFIG,
                "--id", runId
            };
            Client.main(commandStatementStatus);

            // wait for the provided state to be printed
            if (systemOutRule.getLog().contains(state)) {
                return true;
            }

            Thread.sleep(WAIT_ITER_TIME_MILLI);
        }

        return false;
    }

    @Test
    public void testBasicLaunch1() throws InterruptedException {
        String[] commandStatementRun = new String[]{ "workflow", "wes", "launch",
            "--config", TOIL_CONFIG,
            "--entry", "github.com/dockstore-testing/wes-testing/single-descriptor-with-input:main",
            "--json", ResourceHelpers.resourceFilePath("wesIt/w1_test.json"),
            "--inline-workflow"
        };
        Client.main(commandStatementRun);

        final String runId = findWorkflowIdFromDefaultLaunch(systemOutRule.getLog());
        final boolean isSuccessful = waitForWorkflowState(runId, COMPLETED_STATE);

        assertTrue("The workflow did not succeed in time.", isSuccessful);
    }

    @Test
    public void testBasicLaunch2() throws InterruptedException {
        String[] commandStatementRun = new String[]{ "workflow", "wes", "launch",
            "--config", TOIL_CONFIG,
            "--entry", "github.com/dockstore-testing/wes-testing/multi-descriptor-no-input:main",
            "--json", ResourceHelpers.resourceFilePath("wesIt/w1_test.json"), // Toil requires workflow_params to be provided
            "--inline-workflow",
            "--verbose"
        };
        Client.main(commandStatementRun);
        assertTrue("A helper command should be printed to stdout when a workflow is successfully started", systemOutRule.getLog().contains("dockstore workflow wes status --id"));

        final String runId = findWorkflowIdFromVerboseLaunch(systemOutRule.getLog());
        final boolean isError = waitForWorkflowState(runId, EXECUTOR_ERROR_STATE);

        // Toil does not support imports at this time, so multi-descriptor workflows should fail.
        assertTrue("The workflow was not supposed to succeed.", isError);

    }

    @Test
    public void testBasicLaunch3() throws InterruptedException {
        String[] commandStatementRun = new String[]{ "workflow", "wes", "launch",
            "--config", TOIL_CONFIG,
            "--entry", "github.com/dockstore-testing/wes-testing/single-descriptor-no-input:main",
            "--json", ResourceHelpers.resourceFilePath("wesIt/w1_test.json"), // Toil requires workflow_params to be provided
            "--inline-workflow"
        };
        Client.main(commandStatementRun);

        final String runId = findWorkflowIdFromDefaultLaunch(systemOutRule.getLog());
        final boolean isSuccessful = waitForWorkflowState(runId, COMPLETED_STATE);

        assertTrue("The workflow did not succeed in time.", isSuccessful);
    }

    @Test
    public void testCancel() throws InterruptedException {
        String[] commandStatementRun = new String[]{ "workflow", "wes", "launch",
            "--config", TOIL_CONFIG,
            "--entry", "github.com/dockstore-testing/wes-testing/single-descriptor-with-input:main",
            "--json", ResourceHelpers.resourceFilePath("wesIt/w1_test.json"),
            "--inline-workflow"
        };
        Client.main(commandStatementRun);

        final String runId = findWorkflowIdFromDefaultLaunch(systemOutRule.getLog());

        String[] commandStatementCancel = new String[]{ "workflow", "wes", "cancel",
            "--config", TOIL_CONFIG,
            "--id", runId
        };
        Client.main(commandStatementCancel);

        final boolean isCanceled = waitForWorkflowState(runId, CANCELED_STATE);

        assertTrue("The workflow did not cancel in time.", isCanceled);
    }

    @Test
    public void testDirectoryAttachment() throws InterruptedException {
        // These tests pass, and the files provisioned by Toil look correct, but the outputs are not.
        String[] commandStatementRun = new String[]{ "workflow", "wes", "launch",
            "--config", TOIL_CONFIG,
            "--entry", "github.com/dockstore-testing/wes-testing/single-descriptor-nested-input:main",
            "--json", ResourceHelpers.resourceFilePath("wesIt/w4_1_test.json"),
            "-a", ResourceHelpers.resourceFilePath("wesIt/w4_nested"),
            "--inline-workflow",
            "--verbose"
        };
        Client.main(commandStatementRun);
        assertTrue("A helper command should be printed to stdout when a workflow is successfully started", systemOutRule.getLog().contains("dockstore workflow wes status --id"));

        final String runId = findWorkflowIdFromVerboseLaunch(systemOutRule.getLog());
        final boolean isSuccessful = waitForWorkflowState(runId, COMPLETED_STATE);

        assertTrue("The workflow did not succeed in time.", isSuccessful);
    }

    @Test
    public void testRelativeFileAttachment() throws InterruptedException {
        // These tests pass, and the files provisioned by Toil look correct, but the outputs are not.
        String[] commandStatementRun = new String[]{ "workflow", "wes", "launch",
            "--config", TOIL_CONFIG,
            "--entry", "github.com/dockstore-testing/wes-testing/single-descriptor-nested-input:main",
            "--json", ResourceHelpers.resourceFilePath("wesIt/w4_1_test_relative.json"),
            "-a", "src/test/resources/wesIt/w4_nested/w4_2_test.txt",
            "--inline-workflow",
            "--verbose"
        };
        Client.main(commandStatementRun);
        assertTrue("A helper command should be printed to stdout when a workflow is successfully started", systemOutRule.getLog().contains("dockstore workflow wes status --id"));

        final String runId = findWorkflowIdFromVerboseLaunch(systemOutRule.getLog());
        final boolean isSuccessful = waitForWorkflowState(runId, COMPLETED_STATE);

        assertTrue("The workflow did not succeed in time.", isSuccessful);
    }

    @Test
    public void testRelativeDirectoryAttachment() throws InterruptedException {
        // When we pass the relative path to a directory, the CLI will convert it to an absolute path and upload
        // all nested files relative to said absolute path. This means that the attachment JSON will be at the wrong path.
        String[] commandStatementRun = new String[]{ "workflow", "wes", "launch",
            "--config", TOIL_CONFIG,
            "--entry", "github.com/dockstore-testing/wes-testing/single-descriptor-nested-input:main",
            "--json", ResourceHelpers.resourceFilePath("wesIt/w4_1_test_relative.json"),
            "--attach", "src/test/resources/wesIt/w4_nested",
            "--inline-workflow",
            "--verbose"
        };
        Client.main(commandStatementRun);
        assertTrue("A helper command should be printed to stdout when a workflow is successfully started", systemOutRule.getLog().contains("dockstore workflow wes status --id"));

        final String runId = findWorkflowIdFromVerboseLaunch(systemOutRule.getLog());
        final boolean isSuccessful = waitForWorkflowState(runId, EXECUTOR_ERROR_STATE);

        assertTrue("The workflow did not succeed in time.", isSuccessful);
    }

    @Test
    public void testRelativeDirectoryAttachment2() throws InterruptedException {
        // When we pass the relative path to a directory, the CLI will convert it to an absolute path and upload
        // all nested files relative to said absolute path. This means that the attachment JSON will be at the wrong path.
        String[] commandStatementRun = new String[]{ "workflow", "wes", "launch",
            "--config", TOIL_CONFIG,
            "--entry", "github.com/dockstore-testing/wes-testing/single-descriptor-nested-input:main",
            "--json", ResourceHelpers.resourceFilePath("wesIt/w4_1_test.json"),
            "-a", "src/test/resources/wesIt/w4_nested",
            "--inline-workflow",
            "--verbose"
        };
        Client.main(commandStatementRun);
        assertTrue("A helper command should be printed to stdout when a workflow is successfully started", systemOutRule.getLog().contains("dockstore workflow wes status --id"));

        final String runId = findWorkflowIdFromVerboseLaunch(systemOutRule.getLog());
        final boolean isSuccessful = waitForWorkflowState(runId, COMPLETED_STATE);

        assertTrue("The workflow did not succeed in time.", isSuccessful);
    }

    @Test
    public void testComplexNestedDirectoryAttachments() throws InterruptedException {
        // These tests pass, and the files provisioned by Toil look correct, but the outputs are not.
        String[] commandStatementRun = new String[]{ "workflow", "wes", "launch",
            "--config", TOIL_CONFIG,
            "--entry", "github.com/dockstore-testing/wes-testing/single-descriptor-complex-nested-input:main",
            "--json", ResourceHelpers.resourceFilePath("wesIt/w5_1_test.json"),
            "--attach", ResourceHelpers.resourceFilePath("wesIt/w5_nested"),
            "--inline-workflow",
            "--verbose"
        };
        Client.main(commandStatementRun);
        assertTrue("A helper command should be printed to stdout when a workflow is successfully started", systemOutRule.getLog().contains("dockstore workflow wes status --id"));

        final String runId = findWorkflowIdFromVerboseLaunch(systemOutRule.getLog());
        final boolean isSuccessful = waitForWorkflowState(runId, COMPLETED_STATE);

        assertTrue("The workflow did not succeed in time.", isSuccessful);
    }
}
