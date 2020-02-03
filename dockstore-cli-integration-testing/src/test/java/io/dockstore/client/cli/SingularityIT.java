package io.dockstore.client.cli;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import io.dockstore.client.cli.nested.SingularityTest;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.SourceControl;
import io.dropwizard.testing.ResourceHelpers;
import io.swagger.client.ApiClient;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.Workflow;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Category(SingularityTest.class)
public class SingularityIT extends BaseIT {

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

        // add a line to the dockstore config with the location of the cromwell conf file
        generateCromwellConfig();  // this is done in the test because the location varies

        try {
            BufferedReader br = new BufferedReader(new FileReader(ResourceHelpers.resourceFilePath("config_for_singularity")));
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
            ResourceHelpers.resourceFilePath("config_for_singularity"),
            "workflow",
            "launch",
            "--entry",
            SourceControl.GITHUB.toString() + "/DockstoreTestUser2/md5sum-checker/test",
            "--json", ResourceHelpers.resourceFilePath("md5sum_wdl.json")
        });
    }

    private void generateCromwellConfig() {
        // get the path to the cromwell conf file in the current file system
        // this configures cromwell to run with singularity instead of docker
        String cromwellConfPath = ResourceHelpers.resourceFilePath("cromwell_singularity.conf");
        try {
            // append the new cromwell option to the dockstore config
            FileWriter f = new FileWriter(ResourceHelpers.resourceFilePath("config_for_singularity"), true);
            f.write("\ncromwell-vm-options: -Dconfig.file=" + cromwellConfPath);
            f.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

