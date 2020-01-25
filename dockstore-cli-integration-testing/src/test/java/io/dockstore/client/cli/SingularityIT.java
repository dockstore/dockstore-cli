package io.dockstore.client.cli;

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
        testingPostgres.runUpdateStatement("update enduser set isadmin = 't' where username = 'DockstoreTestUser2';");
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        Workflow workflow = workflowApi.manualRegister(SourceControl.GITHUB.getFriendlyName(), "DockstoreTestUser2/md5sum-checker",
                "/checker-workflow-wrapping-workflow.cwl", "test", "cwl", null);

        Workflow refresh = workflowApi.refresh(workflow.getId());

        Client.main(new String[] {
            "--config",
            ResourceHelpers.resourceFilePath("config_for_singularity"),
            "workflow",
            "launch",
            "--entry",
            SourceControl.GITHUB.toString() + "/DockstoreTestUser2/md5sum-checker/test",
            "--json", ResourceHelpers.resourceFilePath("md5sum-wrapper-tool.json")
        });
    }
}
