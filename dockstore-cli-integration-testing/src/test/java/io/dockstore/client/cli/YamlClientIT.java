package io.dockstore.client.cli;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;

import com.google.common.collect.Lists;
import io.dockstore.common.FlushingSystemErrRule;
import io.dockstore.common.FlushingSystemOutRule;
import io.dockstore.common.TestUtility;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;

public class YamlClientIT {

    @Rule
    public final SystemOutRule systemOutRule = new FlushingSystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new FlushingSystemErrRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();

    @Test
    public void testAllYamlHelpMessages() throws IOException {
        checkCommandForHelp(new String[] { "yaml"});
        checkCommandForHelp(new String[] { "yaml", "--help" });
        checkCommandForHelp(new String[] { "yaml", "validate", "--help" });
    }

    private void checkCommandForHelp(String[] argv) throws IOException {
        final ArrayList<String> strings = Lists.newArrayList(argv);
        strings.add("--config");
        strings.add(TestUtility.getConfigFileLocation(true));

        Client.main(strings.toArray(new String[0]));
        System.out.println(strings.toArray(new String[0]));
        Assert.assertTrue(systemOutRule.getLog().contains("Usage: dockstore"));
        systemOutRule.clearLog();
    }

    @Test
    public void missingPathParamter() {
        Client.main(new String[] { "yaml", "validate" });
        Assert.assertTrue(systemOutRule.getLog().contains("ERROR: Missing --path <path>"));
        systemOutRule.clearLog();
    }

    @Test
    public void completeRun() {
        Client.main(new String[] { "yaml", "validate", "--path", "../dockstore-client/src/test/resources/YamlVerifyTestDirectory/correct-directory" });
        assertTrue(systemOutRule.getLog().contains("src/test/resources/YamlVerifyTestDirectory/correct-directory/.dockstore.yml is a valid yaml file"));
        assertTrue(systemOutRule.getLog().contains("src/test/resources/YamlVerifyTestDirectory/correct-directory/.dockstore.yml is a valid dockstore yaml file"));
    }

    @Test
    public void verifyErrorMessagesArePrinted() {
        Client.main(new String[] { "yaml", "validate", "--path", "../dockstore-client/src/test/resources/YamlVerifyTestDirectory/correct-directory" });
        String ErrorMsg = "../dockstore-client/src/test/resources/YamlVerifyTestDirectory/correct-directory/.dockstore.yml is a valid yaml file\n" +
            "../dockstore-client/src/test/resources/YamlVerifyTestDirectory/correct-directory/.dockstore.yml is a valid dockstore yaml file\n";
        Assert.assertEquals(ErrorMsg, systemOutRule.getLog());
    }


}
