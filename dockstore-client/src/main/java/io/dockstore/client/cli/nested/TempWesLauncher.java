package io.dockstore.client.cli.nested;

import java.io.File;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import io.dockstore.openapi.client.ApiClient;
import io.openapi.wes.client.model.RunId;
import io.swagger.client.model.Workflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.dockstore.client.cli.ArgumentUtility.out;

public final class TempWesLauncher {

    private static final Logger LOG = LoggerFactory.getLogger(WESLauncher.class);
    private static final String TAGS = "{\"Client\":\"Dockstore\"}";
    private static final String WORKFLOW_TYPE_VERSION = "1.0";
    private static final String WORKFLOW_ENGINE_PARAMETERS = "{}";

    private TempWesLauncher() {

    }

    public static void launchWesCommand(WorkflowClient workflowClient, String workflowEntry, String workflowParamPath, List<String> attachments) {

        // Get the workflow object associated with the provided entry path
        final Workflow workflow = getWorkflowForEntry(workflowClient, workflowEntry);

        // Can take the following values:
        // 1. A TRS URL returning the raw primary descriptor file contents
        // 2. TODO: A relative path to a value in the 'attachments' list
        // 3. TODO: An absolute path to a value in the 'attachments' list
        // User may enter the version, so we have to extract the path
        String workflowUrl = combineTrsUrlComponents(workflowClient, workflowEntry, workflow);

        // A JSON object containing a key/value pair that points to the test parameter file in the 'attachments' list
        // The key is WES server implementation specific. e.g. {"workflowInput":"params.json"}.
        File workflowParams = loadFile(workflowParamPath);

        // A list of supplementary files that are required to run the workflow. This may include any/all of the following:
        // 1. The primary descriptor file
        // 2. Secondary descriptor files
        // 3. Test parameter files
        // 4. Any other files referenced by the primary descriptor
        List<File> workflowAttachment = loadAttachments(attachments);

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
        }
    }

    private static Workflow getWorkflowForEntry(WorkflowClient workflowClient, String entry) {
        String[] parts = entry.split(":");
        String path = parts[0];
        String version = workflowClient.getVersionID(entry);
        return workflowClient.workflowsApi.getPublishedWorkflowByPath(path, null, null, version);
    }

    private static String combineTrsUrlComponents(WorkflowClient workflowClient, String workflowEntry, Workflow workflow) {
        ApiClient client = workflowClient.getClient().getGa4Ghv20Api().getApiClient();

        // Entries are passed in the form {PATH}:{VERSION} or {PATH}
        final String[] pathAndVersion = workflowEntry.split(":");
        final String path = pathAndVersion[0];

        // Calculate the values needed to supply a TRS URL
        final String basePath = workflowClient.getClient().getGa4Ghv20Api().getApiClient().getBasePath();
        final String versionId = workflowClient.getVersionID(workflowEntry);
        final String entryId = workflowClient.getTrsId(path);
        final String type = "PLAIN_" + workflow.getDescriptorType().getValue();

        // Escape each of the URL path values
        final String escapedId = client.escapeString(entryId);
        final String escapedVersionId = client.escapeString(versionId);
        final String escapedType = client.escapeString(type);
        final String escapedRelativePath = client.escapeString(workflow.getWorkflowPath());

        // Return the TRS URL for a given entry
        return MessageFormat.format("{0}/ga4gh/trs/v2/tools/{1}/versions/{2}/{3}/descriptor/{4}",
            basePath,
            escapedId,
            escapedVersionId,
            escapedType,
            escapedRelativePath);
    }

    private static File loadFile(String workflowParamPath) {
        return workflowParamPath == null ? null : new File(workflowParamPath);
    }

    private static List<File> loadAttachments(List<String> attachments) {
        if (attachments == null) {
            return null;
        }

        List<File> workflowAttachments = new ArrayList<>();
        attachments.forEach(path -> {
            final File attachmentFile = loadFile(path);
            workflowAttachments.add(attachmentFile);
        });

        return workflowAttachments;
    }

    private static String createWorkflowTypeVersion(String workflowType) {
        return "CWL".equalsIgnoreCase(workflowType) ? "v" + WORKFLOW_TYPE_VERSION : WORKFLOW_TYPE_VERSION;
    }

    private static void wesCommandSuggestions(String runId) {
        out("To view the workflow run status, execute: ");
        out(MessageFormat.format("\tdockstore workflow wes status --id {0}", runId));
        out(MessageFormat.format("\tdockstore workflow wes status --id {0} --verbose", runId));
    }
}
