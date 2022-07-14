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

import java.util.Arrays;
import java.util.List;

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
            YamlVerifyUtility.dockstoreValidate(invalidDirectory);
            fail("Invalid directory not caught");
        } catch (YamlVerifyUtility.ValidateYamlException ex) {
            assertEquals(YamlVerifyUtility.INVALID_DOCKSTORE_YML + invalidDirectory +  YamlVerifyUtility.FILE_DOES_NOT_EXIST, ex.getMessage());
        }
    }

    // This test has a valid directory, but .dockstore.yml does not exist
    @Test
    public void invalidFile() {
        final String validDirectory = "src/test/resources/testDirectory2";
        try {
            YamlVerifyUtility.dockstoreValidate(validDirectory);
            fail("File that does not exist was not caught");
        } catch (YamlVerifyUtility.ValidateYamlException ex) {
            final String dockstorePath = validDirectory + "/" + YamlVerifyUtility.DOCKSTOREYML;
            assertEquals(YamlVerifyUtility.INVALID_DOCKSTORE_YML + dockstorePath +  YamlVerifyUtility.FILE_DOES_NOT_EXIST, ex.getMessage());
        }
    }

    // Determines if .dockstore.yml is empty
    @Test
    public void emptyDockstoreYml() {
        final String emptyDockstoreYmlDirectory = "src/test/resources/testDirectory4";
        try {
            YamlVerifyUtility.dockstoreValidate(emptyDockstoreYmlDirectory);
            fail("Empty file not caught");
        } catch (YamlVerifyUtility.ValidateYamlException ex) {
            final String dockstorePath = YamlVerifyUtility.INVALID_DOCKSTORE_YML + emptyDockstoreYmlDirectory + "/" + YamlVerifyUtility.DOCKSTOREYML;
            assertEquals(dockstorePath +  YamlVerifyUtility.EMPTY_FILE, ex.getMessage());
        }
    }

    @Test
    public void invalidYaml() {
        final String baseTestDirectory = "src/test/resources/InvalidYamlSyntax/test";
        final Integer numberTestDirectories = 3;
        for (int i = 1; i <= numberTestDirectories; i++) {
            final String testDirectory = baseTestDirectory + i;
            try {
                YamlVerifyUtility.dockstoreValidate(testDirectory);
                fail("Invalid YAML not caught");
            } catch (YamlVerifyUtility.ValidateYamlException ex) {
                final String dockstorePath = YamlVerifyUtility.INVALID_DOCKSTORE_YML + testDirectory + "/" + YamlVerifyUtility.DOCKSTOREYML;
                assertTrue(ex.getMessage().startsWith(dockstorePath + YamlVerifyUtility.INVALID_YAML));
            }
        }
    }

    // Invalid Yaml test
    @Ignore("This test case is failing due to errors in DockstoreYamlHelper.readAsDockstoreYaml12(contents) see GitHub issue 4985")
    @Test
    public void yamlNotAcceptableForDockstore() {
        final String testDirectory1 = "src/test/resources/YamlVerifyTestDirectory/2ToolsWithNoName";
        try {
            YamlVerifyUtility.dockstoreValidate(testDirectory1);
            fail("Invalid YAML not caught");
        } catch (YamlVerifyUtility.ValidateYamlException ex) {
            System.out.println(ex.getMessage());
        }

    }

    @Test
    public void allFilesNotPresent() {
        final String baseTestDirectory = "src/test/resources/YamlVerifyTestDirectory/no-files-present/";
        List<String> directoryEnds1 = Arrays.asList("tool", "service", "workflow");
        for (String directoryEnd : directoryEnds1) {
            String testDirectory = baseTestDirectory + directoryEnd;
            String errorMsg = YamlVerifyUtility.INVALID_FILE_STRUCTURE
                + testDirectory + "/dockstore.wdl.json" + YamlVerifyUtility.FILE_DOES_NOT_EXIST + System.lineSeparator()
                + testDirectory + "/dockstore.cwl.json" + YamlVerifyUtility.FILE_DOES_NOT_EXIST + System.lineSeparator()
                + testDirectory + "/Dockstore.cwl" + YamlVerifyUtility.FILE_DOES_NOT_EXIST + System.lineSeparator()
                + testDirectory + "/Dockstore2.wdl" + YamlVerifyUtility.FILE_DOES_NOT_EXIST + System.lineSeparator();
            try {
                YamlVerifyUtility.dockstoreValidate(testDirectory);
                fail("non-present test files not caught");
            } catch (YamlVerifyUtility.ValidateYamlException ex) {
                assertEquals(errorMsg, ex.getMessage());
            }
        }
        List<String> directoryEnds2 = Arrays.asList("multiple-workflows", "multiple-tools", "workflows-and-tools-1", "workflows-and-tools-2");
        for (String directoryEnd : directoryEnds2) {
            String testDirectory = baseTestDirectory + directoryEnd;
            String errorMsg = YamlVerifyUtility.INVALID_FILE_STRUCTURE
                + testDirectory + "/dockstore.wdl.json" + YamlVerifyUtility.FILE_DOES_NOT_EXIST + System.lineSeparator()
                + testDirectory + "/dockstore.cwl.json" + YamlVerifyUtility.FILE_DOES_NOT_EXIST + System.lineSeparator()
                + testDirectory + "/Dockstore2.wdl" + YamlVerifyUtility.FILE_DOES_NOT_EXIST + System.lineSeparator()
                + testDirectory + "/dockstore2.wdl.json" + YamlVerifyUtility.FILE_DOES_NOT_EXIST + System.lineSeparator()
                + testDirectory + "/dockstore2.cwl.json" + YamlVerifyUtility.FILE_DOES_NOT_EXIST + System.lineSeparator()
                + testDirectory + "/Dockstore2.cwl" + YamlVerifyUtility.FILE_DOES_NOT_EXIST + System.lineSeparator()
                + testDirectory + "/Dockstore3.wdl" + YamlVerifyUtility.FILE_DOES_NOT_EXIST + System.lineSeparator()
                + testDirectory + "/Dockstore.cwl" + YamlVerifyUtility.FILE_DOES_NOT_EXIST + System.lineSeparator();
            try {
                YamlVerifyUtility.dockstoreValidate(testDirectory);
                fail("non-present test files not caught");
            } catch (YamlVerifyUtility.ValidateYamlException ex) {
                assertEquals(errorMsg, ex.getMessage());
            }
        }
    }


    @Test
    public void allFilesNotPresentTool() {
        final String testDirectory = "src/test/resources/YamlVerifyTestDirectory/no-files-present/tool";
        try {
            YamlVerifyUtility.dockstoreValidate(testDirectory);
            fail("non-present test files not caught");
        } catch (YamlVerifyUtility.ValidateYamlException ex) {
            String errorMsg = YamlVerifyUtility.INVALID_FILE_STRUCTURE
                + testDirectory + "/dockstore.wdl.json" + YamlVerifyUtility.FILE_DOES_NOT_EXIST + System.lineSeparator()
                + testDirectory + "/dockstore.cwl.json" + YamlVerifyUtility.FILE_DOES_NOT_EXIST + System.lineSeparator()
                + testDirectory + "/Dockstore.cwl" + YamlVerifyUtility.FILE_DOES_NOT_EXIST + System.lineSeparator()
                + testDirectory + "/Dockstore2.wdl" + YamlVerifyUtility.FILE_DOES_NOT_EXIST + System.lineSeparator();
            assertEquals(errorMsg, ex.getMessage());
        }
    }

    @Test
    public void allFilesNotPresentService() {
        final String testDirectory = "src/test/resources/YamlVerifyTestDirectory/no-files-present/service";
        try {
            YamlVerifyUtility.dockstoreValidate(testDirectory);
            fail("non-present test files not caught");
        } catch (YamlVerifyUtility.ValidateYamlException ex) {
            String errorMsg = YamlVerifyUtility.INVALID_FILE_STRUCTURE
                + testDirectory + "/dockstore.wdl.json" + YamlVerifyUtility.FILE_DOES_NOT_EXIST + System.lineSeparator()
                + testDirectory + "/dockstore.cwl.json" + YamlVerifyUtility.FILE_DOES_NOT_EXIST + System.lineSeparator()
                + testDirectory + "/Dockstore.cwl" + YamlVerifyUtility.FILE_DOES_NOT_EXIST + System.lineSeparator()
                + testDirectory + "/Dockstore2.wdl" + YamlVerifyUtility.FILE_DOES_NOT_EXIST + System.lineSeparator();
            assertEquals(errorMsg, ex.getMessage());
        }
    }

    @Test
    public void correctYamlAndFiles() {
        final String testDirectory = "src/test/resources/YamlVerifyTestDirectory/correct-directory";
        try {
            YamlVerifyUtility.dockstoreValidate(testDirectory);
            String successMsg = testDirectory + "/" + YamlVerifyUtility.DOCKSTOREYML + YamlVerifyUtility.VALID_YAML_ONLY + System.lineSeparator()
                + testDirectory + "/" + YamlVerifyUtility.DOCKSTOREYML + YamlVerifyUtility.VALID_DOCKSTORE_YML+ System.lineSeparator();
            assertEquals(successMsg, systemOutRule.getLog());
            systemOutRule.clearLog();
        } catch (YamlVerifyUtility.ValidateYamlException ex) {
            fail("Threw exception when it should've passed");
        }
    }

    @Test
    public void someFilesNotPresent() {
        final String testDirectory = "src/test/resources/YamlVerifyTestDirectory/some-files-present";
        try {
            YamlVerifyUtility.dockstoreValidate(testDirectory);
            fail("non-present test files not caught");
        } catch (YamlVerifyUtility.ValidateYamlException ex) {
            String errorMsg = YamlVerifyUtility.INVALID_FILE_STRUCTURE
                + testDirectory + "/dockstore.wdl.json" + YamlVerifyUtility.FILE_DOES_NOT_EXIST + System.lineSeparator()
                + testDirectory + "/Dockstore.cwl" + YamlVerifyUtility.FILE_DOES_NOT_EXIST + System.lineSeparator();
            assertEquals(errorMsg, ex.getMessage());
        }
    }

    @Test
    public void missingFields() {
        final String testDirectory = "src/test/resources/YamlVerifyTestDirectory/invalid-dockstore-yml";
        try {
            YamlVerifyUtility.dockstoreValidate(testDirectory);
            fail("non-present test files not caught");
        } catch (YamlVerifyUtility.ValidateYamlException ex) {
            assertTrue(ex.getMessage().contains("primaryDescriptorPath"));
            assertEquals(testDirectory + "/" + YamlVerifyUtility.DOCKSTOREYML + YamlVerifyUtility.VALID_YAML_ONLY + System.lineSeparator(), systemOutRule.getLog());
            systemOutRule.clearLog();
        }
    }

    @Test
    public void incorrectlyNamedField() {
        final String testDirectory = "src/test/resources/YamlVerifyTestDirectory/incorrectly-named-paramter-in-yml";
        try {
            YamlVerifyUtility.dockstoreValidate(testDirectory);
            fail("non-present test files not caught");
        } catch (YamlVerifyUtility.ValidateYamlException ex) {
            String errorMsg = YamlVerifyUtility.INVALID_DOCKSTORE_YML
                + testDirectory + "/" + YamlVerifyUtility.DOCKSTOREYML + YamlVerifyUtility.CONTAINS_ERRORS
                + "Unknown property: 'publih'. Did you mean: 'publish'?";
            assertEquals(errorMsg, ex.getMessage());
            assertEquals(testDirectory + "/" + YamlVerifyUtility.DOCKSTOREYML + YamlVerifyUtility.VALID_YAML_ONLY + System.lineSeparator(), systemOutRule.getLog());
            systemOutRule.clearLog();
        }
    }
}


