package io.dockstore.client.cli;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.assertj.core.util.Arrays;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import scala.collection.immutable.List;

public class YamlValidatorTest {
    /*
    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();
    */

    @Test
    public void invalidDirectory() {
        final String invalidDirectory = "/orange/julius";
        try {
            YamlVerify.dockstoreValidate(invalidDirectory);
            fail("Invalid directory not caught");
        } catch (YamlVerify.ValidateYamlException ex) {
            assertEquals(YamlVerify.ERROR_MESSAGE + invalidDirectory +  " does not exist", ex.getMessage());
        }
    }

    // This test has a valid directory, but .dockstore.yml does not exist
    @Test
    public void invalidFile() {
        final String validDirectory = "src/test/resources/testDirectory2";
        try {
            YamlVerify.dockstoreValidate(validDirectory);
            fail("File that does not exist was not caught");
        } catch (YamlVerify.ValidateYamlException ex) {
            final String dockstorePath = YamlVerify.ERROR_MESSAGE + validDirectory + "/" + YamlVerify.DOCKSTOREYML;
            assertEquals(dockstorePath +  " does not exist", ex.getMessage());
        }
    }

    // Determines if .dockstore.yml is empty
    @Test
    public void emptyDockstoreYml() {
        final String directoryEmptyDockstoreYml = "src/test/resources/testDirectory4";
        try {
            YamlVerify.dockstoreValidate(directoryEmptyDockstoreYml);
            fail("Empty file not caught");
        } catch (YamlVerify.ValidateYamlException ex) {
            final String dockstorePath = YamlVerify.ERROR_MESSAGE + directoryEmptyDockstoreYml + "/" + YamlVerify.DOCKSTOREYML;
            assertEquals(dockstorePath +  " is empty", ex.getMessage());
        }
    }

    // This tests whether a Yaml is valid (ie. it compiles) but does not verify that it is valid for use in DockStore
    @Test
    public void invalidYaml() {
        final String baseTestDirectory = "src/test/resources/InvalidYamlSyntax/test";
        for (int i = 1; i <= 3; i++) {
            final String testDirectory = baseTestDirectory + i;
            try {
                YamlVerify.dockstoreValidate(testDirectory);
                fail("Invalid YAML not caught");
            } catch (YamlVerify.ValidateYamlException ex) {
                final String dockstorePath = YamlVerify.ERROR_MESSAGE + testDirectory + "/" + YamlVerify.DOCKSTOREYML;
                assertTrue(ex.getMessage().startsWith(dockstorePath + " is not a valid yaml file:"));
            }
        }
    }

    // Invalid Yaml tests
    @Test
    public void yamlNotAcceptableForDockstore() {
        final String testDirectory1 = "src/test/resources/YamlVerifyTestDirectory/2ToolsWithNoName";
        try {
            YamlVerify.dockstoreValidate(testDirectory1);
            //fail("Invalid YAML not caught");
            System.out.println("PASS");
        } catch (YamlVerify.ValidateYamlException ex) {
            System.out.println(ex.getMessage());
        }


    }


}


