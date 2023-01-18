package io.dockstore.client.cli;

import io.dockstore.common.CLICommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.FlushingSystemErrRule;
import io.dockstore.common.FlushingSystemOutRule;
import io.dockstore.common.ToilCompatibleTest;
import io.dockstore.common.WorkflowTest;
import io.dropwizard.testing.ResourceHelpers;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.experimental.categories.Category;

import static io.dockstore.client.cli.Client.API_ERROR;
import static io.dockstore.client.cli.nested.AbstractEntryClient.CHECKSUM_NULL_MESSAGE;
import static io.dockstore.client.cli.nested.AbstractEntryClient.CHECKSUM_VALIDATED_MESSAGE;
import static org.junit.Assert.assertTrue;

@Category({ ConfidentialTest.class, WorkflowTest.class })
public class LaunchWorkflowIT extends BaseIT {

    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();

    @Rule
    public final SystemOutRule systemOutRule = new FlushingSystemOutRule().enableLog().muteForSuccessfulTests();


    @Rule
    public final SystemErrRule systemErrRule = new FlushingSystemErrRule().enableLog().muteForSuccessfulTests();

    @Before
    @Override
    public void resetDBBetweenTests() throws Exception {
        CLICommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
    }



    /**
     * Tests that a developer can launch a CWL workflow locally, instead of getting files from Dockstore
     * Todo: Works locally but not on Travis.  This is due the the relative position of the file paths in the input JSON
     */
    @Ignore
    public void testLocalLaunchCWL() {
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "launch", "--local-entry",
            ResourceHelpers.resourceFilePath("filtercount.cwl.yaml"), "--json", ResourceHelpers.resourceFilePath("filtercount-job.json"),
            "--script" });
    }

    /**
     * This tests that attempting to launch a workflow locally, where no file exists, an IOError will occur
     */
    @Test
    public void testLocalLaunchCWLNoFile() {
        systemExit.expectSystemExitWithStatus(Client.ENTRY_NOT_FOUND);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "launch", "--local-entry",
            "imnotreal.cwl", "--json", "imnotreal-job.json", "--script" });
    }


    /**
     * This tests that attempting to launch a WDL workflow locally, where no file exists, an IOError will occur
     */
    @Test
    public void testLocalLaunchWDLNoFile() {
        systemExit.expectSystemExitWithStatus(Client.ENTRY_NOT_FOUND);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "launch", "--local-entry",
            "imnotreal.wdl", "--json", "imnotreal-job.json", "--script" });
    }

    /**
     * This tests that attempting to launch a workflow remotely, where no file exists, an APIError will occur
     */
    @Test
    @Category(ToilCompatibleTest.class)
    public void testRemoteLaunchCWLNoFile() {
        systemExit.expectSystemExitWithStatus(Client.ENTRY_NOT_FOUND);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "launch", "--entry",
            "imnotreal.cwl", "--json", "imnotreal-job.json", "--script" });
    }

    /**
     * This tests that attempting to launch a WDL workflow remotely, where no file exists, an APIError will occur
     */
    @Test
    public void testRemoteLaunchWDLNoFile() {
        systemExit.expectSystemExitWithStatus(Client.ENTRY_NOT_FOUND);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "launch", "--entry",
            "imnotreal.wdl", "--json", "imnotreal-job.json", "--script" });
    }

    /**
     * Tests that a developer can launch a WDL workflow locally, instead of getting files from Dockstore
     */
    @Test
    public void testLocalLaunchWDL() {
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "launch", "--local-entry",
            ResourceHelpers.resourceFilePath("wdl.wdl"), "--json", ResourceHelpers.resourceFilePath("wdl.json"), "--script" });
    }


    /**
     * Tests that a developer can launch a WDL workflow with a File input being a directory
     */
    @Test
    public void testLocalLaunchWDLWithDir() {
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "launch", "--local-entry",
            ResourceHelpers.resourceFilePath("directorytest.wdl"), "--json", ResourceHelpers.resourceFilePath("directorytest.json"),
            "--script" });
    }


    /**
     * Tests that a developer can launch a WDL workflow locally, with an HTTP/HTTPS URL
     * TODO: cromwell needs to support HTTP/HTTPS file prov
     */
    @Ignore
    public void testLocalLaunchWDLImportHTTP() {
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "launch", "--local-entry",
            ResourceHelpers.resourceFilePath("wdlhttpimport.wdl"), "--json", ResourceHelpers.resourceFilePath("wdlhttp.json"),
            "--script" });
    }

    /**
     * Tests that a only Github, Gitlab and bitbucket http/https imports are valid
     */
    @Test
    public void testLocalLaunchWDLImportIncorrectHTTP() {
        systemExit.expectSystemExitWithStatus(API_ERROR);
        // TODO: looking at the system log doesn't seem to work deterministically with checkAssertionAfterwards
        // re-enable and test with versions of system rules newer than 1.17.1
        //        systemExit.checkAssertionAfterwards(
        //            () -> assertTrue("Output should indicate issues with WDL imports and exit", systemOutRule.getLog().contains("Could not get WDL imports")));
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "launch", "--local-entry",
            ResourceHelpers.resourceFilePath("wdlincorrecthttp.wdl"), "--json", ResourceHelpers.resourceFilePath("wdl.json"), "--script" });
    }

    /**
     * This tests that a published workflow, when launched, attempts to validate descriptor checksums.
     * That cases that are tested: Checksum matches, checksum doesn't match, checksum doesn't exist
     *
     */
    @Test
    public void launchWorkflowChecksumValidation() {
        // register and publish a workflow
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "manual_publish", "--repository",
                "md5sum-checker", "--organization", "DockstoreTestUser2", "--git-version-control", "github",
                "--workflow-path", "/checker-workflow-wrapping-tool.cwl", "--descriptor-type", "cwl", "--workflow-name", "checksumTester", "--script" });

        // ensure checksum validation is acknowledged, and no null checksums were discovered
        systemOutRule.clearLog();
        Client.main(new String[] {"--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "launch", "--entry",
            "github.com/DockstoreTestUser2/md5sum-checker/checksumTester", "--json", ResourceHelpers.resourceFilePath("md5sum_cwl.json"), "--script" });
        assertTrue("Output should indicate that checksums have been validated",
            systemOutRule.getLog().contains(CHECKSUM_VALIDATED_MESSAGE) && !systemOutRule.getLog().contains(CHECKSUM_NULL_MESSAGE));

        // unpublish the workflow
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--unpub", "--entry", "github.com/DockstoreTestUser2/md5sum-checker/checksumTester"});

        // ensure checksum validation is acknowledged for the unpublished workflow, and no null checksums were discovered
        systemOutRule.clearLog();
        Client.main(new String[] {"--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "launch", "--entry",
            "github.com/DockstoreTestUser2/md5sum-checker/checksumTester", "--json", ResourceHelpers.resourceFilePath("md5sum_cwl.json"), "--script" });
        assertTrue("Output should indicate that checksums have been validated",
            systemOutRule.getLog().contains(CHECKSUM_VALIDATED_MESSAGE) && !systemOutRule.getLog().contains(CHECKSUM_NULL_MESSAGE));
    }


}
