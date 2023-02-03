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

import static io.dockstore.client.cli.ArgumentUtility.LAUNCH;
import static io.dockstore.client.cli.Client.CONFIG;
import static io.dockstore.client.cli.Client.WORKFLOW;
import static io.dockstore.client.cli.nested.AbstractEntryClient.CANCEL;
import static io.dockstore.client.cli.nested.AbstractEntryClient.STATUS;
import static io.dockstore.client.cli.nested.WesCommandParser.ATTACH;
import static io.dockstore.client.cli.nested.WesCommandParser.ENTRY;
import static io.dockstore.client.cli.nested.WesCommandParser.ID;
import static io.dockstore.client.cli.nested.WesCommandParser.INLINE_WORKFLOW;
import static io.dockstore.client.cli.nested.WesCommandParser.JSON;
import static io.dockstore.client.cli.nested.WesCommandParser.VERBOSE;
import static io.github.collaboratory.cwl.CWLClient.WES;
import static java.lang.String.join;
import static org.junit.Assert.assertTrue;

@Category(WesTests.class)
public class WesToilIT {
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
            String[] commandStatementStatus = new String[]{ WORKFLOW, WES, STATUS,
                CONFIG, TOIL_CONFIG,
                ID, runId
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
        String[] commandStatementRun = new String[]{ WORKFLOW, WES, LAUNCH,
            CONFIG, TOIL_CONFIG,
            ENTRY, "github.com/dockstore-testing/wes-testing/single-descriptor-with-input:main",
            JSON, ResourceHelpers.resourceFilePath("wesIt/w1_test.json"),
            INLINE_WORKFLOW
        };
        Client.main(commandStatementRun);

        final String runId = findWorkflowIdFromDefaultLaunch(systemOutRule.getLog());
        final boolean isSuccessful = waitForWorkflowState(runId, COMPLETED_STATE);

        assertTrue("The workflow did not succeed in time.", isSuccessful);
    }

    @Test
    public void testBasicLaunch2() throws InterruptedException {
        String[] commandStatementRun = new String[]{ WORKFLOW, WES, LAUNCH,
            CONFIG, TOIL_CONFIG,
            ENTRY, "github.com/dockstore-testing/wes-testing/multi-descriptor-no-input:main",
            JSON, ResourceHelpers.resourceFilePath("wesIt/w1_test.json"), // Toil requires workflow_params to be provided
            INLINE_WORKFLOW,
            VERBOSE
        };
        Client.main(commandStatementRun);
        assertTrue("A helper command should be printed to stdout when a workflow is successfully started", systemOutRule.getLog().contains(join(" ", "dockstore", WORKFLOW, WES,  STATUS, ID)));

        final String runId = findWorkflowIdFromVerboseLaunch(systemOutRule.getLog());
        final boolean isError = waitForWorkflowState(runId, EXECUTOR_ERROR_STATE);

        // Toil does not support imports at this time, so multi-descriptor workflows should fail.
        assertTrue("The workflow was not supposed to succeed.", isError);

    }

    @Test
    public void testBasicLaunch3() throws InterruptedException {
        String[] commandStatementRun = new String[]{ WORKFLOW, WES, LAUNCH,
            CONFIG, TOIL_CONFIG,
            ENTRY, "github.com/dockstore-testing/wes-testing/single-descriptor-no-input:main",
            JSON, ResourceHelpers.resourceFilePath("wesIt/w1_test.json"), // Toil requires workflow_params to be provided
            INLINE_WORKFLOW
        };
        Client.main(commandStatementRun);

        final String runId = findWorkflowIdFromDefaultLaunch(systemOutRule.getLog());
        final boolean isSuccessful = waitForWorkflowState(runId, COMPLETED_STATE);

        assertTrue("The workflow did not succeed in time.", isSuccessful);
    }

    @Test
    public void testCancel() throws InterruptedException {
        String[] commandStatementRun = new String[]{ WORKFLOW, WES, LAUNCH,
            CONFIG, TOIL_CONFIG,
            ENTRY, "github.com/dockstore-testing/wes-testing/single-descriptor-with-input:main",
            JSON, ResourceHelpers.resourceFilePath("wesIt/w1_test.json"),
            INLINE_WORKFLOW
        };
        Client.main(commandStatementRun);

        final String runId = findWorkflowIdFromDefaultLaunch(systemOutRule.getLog());

        String[] commandStatementCancel = new String[]{ WORKFLOW, WES, CANCEL,
            CONFIG, TOIL_CONFIG,
            ID, runId
        };
        Client.main(commandStatementCancel);

        final boolean isCanceled = waitForWorkflowState(runId, CANCELED_STATE);

        assertTrue("The workflow did not " + CANCEL + " in time.", isCanceled);
    }

