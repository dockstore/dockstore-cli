package io.dockstore.client.cli;

import io.dockstore.client.cli.nested.AbstractEntryClient;
import io.dockstore.client.cli.nested.WESLauncher;
import io.dockstore.client.cli.nested.WesRequestData;
import io.dockstore.client.cli.nested.WorkflowClient;
import io.dockstore.common.DescriptorLanguage;
import io.dropwizard.testing.ResourceHelpers;
import io.swagger.client.api.UsersApi;
import io.swagger.client.api.WorkflowsApi;
import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class WESLauncherTest {

    public AbstractEntryClient testAggregateHelper(String configPath) {
        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();

        if (configPath != null) {
            client.setConfigFile(ResourceHelpers.resourceFilePath(configPath));
        }

        AbstractEntryClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        return workflowClient;
    }

    @Test
    public void testTrsUrlCreation() {

        // Create client + launcher
        AbstractEntryClient aec = testAggregateHelper(null);
        WesRequestData wrd = new WesRequestData("myWesUrl");
        aec.setWesRequestData(wrd);
        WESLauncher launcher = new WESLauncher(aec, DescriptorLanguage.WDL, false);
        launcher.initialize();

        final String basePath = "https://dockstore.org";
        final String entryId = "github.com/org/repo";
        final String versionId = "master";
        final String type = "WDL";
        final String relativePath = "rightHere.wdl";
        final String trsUrl = launcher.createTrsUrl(basePath, entryId, versionId, type, relativePath);

        assertTrue("The basepath shouldn't be encoded", trsUrl.contains(basePath));
        assertTrue("workflow path should be encoded", trsUrl.contains("github.com%2Forg%2Frepo"));

    }
}
