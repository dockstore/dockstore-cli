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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import io.dockstore.common.CLICommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.SourceControl;
import io.dockstore.common.WorkflowTest;
import io.dropwizard.testing.ResourceHelpers;
import io.swagger.client.ApiClient;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.Entry;
import io.swagger.client.model.PublishRequest;
import io.swagger.client.model.SourceFile;
import io.swagger.client.model.Workflow;
import io.swagger.client.model.WorkflowVersion;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

import static io.dockstore.client.cli.ArgumentUtility.CONVERT;
import static io.dockstore.client.cli.ArgumentUtility.LAUNCH;
import static io.dockstore.client.cli.Client.CONFIG;
import static io.dockstore.client.cli.Client.SCRIPT_FLAG;
import static io.dockstore.client.cli.Client.WORKFLOW;
import static io.dockstore.client.cli.nested.AbstractEntryClient.ENTRY_2_JSON;
import static io.dockstore.client.cli.nested.WesCommandParser.ENTRY;
import static io.dockstore.client.cli.nested.WesCommandParser.JSON;
import static io.dockstore.common.DescriptorLanguage.WDL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gathers a few tests that focus on WDL workflows, testing things like generation of wdl test parameter files
 * and launching workflows with imports
 *
 * @author dyuen
 */
@Tag(ConfidentialTest.NAME)
@Tag(WorkflowTest.NAME)
class WDLWorkflowIT extends BaseIT {

    // TODO: Remove extra tags and branches on skylab workflows which are not needed
    private static final String SKYLAB_WORKFLOW_REPO = "dockstore-testing/skylab";
    private static final String SKYLAB_WORKFLOW = SourceControl.GITHUB.toString() + "/" + SKYLAB_WORKFLOW_REPO;
    private static final String SKYLAB_WORKFLOW_CHECKER = SourceControl.GITHUB.toString() + "/" + SKYLAB_WORKFLOW_REPO + "/_wdl_checker";
    private static final String UNIFIED_WORKFLOW_REPO = "dockstore-testing/dockstore-workflow-md5sum-unified";
    private static final String UNIFIED_WORKFLOW = SourceControl.GITHUB.toString() + "/" + UNIFIED_WORKFLOW_REPO;

    @SystemStub
    public final SystemOut systemOutRule = new SystemOut();

    @SystemStub
    public final SystemErr systemErrRule = new SystemErr();

    @BeforeEach
    @Override
    public void resetDBBetweenTests() throws Exception {
        CLICommonTestUtilities.cleanStatePrivate1(SUPPORT, testingPostgres);
    }

    /**
     * This checks that the working directory is set as we expect by running a WDL checker workflow
     */
    @Test
    void testRunningCheckerWDLWorkflow() throws IOException {
        final ApiClient webClient = getWebClient(USER_1_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        Workflow workflow = workflowApi
            .manualRegister(SourceControl.GITHUB.getFriendlyName(), UNIFIED_WORKFLOW_REPO, "/checker.wdl", "", WDL.toString(), "/md5sum.wdl.json");
        Workflow refresh = workflowApi.refresh(workflow.getId(), true);
        final PublishRequest publishRequest = SwaggerUtility.createPublishRequest(true);
        workflowApi.publish(refresh.getId(), publishRequest);

        // get test json
        String testVersion = "1.3.0";
        // Also test that files can be gotten by owner even though it's hidden
        List<WorkflowVersion> workflowVersions = refresh.getWorkflowVersions();
        workflowVersions.stream().filter(v -> v.getName().equals(testVersion)).forEach(v -> v.setHidden(true));

        workflowApi.updateWorkflowVersion(refresh.getId(), workflowVersions);
        List<SourceFile> testParameterFiles = workflowApi.getTestParameterFiles(refresh.getId(), testVersion);
        assertEquals(1, testParameterFiles.size());
        Path tempFile = Files.createTempFile("test", "json");

        // Unhiding version because launching is not possible on hidden workflows (even for the owner)
        workflowVersions.forEach(version -> version.setHidden(false));
        workflowApi.updateWorkflowVersion(refresh.getId(), workflowVersions);
        FileUtils.writeStringToFile(tempFile.toFile(), testParameterFiles.get(0).getContent(), StandardCharsets.UTF_8);
        // launch without error
        // run a workflow
        Client.main(new String[] {CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), WORKFLOW, LAUNCH, ENTRY, UNIFIED_WORKFLOW + ":" + testVersion, JSON, tempFile.toFile().getAbsolutePath()});
    }

