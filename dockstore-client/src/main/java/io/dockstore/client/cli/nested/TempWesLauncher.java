package io.dockstore.client.cli.nested;

import java.io.File;
import java.util.List;

public class TempWesLauncher {

    public static void launchWesCommand(String workflowEntry, String workflowParamPath, List<String> attachments) {

        // Can take the following values:
        // 1. A TRS URL returning the raw primary descriptor file contents
        // 2. A relative path to a value in the 'attachments' list
        // 3. An absolute path to a value in the 'attachments' list
        final String workflowUrl;

        // A JSON object containing a key/value pair that points to the test parameter file in the 'attachments' list
        // The key is WES server implementation specific. e.g. {"workflowInput":"params.json"}.
        final File workflowParams;

        // A list of supplementary files that are required to run the workflow. This may include any/all of the following:
        // 1. The primary descriptor file
        // 2. Secondary descriptor files
        // 3. Test parameter files
        // 4. Any other files referenced by the primary descriptor
        final List<File> workflowAttachments;

        // The descriptor type
        final String workflowType;

        // The workflow version
        final String workflowTypeVersion;

        // Tags to be sent with the request. These are effectively arbitrary.
        // TODO: User specified tags?
        final String tags;

        // Parameters to be sent to the workflow engine.
        // TODO: User specified engine parameters?
        final String workflowEngineParameters;
    }
}
