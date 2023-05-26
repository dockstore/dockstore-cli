/*
 *    Copyright 2017 OICR
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import io.dockstore.client.cli.nested.WorkflowClient;
import io.dockstore.common.MuteForSuccessfulTests;
import io.dockstore.common.Utilities;
import io.dockstore.openapi.client.model.WorkflowSubClass;
import io.dropwizard.testing.ResourceHelpers;
import io.swagger.client.api.UsersApi;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.ToolDescriptor;
import io.swagger.client.model.Workflow;
import io.swagger.client.model.WorkflowVersion;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

import static io.dockstore.client.cli.ArgumentUtility.CONVERT;
import static io.dockstore.client.cli.ArgumentUtility.DOWNLOAD;
import static io.dockstore.client.cli.ArgumentUtility.ERROR_MESSAGE_START;
import static io.dockstore.client.cli.ArgumentUtility.LAUNCH;
import static io.dockstore.client.cli.Client.CONFIG;
import static io.dockstore.client.cli.Client.IO_ERROR;
import static io.dockstore.client.cli.Client.PLUGIN;
import static io.dockstore.client.cli.Client.SCRIPT_FLAG;
import static io.dockstore.client.cli.Client.TOOL;
import static io.dockstore.client.cli.Client.WORKFLOW;
import static io.dockstore.client.cli.nested.AbstractEntryClient.CWL_2_JSON;
import static io.dockstore.client.cli.nested.WesCommandParser.ENTRY;
import static io.dockstore.client.cli.nested.WesCommandParser.JSON;
import static io.dockstore.common.DescriptorLanguage.CWL;
import static io.dockstore.common.DescriptorLanguage.WDL;
import static io.github.collaboratory.wdl.WDLClient.CALL;
import static io.github.collaboratory.wdl.WDLClient.COMMAND;
import static io.github.collaboratory.wdl.WDLClient.OUTPUT;
import static io.github.collaboratory.wdl.WDLClient.TASK;
import static io.github.collaboratory.wdl.WDLClient.WDL_CHECK_ERROR_MESSAGE;
import static io.github.collaboratory.wdl.WDLClient.WDL_CHECK_WARNING_MESSAGE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static uk.org.webcompere.systemstubs.SystemStubs.catchSystemExit;

@ExtendWith(SystemStubsExtension.class)
@ExtendWith(MuteForSuccessfulTests.class)
@ExtendWith(LaunchTestIT.TestStatus.class)
class LaunchTestIT {
    public static final long LAST_MODIFIED_TIME_100 = 100L;
    public static final long LAST_MODIFIED_TIME_1000 = 1000L;

    //create tests that will call client.checkEntryFile for workflow launch with different files and descriptor

    @SystemStub
    public final SystemOut systemOutRule = new SystemOut();

    @SystemStub
    public final SystemErr systemErrRule = new SystemErr();

    @Test
    void wdlCorrect() {
        //Test when content and extension are wdl  --> no need descriptor
        File helloWDL = new File(ResourceHelpers.resourceFilePath("hello.wdl"));
        File helloJSON = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add("--local-entry");
        args.add(JSON);
        args.add(helloJSON.getAbsolutePath());

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        Client.SCRIPT.set(true);
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(helloWDL.getAbsolutePath(), args, null);

        assertTrue(systemOutRule.getText().contains("Cromwell exit code: 0"), "output should include a successful cromwell run");
    }

    @Test
    void cwlCorrect() {
        //Test when content and extension are cwl  --> no need descriptor

        File cwlFile = new File(ResourceHelpers.resourceFilePath("1st-workflow.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("1st-workflow-job.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add("--local-entry");
        args.add(JSON);
        args.add(cwlJSON.getAbsolutePath());

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        // do not use a cache
        runWorkflow(cwlFile, args, api, usersApi, client, false);
    }

    @Test
    void cwlSupportsVersion12() {
        // 1st-workflow-12.cwl is a version of 1st-workflow.cwl to which key new CWL version 1.2 features have been added.
        File cwlFile = new File(ResourceHelpers.resourceFilePath("1st-workflow-12.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("1st-workflow-job.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add("--local-entry");
        args.add(JSON);
        args.add(cwlJSON.getAbsolutePath());

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        // do not use a cache
        runWorkflow(cwlFile, args, api, usersApi, client, false);
    }

    /**
     * cwltool should fail to run a workflow with a step of class "Operation"
     */
    @Test
    void cwlFailsStepWithClassOperation() throws Exception {
        File cwlFile = new File(ResourceHelpers.resourceFilePath("class-operation.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("1st-workflow-job.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add("--local-entry");
        args.add(JSON);
        args.add(cwlJSON.getAbsolutePath());

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        // do not use a cache
        catchSystemExit(() -> runWorkflow(cwlFile, args, api, usersApi, client, false));
        assertTrue(systemErrRule.getText().contains("Workflow has unrunnable abstract Operation"),
                "output should include a failed cwltool run");
    }

    @Test
    void wdlMetadataNoopPluginTest() {
        //Test when content and extension are wdl  --> no need descriptor
        File helloWDL = new File(ResourceHelpers.resourceFilePath("hello.wdl"));
        File helloJSON = new File(ResourceHelpers.resourceFilePath("hello.metadata.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add("--local-entry");
        args.add(JSON);
        args.add(helloJSON.getAbsolutePath());
        args.add("--wdl-output-target");
        args.add("noop://nowhere.test");

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        Client.SCRIPT.set(true);
        client.setConfigFile(ResourceHelpers.resourceFilePath("config.withTestPlugin"));

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(helloWDL.getAbsolutePath(), args, null);

        assertTrue(systemOutRule.getText().contains("Cromwell exit code: 0"), "output should include a successful cromwell run");
        assertTrue(systemOutRule.getText().contains("really cool metadata"),
                "output should include a noop " + PLUGIN + " run with metadata");
    }

    @Test
    void cwlMetadataNoopPluginTest() {

        File cwlFile = new File(ResourceHelpers.resourceFilePath("1st-workflow.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("collab-cwl-noop-job.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add("--local-entry");
        args.add(JSON);
        args.add(cwlJSON.getAbsolutePath());

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        Client.SCRIPT.set(true);
        client.setConfigFile(ResourceHelpers.resourceFilePath("config.withTestPlugin"));

        PluginClient.handleCommand(Lists.newArrayList(DOWNLOAD), Utilities.parseConfig(client.getConfigFile()));

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(cwlFile.getAbsolutePath(), args, null);

        assertTrue(systemOutRule.getText().contains("Final process status is success"),
                "output should include a successful cwltool run");
        assertTrue(systemOutRule.getText().contains("really cool metadata"),
                "output should include a noop " + PLUGIN + " run with metadata");
    }



    @Test
    void runWorkflowConvert() {
        File cwlFile = new File(ResourceHelpers.resourceFilePath("smcFusionQuant-INTEGRATE-workflow.cwl"));

        ArrayList<String> args = new ArrayList<>();
        args.add(WORKFLOW);
        args.add(CONVERT);
        args.add(CWL_2_JSON);
        args.add("--cwl");
        args.add(cwlFile.getAbsolutePath());

        runClientCommand(args);
        final String log = systemOutRule.getText();
        Gson gson = new Gson();
        final Map<String, Map<String, Object>> map = gson.fromJson(log, Map.class);
        assertEquals(4, map.size());
        assertTrue(
                map.containsKey("TUMOR_FASTQ_1") && map.containsKey("TUMOR_FASTQ_2") && map.containsKey("index") && map.containsKey("OUTPUT"));
    }

    @Test
    void cwlCorrectWithCache() {
        //Test when content and extension are cwl  --> no need descriptor

        File cwlFile = new File(ResourceHelpers.resourceFilePath("1st-workflow.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("1st-workflow-job.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add("--local-entry");
        args.add(JSON);
        args.add(cwlJSON.getAbsolutePath());

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        // use a cache
        runWorkflow(cwlFile, args, api, usersApi, client, true);
    }

    private void runClientCommand(ArrayList<String> args) {
        args.add(0, ResourceHelpers.resourceFilePath("config"));
        args.add(0, CONFIG);
        args.add(0, SCRIPT_FLAG);
        Client.main(args.toArray(new String[0]));
    }

    private void runClientCommandConfig(ArrayList<String> args, File config) {
        //used to run client with a specified config file
        args.add(0, config.getPath());
        args.add(0, CONFIG);
        args.add(0, SCRIPT_FLAG);
        Client.main(args.toArray(new String[0]));
    }


    private void runWorkflow(File cwlFile, ArrayList<String> args, WorkflowsApi api, UsersApi usersApi, Client client, boolean useCache) {
        client.setConfigFile(ResourceHelpers.resourceFilePath(useCache ? "config.withCache" : "config"));
        Client.SCRIPT.set(true);
        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(cwlFile.getAbsolutePath(), args, null);

        assertTrue(systemOutRule.getText().contains("Final process status is success"),
                "output should include a successful cwltool run");
    }

    @Test
    void cwlWrongExt() {
        //Test when content = cwl but ext = wdl, ask for descriptor

        File file = new File(ResourceHelpers.resourceFilePath("wrongExtcwl.wdl"));
        File json = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add("--local-entry");
        args.add(file.getAbsolutePath());
        args.add(JSON);
        args.add(json.getAbsolutePath());

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, null);

        assertTrue(systemOutRule.getText()
            .contains("Entry file is ambiguous, please re-enter command with '--descriptor <descriptor>' at the end"),
                "output should tell user to specify the descriptor");
    }

    @Test
    void cwlWrongExtForce() {
        //Test when content = cwl but ext = wdl, descriptor provided --> CWL

        File file = new File(ResourceHelpers.resourceFilePath("wrongExtcwl.wdl"));
        File json = new File(ResourceHelpers.resourceFilePath("1st-workflow-job.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add("--local-entry");
        args.add(file.getAbsolutePath());
        args.add(JSON);
        args.add(json.getAbsolutePath());
        args.add("--descriptor");
        args.add(CWL.getShortName());

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));
        Client.SCRIPT.set(true);

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, CWL.getShortName());

        assertTrue(
                systemOutRule.getText().contains("This is a CWL file.. Please put the correct extension to the entry file name."),
                "output should include a successful cromwell run");
    }

    @Test
    void wdlWrongExt() {
        //Test when content = wdl but ext = cwl, ask for descriptor

        File file = new File(ResourceHelpers.resourceFilePath("wrongExtwdl.cwl"));
        File json = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add("--local-entry");
        args.add(JSON);
        args.add(json.getAbsolutePath());

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));
        Client.SCRIPT.set(true);

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, null);

        assertTrue(systemOutRule.getText()
            .contains("Entry file is ambiguous, please re-enter command with '--descriptor <descriptor>' at the end"),
                "output should include a successful cromwell run");
    }

    @Test
    void randomExtCwl() {
        //Test when content is random, but ext = cwl
        File file = new File(ResourceHelpers.resourceFilePath("random.cwl"));
        File json = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add("--local-entry");
        args.add(JSON);
        args.add(json.getAbsolutePath());

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));
        Client.SCRIPT.set(true);

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, null);

        assertTrue(systemOutRule.getText()
            .contains("Entry file is ambiguous, please re-enter command with '--descriptor <descriptor>' at the end"),
                "output should include a successful cromwell run");
    }

    @Test
    void randomExtWdl() {
        //Test when content is random, but ext = wdl
        File file = new File(ResourceHelpers.resourceFilePath("random.wdl"));
        File json = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add("--local-entry");
        args.add(JSON);
        args.add(json.getAbsolutePath());

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));
        Client.SCRIPT.set(true);

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, null);

        assertTrue(systemOutRule.getText()
            .contains("Entry file is ambiguous, please re-enter command with '--descriptor <descriptor>' at the end"),
                "output should include a successful cromwell run");
    }

    @Test
    void wdlWrongExtForce() {
        //Test when content = wdl but ext = cwl, descriptor provided --> WDL

        File file = new File(ResourceHelpers.resourceFilePath("wrongExtwdl.cwl"));
        File json = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add(ENTRY);
        args.add(file.getAbsolutePath());
        args.add("--local-entry");
        args.add(JSON);
        args.add(json.getAbsolutePath());
        args.add("--descriptor");
        args.add(WDL.getShortName());

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));
        Client.SCRIPT.set(true);

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, WDL.getShortName());

        assertTrue(
                systemOutRule.getText().contains("This is a WDL file.. Please put the correct extension to the entry file name."),
                "output should include a successful cromwell run");
    }

    @Test
    void cwlWrongExtForce1() throws Exception {
        //Test when content = cwl but ext = wdl, descriptor provided --> !CWL

        File file = new File(ResourceHelpers.resourceFilePath("wrongExtcwl.wdl"));
        File json = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add(ENTRY);
        args.add("wrongExtcwl.wdl");
        args.add("--local-entry");
        args.add(JSON);
        args.add(json.getAbsolutePath());
        args.add("--descriptor");
        args.add(WDL.getShortName());

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));
        Client.SCRIPT.set(true);

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        catchSystemExit(() -> workflowClient.checkEntryFile(file.getAbsolutePath(), args, WDL.getShortName()));
    }

    @Test
    void wdlWrongExtForce1() throws Exception {
        //Test when content = wdl but ext = cwl, descriptor provided --> !WDL

        File file = new File(ResourceHelpers.resourceFilePath("wrongExtwdl.cwl"));
        File json = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add(ENTRY);
        args.add("wrongExtwdl.cwl");
        args.add("--local-entry");
        args.add(JSON);
        args.add(json.getAbsolutePath());
        args.add("--descriptor");
        args.add(CWL.getShortName());

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));
        Client.SCRIPT.set(true);

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        catchSystemExit(() -> workflowClient.checkEntryFile(file.getAbsolutePath(), args, CWL.getShortName()));
    }

    @Test
    void cwlNoExt() {
        //Test when content = cwl but no ext

        File file = new File(ResourceHelpers.resourceFilePath("cwlNoExt"));
        File json = new File(ResourceHelpers.resourceFilePath("1st-workflow-job.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add(ENTRY);
        args.add("cwlNoExt");
        args.add("--local-entry");
        args.add(JSON);
        args.add(json.getAbsolutePath());

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));
        Client.SCRIPT.set(true);
        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, null);

        assertTrue(systemOutRule.getText().contains("This is a CWL file.. Please put an extension to the entry file name."),
                "output should contain a validation issue");
    }

    @Test
    void wdlNoExt() {
        //Test when content = wdl but no ext

        File file = new File(ResourceHelpers.resourceFilePath("wdlNoExt"));
        File json = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add(ENTRY);
        args.add(file.getAbsolutePath());
        args.add("--local-entry");
        args.add(JSON);
        args.add(json.getAbsolutePath());

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));
        Client.SCRIPT.set(true);

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, null);

        assertTrue(systemOutRule.getText().contains("This is a WDL file.. Please put an extension to the entry file name."),
                "output should include a successful cromwell run");

    }

    @Test
    void randomNoExt() throws Exception {
        //Test when content is neither CWL nor WDL, and there is no extension

        File file = new File(ResourceHelpers.resourceFilePath("random"));
        File json = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add(ENTRY);
        args.add(file.getAbsolutePath());
        args.add("--local-entry");
        args.add(JSON);
        args.add(json.getAbsolutePath());

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));
        Client.SCRIPT.set(true);

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        catchSystemExit(() -> workflowClient.checkEntryFile(file.getAbsolutePath(), args, null));
        assertTrue(systemErrRule.getText()
            .contains("Entry file is invalid. Please enter a valid workflow file with the correct extension on the file name."),
                "output should include an error message of invalid file");
    }

    @Test
    void randomWithExt() throws Exception {
        //Test when content is neither CWL nor WDL, and there is no extension

        File file = new File(ResourceHelpers.resourceFilePath("hello.txt"));
        File json = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add(ENTRY);
        args.add(file.getAbsolutePath());
        args.add("--local-entry");
        args.add(JSON);
        args.add(json.getAbsolutePath());

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));
        Client.SCRIPT.set(true);

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        catchSystemExit(() -> workflowClient.checkEntryFile(file.getAbsolutePath(), args, null));
        assertTrue(systemErrRule.getText()
                .contains("Entry file is invalid. Please enter a valid workflow file with the correct extension on the file name."),
                "output should include an error message of invalid file");
    }

    @Test
    void wdlNoTask() throws Exception {
        //Test when content is missing 'task'

        File file = new File(ResourceHelpers.resourceFilePath("noTask.wdl"));
        File json = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add(ENTRY);
        args.add(file.getAbsolutePath());
        args.add("--local-entry");
        args.add(JSON);
        args.add(json.getAbsolutePath());

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));
        Client.SCRIPT.set(true);

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        catchSystemExit(() -> workflowClient.checkEntryFile(file.getAbsolutePath(), args, null));
        assertTrue(systemOutRule.getText().contains(WDL_CHECK_WARNING_MESSAGE + "'" + TASK + "' '" + COMMAND + "' '" + OUTPUT + "'"),
                "output should include an error message and exit");

        assertTrue(systemErrRule.getText().contains(ERROR_MESSAGE_START));
    }

    @Test
    void wdlWithSubWorkflows() {

        //Tests if file provisioning can handle a json parameter that specifies a file path containing spaces
        File workflowWDL = new File(ResourceHelpers.resourceFilePath("sub-workflow-test.wdl"));
        File workflowJSON = new File(ResourceHelpers.resourceFilePath("sub-workflow-test.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add(WORKFLOW);
        args.add(LAUNCH);
        args.add("--local-entry");
        args.add(workflowWDL.getAbsolutePath());
        args.add(JSON);
        args.add(workflowJSON.getPath());

        try {
            File config = new File(ResourceHelpers.resourceFilePath("clientConfig"));
            runClientCommandConfig(args, config);
        } catch (Exception ex) {
            fail("Workflow that should pass caused an exception");
        }

    }


    @Test
    void wdlNoCommand() throws Exception {
        //Test when content is missing 'command'

        File file = new File(ResourceHelpers.resourceFilePath("noCommand.wdl"));
        File json = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add(ENTRY);
        args.add(file.getAbsolutePath());
        args.add("--local-entry");
        args.add(JSON);
        args.add(json.getAbsolutePath());

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));
        Client.SCRIPT.set(true);

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        catchSystemExit(() -> workflowClient.checkEntryFile(file.getAbsolutePath(), args, null));
        assertTrue(systemOutRule.getText().contains(WDL_CHECK_WARNING_MESSAGE + "'" + COMMAND + "'"),
                "output should contain a warning that command is missing");
        assertTrue(systemErrRule.getText().contains(ERROR_MESSAGE_START),
                "given that noCommand.wdl is an invalid WDL, an error message should be given");

    }

    @Test
    void wdlNoWfCall() throws Exception {
        //Test when content is missing 'workflow' and 'call'

        File file = new File(ResourceHelpers.resourceFilePath("noWfCall.wdl"));
        File json = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add(ENTRY);
        args.add(file.getAbsolutePath());
        args.add("--local-entry");
        args.add(JSON);
        args.add(json.getAbsolutePath());

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));
        Client.SCRIPT.set(true);

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        catchSystemExit(() -> workflowClient.checkEntryFile(file.getAbsolutePath(), args, null));
        assertTrue(systemErrRule.getText().contains(WDL_CHECK_ERROR_MESSAGE + "'" + WORKFLOW + "'"),
                "output should include an error message and exit");
    }

    @Test
    void wdlWorkflowAndCallOnly() throws Exception {
        //Test when content only contains 'workflow' and 'call'

        File file = new File(ResourceHelpers.resourceFilePath("wfAndCallOnly.wdl"));
        File json = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add(ENTRY);
        args.add(file.getAbsolutePath());
        args.add("--local-entry");
        args.add(JSON);
        args.add(json.getAbsolutePath());

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));
        Client.SCRIPT.set(true);

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        catchSystemExit(() -> workflowClient.checkEntryFile(file.getAbsolutePath(), args, null));
        assertTrue(systemOutRule.getText().contains(WDL_CHECK_WARNING_MESSAGE + "'" + TASK + "' '" + COMMAND + "' '" + OUTPUT + "'"),
                "output should contain a warning that task, command and output are missing");
        assertTrue(systemErrRule.getText().contains(ERROR_MESSAGE_START),
                "given that wfAndCallOnly.wdl is an invalid WDL, an error message should be given");
    }

    @Test
    void wdlCommandOnly() throws Exception {
        //Test when content is only 'command'

        File file = new File(ResourceHelpers.resourceFilePath("commandOnly.wdl"));
        File json = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add(ENTRY);
        args.add(file.getAbsolutePath());
        args.add("--local-entry");
        args.add(JSON);
        args.add(json.getAbsolutePath());

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));
        Client.SCRIPT.set(true);

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        catchSystemExit(() -> workflowClient.checkEntryFile(file.getAbsolutePath(), args, null));
        assertTrue(systemOutRule.getText().contains(WDL_CHECK_WARNING_MESSAGE + "'" + TASK + "' '" +  OUTPUT + "'"),
                "output should include an error message that task and output are missing");
        assertTrue(systemErrRule.getText().contains(WDL_CHECK_ERROR_MESSAGE + "'" + WORKFLOW + "' '" + CALL + "'"),
                "stderr should have an error that says workflow and call are missing");


    }

    @Test
    void cwlNoInput() throws Exception {
        //Test when content is missing 'input'

        File file = new File(ResourceHelpers.resourceFilePath("noInput.cwl"));
        File json = new File(ResourceHelpers.resourceFilePath("1st-workflow-job.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add(ENTRY);
        args.add(file.getAbsolutePath());
        args.add("--local-entry");
        args.add(JSON);
        args.add(json.getAbsolutePath());

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));
        Client.SCRIPT.set(true);

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        catchSystemExit(() ->  workflowClient.checkEntryFile(file.getAbsolutePath(), args, null));
        assertTrue(systemErrRule.getText().contains("Required fields that are missing from CWL file : 'inputs'"),
                "output should include an error message and exit");
    }

    @Test
    @Disabled("Detection code is not robust enough for biowardrobe wdl using --local-entry")
    void toolAsWorkflow() throws Exception {
        File cwlFile = new File(ResourceHelpers.resourceFilePath("dir6.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("dir6.cwl.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add(WORKFLOW);
        args.add(LAUNCH);
        args.add("--local-entry");
        args.add(cwlFile.getAbsolutePath());
        args.add(JSON);
        args.add(cwlJSON.getAbsolutePath());


        catchSystemExit(() -> runClientCommand(args));
        assertTrue(systemErrRule.getText().contains("Expected a workflow but the"), "Out should suggest to run as tool instead");
    }

    @Test
    void workflowAsTool() throws Exception {
        File file = new File(ResourceHelpers.resourceFilePath("noInput.cwl"));
        File json = new File(ResourceHelpers.resourceFilePath("1st-workflow-job.json"));
        ArrayList<String> args = new ArrayList<>();
        args.add(TOOL);
        args.add(LAUNCH);
        args.add("--local-entry");
        args.add(file.getAbsolutePath());
        args.add(JSON);
        args.add(json.getAbsolutePath());

        catchSystemExit(() -> runClientCommand(args));
        assertTrue(systemErrRule.getText().contains("Expected a tool but the"), "Out should suggest to run as workflow instead");
    }

    @Test
    void cwlNoOutput() throws Exception {
        File file = new File(ResourceHelpers.resourceFilePath("noOutput.cwl"));
        File json = new File(ResourceHelpers.resourceFilePath("1st-workflow-job.json"));
        ArrayList<String> args = new ArrayList<>();
        args.add(TOOL);
        args.add(LAUNCH);
        args.add("--local-entry");
        args.add(file.getAbsolutePath());
        args.add(JSON);
        args.add(json.getAbsolutePath());

        catchSystemExit(() -> runClientCommand(args));
        assertTrue(systemErrRule.getText().contains("Required fields that are missing from CWL file : 'outputs'"),
                "output should include an error message and exit");
    }

    @Test
    void cwlIncompleteOutput() {
        File file = new File(ResourceHelpers.resourceFilePath("incompleteOutput.cwl"));
        File json = new File(ResourceHelpers.resourceFilePath("1st-workflow-job.json"));
        ArrayList<String> args = new ArrayList<>();
        args.add(TOOL);
        args.add(LAUNCH);
        args.add("--local-entry");
        args.add(file.getAbsolutePath());
        args.add(JSON);
        args.add(json.getAbsolutePath());

        runClientCommand(args);
        assertTrue(systemErrRule.getText().contains("\"outputs\" section is not valid"),
                "output should include an error message");
    }

    @Test
    void cwlIdContainsNonWord() throws Exception {
        File file = new File(ResourceHelpers.resourceFilePath("idNonWord.cwl"));
        File json = new File(ResourceHelpers.resourceFilePath("1st-workflow-job.json"));
        ArrayList<String> args = new ArrayList<>();
        args.add(TOOL);
        args.add(LAUNCH);
        args.add("--local-entry");
        args.add(file.getAbsolutePath());
        args.add(JSON);
        args.add(json.getAbsolutePath());

        catchSystemExit(() -> runClientCommand(args));
        assertTrue(systemOutRule.getText().contains("Provisioning your input files to your local machine"),
                "output should have started provisioning");
    }

    @Test
    void cwlMissingIdParameters() throws Exception {
        File file = new File(ResourceHelpers.resourceFilePath("missingIdParameters.cwl"));
        File json = new File(ResourceHelpers.resourceFilePath("1st-workflow-job.json"));
        ArrayList<String> args = new ArrayList<>();
        args.add(TOOL);
        args.add(LAUNCH);
        args.add("--local-entry");
        args.add(file.getAbsolutePath());
        args.add(JSON);
        args.add(json.getAbsolutePath());

        runClientCommand(args);
        assertTrue(systemErrRule.getText().contains("while parsing a block collection"),
                "output should include an error message");
    }

    @Test
    void entry2jsonNoVersion() throws Exception {
        /*
         * Make a runtime JSON template for input to the workflow
         * but don't provide a version at the end of the entry
         * E.g dockstore workflow convert entry2json --entry quay.io/collaboratory/dockstore-tool-linux-sort
         * Dockstore will try to use the 'master' version, however the 'master' version
         * is not valid so Dockstore should print an error message and exit
         * */
        WorkflowVersion aWorkflowVersion1 = new WorkflowVersion();
        aWorkflowVersion1.setName("master");
        aWorkflowVersion1.setValid(false);
        aWorkflowVersion1.setLastModified(LAST_MODIFIED_TIME_100);

        List<WorkflowVersion> listWorkflowVersions = new ArrayList<>();
        listWorkflowVersions.add(aWorkflowVersion1);

        Workflow workflow = new Workflow();
        workflow.setWorkflowVersions(listWorkflowVersions);
        workflow.setLastModified(1);

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();

        doReturn(workflow).when(api).getPublishedWorkflowByPath(anyString(), eq(WorkflowSubClass.BIOWORKFLOW.toString()), eq("versions"), eq(null));

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);

        catchSystemExit(() ->  workflowClient.downloadTargetEntry("quay.io/collaboratory/dockstore-tool-linux-sort", ToolDescriptor.TypeEnum.WDL, false));
        assertTrue(systemErrRule.getText().contains("Cannot use workflow version 'master'"),
                "output should include error message");
    }

    @Test
    void entry2jsonBadVersion() throws Exception {
        /*
         * Make a runtime JSON template for input to the workflow
         * but provide a non existent version at the end of the entry
         * E.g dockstore workflow convert entry2json --entry quay.io/collaboratory/dockstore-tool-linux-sort:2.0.0
         * Dockstore will try to use the last modified version (1.0.0) and print an explanation message.
         * The last modified version is not valid so Dockstore should print an error message and exit
         * */

        WorkflowVersion aWorkflowVersion1 = new WorkflowVersion();
        aWorkflowVersion1.setName("1.0.0");
        aWorkflowVersion1.setValid(false);
        aWorkflowVersion1.setLastModified(LAST_MODIFIED_TIME_1000);

        List<WorkflowVersion> listWorkflowVersions = new ArrayList<>();
        listWorkflowVersions.add(aWorkflowVersion1);

        Workflow workflow = new Workflow();
        workflow.setWorkflowVersions(listWorkflowVersions);
        workflow.setLastModified(1);

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();

        doReturn(workflow).when(api).getPublishedWorkflowByPath(anyString(), eq(WorkflowSubClass.BIOWORKFLOW.toString()), eq("versions"), eq(null));

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        catchSystemExit(() -> workflowClient.downloadTargetEntry("quay.io/collaboratory/dockstore-tool-linux-sort:2.0.0", ToolDescriptor.TypeEnum.WDL, false));
        assertTrue((systemOutRule.getText().contains("Could not locate workflow with version '2.0.0'") && systemErrRule.getText()
                .contains("Cannot use workflow version '1.0.0'")), "output should include error messages");
    }

    @Test
    void cwl2jsonNoOutput() throws Exception {
        File file = new File(ResourceHelpers.resourceFilePath("noOutput.cwl"));
        ArrayList<String> args = new ArrayList<>();
        args.add(TOOL);
        args.add(CONVERT);
        args.add(CWL_2_JSON);
        args.add("--cwl");
        args.add(file.getAbsolutePath());

        catchSystemExit(() -> runClientCommand(args));
        assertTrue(systemErrRule.getText().contains("\"outputs\" section is not valid"),
                "output should include an error message");
    }

    @Test
    void malJsonWorkflowWdlLocal() throws Exception {
        //checks if json input has broken syntax for workflows

        File helloWdl = new File(ResourceHelpers.resourceFilePath("hello.wdl"));
        File jsonFile = new File(ResourceHelpers.resourceFilePath("testInvalidJSON.json"));
        ArrayList<String> args = new ArrayList<>();
        args.add(WORKFLOW);
        args.add(LAUNCH);
        args.add("--local-entry");
        args.add(helloWdl.getAbsolutePath());
        args.add(JSON);
        args.add(jsonFile.getAbsolutePath());

        File config = new File(ResourceHelpers.resourceFilePath("clientConfig"));
        catchSystemExit(() -> runClientCommandConfig(args, config));
        assertTrue(systemErrRule.getText().contains("Could not launch, syntax error in json file: " + jsonFile),
                "output should include an error message");
    }

    @Test
    void malJsonToolWdlLocal() throws Exception {
        //checks if json input has broken syntax for tools

        File helloWdl = new File(ResourceHelpers.resourceFilePath("hello.wdl"));
        File jsonFile = new File(ResourceHelpers.resourceFilePath("testInvalidJSON.json"));
        ArrayList<String> args = new ArrayList<>();
        args.add(TOOL);
        args.add(LAUNCH);
        args.add("--local-entry");
        args.add(helloWdl.getAbsolutePath());
        args.add(JSON);
        args.add(jsonFile.getAbsolutePath());

        File config = new File(ResourceHelpers.resourceFilePath("clientConfig"));
        catchSystemExit(() -> runClientCommandConfig(args, config));
        assertTrue(systemErrRule.getText().contains("Could not launch, syntax error in json file: " + jsonFile),
                "output should include an error message");
    }

    @Test
    void provisionInputWithPathSpaces() {
        //Tests if file provisioning can handle a json parameter that specifies a file path containing spaces
        File helloWDL = new File(ResourceHelpers.resourceFilePath("helloSpaces.wdl"));
        File helloJSON = new File(ResourceHelpers.resourceFilePath("helloSpaces.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add(WORKFLOW);
        args.add(LAUNCH);
        args.add("--local-entry");
        args.add(helloWDL.getAbsolutePath());
        args.add(JSON);
        args.add(helloJSON.getPath());

        File config = new File(ResourceHelpers.resourceFilePath("clientConfig"));
        runClientCommandConfig(args, config);

        assertTrue(systemOutRule.getText().contains("Cromwell exit code: 0"), "output should include a successful cromwell run");
    }


    @Test
    void missingTestParameterFileWDLFailure() throws Exception {
        // Run a workflow without specifying test parameter files, defer errors to the
        // Cromwell workflow engine.
        File file = new File(ResourceHelpers.resourceFilePath("hello.wdl"));
        ArrayList<String> args = new ArrayList<>();
        args.add(WORKFLOW);
        args.add(LAUNCH);
        args.add("--local-entry");
        args.add(file.getAbsolutePath());

        File config = new File(ResourceHelpers.resourceFilePath("clientConfig"));
        int exitcode = catchSystemExit(() -> runClientCommandConfig(args, config));
        assertEquals(IO_ERROR, exitcode);
        assertTrue(systemOutRule.getText().contains("Required workflow input"),
                "This workflow cannot run without test files, it should raise an exception from the workflow engine");
    }

    @Test
    void missingTestParameterFileWDLSuccess() {
        // Run a workflow without specifying test parameter files, defer errors to the
        // Cromwell workflow engine.
        File file = new File(ResourceHelpers.resourceFilePath("no-input-echo.wdl"));
        ArrayList<String> args = new ArrayList<>();
        args.add(WORKFLOW);
        args.add(LAUNCH);
        args.add("--local-entry");
        args.add(file.getAbsolutePath());

        File config = new File(ResourceHelpers.resourceFilePath("clientConfig"));
        runClientCommandConfig(args, config);
        assertTrue(systemOutRule.getText().contains("Cromwell exit code: 0"), "output should include a successful cromwell run");

    }


    void testCWLWorkflowOrToolWithNoTestParameterFile(String type, File entry, String expectedMessage) {
        systemErrRule.clear();
        ArrayList<String> args = new ArrayList<>();
        args.add(type);
        args.add(LAUNCH);
        args.add("--local-entry");
        args.add(entry.getAbsolutePath());
        File config = new File(ResourceHelpers.resourceFilePath("clientConfig"));
        runClientCommandConfig(args, config);
        assertTrue(systemErrRule.getText().contains(expectedMessage),
                "CWLTool should be able to run this workflow without any problems");
    }

    @Test
    void missingTestParameterFileCWL() {
        testCWLWorkflowOrToolWithNoTestParameterFile(WORKFLOW, new File(ResourceHelpers.resourceFilePath("no-input-echo.cwl")), "[job no-input-echo.cwl] completed success");
        testCWLWorkflowOrToolWithNoTestParameterFile(TOOL, new File(ResourceHelpers.resourceFilePath("no-input-echo.cwl")), "[job no-input-echo.cwl] completed success");
        testCWLWorkflowOrToolWithNoTestParameterFile(WORKFLOW, new File(ResourceHelpers.resourceFilePath("hello_world.cwl")), "[job hello_world.cwl] completed success");
        testCWLWorkflowOrToolWithNoTestParameterFile(TOOL, new File(ResourceHelpers.resourceFilePath("hello_world.cwl")), "[job hello_world.cwl] completed success");
    }

    public static class TestStatus implements org.junit.jupiter.api.extension.TestWatcher {
        @Override
        public void testSuccessful(ExtensionContext context) {
            System.out.printf("Test successful: %s%n", context.getTestMethod().get());
        }

        @Override
        public void testFailed(ExtensionContext context, Throwable cause) {
            System.out.printf("Test failed: %s%n", context.getTestMethod().get());
        }
    }
}
