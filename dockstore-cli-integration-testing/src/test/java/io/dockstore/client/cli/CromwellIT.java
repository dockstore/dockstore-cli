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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import com.google.common.io.Files;
import com.google.gson.Gson;
import io.dockstore.client.cli.nested.AbstractEntryClient;
import io.dockstore.client.cli.nested.LanguageClientFactory;
import io.dockstore.client.cli.nested.LanguageClientInterface;
import io.dockstore.client.cli.nested.ToolClient;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.WDLFileProvisioning;
import io.dockstore.common.WdlBridge;
import io.dropwizard.testing.ResourceHelpers;
import io.swagger.client.ApiException;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.experimental.categories.Category;
import wdl.draft3.parser.WdlParser;

/**
 * This tests integration with the CromWell engine and what will eventually be wdltool.
 *
 * @author dyuen
 */
public class CromwellIT {

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final ExpectedSystemExit exit = ExpectedSystemExit.none();

    @Test
    public void runWDLWorkflow() throws IOException, ApiException {
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));
        AbstractEntryClient main = new ToolClient(client, false);
        LanguageClientInterface wdlClient = LanguageClientFactory.createLanguageCLient(main, DescriptorLanguage.WDL)
            .orElseThrow(RuntimeException::new);
        File workflowFile = new File(ResourceHelpers.resourceFilePath("wdl.wdl"));
        File parameterFile = new File(ResourceHelpers.resourceFilePath("wdl.json"));
        // run a workflow
        final long run = wdlClient.launch(workflowFile.getAbsolutePath(), true, null, parameterFile.getAbsolutePath(), null, null);
        Assert.assertEquals(0, run);
    }

    @Test
    public void failRunWDLWorkflow() throws IOException, ApiException {
        exit.expectSystemExitWithStatus(3);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));
        AbstractEntryClient main = new ToolClient(client, false);
        LanguageClientInterface wdlClient = LanguageClientFactory.createLanguageCLient(main, DescriptorLanguage.WDL)
            .orElseThrow(RuntimeException::new);
        File workflowFile = new File(ResourceHelpers.resourceFilePath("wdl.wdl"));
        File parameterFile = new File(ResourceHelpers.resourceFilePath("wdl_wrong.json"));
        // run a workflow
        final long run = wdlClient.launch(workflowFile.getAbsolutePath(), true, null, parameterFile.getAbsolutePath(), null, null);
        Assert.assertTrue(run != 0);
    }

    @Test
    @Category(ConfidentialTest.class)
    public void fileProvisioning() throws IOException, ApiException {
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
            wdlInputs = wdlBridge.getInputFiles(workflowFile.getAbsolutePath());
        } catch (WdlParser.SyntaxError ex) {
            Assert.fail("Should not have any issue parsing file");
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
        Assert.assertEquals(0, run);
        // let's check that provisioning out occured
        final Collection<File> files = FileUtils.listFiles(tempDir, null, true);
        Assert.assertEquals(2, files.size());
    }

    /**
     * This tests compatibility with Cromwell 30.2 by running a workflow (https://github.com/dockstore/dockstore/issues/1211)
     */
    @Test
    public void testRunWorkflow() throws IOException, ApiException {
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));
        AbstractEntryClient main = new ToolClient(client, false);
        LanguageClientInterface wdlClient = LanguageClientFactory.createLanguageCLient(main, DescriptorLanguage.WDL)
            .orElseThrow(RuntimeException::new);
        File workflowFile = new File(ResourceHelpers.resourceFilePath("hello_world.wdl"));
        File parameterFile = new File(ResourceHelpers.resourceFilePath("hello_world.json"));
        // run a workflow
        final long run = wdlClient.launch(workflowFile.getAbsolutePath(), true, null, parameterFile.getAbsolutePath(), null, null);
        Assert.assertEquals(0, run);
    }
}
