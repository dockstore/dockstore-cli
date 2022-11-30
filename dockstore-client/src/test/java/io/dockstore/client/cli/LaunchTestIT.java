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
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import io.dockstore.client.cli.nested.WorkflowClient;
import io.dockstore.common.FlushingSystemErrRule;
import io.dockstore.common.FlushingSystemOutRule;
import io.dockstore.common.Utilities;
import io.dockstore.openapi.client.model.WorkflowSubClass;
import io.dropwizard.testing.ResourceHelpers;
import io.swagger.client.api.UsersApi;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.ToolDescriptor;
import io.swagger.client.model.Workflow;
import io.swagger.client.model.WorkflowVersion;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.Assertion;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.rules.ExpectedException;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import static io.dockstore.client.cli.Client.IO_ERROR;
import static io.dockstore.common.DescriptorLanguage.CWL;
import static io.dockstore.common.DescriptorLanguage.WDL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class LaunchTestIT {
    public static final long LAST_MODIFIED_TIME_100 = 100L;
    public static final long LAST_MODIFIED_TIME_1000 = 1000L;

    //create tests that will call client.checkEntryFile for workflow launch with different files and descriptor

    @Rule
    public final SystemOutRule systemOutRule = new FlushingSystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new FlushingSystemErrRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final ExpectedSystemExit exit = ExpectedSystemExit.none();

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Rule
    public TestRule watcher = new TestWatcher() {
        protected void starting(Description description) {
            System.out.println("Starting test: " + description.getMethodName());
        }
    };

    @Test
    public void wdlCorrect() {
        //Test when content and extension are wdl  --> no need descriptor
        File helloWDL = new File(ResourceHelpers.resourceFilePath("hello.wdl"));
        File helloJSON = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add("--local-entry");
        args.add("--json");
        args.add(helloJSON.getAbsolutePath());

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        Client.SCRIPT.set(true);
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(helloWDL.getAbsolutePath(), args, null);

        assertTrue("output should include a successful cromwell run", systemOutRule.getLog().contains("Cromwell exit code: 0"));
    }

    @Test
    public void cwlCorrect() {
        //Test when content and extension are cwl  --> no need descriptor

        File cwlFile = new File(ResourceHelpers.resourceFilePath("1st-workflow.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("1st-workflow-job.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add("--local-entry");
        args.add("--json");
        args.add(cwlJSON.getAbsolutePath());

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        // do not use a cache
        runWorkflow(cwlFile, args, api, usersApi, client, false);
    }

    @Test
    public void cwlHandlesVersion12() {
        // 1st-workflow-12.cwl is a version of 1st-workflow.cwl to which key new CWL version 1.2 features have been added.
        File cwlFile = new File(ResourceHelpers.resourceFilePath("1st-workflow-12.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("1st-workflow-job.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add("--local-entry");
        args.add("--json");
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
    public void cwlFailsClassOperation() {
        File cwlFile = new File(ResourceHelpers.resourceFilePath("class-operation.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("1st-workflow-job.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add("--local-entry");
        args.add("--json");
        args.add(cwlJSON.getAbsolutePath());

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        // do not use a cache
        exit.expectSystemExit();
        exit.checkAssertionAfterwards(new Assertion() {
            @Override
            public void checkAssertion() throws Exception {
                assertTrue("output should include a failed cwltool run", systemErrRule.getLog().contains("Workflow has unrunnable abstract Operation"));
            }
        });
        runWorkflowUnchecked(cwlFile, args, api, usersApi, client, false);
    }

    @Test
    public void wdlMetadataNoopPluginTest() {
        //Test when content and extension are wdl  --> no need descriptor
        File helloWDL = new File(ResourceHelpers.resourceFilePath("hello.wdl"));
        File helloJSON = new File(ResourceHelpers.resourceFilePath("hello.metadata.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add("--local-entry");
        args.add("--json");
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

        assertTrue("output should include a successful cromwell run", systemOutRule.getLog().contains("Cromwell exit code: 0"));
        assertTrue("output should include a noop plugin run with metadata", systemOutRule.getLog().contains("really cool metadata"));
    }

    @Test
    public void cwlMetadataNoopPluginTest() {

        File cwlFile = new File(ResourceHelpers.resourceFilePath("1st-workflow.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("collab-cwl-noop-job.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add("--local-entry");
        args.add("--json");
        args.add(cwlJSON.getAbsolutePath());

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        Client.SCRIPT.set(true);
        client.setConfigFile(ResourceHelpers.resourceFilePath("config.withTestPlugin"));

        PluginClient.handleCommand(Lists.newArrayList("download"), Utilities.parseConfig(client.getConfigFile()));

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(cwlFile.getAbsolutePath(), args, null);

        assertTrue("output should include a successful cwltool run", systemOutRule.getLog().contains("Final process status is success"));
        assertTrue("output should include a noop plugin run with metadata", systemOutRule.getLog().contains("really cool metadata"));
    }



    @Test
    public void runWorkflowConvert() {
        File cwlFile = new File(ResourceHelpers.resourceFilePath("smcFusionQuant-INTEGRATE-workflow.cwl"));

        ArrayList<String> args = new ArrayList<>();
        args.add("workflow");
        args.add("convert");
        args.add("cwl2json");
        args.add("--cwl");
        args.add(cwlFile.getAbsolutePath());

        runClientCommand(args);
        final String log = systemOutRule.getLog();
        Gson gson = new Gson();
        final Map<String, Map<String, Object>> map = gson.fromJson(log, Map.class);
        assertEquals(4, map.size());
        assertTrue(
            map.containsKey("TUMOR_FASTQ_1") && map.containsKey("TUMOR_FASTQ_2") && map.containsKey("index") && map.containsKey("OUTPUT"));
    }

    @Test
    public void cwlCorrectWithCache() {
        //Test when content and extension are cwl  --> no need descriptor

        File cwlFile = new File(ResourceHelpers.resourceFilePath("1st-workflow.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("1st-workflow-job.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add("--local-entry");
        args.add("--json");
        args.add(cwlJSON.getAbsolutePath());

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        // use a cache
        runWorkflow(cwlFile, args, api, usersApi, client, true);
    }

    private void runClientCommand(ArrayList<String> args) {
        args.add(0, ResourceHelpers.resourceFilePath("config"));
        args.add(0, "--config");
        args.add(0, "--script");
        Client.main(args.toArray(new String[0]));
    }

    private void runClientCommandConfig(ArrayList<String> args, File config) {
        //used to run client with a specified config file
        args.add(0, config.getPath());
        args.add(0, "--config");
        args.add(0, "--script");
        Client.main(args.toArray(new String[0]));
    }


    private void runWorkflowUnchecked(File cwlFile, ArrayList<String> args, WorkflowsApi api, UsersApi usersApi, Client client, boolean useCache) {
        client.setConfigFile(ResourceHelpers.resourceFilePath(useCache ? "config.withCache" : "config"));
        Client.SCRIPT.set(true);
        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(cwlFile.getAbsolutePath(), args, null);
    }

    private void runWorkflow(File cwlFile, ArrayList<String> args, WorkflowsApi api, UsersApi usersApi, Client client, boolean useCache) {
        runWorkflowUnchecked(cwlFile, args, api, usersApi, client, useCache);
        assertTrue("output should include a successful cwltool run", systemOutRule.getLog().contains("Final process status is success"));
    }

    @Test
    public void cwlWrongExt() {
        //Test when content = cwl but ext = wdl, ask for descriptor

        File file = new File(ResourceHelpers.resourceFilePath("wrongExtcwl.wdl"));
        File json = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add("--local-entry");
        args.add(file.getAbsolutePath());
        args.add("--json");
        args.add(json.getAbsolutePath());

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, null);

        assertTrue("output should tell user to specify the descriptor", systemOutRule.getLog()
            .contains("Entry file is ambiguous, please re-enter command with '--descriptor <descriptor>' at the end"));
    }

    @Test
    public void cwlWrongExtForce() {
        //Test when content = cwl but ext = wdl, descriptor provided --> CWL

        File file = new File(ResourceHelpers.resourceFilePath("wrongExtcwl.wdl"));
        File json = new File(ResourceHelpers.resourceFilePath("1st-workflow-job.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add("--local-entry");
        args.add(file.getAbsolutePath());
        args.add("--json");
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

        assertTrue("output should include a successful cromwell run",
            systemOutRule.getLog().contains("This is a CWL file.. Please put the correct extension to the entry file name."));
    }

    @Test
    public void wdlWrongExt() {
        //Test when content = wdl but ext = cwl, ask for descriptor

        File file = new File(ResourceHelpers.resourceFilePath("wrongExtwdl.cwl"));
        File json = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add("--local-entry");
        args.add("--json");
        args.add(json.getAbsolutePath());

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));
        Client.SCRIPT.set(true);

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, null);

        assertTrue("output should include a successful cromwell run", systemOutRule.getLog()
            .contains("Entry file is ambiguous, please re-enter command with '--descriptor <descriptor>' at the end"));
    }

    @Test
    public void randomExtCwl() {
        //Test when content is random, but ext = cwl
        File file = new File(ResourceHelpers.resourceFilePath("random.cwl"));
        File json = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add("--local-entry");
        args.add("--json");
        args.add(json.getAbsolutePath());

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));
        Client.SCRIPT.set(true);

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, null);

        assertTrue("output should include a successful cromwell run", systemOutRule.getLog()
            .contains("Entry file is ambiguous, please re-enter command with '--descriptor <descriptor>' at the end"));
    }

    @Test
    public void randomExtWdl() {
        //Test when content is random, but ext = wdl
        File file = new File(ResourceHelpers.resourceFilePath("random.wdl"));
        File json = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add("--local-entry");
        args.add("--json");
        args.add(json.getAbsolutePath());

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));
        Client.SCRIPT.set(true);

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, null);

        assertTrue("output should include a successful cromwell run", systemOutRule.getLog()
            .contains("Entry file is ambiguous, please re-enter command with '--descriptor <descriptor>' at the end"));
    }

    @Test
    public void wdlWrongExtForce() {
        //Test when content = wdl but ext = cwl, descriptor provided --> WDL

        File file = new File(ResourceHelpers.resourceFilePath("wrongExtwdl.cwl"));
        File json = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add("--entry");
        args.add(file.getAbsolutePath());
        args.add("--local-entry");
        args.add("--json");
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

        assertTrue("output should include a successful cromwell run",
            systemOutRule.getLog().contains("This is a WDL file.. Please put the correct extension to the entry file name."));
    }

    @Test
    public void cwlWrongExtForce1() {
        //Test when content = cwl but ext = wdl, descriptor provided --> !CWL

        File file = new File(ResourceHelpers.resourceFilePath("wrongExtcwl.wdl"));
        File json = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add("--entry");
        args.add("wrongExtcwl.wdl");
        args.add("--local-entry");
        args.add("--json");
        args.add(json.getAbsolutePath());
        args.add("--descriptor");
        args.add(WDL.getShortName());

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));
        Client.SCRIPT.set(true);

        exit.expectSystemExit();

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, WDL.getShortName());
    }

    @Test
    public void wdlWrongExtForce1() {
        //Test when content = wdl but ext = cwl, descriptor provided --> !WDL

        File file = new File(ResourceHelpers.resourceFilePath("wrongExtwdl.cwl"));
        File json = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add("--entry");
        args.add("wrongExtwdl.cwl");
        args.add("--local-entry");
        args.add("--json");
        args.add(json.getAbsolutePath());
        args.add("--descriptor");
        args.add(CWL.getShortName());

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));
        Client.SCRIPT.set(true);

        exit.expectSystemExit();

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, CWL.getShortName());
    }

    @Test
    public void cwlNoExt() {
        //Test when content = cwl but no ext

        File file = new File(ResourceHelpers.resourceFilePath("cwlNoExt"));
        File json = new File(ResourceHelpers.resourceFilePath("1st-workflow-job.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add("--entry");
        args.add("cwlNoExt");
        args.add("--local-entry");
        args.add("--json");
        args.add(json.getAbsolutePath());

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));
        Client.SCRIPT.set(true);
        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, null);

        assertTrue("output should contain a validation issue",
            systemOutRule.getLog().contains("This is a CWL file.. Please put an extension to the entry file name."));
    }

    @Test
    public void wdlNoExt() {
        //Test when content = wdl but no ext

        File file = new File(ResourceHelpers.resourceFilePath("wdlNoExt"));
        File json = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add("--entry");
        args.add(file.getAbsolutePath());
        args.add("--local-entry");
        args.add("--json");
        args.add(json.getAbsolutePath());

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));
        Client.SCRIPT.set(true);

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, null);

        assertTrue("output should include a successful cromwell run",
            systemOutRule.getLog().contains("This is a WDL file.. Please put an extension to the entry file name."));

    }

    @Test
    public void randomNoExt() {
        //Test when content is neither CWL nor WDL, and there is no extension

        File file = new File(ResourceHelpers.resourceFilePath("random"));
        File json = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add("--entry");
        args.add(file.getAbsolutePath());
        args.add("--local-entry");
        args.add("--json");
        args.add(json.getAbsolutePath());

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));
        Client.SCRIPT.set(true);

        exit.expectSystemExit();
        exit.checkAssertionAfterwards(() -> assertTrue("output should include an error message of invalid file", systemErrRule.getLog()
            .contains("Entry file is invalid. Please enter a valid workflow file with the correct extension on the file name.")));
        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, null);
    }

    @Test
    public void randomWithExt() {
        //Test when content is neither CWL nor WDL, and there is no extension

        File file = new File(ResourceHelpers.resourceFilePath("hello.txt"));
        File json = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add("--entry");
        args.add(file.getAbsolutePath());
        args.add("--local-entry");
        args.add("--json");
        args.add(json.getAbsolutePath());

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));
        Client.SCRIPT.set(true);

        exit.expectSystemExit();
        exit.checkAssertionAfterwards(() -> assertTrue("output should include an error message of invalid file", systemErrRule.getLog()
            .contains("Entry file is invalid. Please enter a valid workflow file with the correct extension on the file name.")));
        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, null);
    }

    @Test
    public void wdlNoTask() {
        //Test when content is missing 'task'

        File file = new File(ResourceHelpers.resourceFilePath("noTask.wdl"));
        File json = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add("--entry");
        args.add(file.getAbsolutePath());
        args.add("--local-entry");
        args.add("--json");
        args.add(json.getAbsolutePath());

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));
        Client.SCRIPT.set(true);

        exit.expectSystemExit();
        exit.checkAssertionAfterwards(() -> assertTrue("output should include an error message and exit",
            systemErrRule.getLog().contains("Required fields that are missing from WDL file : 'task'")));
        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, null);
    }

    @Test
    public void wdlNoCommand() {
        //Test when content is missing 'command'

        File file = new File(ResourceHelpers.resourceFilePath("noCommand.wdl"));
        File json = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add("--entry");
        args.add(file.getAbsolutePath());
        args.add("--local-entry");
        args.add("--json");
        args.add(json.getAbsolutePath());

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));
        Client.SCRIPT.set(true);

        exit.expectSystemExit();
        exit.checkAssertionAfterwards(() -> assertTrue("output should include an error message and exit",
            systemErrRule.getLog().contains("Required fields that are missing from WDL file : 'command'")));
        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, null);
    }

    @Test
    public void wdlNoWfCall() {
        //Test when content is missing 'workflow' and 'call'

        File file = new File(ResourceHelpers.resourceFilePath("noWfCall.wdl"));
        File json = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add("--entry");
        args.add(file.getAbsolutePath());
        args.add("--local-entry");
        args.add("--json");
        args.add(json.getAbsolutePath());

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));
        Client.SCRIPT.set(true);

        exit.expectSystemExit();
        exit.checkAssertionAfterwards(() -> assertTrue("output should include an error message and exit",
            systemErrRule.getLog().contains("Required fields that are missing from WDL file : 'workflow' 'call'")));
        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, null);
    }

    @Test
    public void cwlNoInput() {
        //Test when content is missing 'input'

        File file = new File(ResourceHelpers.resourceFilePath("noInput.cwl"));
        File json = new File(ResourceHelpers.resourceFilePath("1st-workflow-job.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add("--entry");
        args.add(file.getAbsolutePath());
        args.add("--local-entry");
        args.add("--json");
        args.add(json.getAbsolutePath());

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));
        Client.SCRIPT.set(true);

        exit.expectSystemExit();
        exit.checkAssertionAfterwards(() -> assertTrue("output should include an error message and exit",
            systemErrRule.getLog().contains("Required fields that are missing from CWL file : 'inputs'")));
        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, null);
    }

    @Test
    @Ignore("Detection code is not robust enough for biowardrobe wdl using --local-entry")
    public void toolAsWorkflow() {
        File cwlFile = new File(ResourceHelpers.resourceFilePath("dir6.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("dir6.cwl.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add("workflow");
        args.add("launch");
        args.add("--local-entry");
        args.add(cwlFile.getAbsolutePath());
        args.add("--json");
        args.add(cwlJSON.getAbsolutePath());

        exit.expectSystemExit();
        exit.checkAssertionAfterwards(
            () -> assertTrue("Out should suggest to run as tool instead", systemErrRule.getLog().contains("Expected a workflow but the")));
        runClientCommand(args);
    }

    @Test
    public void workflowAsTool() {
        File file = new File(ResourceHelpers.resourceFilePath("noInput.cwl"));
        File json = new File(ResourceHelpers.resourceFilePath("1st-workflow-job.json"));
        ArrayList<String> args = new ArrayList<>();
        args.add("tool");
        args.add("launch");
        args.add("--local-entry");
        args.add(file.getAbsolutePath());
        args.add("--json");
        args.add(json.getAbsolutePath());

        exit.expectSystemExit();
        exit.checkAssertionAfterwards(
            () -> assertTrue("Out should suggest to run as workflow instead", systemErrRule.getLog().contains("Expected a tool but the")));
        runClientCommand(args);
    }

    @Test
    public void cwlNoOutput() {
        File file = new File(ResourceHelpers.resourceFilePath("noOutput.cwl"));
        File json = new File(ResourceHelpers.resourceFilePath("1st-workflow-job.json"));
        ArrayList<String> args = new ArrayList<>();
        args.add("tool");
        args.add("launch");
        args.add("--local-entry");
        args.add(file.getAbsolutePath());
        args.add("--json");
        args.add(json.getAbsolutePath());

        exit.expectSystemExit();
        exit.checkAssertionAfterwards(() -> assertTrue("output should include an error message and exit",
            systemErrRule.getLog().contains("Required fields that are missing from CWL file : 'outputs'")));
        runClientCommand(args);
    }

    @Test
    public void cwlIncompleteOutput() {
        File file = new File(ResourceHelpers.resourceFilePath("incompleteOutput.cwl"));
        File json = new File(ResourceHelpers.resourceFilePath("1st-workflow-job.json"));
        ArrayList<String> args = new ArrayList<>();
        args.add("tool");
        args.add("launch");
        args.add("--local-entry");
        args.add(file.getAbsolutePath());
        args.add("--json");
        args.add(json.getAbsolutePath());

        runClientCommand(args);

        assertTrue("output should include an error message", systemErrRule.getLog().contains("\"outputs\" section is not valid"));
    }

    @Test
    public void cwlIdContainsNonWord() {
        File file = new File(ResourceHelpers.resourceFilePath("idNonWord.cwl"));
        File json = new File(ResourceHelpers.resourceFilePath("1st-workflow-job.json"));
        ArrayList<String> args = new ArrayList<>();
        args.add("tool");
        args.add("launch");
        args.add("--local-entry");
        args.add(file.getAbsolutePath());
        args.add("--json");
        args.add(json.getAbsolutePath());

        exit.expectSystemExit();
        exit.checkAssertionAfterwards(() -> assertTrue("output should have started provisioning",
            systemOutRule.getLog().contains("Provisioning your input files to your local machine")));
        runClientCommand(args);
    }

    @Test
    public void cwlMissingIdParameters() {
        File file = new File(ResourceHelpers.resourceFilePath("missingIdParameters.cwl"));
        File json = new File(ResourceHelpers.resourceFilePath("1st-workflow-job.json"));
        ArrayList<String> args = new ArrayList<>();
        args.add("tool");
        args.add("launch");
        args.add("--local-entry");
        args.add(file.getAbsolutePath());
        args.add("--json");
        args.add(json.getAbsolutePath());

        runClientCommand(args);

        assertTrue("output should include an error message",
            systemErrRule.getLog().contains("while parsing a block collection"));
    }

    @Test
    public void entry2jsonNoVersion() throws IOException {
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

        exit.expectSystemExit();
        exit.checkAssertionAfterwards(() -> assertTrue("output should include error message",
            systemErrRule.getLog().contains("Cannot use workflow version 'master'")));

        workflowClient.downloadTargetEntry("quay.io/collaboratory/dockstore-tool-linux-sort", ToolDescriptor.TypeEnum.WDL, false);
    }

    @Test
    public void entry2jsonBadVersion() throws IOException {
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

        exit.expectSystemExit();
        exit.checkAssertionAfterwards(() -> assertTrue("output should include error messages",
            (systemOutRule.getLog().contains("Could not locate workflow with version '2.0.0'") && systemErrRule.getLog()
                .contains("Cannot use workflow version '1.0.0'"))));

        workflowClient.downloadTargetEntry("quay.io/collaboratory/dockstore-tool-linux-sort:2.0.0", ToolDescriptor.TypeEnum.WDL, false);
    }

    @Test
    public void cwl2jsonNoOutput() {
        exit.expectSystemExit();
        File file = new File(ResourceHelpers.resourceFilePath("noOutput.cwl"));
        ArrayList<String> args = new ArrayList<>();
        args.add("tool");
        args.add("convert");
        args.add("cwl2json");
        args.add("--cwl");
        args.add(file.getAbsolutePath());

        runClientCommand(args);
        exit.checkAssertionAfterwards(() -> assertTrue("output should include an error message",
            systemErrRule.getLog().contains("\"outputs section is not valid\"")));
    }

    @Test
    public void malJsonWorkflowWdlLocal() {
        //checks if json input has broken syntax for workflows

        File helloWdl = new File(ResourceHelpers.resourceFilePath("hello.wdl"));
        File jsonFile = new File(ResourceHelpers.resourceFilePath("testInvalidJSON.json"));
        ArrayList<String> args = new ArrayList<>();
        args.add("workflow");
        args.add("launch");
        args.add("--local-entry");
        args.add(helloWdl.getAbsolutePath());
        args.add("--json");
        args.add(jsonFile.getAbsolutePath());

        exit.expectSystemExit();
        exit.checkAssertionAfterwards(() -> assertTrue("output should include an error message",
            systemErrRule.getLog().contains("Could not launch, syntax error in json file: " + jsonFile)));
        File config = new File(ResourceHelpers.resourceFilePath("clientConfig"));
        runClientCommandConfig(args, config);
    }

    @Test
    public void malJsonToolWdlLocal() {
        //checks if json input has broken syntax for tools

        File helloWdl = new File(ResourceHelpers.resourceFilePath("hello.wdl"));
        File jsonFile = new File(ResourceHelpers.resourceFilePath("testInvalidJSON.json"));
        ArrayList<String> args = new ArrayList<>();
        args.add("tool");
        args.add("launch");
        args.add("--local-entry");
        args.add(helloWdl.getAbsolutePath());
        args.add("--json");
        args.add(jsonFile.getAbsolutePath());

        exit.expectSystemExit();
        exit.checkAssertionAfterwards(() -> assertTrue("output should include an error message",
            systemErrRule.getLog().contains("Could not launch, syntax error in json file: " + jsonFile)));
        File config = new File(ResourceHelpers.resourceFilePath("clientConfig"));
        runClientCommandConfig(args, config);
    }

    @Test
    public void provisionInputWithPathSpaces() {
        //Tests if file provisioning can handle a json parameter that specifies a file path containing spaces
        File helloWDL = new File(ResourceHelpers.resourceFilePath("helloSpaces.wdl"));
        File helloJSON = new File(ResourceHelpers.resourceFilePath("helloSpaces.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add("workflow");
        args.add("launch");
        args.add("--local-entry");
        args.add(helloWDL.getAbsolutePath());
        args.add("--json");
        args.add(helloJSON.getPath());

        File config = new File(ResourceHelpers.resourceFilePath("clientConfig"));
        runClientCommandConfig(args, config);

        assertTrue("output should include a successful cromwell run", systemOutRule.getLog().contains("Cromwell exit code: 0"));
    }


    @Test
    public void missingTestParameterFileWDLFailure() {
        // Run a workflow without specifying test parameter files, defer errors to the
        // Cromwell workflow engine.
        File file = new File(ResourceHelpers.resourceFilePath("hello.wdl"));
        ArrayList<String> args = new ArrayList<>();
        args.add("workflow");
        args.add("launch");
        args.add("--local-entry");
        args.add(file.getAbsolutePath());

        File config = new File(ResourceHelpers.resourceFilePath("clientConfig"));
        exit.expectSystemExitWithStatus(IO_ERROR);
        runClientCommandConfig(args, config);
        assertTrue("This workflow cannot run without test files, it should raise an exception from the workflow engine",
                systemOutRule.getLog().contains("problems running command:"));
    }

    @Test
    public void missingTestParameterFileWDLSuccess() {
        // Run a workflow without specifying test parameter files, defer errors to the
        // Cromwell workflow engine.
        File file = new File(ResourceHelpers.resourceFilePath("no-input-echo.wdl"));
        ArrayList<String> args = new ArrayList<>();
        args.add("workflow");
        args.add("launch");
        args.add("--local-entry");
        args.add(file.getAbsolutePath());

        File config = new File(ResourceHelpers.resourceFilePath("clientConfig"));
        runClientCommandConfig(args, config);
        assertTrue("output should include a successful cromwell run", systemOutRule.getLog().contains("Cromwell exit code: 0"));

    }

    @Test
    public void missingTestParameterFileCWL() {
        // Tests that the CWLrunner is able to handle workflows that do not specify
        // test parameter files.
        File file = new File(ResourceHelpers.resourceFilePath("no-input-echo.cwl"));
        ArrayList<String> args = new ArrayList<>();
        args.add("workflow");
        args.add("launch");
        args.add("--local-entry");
        args.add(file.getAbsolutePath());

        File config = new File(ResourceHelpers.resourceFilePath("clientConfig"));
        exit.expectSystemExitWithStatus(1);
        runClientCommandConfig(args, config);
        // FIXME: The CWLTool should be able to execute this workflow, there is an
        //        issue with how outputs are handled.
        //        https://github.com/dockstore/dockstore/issues/4922
        if (false) {
            assertTrue("CWLTool should be able to run this workflow without any problems",
                    systemOutRule.getLog().contains("[job no-input-echo.cwl] completed success"));
        }
    }

}
