package io.dockstore.client.cli.nested;

import java.io.File;
import java.io.IOException;
import java.util.List;

import io.dockstore.common.DescriptorLanguage;
import io.openapi.wes.client.ApiException;
import io.openapi.wes.client.model.RunId;
import io.swagger.client.model.Workflow;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.dockstore.client.cli.ArgumentUtility.out;

public class TempWesLauncher {

    private static final Logger LOG = LoggerFactory.getLogger(WESLauncher.class);
    private static final String TAGS = "{\"Client\":\"Dockstore\"}";
    private static final String WORKFLOW_TYPE_VERSION = "1.0";
    private static final String WORKFLOW_ENGINE_PARAMETERS = "{}";

    public static void launchWesCommand(WorkflowClient workflowClient, String workflowEntry, String workflowParamPath, List<String> attachments) {

        // Get the workflow object associated with the provided entry path
        final Workflow workflow = getWorkflowForEntry(workflowClient, workflowEntry);

        // Can take the following values:
        // 1. A TRS URL returning the raw primary descriptor file contents
        // 2. TODO: A relative path to a value in the 'attachments' list
        // 3. TODO: An absolute path to a value in the 'attachments' list
        // User may enter the version, so we have to extract the path
        String workflowUrl = workflow.getWorkflowPath();

        // A JSON object containing a key/value pair that points to the test parameter file in the 'attachments' list
        // The key is WES server implementation specific. e.g. {"workflowInput":"params.json"}.
        File workflowParams = null;

        // A list of supplementary files that are required to run the workflow. This may include any/all of the following:
        // 1. The primary descriptor file
        // 2. Secondary descriptor files
        // 3. Test parameter files
        // 4. Any other files referenced by the primary descriptor
        List<File> workflowAttachment = null;

        // The descriptor type
        String workflowType = workflow.getDescriptorType().getValue();

        // The workflow version
        String workflowTypeVersion = createWorkflowTypeVersion(workflowType);

        try {
            RunId response = workflowClient
                .getWorkflowExecutionServiceApi()
                .runWorkflow(
                    workflowParams,
                    workflowType,
                    workflowTypeVersion,
                    TAGS,                       // TODO: User specified tags?
                    WORKFLOW_ENGINE_PARAMETERS, // TODO: User specified engine parameters?
                    workflowUrl,
                    workflowAttachment);

            String runID = response.getRunId();
            out("Launched WES run with id: " + runID);
            wesCommandSuggestions(runID);
        } catch (io.openapi.wes.client.ApiException e) {
            LOG.error("Error launching WES run", e);
        } finally {

            // Only attempt to cleanup the temporary directory if we created it in the first place
//            if (tempDir != null) {
//                try {
//                    FileUtils.deleteDirectory(tempDir);
//                } catch (IOException ioe) {
//                    LOG.error("Could not delete temporary directory" + tempDir + " for workflow attachment files", ioe);
//                }
//            }
        }

    }

    private static Workflow getWorkflowForEntry(WorkflowClient workflowClient, String entry) {
        String[] parts = entry.split(":");
        String path = parts[0];
        String version = workflowClient.getVersionID(entry);
        return workflowClient.workflowsApi.getPublishedWorkflowByPath(path, null, null, version);
    }

    private static String createWorkflowTypeVersion(String workflowType) {
        return "CWL".equalsIgnoreCase(workflowType) ? "v" + WORKFLOW_TYPE_VERSION : WORKFLOW_TYPE_VERSION;
    }

    private static void wesCommandSuggestions(String runId) {
        out("TODO");
    }
}
