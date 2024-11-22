/*
 *    Copyright 2018 OICR
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static uk.org.webcompere.systemstubs.SystemStubs.catchSystemExit;

import com.google.common.io.Files;
import com.google.gson.Gson;
import io.dockstore.client.cli.nested.AbstractEntryClient;
import io.dockstore.client.cli.nested.LanguageClientFactory;
import io.dockstore.client.cli.nested.LanguageClientInterface;
import io.dockstore.client.cli.nested.ToolClient;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.MuteForSuccessfulTests;
import io.dockstore.common.ToolTest;
import io.dockstore.common.WDLFileProvisioning;
import io.dockstore.common.WdlBridge;
import io.dockstore.openapi.client.ApiException;
import io.dropwizard.testing.ResourceHelpers;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;
import wdl.draft3.parser.WdlParser;

/**
 * This tests integration with the CromWell engine and what will eventually be wdltool.
 *
 * @author dyuen
 */
@ExtendWith(SystemStubsExtension.class)
@ExtendWith(MuteForSuccessfulTests.class)
@Tag(ConfidentialTest.NAME)
@Tag(ToolTest.NAME)
class CromwellIT {

    @SystemStub
    public final SystemOut systemOutRule = new SystemOut();

    @SystemStub
    public final SystemErr systemErrRule = new SystemErr();

    @Test
    void runWDLWorkflow() throws IOException, ApiException {
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));
        AbstractEntryClient main = new ToolClient(client, false);
        LanguageClientInterface wdlClient = LanguageClientFactory.createLanguageCLient(main, DescriptorLanguage.WDL)
            .orElseThrow(RuntimeException::new);
        File workflowFile = new File(ResourceHelpers.resourceFilePath("wdl.wdl"));
        File parameterFile = new File(ResourceHelpers.resourceFilePath("wdl.json"));
        // run a workflow
        final long run = wdlClient.launch(workflowFile.getAbsolutePath(), true, null, parameterFile.getAbsolutePath(), null, null);
        assertEquals(0, run);
    }

    @Test
    void failRunWDLWorkflow() throws Exception {
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));
        AbstractEntryClient main = new ToolClient(client, false);
        LanguageClientInterface wdlClient = LanguageClientFactory.createLanguageCLient(main, DescriptorLanguage.WDL)
            .orElseThrow(RuntimeException::new);
        File workflowFile = new File(ResourceHelpers.resourceFilePath("wdl.wdl"));
        File parameterFile = new File(ResourceHelpers.resourceFilePath("wdl_wrong.json"));
        // run a workflow
        int exitCode = catchSystemExit(() -> wdlClient.launch(workflowFile.getAbsolutePath(), true, null, parameterFile.getAbsolutePath(), null, null));
        assertEquals(Client.IO_ERROR, exitCode);
    }

    @Test
    void fileProvisioning() throws IOException, ApiException {
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));
        AbstractEntryClient main = new ToolClient(client, false);
        LanguageClientInterface wdlClient = LanguageClientFactory.createLanguageCLient(main, DescriptorLanguage.WDL)
            .orElseThrow(RuntimeException::new);
        File workflowFile = new File(ResourceHelpers.resourceFilePath("wdlfileprov.wdl"));
        File parameterFile = new File(ResourceHelpers.resourceFilePath("wdlfileprov.json"));
        WdlBridge wdlBridge = new WdlBridge();
        Map<String, String> wdlInputs = null;
        try {
            wdlInputs = wdlBridge.getInputFiles(workflowFile.getAbsolutePath(), "/wdlfileprov.wdl");
        } catch (WdlParser.SyntaxError ex) {
            fail("Should not have any issue parsing file");
        }

        WDLFileProvisioning wdlFileProvisioning = new WDLFileProvisioning(ResourceHelpers.resourceFilePath("config_file.txt"));
        Gson gson = new Gson();
        String jsonString;

        final File tempDir = Files.createTempDir();

        jsonString = FileUtils.readFileToString(parameterFile, StandardCharsets.UTF_8);
        Map<String, Object> inputJson = gson.fromJson(jsonString, HashMap.class);

        Map<String, Object> fileMap = wdlFileProvisioning.pullFiles(inputJson, wdlInputs);

        String newJsonPath = wdlFileProvisioning.createUpdatedInputsJson(inputJson, fileMap);
        // run a workflow
        final long run = wdlClient.launch(workflowFile.getAbsolutePath(), true, null, newJsonPath, tempDir.getAbsolutePath(), null);
        assertEquals(0, run);
        // let's check that provisioning out occurred
        final Collection<File> files = FileUtils.listFiles(tempDir, null, true);
        assertEquals(2, files.size());
    }

    /**
     * This tests compatibility with Cromwell 30.2 by running a workflow (https://github.com/dockstore/dockstore/issues/1211)
     */
    @Test
    void testRunWorkflow() throws IOException, ApiException {
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));
        AbstractEntryClient main = new ToolClient(client, false);
        LanguageClientInterface wdlClient = LanguageClientFactory.createLanguageCLient(main, DescriptorLanguage.WDL)
            .orElseThrow(RuntimeException::new);
        File workflowFile = new File(ResourceHelpers.resourceFilePath("hello_world.wdl"));
        File parameterFile = new File(ResourceHelpers.resourceFilePath("hello_world.json"));
        // run a workflow
        final long run = wdlClient.launch(workflowFile.getAbsolutePath(), true, null, parameterFile.getAbsolutePath(), null, null);
        assertEquals(0, run);
    }

    /**
     * This tests that backslashes are not removed from the input and show up when the input is echoed by the workflow
     */
    @Test
    void testRunWorkflowWithBackslashInInput() throws IOException, ApiException {
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));
        AbstractEntryClient main = new ToolClient(client, false);
        LanguageClientInterface wdlClient = LanguageClientFactory.createLanguageCLient(main, DescriptorLanguage.WDL)
                .orElseThrow(RuntimeException::new);
        File workflowFile = new File(ResourceHelpers.resourceFilePath("pass_escape_in_input.wdl"));
        File parameterFile = new File(ResourceHelpers.resourceFilePath("pass_escape_in_input.wdl.json"));
        // run a workflow
        systemOutRule.clear();
        final long run = wdlClient.launch(workflowFile.getAbsolutePath(), true, null, parameterFile.getAbsolutePath(), null, null);
        assertEquals(0, run);
        assertTrue(systemOutRule.getText().contains("\"test_location.find_tools.result\": \"t t\\t\\\\t\""));
    }
}
