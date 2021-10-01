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
        final String clientConfig = ResourceHelpers.resourceFilePath("clientConfig");
        final String[] commandNames = {"", "launch", "status", "cancel", "service-info"};

        for (String command : commandNames) {
            String[] commandStatement;

            if (command.length() == 0) {
                commandStatement = new String[]{ "workflow", "wes", "--help", "--config", clientConfig };
            } else {
                commandStatement = new String[]{ "workflow", "wes", command, "--help", "--config", clientConfig };
            }

            Client.main(commandStatement);
            Assert.assertTrue("There are unexpected error logs", systemErrRule.getLog().isBlank());
        }
    }
}
