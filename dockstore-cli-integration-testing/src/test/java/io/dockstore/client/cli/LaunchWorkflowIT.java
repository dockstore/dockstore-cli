package io.dockstore.client.cli;

import io.dockstore.common.CLICommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.ToilCompatibleTest;
import io.dockstore.common.WorkflowTest;
import io.dropwizard.testing.ResourceHelpers;
import org.junit.experimental.categories.Category;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

import static io.dockstore.client.cli.ArgumentUtility.LAUNCH;
import static io.dockstore.client.cli.Client.CONFIG;
import static io.dockstore.client.cli.Client.SCRIPT_FLAG;
import static io.dockstore.client.cli.Client.WORKFLOW;
import static io.dockstore.client.cli.nested.AbstractEntryClient.CHECKSUM_NULL_MESSAGE;
import static io.dockstore.client.cli.nested.AbstractEntryClient.CHECKSUM_VALIDATED_MESSAGE;
import static io.dockstore.client.cli.nested.AbstractEntryClient.MANUAL_PUBLISH;
import static io.dockstore.client.cli.nested.AbstractEntryClient.PUBLISH;
import static io.dockstore.client.cli.nested.WesCommandParser.ENTRY;
import static io.dockstore.client.cli.nested.WesCommandParser.JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.org.webcompere.systemstubs.SystemStubs.catchSystemExit;

@Tag(ConfidentialTest.NAME)
@Tag(WorkflowTest.NAME)
class LaunchWorkflowIT extends BaseIT {

    @SystemStub
    public final SystemOut systemOutRule = new SystemOut();

    @SystemStub
    public final SystemErr systemErrRule = new SystemErr();

    @BeforeEach
    @Override
    public void resetDBBetweenTests() throws Exception {
        CLICommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
    }



