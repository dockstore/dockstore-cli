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

import io.dockstore.common.MuteForSuccessfulTests;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

import static io.dockstore.client.cli.Client.TOOL;
import static io.dockstore.client.cli.Client.WORKFLOW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@ExtendWith(SystemStubsExtension.class)
@ExtendWith(MuteForSuccessfulTests.class)
class YamlValidatorTest {

    @SystemStub
    public final SystemOut systemOutRule = new SystemOut();

    @SystemStub
    public final SystemErr systemErrRule = new SystemErr();

    @Test
    void invalidDirectory() {
        final String invalidDirectory = "/orange/julius";
        try {
            YamlVerifyUtility.dockstoreValidate(invalidDirectory);
            fail("Invalid directory not caught");
        } catch (YamlVerifyUtility.ValidateYamlException ex) {
            assertEquals(YamlVerifyUtility.INVALID_DOCKSTORE_YML + invalidDirectory +  YamlVerifyUtility.FILE_DOES_NOT_EXIST,
                    ex.getMessage());
        }
    }

    // This test has a valid directory, but .dockstore.yml does not exist
    @Test
    void invalidFile() {
        final String validDirectory = "src/test/resources/testDirectory2";
        try {
            YamlVerifyUtility.dockstoreValidate(validDirectory);
            fail("File that does not exist was not caught");
        } catch (YamlVerifyUtility.ValidateYamlException ex) {
            final String dockstorePath = validDirectory + "/" + YamlVerifyUtility.DOCKSTOREYML;
            assertEquals(YamlVerifyUtility.INVALID_DOCKSTORE_YML + dockstorePath +  YamlVerifyUtility.FILE_DOES_NOT_EXIST,
                    ex.getMessage());
        }
    }