    @Test
    public void testDirectoryAttachment() throws InterruptedException {
        // These tests pass, and the files provisioned by Toil look correct, but the outputs are not.
        String[] commandStatementRun = new String[]{ WORKFLOW, WES, LAUNCH,
            CONFIG, TOIL_CONFIG,
            ENTRY, "github.com/dockstore-testing/wes-testing/single-descriptor-nested-input:main",
            JSON, ResourceHelpers.resourceFilePath("wesIt/w4_1_test.json"),
            "-a", ResourceHelpers.resourceFilePath("wesIt/w4_nested"),
            INLINE_WORKFLOW,
            VERBOSE
        };
        Client.main(commandStatementRun);
        assertTrue("A helper command should be printed to stdout when a workflow is successfully started", systemOutRule.getLog().contains(join(" ", "dockstore workflow", WES, STATUS, ID)));

        final String runId = findWorkflowIdFromVerboseLaunch(systemOutRule.getLog());
        final boolean isSuccessful = waitForWorkflowState(runId, COMPLETED_STATE);

        assertTrue("The workflow did not succeed in time.", isSuccessful);
    }

    @Test
    public void testRelativeFileAttachment() throws InterruptedException {
        // These tests pass, and the files provisioned by Toil look correct, but the outputs are not.
        String[] commandStatementRun = new String[]{ WORKFLOW, WES, LAUNCH,
            CONFIG, TOIL_CONFIG,
            ENTRY, "github.com/dockstore-testing/wes-testing/single-descriptor-nested-input:main",
            JSON, ResourceHelpers.resourceFilePath("wesIt/w4_1_test_relative.json"),
            "-a", "src/test/resources/wesIt/w4_nested/w4_2_test.txt",
            INLINE_WORKFLOW,
            VERBOSE
        };
        Client.main(commandStatementRun);
        assertTrue("A helper command should be printed to stdout when a workflow is successfully started", systemOutRule.getLog().contains(join(" ", "dockstore workflow", WES, STATUS, ID)));

        final String runId = findWorkflowIdFromVerboseLaunch(systemOutRule.getLog());
        final boolean isSuccessful = waitForWorkflowState(runId, COMPLETED_STATE);

        assertTrue("The workflow did not succeed in time.", isSuccessful);
    }

    @Test
    public void testRelativeDirectoryAttachment() throws InterruptedException {
        // When we pass the relative path to a directory, the CLI will convert it to an absolute path and upload
        // all nested files relative to said absolute path. This means that the attachment JSON will be at the wrong path.
        String[] commandStatementRun = new String[]{ WORKFLOW, WES, LAUNCH,
            CONFIG, TOIL_CONFIG,
            ENTRY, "github.com/dockstore-testing/wes-testing/single-descriptor-nested-input:main",
            JSON, ResourceHelpers.resourceFilePath("wesIt/w4_1_test_relative.json"),
            ATTACH, "src/test/resources/wesIt/w4_nested",
            INLINE_WORKFLOW,
            VERBOSE
        };
        Client.main(commandStatementRun);
        assertTrue("A helper command should be printed to stdout when a workflow is successfully started", systemOutRule.getLog().contains(join(" ", "dockstore workflow", WES, STATUS, ID)));

        final String runId = findWorkflowIdFromVerboseLaunch(systemOutRule.getLog());
        final boolean isSuccessful = waitForWorkflowState(runId, EXECUTOR_ERROR_STATE);

        assertTrue("The workflow did not succeed in time.", isSuccessful);
    }

    @Test
    public void testRelativeDirectoryAttachment2() throws InterruptedException {
        // When we pass the relative path to a directory, the CLI will convert it to an absolute path and upload
        // all nested files relative to said absolute path. This means that the attachment JSON will be at the wrong path.
        String[] commandStatementRun = new String[]{ WORKFLOW, WES, LAUNCH,
            CONFIG, TOIL_CONFIG,
            ENTRY, "github.com/dockstore-testing/wes-testing/single-descriptor-nested-input:main",
            JSON, ResourceHelpers.resourceFilePath("wesIt/w4_1_test.json"),
            "-a", "src/test/resources/wesIt/w4_nested",
            INLINE_WORKFLOW,
            VERBOSE
        };
        Client.main(commandStatementRun);
        assertTrue("A helper command should be printed to stdout when a workflow is successfully started", systemOutRule.getLog().contains(join(" ", "dockstore workflow", WES, STATUS, ID)));

        final String runId = findWorkflowIdFromVerboseLaunch(systemOutRule.getLog());
        final boolean isSuccessful = waitForWorkflowState(runId, COMPLETED_STATE);

        assertTrue("The workflow did not succeed in time.", isSuccessful);
    }

    @Test
    public void testComplexNestedDirectoryAttachments() throws InterruptedException {
        // These tests pass, and the files provisioned by Toil look correct, but the outputs are not.
        String[] commandStatementRun = new String[]{ WORKFLOW, WES, LAUNCH,
            CONFIG, TOIL_CONFIG,
            ENTRY, "github.com/dockstore-testing/wes-testing/single-descriptor-complex-nested-input:main",
            JSON, ResourceHelpers.resourceFilePath("wesIt/w5_1_test.json"),
            ATTACH, ResourceHelpers.resourceFilePath("wesIt/w5_nested"),
            INLINE_WORKFLOW,
            VERBOSE
        };
        Client.main(commandStatementRun);
        assertTrue("A helper command should be printed to stdout when a workflow is successfully started", systemOutRule.getLog().contains(join(" ", "dockstore workflow", WES, STATUS, ID)));

        final String runId = findWorkflowIdFromVerboseLaunch(systemOutRule.getLog());
        final boolean isSuccessful = waitForWorkflowState(runId, COMPLETED_STATE);

        assertTrue("The workflow did not succeed in time.", isSuccessful);
    }
}