    /**
     * Tests that a developer can launch a CWL workflow locally, instead of getting files from Dockstore
     * Todo: Works locally but not on Travis.  This is due the the relative position of the file paths in the input JSON
     */
    @Test
    @Disabled("broken on CI")
    void testLocalLaunchCWL() {
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, LAUNCH, "--local-entry",
            ResourceHelpers.resourceFilePath("filtercount.cwl.yaml"), JSON, ResourceHelpers.resourceFilePath("filtercount-job.json"),
            SCRIPT_FLAG });
    }

    /**
     * This tests that attempting to launch a workflow locally, where no file exists, an IOError will occur
     */
    @Test
    void testLocalLaunchCWLNoFile() throws Exception {
        int exitCode = catchSystemExit(() ->  Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, LAUNCH, "--local-entry",
                "imnotreal.cwl", JSON, "imnotreal-job.json", SCRIPT_FLAG }));
        assertEquals(Client.ENTRY_NOT_FOUND, exitCode);
    }


    /**
     * This tests that attempting to launch a WDL workflow locally, where no file exists, an IOError will occur
     */
    @Test
    void testLocalLaunchWDLNoFile() throws Exception {
        int exitCode = catchSystemExit(() -> Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, LAUNCH, "--local-entry",
                "imnotreal.wdl", JSON, "imnotreal-job.json", SCRIPT_FLAG }));
        assertEquals(Client.ENTRY_NOT_FOUND, exitCode);
    }

    /**
     * This tests that attempting to launch a workflow remotely, where no file exists, an APIError will occur
     */
    @Test
    @Category(ToilCompatibleTest.class)
    void testRemoteLaunchCWLNoFile() throws Exception {
        int exitCode = catchSystemExit(() -> Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, LAUNCH, ENTRY,
                "imnotreal.cwl", JSON, "imnotreal-job.json", SCRIPT_FLAG }));
        assertEquals(Client.ENTRY_NOT_FOUND, exitCode);
    }

    /**
     * This tests that attempting to launch a WDL workflow remotely, where no file exists, an APIError will occur
     */
    @Test
    void testRemoteLaunchWDLNoFile() throws Exception {
        int exitCode = catchSystemExit(() -> Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, LAUNCH, ENTRY,
                "imnotreal.wdl", JSON, "imnotreal-job.json", SCRIPT_FLAG }));
        assertEquals(Client.ENTRY_NOT_FOUND, exitCode);
    }

    /**
     * Tests that a developer can launch a WDL workflow locally, instead of getting files from Dockstore
     */
    @Test
    void testLocalLaunchWDL() {
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, LAUNCH, "--local-entry",
            ResourceHelpers.resourceFilePath("wdl.wdl"), JSON, ResourceHelpers.resourceFilePath("wdl.json"), SCRIPT_FLAG });
    }


    /**
     * Tests that a developer can launch a WDL workflow with a File input being a directory
     */
    @Test
    void testLocalLaunchWDLWithDir() {
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, LAUNCH, "--local-entry",
            ResourceHelpers.resourceFilePath("directorytest.wdl"), JSON, ResourceHelpers.resourceFilePath("directorytest.json"),
            SCRIPT_FLAG });
    }


    /**
     * Tests that a developer can launch a WDL workflow locally, with an HTTP/HTTPS URL
     * TODO: cromwell needs to support HTTP/HTTPS file prov
     */
    @Test
    @Disabled("cromwell needs to support HTTP/HTTPS file prov")
    void testLocalLaunchWDLImportHTTP() {
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, LAUNCH, "--local-entry",
            ResourceHelpers.resourceFilePath("wdlhttpimport.wdl"), JSON, ResourceHelpers.resourceFilePath("wdlhttp.json"),
            SCRIPT_FLAG });
    }

    /**
     * Tests that a only Github, Gitlab and bitbucket http/https imports are valid
     */
    @Test
    void testLocalLaunchWDLImportIncorrectHTTP() throws Exception {
        // TODO: looking at the system log doesn't seem to work deterministically with checkAssertionAfterwards
        // re-enable and test with versions of system rules newer than 1.17.1
        //        systemExit.checkAssertionAfterwards(
        //            () -> assertTrue("Output should indicate issues with WDL imports and exit", systemOutRule.getLog().contains("Could not get WDL imports")));
        int exitCode = catchSystemExit(() -> Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, LAUNCH, "--local-entry",
                ResourceHelpers.resourceFilePath("wdlincorrecthttp.wdl"), JSON, ResourceHelpers.resourceFilePath("wdl.json"), SCRIPT_FLAG }));
        assertEquals(Client.API_ERROR, exitCode);
    }

    /**
     * This tests that a published workflow, when launched, attempts to validate descriptor checksums.
     * That cases that are tested: Checksum matches, checksum doesn't match, checksum doesn't exist
     *
     */
    @Test
    void launchWorkflowChecksumValidation() {
        // register and publish a workflow
        Client.main(
            new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, MANUAL_PUBLISH, "--repository",
                "md5sum-checker", "--organization", "DockstoreTestUser2", "--git-version-control", "github",
                "--workflow-path", "/checker-workflow-wrapping-tool.cwl", "--descriptor-type", "cwl", "--workflow-name", "checksumTester", SCRIPT_FLAG });

        // ensure checksum validation is acknowledged, and no null checksums were discovered
        systemOutRule.clear();
        Client.main(new String[] {CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, LAUNCH, ENTRY,
            "github.com/DockstoreTestUser2/md5sum-checker/checksumTester", JSON, ResourceHelpers.resourceFilePath("md5sum_cwl.json"), SCRIPT_FLAG });
        assertTrue(
                systemOutRule.getText().contains(CHECKSUM_VALIDATED_MESSAGE) && !systemOutRule.getText().contains(CHECKSUM_NULL_MESSAGE),
                "Output should indicate that checksums have been validated");

        // unpublish the workflow
        Client.main(
            new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, PUBLISH, "--unpub", ENTRY, "github.com/DockstoreTestUser2/md5sum-checker/checksumTester"});

        // ensure checksum validation is acknowledged for the unpublished workflow, and no null checksums were discovered
        systemOutRule.clear();
        Client.main(new String[] {CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, LAUNCH, ENTRY,
            "github.com/DockstoreTestUser2/md5sum-checker/checksumTester", JSON, ResourceHelpers.resourceFilePath("md5sum_cwl.json"), SCRIPT_FLAG });
        assertTrue(
                systemOutRule.getText().contains(CHECKSUM_VALIDATED_MESSAGE) && !systemOutRule.getText().contains(CHECKSUM_NULL_MESSAGE),
                "Output should indicate that checksums have been validated");
    }


}
