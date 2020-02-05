package io.dockstore.client.cli;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import io.dockstore.client.cli.nested.SingularityTest;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.SourceControl;
import io.dropwizard.testing.ResourceHelpers;
import io.swagger.client.ApiClient;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.Workflow;
import org.apache.commons.io.FileUtils;
import org.bouncycastle.util.io.TeeOutputStream;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TemporaryFolder;

@Category(SingularityTest.class)
public class SingularityIT extends BaseIT {

    @ClassRule
    public static TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();

    @Before
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false);
    }

    /**
     * Tests that a simple CWL workflow can be run in Singularity instead of Docker
     */
    @Test
    public void runCwlWorkflow() {
        // manually register the CWL version of the md5sum-checker workflow locally
        testingPostgres.runUpdateStatement("update enduser set isadmin = 't' where username = 'DockstoreTestUser2';");
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        Workflow workflow = workflowApi.manualRegister(SourceControl.GITHUB.getFriendlyName(), "DockstoreTestUser2/md5sum-checker",
                "/checker-workflow-wrapping-workflow.cwl", "test", "cwl", null);
        workflowApi.refresh(workflow.getId());

        // start saving the output so as to make sure that Singularity actually runs instead of Docker
        PrintStream old = System.out;  // save the regular output stream so it can be reset later
        TeeOutputStream teeOut = new TeeOutputStream(System.out, outContent);
        PrintStream out = new PrintStream(teeOut, true);  // also print it to the screen
        System.setOut(out);

        // run the md5sum-checker workflow
        Client.main(new String[] {
            "--config",  // this config file passes the --singularity option to cwltool
            ResourceHelpers.resourceFilePath("config_for_singularity"),
            "workflow",
            "launch",
            "--entry",
            SourceControl.GITHUB.toString() + "/DockstoreTestUser2/md5sum-checker/test",
            "--json", ResourceHelpers.resourceFilePath("md5sum_cwl.json")
        });
        System.setOut(old);  // reset the output stream to stdout

        // the message "Creating SIF file" will only be in the output if the Singularity command starts successfully
        Assert.assertTrue(outContent.toString().contains("Creating SIF file"));
    }

    /**
     * Tests that a simple WDL workflow can be run in Singularity instead of Docker
     */
    @Test
    public void runWDLWorkflow() {
        // manually register the WDL version of the md5sum-checker workflow locally
        testingPostgres.runUpdateStatement("update enduser set isadmin = 't' where username = 'DockstoreTestUser2';");
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        Workflow workflow = workflowApi.manualRegister(SourceControl.GITHUB.getFriendlyName(), "DockstoreTestUser2/md5sum-checker",
                "/checker-workflow-wrapping-workflow.wdl", "test", "wdl", null);
        workflowApi.refresh(workflow.getId());

        // make a tmp copy of the dockstore config and add to it the cromwell conf file option
        File tmpConfig = generateCromwellConfig();  // this is done in the test because the location varies

        // start saving the output so as to make sure that Singularity actually runs instead of Docker
        PrintStream old = System.out;  // save the regular output stream so it can be reset later
        TeeOutputStream teeOut = new TeeOutputStream(System.out, outContent);
        PrintStream out = new PrintStream(teeOut, true);  // also print it to the screen
        System.setOut(out);

        // run the md5sum-checker workflow
        Client.main(new String[] {
            "--config",
            tmpConfig.getAbsolutePath(),
            "workflow",
            "launch",
            "--entry",
            SourceControl.GITHUB.toString() + "/DockstoreTestUser2/md5sum-checker/test",
            "--json", ResourceHelpers.resourceFilePath("md5sum_wdl.json")
        });
        System.setOut(old);  // reset the output stream to stdout

        // the message "Creating SIF file" will only be in the output if the Singularity command starts successfully
        Assert.assertTrue(outContent.toString().contains("Creating SIF file"));
    }

    private File generateCromwellConfig() {
        // get the path to the cromwell conf file in the current file system
        // this configures cromwell to run with singularity instead of docker
        String cromwellConfPath = ResourceHelpers.resourceFilePath("cromwell_singularity.conf");

        try {
            // make a new file in the tmp folder for this test
            File tmpFile = temporaryFolder.newFile("config_for_singularity");
            // copy the dockstore config for singularity from the resources directory into the new tmp file
            File configTemplate = new File(ResourceHelpers.resourceFilePath("config_for_singularity"));
            FileUtils.copyFile(configTemplate, tmpFile);

            // append the new cromwell option to the tmp dockstore config
            FileUtils.writeStringToFile(tmpFile, "\ncromwell-vm-options: -Dconfig.file=" + cromwellConfPath,
                    StandardCharsets.UTF_8, true);
            return tmpFile;

        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}

