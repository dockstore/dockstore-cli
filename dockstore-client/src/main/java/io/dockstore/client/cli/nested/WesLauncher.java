package io.dockstore.client.cli.nested;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import io.dockstore.client.cli.SwaggerUtility;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.ApiException;
import io.dockstore.openapi.client.model.WorkflowSubClass;
import io.openapi.wes.client.model.RunId;
import io.swagger.client.model.ToolDescriptor;
import io.swagger.client.model.Workflow;
import io.swagger.client.model.WorkflowVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.dockstore.client.cli.ArgumentUtility.errorMessage;
import static io.dockstore.client.cli.ArgumentUtility.exceptionMessage;
import static io.dockstore.client.cli.ArgumentUtility.out;
import static io.dockstore.client.cli.Client.CLIENT_ERROR;
import static io.dockstore.client.cli.Client.IO_ERROR;

public final class WesLauncher {

    private static final Logger LOG = LoggerFactory.getLogger(WesLauncher.class);
    private static final String TAGS = "{\"Client\":\"Dockstore\"}";
    private static final String WORKFLOW_TYPE_VERSION = "1.0";
    private static final String WORKFLOW_ENGINE_PARAMETERS = "{}";

    private static final String DOCKSTORE_ROOT_TEMP_DIR_PREFIX = "DockstoreWesLaunch";
    private static final String DOCKSTORE_NESTED_TEMP_DIR_PREFIX = "UnzippedWorkflow";


    private WesLauncher() {

    }

