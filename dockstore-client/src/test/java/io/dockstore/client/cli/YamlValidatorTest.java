/*
 *    Copyright 2022 OICR and UCSC
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package io.dockstore.client.cli;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class YamlValidatorTest {

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();


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

    // Invalid Yaml test
    @Ignore
    @Test // This test case is failing due to errors in DockstoreYamlHelper.readAsDockstoreYaml12(contents)
    public void yamlNotAcceptableForDockstore() {
        final String testDirectory1 = "src/test/resources/YamlVerifyTestDirectory/2ToolsWithNoName";
        try {
            YamlVerify.dockstoreValidate(testDirectory1);
            fail("Invalid YAML not caught");
        } catch (YamlVerify.ValidateYamlException ex) {
            System.out.println(ex.getMessage());
        }

    }

    @Test
    public void allFilesNotPresent() {
        final String testDirectory = "src/test/resources/YamlVerifyTestDirectory/no-files-present";
        try {
            YamlVerify.dockstoreValidate(testDirectory);
            fail("non-present test files not caught");
        } catch (YamlVerify.ValidateYamlException ex) {
            String errorMsg = "Your file structure has the following errors:\n"
                + "src/test/resources/YamlVerifyTestDirectory/no-files-present/dockstore.wdl.json does not exist\n"
                + "src/test/resources/YamlVerifyTestDirectory/no-files-present/dockstore.cwl.json does not exist\n"
                + "src/test/resources/YamlVerifyTestDirectory/no-files-present/Dockstore.cwl does not exist\n"
                + "src/test/resources/YamlVerifyTestDirectory/no-files-present/Dockstore2.wdl does not exist\n";

            assertEquals(errorMsg, ex.getMessage());
        }
    }

    @Test
    public void correctYamlAndFiles() {
        final String testDirectory = "src/test/resources/YamlVerifyTestDirectory/correct-directory";
        try {
            YamlVerify.dockstoreValidate(testDirectory);
            assertTrue(systemOutRule.getLog().contains("src/test/resources/YamlVerifyTestDirectory/correct-directory/.dockstore.yml is a valid yaml file"));
            assertTrue(systemOutRule.getLog().contains("src/test/resources/YamlVerifyTestDirectory/correct-directory/.dockstore.yml is a valid dockstore yaml file"));
        } catch (YamlVerify.ValidateYamlException ex) {
            fail("Threw exception when it should've passed");
        }
    }

    @Test
    public void someFilesNotPresent() {
        final String testDirectory = "src/test/resources/YamlVerifyTestDirectory/some-files-present";
        try {
            YamlVerify.dockstoreValidate(testDirectory);
            fail("non-present test files not caught");
        } catch (YamlVerify.ValidateYamlException ex) {
            String errorMsg = "Your file structure has the following errors:\n"
                + "src/test/resources/YamlVerifyTestDirectory/some-files-present/dockstore.wdl.json does not exist\n"
                + "src/test/resources/YamlVerifyTestDirectory/some-files-present/Dockstore.cwl does not exist\n";
            assertEquals(errorMsg, ex.getMessage());
        }
    }

}


