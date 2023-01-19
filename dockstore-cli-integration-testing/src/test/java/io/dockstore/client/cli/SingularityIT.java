package io.dockstore.client.cli;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import io.dockstore.client.cli.nested.SingularityTest;
import io.dockstore.common.CLICommonTestUtilities;
import io.dockstore.common.FlushingSystemErr;
import io.dockstore.common.FlushingSystemOut;
import io.dockstore.common.SourceControl;
import io.dropwizard.testing.ResourceHelpers;
import io.swagger.client.ApiClient;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.Workflow;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.org.webcompere.systemstubs.SystemStubs.catchSystemExit;

@Tag(SingularityTest.NAME)
@ExtendWith(SystemStubsExtension.class)
public class SingularityIT extends BaseIT {

    @TempDir
    public static File temporaryFolder;

    private static final String SINGULARITY_CONFIG_TEMPLATE = ResourceHelpers.resourceFilePath("config_for_singularity");

    @SystemStub
    public final SystemOut systemOutRule = new FlushingSystemOut();

    @SystemStub
    public final SystemErr systemErrRule = new FlushingSystemErr();

    @BeforeEach
    @Override
    public void resetDBBetweenTests() throws Exception {
        CLICommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
    }

    /**
     * Tests that a simple CWL workflow can be run in Singularity instead of Docker
     */
    @Test
    public void runCwlWorkflow() throws Exception {
        // manually register the CWL version of the md5sum-checker workflow locally
        testingPostgres.runUpdateStatement("update enduser set isadmin = 't' where username = 'DockstoreTestUser2';");
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        Workflow workflow = workflowApi.manualRegister(SourceControl.GITHUB.getFriendlyName(), "DockstoreTestUser2/md5sum-checker",
                "/checker-workflow-wrapping-workflow.cwl", "test", "cwl", null);
        workflowApi.refresh(workflow.getId(), true);

        // run the md5sum-checker workflow
        catchSystemExit(() -> {
            Client.main(new String[] { "--config",  // this config file passes the --singularity option to cwltool
                    SINGULARITY_CONFIG_TEMPLATE, "workflow", "launch", "--entry",
                    SourceControl.GITHUB + "/DockstoreTestUser2/md5sum-checker/test", "--json",
                    ResourceHelpers.resourceFilePath("md5sum_cwl.json") });
        });

        // the message "Creating SIF file" will only be in the output if the Singularity command starts successfully
        assertTrue(systemOutRule.getText().contains("Creating SIF file"), "assert output does not contain singularity command: " + systemOutRule.getText());
    }

    /**
     * Tests that a simple WDL workflow can be run in Singularity instead of Docker
     */
    @Test
    public void runWDLWorkflow() throws Exception {
        // manually register the WDL version of the md5sum-checker workflow locally
        testingPostgres.runUpdateStatement("update enduser set isadmin = 't' where username = 'DockstoreTestUser2';");
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        Workflow workflow = workflowApi.manualRegister(SourceControl.GITHUB.getFriendlyName(), "DockstoreTestUser2/md5sum-checker",
                "/checker-workflow-wrapping-workflow.wdl", "test", "wdl", null);
        workflowApi.refresh(workflow.getId(), true);

        // make a tmp copy of the dockstore config and add the cromwell conf file option to it
        // this is done in the test because the path to the cromwell conf is different if it's running locally vs. on Travis
        File tmpConfig = generateCromwellConfig();

        // run the md5sum-checker workflow
        catchSystemExit(() -> {
            Client.main(new String[] { "--config", tmpConfig.getAbsolutePath(), "workflow", "launch", "--entry",
                    SourceControl.GITHUB + "/DockstoreTestUser2/md5sum-checker/test", "--json",
                    ResourceHelpers.resourceFilePath("md5sum_wdl.json") });
        });

        // the phrase "singularity exec" will only be in the output if Singularity is actually being used
        assertTrue(systemOutRule.getText().contains("singularity exec"), "assert output contains singularity command");
    }

    /**
     * Makes a tmp file that's a copy of the contents of the config_for_singularity file in resources,
     * and adds a line setting the cromwell-vm-options to the cromwell_singularity.conf path.
     * This is needed to get the absolute path of the file, which is different if it's being run locally or on Travis.
     * @return File object of the tmp file created
     */
    private File generateCromwellConfig() throws IOException {
        // get the path to the cromwell conf file in the current file system
        // this configures cromwell to run with singularity instead of docker
        String cromwellConfPath = ResourceHelpers.resourceFilePath("cromwell_singularity.conf");

        // make a new file in the tmp folder for this test
        File tmpFile = new File(temporaryFolder, "config_for_singularity");
        // copy the dockstore config for singularity from the resources directory into the new tmp file
        File configTemplate = new File(SINGULARITY_CONFIG_TEMPLATE);
        FileUtils.copyFile(configTemplate, tmpFile);

        // append the new cromwell option to the tmp dockstore config
        FileUtils.writeStringToFile(tmpFile, "\ncromwell-vm-options: -Dconfig.file=" + cromwellConfPath,
                StandardCharsets.UTF_8, true);
        return tmpFile;
    }
}

