package io.dockstore.client.cli.nested;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import io.dockstore.openapi.client.ApiClient;
import io.openapi.wes.client.model.RunId;
import io.swagger.client.model.Workflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.dockstore.client.cli.ArgumentUtility.errorMessage;
import static io.dockstore.client.cli.ArgumentUtility.out;
import static io.dockstore.client.cli.Client.CLIENT_ERROR;

public final class WesLauncher {

    private static final Logger LOG = LoggerFactory.getLogger(WesLauncher.class);
    private static final String TAGS = "{\"Client\":\"Dockstore\"}";
    private static final String WORKFLOW_TYPE_VERSION = "1.0";
    private static final String WORKFLOW_ENGINE_PARAMETERS = "{}";

    private WesLauncher() {

    }

    /**
     * This creates all the necessary parts of a WES launch, and then passes it on to the GA4GH WES client for the actual launch.
     *
     * @param workflowClient The WorkflowClient for the request
     * @param workflowEntry The entry path, (i.e. github.com/myRepo/myWorkflow:version)
     * @param workflowParamPath The path to a file to be used as an input JSON. (i.e. /path/to/file.json)
     * @param filePaths A list of paths to files to be attached to the request.
     */
    public static void launchWesCommand(WorkflowClient workflowClient, String workflowEntry, String workflowParamPath, List<String> filePaths) {

        // Get the workflow object associated with the provided entry path
        final Workflow workflow = getWorkflowForEntry(workflowClient, workflowEntry);

        // Can take the following values:
        // 1. A TRS URL returning the raw primary descriptor file contents
        // 2. TODO: A path to a file in the 'attachments' list
        String workflowUrl = combineTrsUrlComponents(workflowClient, workflowEntry, workflow);

        // A JSON object containing a key/value pair that points to the test parameter file in the 'attachments' list
        // The key is WES server implementation specific. e.g. {"workflowInput":"params.json"}.
        File workflowParams = fetchFile(workflowParamPath).orElse(null);

        // A list of supplementary files that are required to run the workflow. This may include any/all of the following:
        // 1. The primary descriptor file
        // 2. Secondary descriptor files
        // 3. Test parameter files
        // 4. Any other files referenced by the workflow
        // TODO: Allow users to specify a directory to upload?
        // TODO: Automatically attach all files referenced in remote Dockstore entry?
        List<File> workflowAttachment = fetchFiles(filePaths);

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

    /**
     * Obtains the workflow on Dockstore
     *
     * @param workflowClient The WorkflowClient
     * @param workflowEntry The workflow entry (i.e. github.com/myRepo/myWorkflow:version)
     * @return A Workflow object for the corresponding workflow+version
     */
    public static Workflow getWorkflowForEntry(WorkflowClient workflowClient, String workflowEntry) {
        String[] parts = workflowEntry.split(":");
        String path = parts[0];
        String version = workflowClient.getVersionID(workflowEntry);
        return workflowClient.getWorkflowsApi().getPublishedWorkflowByPath(path, null, null, version);
    }

    /**
     * Creates a TRS url for a Dockstore entry
     *
     * @param workflowClient The WorkflowClient
     * @param workflowEntry The workflow entry (i.e. github.com/myRepo/myWorkflow:version)
     * @param workflow The workflow object we are creating a TRS url for
     * @return A string representing a TRS URL for an entry
     */
    public static String combineTrsUrlComponents(WorkflowClient workflowClient, String workflowEntry, Workflow workflow) {
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

    /**
     * Attempts to load a file from the provided path. Errors out if the file doesn't exist.
     *
     * @param filePath Path to a file or null
     * @return An Optional File object, this may be empty if the filepath is null
     */
    public static Optional<File> fetchFile(String filePath) {
        if (filePath == null) {
            return Optional.empty();
        }

        // Verify the file path exists
        Path path = Paths.get(filePath);
        if (!Files.isRegularFile(path)) {
            errorMessage(MessageFormat.format("Unable to locate file: {0}", filePath), CLIENT_ERROR);
        }

        // TODO: Handle path expansions? i.e. '~/my/file.txt'
        return Optional.of(new File(filePath));
    }

    /**
     * Given a list of string paths, this attempts to convert it to a list of files.
     *
     * @param filePaths A list of paths that correspond to files that need to be attached to the WES request, this may be null
     * @return A list of File objects, which may be empty
     */
    public static List<File> fetchFiles(List<String> filePaths) {

        // Return an empty list if no attachments were passed
        if (filePaths == null) {
            return Collections.emptyList();
        }

        List<File> workflowAttachments = new ArrayList<>();
        filePaths.forEach(path -> {
            final Optional<File> attachmentFile = fetchFile(path);
            attachmentFile.ifPresent(workflowAttachments::add);
        });

        return workflowAttachments;
    }

    /**
     * Calculates the proper versioning for a workflow
     *
     * @param workflowType WDL/CWL/NEXTFLOW/etc...
     * @return A String type
     */
    public static String createWorkflowTypeVersion(String workflowType) {
        return "CWL".equalsIgnoreCase(workflowType) ? "v" + WORKFLOW_TYPE_VERSION : WORKFLOW_TYPE_VERSION;
    }

    /**
     * After a workflow is successfully launched, this will print an easy-to-use command to get the workflow status.
     *
     * @param runId The ID of the launched workflow. This ID is provided from the WES server.
     */
    public static void wesCommandSuggestions(String runId) {
        out("To view the workflow run status, execute: ");
        out(MessageFormat.format("\tdockstore workflow wes status --id {0}", runId));
        out(MessageFormat.format("\tdockstore workflow wes status --id {0} --verbose", runId));
    }
}