    /**
     * This tests workflow convert entry2json when the main descriptor is nested far within the GitHub repo with secondary descriptors too
     */
    @Test
    void testEntryConvertWDLWithSecondaryDescriptors() {
        final ApiClient webClient = getWebClient(USER_1_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        Workflow workflow = workflowApi.manualRegister(SourceControl.GITHUB.getFriendlyName(), SKYLAB_WORKFLOW_REPO,
            "/pipelines/smartseq2_single_sample/SmartSeq2SingleSample.wdl", "", WDL.toString(), null);
        Workflow refresh = workflowApi.refresh(workflow.getId(), true);
        final PublishRequest publishRequest = SwaggerUtility.createPublishRequest(true);
        workflowApi.publish(refresh.getId(), publishRequest);
        checkOnConvert(SKYLAB_WORKFLOW, "Dockstore_Testing", "SmartSeq2SingleCell");
    }

    /**
     * This tests workflow convert entry2json when the main descriptor is nested far within the GitHub repo with secondary descriptors too
     */
    @Test
    void testEntryConvertCheckerWDLWithSecondaryDescriptors() {
        final ApiClient webClient = getWebClient(USER_1_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        // register underlying workflow
        Workflow workflow = workflowApi.manualRegister(SourceControl.GITHUB.getFriendlyName(), SKYLAB_WORKFLOW_REPO,
            "/pipelines/smartseq2_single_sample/SmartSeq2SingleSample.wdl", "", WDL.toString(), null);
        Workflow refresh = workflowApi.refresh(workflow.getId(), true);
        final PublishRequest publishRequest = SwaggerUtility.createPublishRequest(true);
        workflowApi.publish(refresh.getId(), publishRequest);
        // register checker workflow
        Entry checkerWorkflow = workflowApi
            .registerCheckerWorkflow("/test/smartseq2_single_sample/pr/test_smartseq2_single_sample_PR.wdl", workflow.getId(), WDL.toString(),
                "/test/smartseq2_single_sample/pr/dockstore_test_inputs.json");
        workflowApi.refresh(checkerWorkflow.getId(), true);
        checkOnConvert(SKYLAB_WORKFLOW_CHECKER, "feature/upperThingy", "TestSmartSeq2SingleCellPR");
    }

    private void checkOnConvert(String skylabWorkflowChecker, String branch, String prefix) {
        Client.main(
            new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, CONVERT, ENTRY_2_JSON, ENTRY,
                skylabWorkflowChecker + ":" + branch, SCRIPT_FLAG });
        List<String> stringList = new ArrayList<>();
        stringList.add("\"" + prefix + ".gtf_file\": \"File\"");
        stringList.add("\"" + prefix + ".genome_ref_fasta\": \"File\"");
        stringList.add("\"" + prefix + ".rrna_intervals\": \"File\"");
        stringList.add("\"" + prefix + ".fastq2\": \"File\"");
        stringList.add("\"" + prefix + ".hisat2_ref_index\": \"File\"");
        stringList.add("\"" + prefix + ".hisat2_ref_trans_name\": \"String\"");
        stringList.add("\"" + prefix + ".stranded\": \"String\"");
        stringList.add("\"" + prefix + ".sample_name\": \"String\"");
        stringList.add("\"" + prefix + ".output_name\": \"String\"");
        stringList.add("\"" + prefix + ".fastq1\": \"File\"");
        stringList.add("\"" + prefix + ".hisat2_ref_trans_index\": \"File\"");
        stringList.add("\"" + prefix + ".hisat2_ref_name\": \"String\"");
        stringList.add("\"" + prefix + ".rsem_ref_index\": \"File\"");
        stringList.add("\"" + prefix + ".gene_ref_flat\": \"File\"");
        stringList.forEach(string -> {
            assertTrue(systemOutRule.getText().contains(string));
        });
        systemOutRule.clear();
    }
}
