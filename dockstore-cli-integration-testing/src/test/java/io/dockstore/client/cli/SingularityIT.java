package io.dockstore.client.cli;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import io.dockstore.client.cli.nested.SingularityTest;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.SourceControl;
import io.dropwizard.testing.ResourceHelpers;
import io.swagger.client.ApiClient;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.Workflow;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TemporaryFolder;

@Category(SingularityTest.class)
public class SingularityIT extends BaseIT {

    @ClassRule
    public static TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false);
    }

    @Test
    public void runCwlWorkflow() {
        // manually register the CWL version of the md5sum-checker workflow locally
        testingPostgres.runUpdateStatement("update enduser set isadmin = 't' where username = 'DockstoreTestUser2';");
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        Workflow workflow = workflowApi.manualRegister(SourceControl.GITHUB.getFriendlyName(), "DockstoreTestUser2/md5sum-checker",
                "/checker-workflow-wrapping-workflow.cwl", "test", "cwl", null);
        workflowApi.refresh(workflow.getId());

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
    }

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

        try {
            BufferedReader br = new BufferedReader(new FileReader(tmpConfig));
            String line;
            while ((line = br.readLine()) != null) {
                System.out.println(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

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
            FileUtils.writeStringToFile(new File("md5sum.input"),
                    "\ncromwell-vm-options: -Dconfig.file=" + cromwellConfPath, StandardCharsets.UTF_8, true);
            return tmpFile;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}

