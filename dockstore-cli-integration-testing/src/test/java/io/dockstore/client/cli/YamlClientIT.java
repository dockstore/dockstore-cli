package io.dockstore.client.cli;

import java.io.IOException;

import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.FlushingSystemErrRule;
import io.dockstore.common.FlushingSystemOutRule;
import io.dockstore.common.TestUtility;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;

import static org.junit.Assert.assertTrue;

public class YamlClientIT extends BaseIT {

    @Rule
    public final SystemOutRule systemOutRule = new FlushingSystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new FlushingSystemErrRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();

    @Before
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate1(SUPPORT);
        Client.DEBUG.set(false);
    }



    @Test
    public void missingPathParameter() throws IOException {
        Client.main(new String[] { "--config", TestUtility.getConfigFileLocation(true), "yaml", "validate" });
        Assert.assertTrue(systemOutRule.getLog().contains("ERROR: Missing --path <path>"));
        systemOutRule.clearLog();
    }

    @Test
    public void completeRun() throws IOException {
        Client.main(new String[] { "--config", TestUtility.getConfigFileLocation(true), "yaml", "validate", "--path", "../dockstore-client/src/test/resources/YamlVerifyTestDirectory/correct-directory" });
        assertTrue(systemOutRule.getLog().contains("src/test/resources/YamlVerifyTestDirectory/correct-directory/.dockstore.yml is a valid yaml file"));
        assertTrue(systemOutRule.getLog().contains("src/test/resources/YamlVerifyTestDirectory/correct-directory/.dockstore.yml is a valid dockstore yaml file"));
        systemOutRule.clearLog();
    }

    @Test
    public void verifyErrorMessagesArePrinted() throws IOException {
        Client.main(new String[] { "--config", TestUtility.getConfigFileLocation(true), "yaml", "validate", "--path", "../dockstore-client/src/test/resources/YamlVerifyTestDirectory/some-files-present" });
        assertTrue(systemOutRule.getLog().contains("../dockstore-client/src/test/resources/YamlVerifyTestDirectory/some-files-present/.dockstore.yml is a valid yaml file"));
        String errorMsg = "Your file structure has the following errors:\n"
            + "../dockstore-client/src/test/resources/YamlVerifyTestDirectory/some-files-present/dockstore.wdl.json does not exist\n"
            + "../dockstore-client/src/test/resources/YamlVerifyTestDirectory/some-files-present/Dockstore.cwl does not exist";
        assertTrue(systemOutRule.getLog().contains(errorMsg));
        systemOutRule.clearLog();
    }



}
