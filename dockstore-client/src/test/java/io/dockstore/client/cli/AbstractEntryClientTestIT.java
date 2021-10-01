package io.dockstore.client.cli;

import io.dropwizard.testing.ResourceHelpers;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;

public class AbstractEntryClientTestIT {
    
    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog();
    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog();

    /**
     *
     */
    @Test
    public void testWESHelpMessages() {
        String clientConfig = ResourceHelpers.resourceFilePath("clientConfig");
        String[] command = { "workflow", "wes", "--help", "--config", clientConfig };
        Client.main(command);
        Assert.assertTrue("There are unexpected error logs", systemErrRule.getLog().isBlank());

        String[] commandLaunch = { "workflow", "wes", "launch", "--help", "--config", clientConfig };
        Client.main(commandLaunch);
        Assert.assertTrue("There are unexpected error logs", systemErrRule.getLog().isBlank());

        String[] commandStatus = { "workflow", "wes", "status", "--help", "--config", clientConfig };
        Client.main(commandStatus);
        Assert.assertTrue("There are unexpected error logs", systemErrRule.getLog().isBlank());

        String[] commandCancel = { "workflow", "wes", "cancel", "--help", "--config", clientConfig };
        Client.main(commandCancel);
        Assert.assertTrue("There are unexpected error logs", systemErrRule.getLog().isBlank());

        String[] commandServiceInfo = { "workflow", "wes", "service-info", "--help", "--config", clientConfig };
        Client.main(commandServiceInfo);
        Assert.assertTrue("There are unexpected error logs", systemErrRule.getLog().isBlank());
    }
}