    /**
     * This creates all the necessary parts of a WES launch, and then passes it on to the GA4GH WES client for the actual launch.
     *
     * @param workflowClient The WorkflowClient for the request
     * @param workflowEntry The entry path, (i.e. github.com/myRepo/myWorkflow:version)
     * @param inlineWorkflow Determines if the entry is locally provisioned or not, this alters the format that the WES request is made in.
     * @param workflowParamPath The path to a file to be used as an input JSON. (i.e. /path/to/file.json)
     * @param filePaths A list of paths to files to be attached to the request.
     */
    public static void launchWesCommand(WorkflowClient workflowClient, String workflowEntry, boolean inlineWorkflow, String workflowParamPath, List<String> filePaths) {

        // Get the workflow object associated with the provided entry path
        final Workflow workflow = getWorkflowForEntry(workflowClient, workflowEntry);

        // The descriptor type
        String workflowType = workflow.getDescriptorType().getValue();

        // Get the workflow version we are launching
        final String versionId = workflowClient.getVersionID(workflowEntry);
        final Optional<WorkflowVersion> workflowVersionOpt = workflow.getWorkflowVersions()
            .stream()
            .filter(wv -> wv.getName().equals(versionId))
            .findFirst();

        if (workflowVersionOpt.isEmpty()) {
            errorMessage(MessageFormat.format("Unable to locate version: {0}", versionId), CLIENT_ERROR);
        }
        final WorkflowVersion workflowVersion = workflowVersionOpt.get();

        // Can take the following values:
        // 1. A TRS URL returning the raw primary descriptor file contents
        // 2. The name of a file in the attachments list
        String workflowUrl = inlineWorkflow
            ? workflowVersion.getWorkflowPath().replaceAll("^/+", "") // Remove all leading slashes
            : combineTrsUrlComponents(workflowClient, workflowEntry, workflow, workflowVersion);

        // A JSON object containing a key/value pair that points to the test parameter file in the 'attachments' list
        // The key is WES server implementation specific. e.g. {"workflowInput":"params.json"}.
        File workflowParams = fetchFile(workflowParamPath, null, null).orElse(null);

        // A list of supplementary files that are required to run the workflow. This may include any/all of the following:
        // 1. The primary descriptor file
        // 2. Secondary descriptor files
        // 3. Test parameter files
        // 4. Any other files referenced by the workflow
        // TODO: Allow users to specify a directory to upload?
        // 6. Automatically attach all files referenced in remote Dockstore entry?
        List<File> workflowAttachment = new ArrayList<>(fetchFilesAndDirectories(filePaths));
        if (inlineWorkflow) {
            // Download all workflow files and place them into a temporary directory, then add them as attachments to the WES request
            final File unzippedWorkflowDir = provisionFilesLocally(workflowClient, workflowEntry, workflowType);
            workflowAttachment.addAll(fetchFilesFromLocalDirectory(unzippedWorkflowDir.getAbsolutePath()));
        }

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
    public static Workflow getWorkflowForEntry(WorkflowClient workflowClient, String workflowEntry) throws ApiException {
        String[] parts = workflowEntry.split(":");
        String path = parts[0];
        String version = workflowClient.getVersionID(workflowEntry);
        return workflowClient.getWorkflowsApi().getPublishedWorkflowByPath(path, WorkflowSubClass.BIOWORKFLOW.toString(), null, version);
    }

    /**
     * Provisions a workflow's associated files in a temporary local directory
     *
     * @param workflowClient The WorkflowClient
     * @param workflowEntry The workflow entry (i.e. github.com/myRepo/myWorkflow:version)
     * @param descriptorType The type of descriptor for this workflow (WDL, CWL, etc...)
     * @return A zipped File object
     */
    public static File provisionFilesLocally(WorkflowClient workflowClient, String workflowEntry, String descriptorType) {

        // A temporary directory which will house all downloaded content
        File tempDir;

        try {
            tempDir = Files.createTempDirectory(DOCKSTORE_ROOT_TEMP_DIR_PREFIX).toFile().getAbsoluteFile();
        } catch (IOException ex) {
            exceptionMessage(ex, "Could not create a temporary working directory.", IO_ERROR);
            throw new RuntimeException(ex);
        }

        // A zip file containing the content of the provided workflow
        File zippedWorkflow;

        // Download the contents locally
        try {
            zippedWorkflow = workflowClient.downloadTargetEntry(workflowEntry,
                ToolDescriptor.TypeEnum.fromValue(descriptorType), false, tempDir);
            zippedWorkflow.deleteOnExit();
        } catch (IOException ex) {
            exceptionMessage(ex, "A problem was encountered while downloading the entry.", IO_ERROR);
            throw new RuntimeException(ex);
        }

        // The directory where the unzipped workflow contents are located
        File unzippedWorkflowDir;

        // Unzip the workflow to a nested temporary directory
        try {
            unzippedWorkflowDir = Files.createTempDirectory(Path.of(tempDir.getAbsolutePath()), DOCKSTORE_NESTED_TEMP_DIR_PREFIX).toFile().getAbsoluteFile();
            SwaggerUtility.unzipFile(zippedWorkflow, unzippedWorkflowDir);
            unzippedWorkflowDir.deleteOnExit();
        } catch (IOException ex) {
            exceptionMessage(ex, "A problem was encountered while unzipping the entry.", IO_ERROR);
            throw new RuntimeException(ex);
        }

        out("Successfully downloaded files for entry '" + workflowEntry + "'");
        return unzippedWorkflowDir;
    }

    /**
     * Creates a TRS url for a Dockstore entry
     *
     * @param workflowClient The WorkflowClient
     * @param workflowEntry The workflow entry (i.e. github.com/myRepo/myWorkflow:version)
     * @param workflow The workflow object we are creating a TRS url for
     * @return A string representing a TRS URL for an entry
     */
    public static String combineTrsUrlComponents(WorkflowClient workflowClient, String workflowEntry, Workflow workflow, WorkflowVersion workflowVersion) {
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
        final String escapedRelativePath = client.escapeString(workflowVersion.getWorkflowPath());

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
     * @param removablePrefix Used to determine the relative path of the file located at a path that contains the String removablePrefix
     * @param desiredSuffix Used to determine the relative path of the file, where the desired suffix is the relative path
     * @return An Optional WesFile object, this may be empty if the filepath is null
     */
    public static Optional<WesFile> fetchFile(String filePath, String removablePrefix, String desiredSuffix) {
        if (filePath == null) {
            return Optional.empty();
        }

        // If the path isn't a file or a directory, we've done something wrong on our end.
        // We don't want to attach a directory directly, so return an empty optional instead
        Path path = Paths.get(filePath);
        if (Files.isDirectory(path)) {
            return Optional.empty();
        } else if (!Files.isRegularFile(path)) {
            errorMessage(MessageFormat.format("Unable to locate resource: {0}", filePath), CLIENT_ERROR);
        }

        // TODO: Handle path expansions? i.e. '~/my/file.txt'
        return Optional.of(new WesFile(filePath, removablePrefix, desiredSuffix));
    }

    /**
     * Given a list of string paths, this attempts to convert it to a list of files.
     *
     * @param filePaths A list of paths that correspond to files that need to be attached to the WES request, this may be null
     * @return A list of File objects, which may be empty
     */
    public static List<WesFile> fetchFilesAndDirectories(List<String> filePaths) {

        // Return an empty list if no attachments were passed
        if (filePaths == null) {
            return Collections.emptyList();
        }

        List<WesFile> workflowAttachments = new ArrayList<>();
        filePaths.forEach(path -> {

            // Verify the path leads to a file or directory, otherwise raise an error and exit
            Path resourcePath = Paths.get(path);
            if (Files.isRegularFile(resourcePath)) {
                // Add individual file
                final Optional<WesFile> attachmentFile = fetchFile(resourcePath.toString(), null, resourcePath.toString());
                attachmentFile.ifPresent(workflowAttachments::add);
            } else if (Files.isDirectory(resourcePath)) {
                // Walk directory tree and add all files and directories
                workflowAttachments.addAll(fetchFilesFromLocalDirectory(resourcePath.toAbsolutePath().toString()));
            } else {
                errorMessage(MessageFormat.format("Unable to locate: {0}", resourcePath), CLIENT_ERROR);
            }
        });

        return workflowAttachments;
    }

    /**
     * Fetches all files from the provided directory path
     *
     * @param localDirectory Path of a local directory
     * @return A list of WesFiles found as child files of the provided directory
     */
    public static List<WesFile> fetchFilesFromLocalDirectory(String localDirectory) {
        List<WesFile> workflowAttachments = new ArrayList<>();
        try {
            Files.walk(Path.of(localDirectory)).forEach(path -> {
                final Optional<WesFile> attachmentFile = fetchFile(path.toAbsolutePath().toString(), localDirectory, null);
                attachmentFile.ifPresent(workflowAttachments::add);
            });
        } catch (IOException ex) {
            exceptionMessage(ex, "A problem was encountered while attaching files from a local directory.", IO_ERROR);
            throw new RuntimeException(ex);
        }
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
