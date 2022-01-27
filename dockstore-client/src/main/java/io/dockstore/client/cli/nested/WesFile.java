package io.dockstore.client.cli.nested;

import java.io.File;
import java.text.MessageFormat;

import static io.dockstore.client.cli.ArgumentUtility.errorMessage;
import static io.dockstore.client.cli.Client.CLIENT_ERROR;

public class WesFile extends File {

    private final String removablePrefix;
    private final String desiredSuffix;

    public WesFile(String filepath, String removablePrefix, String desiredSuffix) {
        super(filepath);

        this.removablePrefix = removablePrefix;
        this.desiredSuffix = desiredSuffix;
    }

    /**
     * Gets the name for a file that will be attached in a WES request.
     *
     * @return A non-null String value representing the name of the file. This may be the direct filename, or a relative path to the file.
     */
    @Override
    public String getName() {
        // We need to consider files that were either
        // 1. Pulled from Dockstore and stored in a temporary directory
        // 2. Attached from a user's local file (using -a / --attach)

        // If the user attached the file with -a / --attach, using a relative path such as "relative/path/file.json", then we return
        // this input path as the WES filename. If the filepath was passed in as an absolute path, raise an error as this should be denied
        // by WES.
        if (this.desiredSuffix != null) {

            if (this.desiredSuffix.startsWith("/")) {
                errorMessage(MessageFormat.format("Unable to attach {0} to the WES request. Absolute paths are not allowed.",
                    this.desiredSuffix), CLIENT_ERROR);
            } else if (!this.getAbsolutePath().endsWith(this.desiredSuffix)) {
                // This should never occur, but just in case...
                throw new RuntimeException(MessageFormat.format("Path to file object does not contain the provided suffix {0}", this.desiredSuffix));
            }

            return this.desiredSuffix;
        }

        // If the file was provisioned locally from an entry on Dockstore, we can remove the path to the temporary directory
        // from the file's absolute path to obtain a relative path.
        if (this.removablePrefix != null) {
            // Get the path to the file, minus the temporary directory that was created
            final String relativeFileName = this.getAbsolutePath().substring(this.removablePrefix.length());

            // Ensure the path has no leading slashes. No absolute references should be passed to WES (these will be blocked)
            return relativeFileName.replaceAll("^/+", "");
        }

        // If neither a removable prefix or desired suffix was passed to the constructor, get the normal filename from the superclass
        return super.getName();
    }
}