    // Determines if .dockstore.yml is empty
    @Test
    void emptyDockstoreYml() {
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
    void invalidYaml() {
        final String baseTestDirectory = "src/test/resources/InvalidYamlSyntax/test";
        final int numberTestDirectories = 3;
        for (int i = 1; i <= numberTestDirectories; i++) {
            final String testDirectory = baseTestDirectory + i;
            try {
                YamlVerifyUtility.dockstoreValidate(testDirectory);
                fail("Invalid YAML not caught");
            } catch (YamlVerifyUtility.ValidateYamlException ex) {
                final String dockstorePath = YamlVerifyUtility.INVALID_DOCKSTORE_YML + testDirectory + "/" + YamlVerifyUtility.DOCKSTOREYML;
                Assertions.assertTrue(ex.getMessage().startsWith(dockstorePath + YamlVerifyUtility.INVALID_YAML));
            }
        }
    }

    // Invalid Yaml test
    @Test
    void yamlNotAcceptableForDockstore() {
        final String testDirectory1 = "src/test/resources/YamlVerifyTestDirectory/2ToolsWithNoName";
        try {
            YamlVerifyUtility.dockstoreValidate(testDirectory1);
            fail("Invalid YAML not caught");
        } catch (YamlVerifyUtility.ValidateYamlException ex) {
            System.out.println(ex.getMessage());
        }

    }

    @Test
    void allFilesNotPresent() {
        final String baseTestDirectory = "src/test/resources/YamlVerifyTestDirectory/no-files-present/";
        List<String> directoryEnds1 = Arrays.asList(TOOL, "service", WORKFLOW);
        for (String directoryEnd : directoryEnds1) {
            String testDirectory = baseTestDirectory + directoryEnd;
            String errorMsg = YamlVerifyUtility.MISSING_FILE_ERROR
                + testDirectory + "/dockstore.wdl.json" + YamlVerifyUtility.FILE_DOES_NOT_EXIST + System.lineSeparator()
                + testDirectory + "/dockstore.cwl.json" + YamlVerifyUtility.FILE_DOES_NOT_EXIST + System.lineSeparator()
                + testDirectory + "/Dockstore.cwl" + YamlVerifyUtility.FILE_DOES_NOT_EXIST + System.lineSeparator()
                + testDirectory + "/Dockstore2.cwl" + YamlVerifyUtility.FILE_DOES_NOT_EXIST;
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
            String errorMsg = YamlVerifyUtility.MISSING_FILE_ERROR
                + testDirectory + "/test2.file" + YamlVerifyUtility.FILE_DOES_NOT_EXIST + System.lineSeparator()
                + testDirectory + "/test3.file" + YamlVerifyUtility.FILE_DOES_NOT_EXIST + System.lineSeparator()
                + testDirectory + "/test1.file" + YamlVerifyUtility.FILE_DOES_NOT_EXIST + System.lineSeparator()
                + testDirectory + "/test5.file" + YamlVerifyUtility.FILE_DOES_NOT_EXIST + System.lineSeparator()
                + testDirectory + "/test6.file" + YamlVerifyUtility.FILE_DOES_NOT_EXIST + System.lineSeparator()
                + testDirectory + "/test7.file" + YamlVerifyUtility.FILE_DOES_NOT_EXIST + System.lineSeparator()
                + testDirectory + "/test4.file" + YamlVerifyUtility.FILE_DOES_NOT_EXIST + System.lineSeparator()
                + testDirectory + "/test8.file" + YamlVerifyUtility.FILE_DOES_NOT_EXIST;
            try {
                YamlVerifyUtility.dockstoreValidate(testDirectory);
                fail("non-present test files not caught");
            } catch (YamlVerifyUtility.ValidateYamlException ex) {
                assertEquals(errorMsg, ex.getMessage());
            }
        }
    }

    @Test
    void correctYamlAndFiles() {
        final String testDirectory = "src/test/resources/YamlVerifyTestDirectory/correct-directory/workflow";
        try {
            YamlVerifyUtility.dockstoreValidate(testDirectory);
            String successMsg = testDirectory + "/" + YamlVerifyUtility.DOCKSTOREYML + YamlVerifyUtility.VALID_YAML_ONLY + System.lineSeparator()
                + testDirectory + "/" + YamlVerifyUtility.DOCKSTOREYML + YamlVerifyUtility.VALID_DOCKSTORE_YML + System.lineSeparator();
            assertEquals(successMsg, systemOutRule.getText());
            systemOutRule.clear();
        } catch (YamlVerifyUtility.ValidateYamlException ex) {
            fail("Threw exception when it should've passed");
        }
    }

    @Test
    void someFilesNotPresent() {
        final String testDirectory = "src/test/resources/YamlVerifyTestDirectory/some-files-present";
        try {
            YamlVerifyUtility.dockstoreValidate(testDirectory);
            fail("non-present test files not caught");
        } catch (YamlVerifyUtility.ValidateYamlException ex) {
            String errorMsg = YamlVerifyUtility.MISSING_FILE_ERROR
                + testDirectory + "/dockstore.wdl.json" + YamlVerifyUtility.FILE_DOES_NOT_EXIST + System.lineSeparator()
                + testDirectory + "/Dockstore.cwl" + YamlVerifyUtility.FILE_DOES_NOT_EXIST;
            assertEquals(errorMsg, ex.getMessage());
        }
    }

    @Test
    void missingFields() {
        final String testDirectory = "src/test/resources/YamlVerifyTestDirectory/invalid-dockstore-yml";
        try {
            YamlVerifyUtility.dockstoreValidate(testDirectory);
            fail("non-present test files not caught");
        } catch (YamlVerifyUtility.ValidateYamlException ex) {
            Assertions.assertTrue(ex.getMessage().contains("primaryDescriptorPath"));
            assertEquals(testDirectory + "/" + YamlVerifyUtility.DOCKSTOREYML + YamlVerifyUtility.VALID_YAML_ONLY + System.lineSeparator(), systemOutRule.getText());
            systemOutRule.clear();
        }
    }

    @Test
    void incorrectlyNamedField() {
        final String testDirectory = "src/test/resources/YamlVerifyTestDirectory/incorrectly-named-parameter-in-yml";
        try {
            YamlVerifyUtility.dockstoreValidate(testDirectory);
            fail("non-present test files not caught");
        } catch (YamlVerifyUtility.ValidateYamlException ex) {
            String errorMsg = YamlVerifyUtility.INVALID_DOCKSTORE_YML
                + testDirectory + "/" + YamlVerifyUtility.DOCKSTOREYML + YamlVerifyUtility.CONTAINS_ERRORS
                + "Unknown property: 'publih'. Did you mean: 'publish'?";
            assertEquals(errorMsg, ex.getMessage());
            assertEquals(testDirectory + "/" + YamlVerifyUtility.DOCKSTOREYML + YamlVerifyUtility.VALID_YAML_ONLY + System.lineSeparator(), systemOutRule.getText());
            systemOutRule.clear();
        }
    }

    @Test
    void dotGitHubDirectory() {
        String testDirectory = "src/test/resources/YamlVerifyTestDirectory/github-test/correct-directory/.github";
        try {
            YamlVerifyUtility.dockstoreValidate(testDirectory);
            String successMsg = testDirectory + "/" + YamlVerifyUtility.DOCKSTOREYML + YamlVerifyUtility.VALID_YAML_ONLY + System.lineSeparator()
                + testDirectory + "/" + YamlVerifyUtility.DOCKSTOREYML + YamlVerifyUtility.VALID_DOCKSTORE_YML + System.lineSeparator();
            assertEquals(successMsg, systemOutRule.getText());
            systemOutRule.clear();
        } catch (YamlVerifyUtility.ValidateYamlException ex) {
            fail("Threw exception when it should've passed");
        }
        testDirectory = "src/test/resources/YamlVerifyTestDirectory/github-test/some-files-present";
        try {
            YamlVerifyUtility.dockstoreValidate(testDirectory + "/" + YamlVerifyUtility.GITHUB_DIRECTORY_NAME);
            fail("non-present test files not caught");
        } catch (YamlVerifyUtility.ValidateYamlException ex) {
            String errorMsg = YamlVerifyUtility.MISSING_FILE_ERROR
                + testDirectory + "/dockstore.wdl.json" + YamlVerifyUtility.FILE_DOES_NOT_EXIST + System.lineSeparator()
                + testDirectory + "/Dockstore.cwl" + YamlVerifyUtility.FILE_DOES_NOT_EXIST;
            assertEquals(errorMsg, ex.getMessage());
        }
    }

    @Test
    void invalidSubclass() {
        String testDirectory = "src/test/resources/YamlVerifyTestDirectory/invalid-subclass";
        try {
            YamlVerifyUtility.dockstoreValidate(testDirectory);
        } catch (YamlVerifyUtility.ValidateYamlException ex) {
            String errorMsg = YamlVerifyUtility.INVALID_DOCKSTORE_YML + testDirectory + "/" + YamlVerifyUtility.DOCKSTOREYML
                    + YamlVerifyUtility.CONTAINS_ERRORS
                    // The below part of the error message is generated by this line,
                    // https://github.com/dockstore/dockstore-cli/blob/5aac6e6d221b7618bc9769a95834f73f37f75aaf/dockstore-client/src/main/java/io/dockstore/client/cli/YamlVerifyUtility.java#L188
                    + "Property \"workflows[0].subclass\" must be a supported descriptor language (\"CWL\", \"WDL\", " +
                    "\"GALAXY\", or \"NFL\") (current value: \"INVALID_SUBCLASS\")";
            assertEquals(errorMsg, ex.getMessage());
        }
    }


    @Test
    void noPrimaryDescriptorPath() {
        final String baseTestDirectory = "src/test/resources/YamlVerifyTestDirectory/no-primary-descriptor-path/";
        List<String> directoryEnds = Arrays.asList(TOOL, WORKFLOW);
        for (String directoryEnd : directoryEnds) {
            try {
                String testDirectory = baseTestDirectory + directoryEnd;
                YamlVerifyUtility.dockstoreValidate(testDirectory);
                fail("A .dockstore.yml that does not contain as primary descriptor path passed");
            } catch (YamlVerifyUtility.ValidateYamlException ex) {
                assertTrue(ex.getMessage().contains("primaryDescriptorPath\" is missing but required"));
            }
        }
    }

    @Test
    void emptyPrimaryDescriptorPath() {
        final String baseTestDirectory = "src/test/resources/YamlVerifyTestDirectory/empty-primary-descriptor-path/";
        List<String> directoryEnds = Arrays.asList(TOOL, WORKFLOW);
        for (String directoryEnd : directoryEnds) {
            try {
                String testDirectory = baseTestDirectory + directoryEnd;
                YamlVerifyUtility.dockstoreValidate(testDirectory);
                fail("A .dockstore.yml that has an empty primary descriptor path passed");
            } catch (YamlVerifyUtility.ValidateYamlException ex) {
                assertTrue(ex.getMessage().contains("primaryDescriptorPath\" is missing but required"));
            }
        }
    }


}


