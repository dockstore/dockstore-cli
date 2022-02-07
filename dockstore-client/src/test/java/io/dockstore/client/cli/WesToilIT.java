package io.dockstore.client.cli;

import io.dropwizard.testing.ResourceHelpers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;

import static org.junit.Assert.assertTrue;

public class WesToilIT {

    public static final String TOIL_CONFIG = ResourceHelpers.resourceFilePath("wesIt/config_toil");

    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();
    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog();
    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog();

    @Test
    public void testBasicLaunch1() {
            String[] commandStatement = new String[]{ "workflow", "wes", "launch",
                "--config", TOIL_CONFIG,
                "--entry", "github.com/dockstore-testing/wes-testing/single-descriptor-with-input:main",
                "--json", ResourceHelpers.resourceFilePath("wesIt/w1_test.json"),
                "--inline-workflow"
            };
            Client.main(commandStatement);
            assertTrue("A helper command should be printed to stdout", systemOutRule.getLog().contains("dockstore workflow wes status --id"));
    }
}
