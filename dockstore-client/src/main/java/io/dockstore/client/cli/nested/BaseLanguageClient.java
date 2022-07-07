package io.dockstore.client.cli.nested;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.google.common.base.Joiner;
import com.google.common.io.Files;
import io.dockstore.client.cli.nested.notificationsclients.NotificationsClient;
import io.dockstore.common.Utilities;
import io.dockstore.openapi.client.api.Ga4Ghv20Api;
import io.dockstore.openapi.client.model.Checksum;
import io.dockstore.openapi.client.model.FileWrapper;
import io.dockstore.openapi.client.model.ToolFile;
import io.swagger.client.ApiException;
import io.swagger.client.model.ToolDescriptor;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.dockstore.client.cli.ArgumentUtility.err;
import static io.dockstore.client.cli.ArgumentUtility.errorMessage;
import static io.dockstore.client.cli.ArgumentUtility.exceptionMessage;
import static io.dockstore.client.cli.ArgumentUtility.out;
import static io.dockstore.client.cli.Client.API_ERROR;
import static io.dockstore.client.cli.Client.ENTRY_NOT_FOUND;
import static io.dockstore.client.cli.Client.GENERIC_ERROR;
import static io.dockstore.client.cli.Client.IO_ERROR;
import static io.dockstore.client.cli.nested.AbstractEntryClient.CHECKSUM_MISMATCH_MESSAGE;
import static io.dockstore.client.cli.nested.AbstractEntryClient.CHECKSUM_NULL_MESSAGE;
import static io.dockstore.client.cli.nested.AbstractEntryClient.CHECKSUM_VALIDATED_MESSAGE;

/**
 * A base class for all language clients
 * Clients for CWL, WDL, Nextflow, etc should extend this and implement the abstract functions.
 */
public abstract class BaseLanguageClient {
    private static final Logger LOG = LoggerFactory.getLogger(BaseLanguageClient.class);
    protected final AbstractEntryClient abstractEntryClient;
    protected INIConfiguration config;
    protected String notificationsWebHookURL;
    protected NotificationsClient notificationsClient;
    protected BaseLauncher launcher;

    // Fields set on initial launch
    protected boolean isLocalEntry;
    protected String entry;
    protected String wdlOutputTarget;
    protected String yamlParameterFile;
    protected String jsonParameterFile;
    protected String uuid;

    // Fields generated during setup and running of entry
    protected File tempLaunchDirectory;
    protected File localPrimaryDescriptorFile;
    protected File zippedEntryFile;
    protected String selectedParameterFile;
    protected File provisionedParameterFile;
    protected String workingDirectory;
    protected String stdout;
    protected String stderr;

    public BaseLanguageClient(AbstractEntryClient abstractEntryClient, BaseLauncher launcher) {
        this.abstractEntryClient = abstractEntryClient;
        this.launcher = launcher;
    }

    public void setLauncher(BaseLauncher launcher) {
        this.launcher = launcher;
    }

    /**
     * Selects the intended parameter file
     */
    public abstract String selectParameterFile();

    /**
     * Provision the input files based on the selected parameter file.
     * Creates an updated version of the parameter file with new local file locations.
     * Must set the variable workingDirectory
     * @return Updated parameter file
     */
    public abstract File provisionInputFiles();

    /**
     * Runs the tool/workflow with the selected launcher
     * Must set the variables stdout and stderr
     * @throws ExecuteException
     */
    public abstract void executeEntry() throws ExecuteException;

    /**
     * Provisions the output files to their appropriate location
     */
    public abstract void provisionOutputFiles();

    /**
     * Download files and put them in a temporary directory
     * Must set the variables localPrimaryDescriptorFile, zippedEntryFile, and provisionedParameterFile
     */
    public abstract void downloadFiles();

    /**
     * Setup for notifications to webhook
     */
    public void setupNotifications() {
        config = Utilities.parseConfig(abstractEntryClient.getConfigFile());
        notificationsWebHookURL = config.getString("notifications", "");
        notificationsClient = new NotificationsClient(notificationsWebHookURL, uuid);
    }

    /**
     * Sets some high level launch variables
     * @param entryVal
     * @param localEntry
     * @param yamlFile
     * @param jsonFile
     * @param outputTarget
     * @param notificationUUID
     */
    public void setLaunchInformation(String entryVal, boolean localEntry, String yamlFile, String jsonFile, String outputTarget, String notificationUUID) {
        this.entry = entryVal;
        this.isLocalEntry = localEntry;
        this.yamlParameterFile = yamlFile;
        this.jsonParameterFile = jsonFile;
        this.wdlOutputTarget = outputTarget;
        this.uuid = notificationUUID;
    }

    /**
     * Common code to setup and launch a pipeline
     * @return Exit code of process
     */
    public long launchPipeline(String entryVal, boolean localEntry, ToolDescriptor.TypeEnum type, String yamlFile, String jsonFile, String outputTarget, String notificationUUID) throws ApiException {
        // Initialize client with some launch information
        setLaunchInformation(entryVal, localEntry, yamlFile, jsonFile, outputTarget, notificationUUID);

        // Load up Docker images
        abstractEntryClient.loadDockerImages();

        // Select the appropriate parameter file
        selectedParameterFile = selectParameterFile();

        // Setup the launcher (Download dependencies)
        launcher.initialize();

        // Setup notifications
        setupNotifications();

        // Setup temp directory and download files
        downloadFiles();

        /*
         Don't download the input files (from the input JSON or YAML) if we are making a request
         to a WES endpoint. We want the WES endpoint to download the input files becuase they
         could be very large and in that case we cannot send their contents in a POST request
         efficiently.
         TODO if there are input files on the local file system maybe we should send those to
         the WES endpoint instead of assuming they will exist on the file system at the WES
         endpoint.
        */
        if (!abstractEntryClient.isWesCommand()) {
            if (provisionedParameterFile != null || selectedParameterFile != null) {
                try {
                    provisionedParameterFile = provisionInputFiles();
                } catch (ApiException ex) {
                    if (abstractEntryClient.getEntryType().equalsIgnoreCase("tool")) {
                        exceptionMessage(ex, "The tool entry does not exist. Did you mean to launch a local tool or a workflow?",
                                ENTRY_NOT_FOUND);
                    } else {
                        exceptionMessage(ex, "The workflow entry does not exist. Did you mean to launch a local workflow or a tool?",
                                ENTRY_NOT_FOUND);
                    }
                } catch (Exception ex) {
                    exceptionMessage(ex, ex.getMessage(), GENERIC_ERROR);
                }
            } else {
                LOG.debug("No test parameter file provided, skipping provisioning");
            }
        }

        // Don't validate descriptors if the entry is local or a flag to ignore validation was part of the command
        if (!localEntry && !abstractEntryClient.getIgnoreChecksums()) {
            validateDescriptorChecksum(type, entryVal);
        }


        // Update the launcher with references to the files to be launched
        launcher.setFiles(localPrimaryDescriptorFile, zippedEntryFile, provisionedParameterFile, selectedParameterFile, workingDirectory, entryVal);
        try {
            // Attempt to run launcher
            launcher.printLaunchMessage();
            executeEntry();

            // Provision the output files if run is successful
            provisionOutputFiles();
        } catch (ApiException ex) {
            exceptionMessage(ex, ex.getMessage(), API_ERROR);
        }  catch (IOException ex) {
            exceptionMessage(ex, ex.getMessage(), IO_ERROR);
        } catch (Exception ex) {
            exceptionMessage(ex, ex.getMessage(), GENERIC_ERROR);
        }

        notificationsClient.sendMessage(NotificationsClient.COMPLETED, true);

        return 0;
    }

    /**
     * Validates the locally downloaded descriptor file has the same SHA-1 checksum as the descriptor stored in the database
     * @param type CWL or WDL or NFL
     * @param entryVal Tool/workflow path
     * @return void errors out if checksums do not match, provides a warning if a remote checksum is null
     */
    public void validateDescriptorChecksum(ToolDescriptor.TypeEnum type, String entryVal) {
        final String[] parts = entryVal.split(":");
        final String path = parts[0];

        // workflows/checkers have a special prefix for TRS endpoints
        final String ga4ghv20Path = abstractEntryClient.getTrsId(path);

        // Get the entry version we are validating for
        final String versionID = abstractEntryClient.getVersionID(entryVal);

        final List<ToolFile> allDescriptors = abstractEntryClient.getAllToolDescriptors(type.toString(), ga4ghv20Path, versionID);

        final Ga4Ghv20Api ga4ghv20api = abstractEntryClient.getClient().getGa4Ghv20Api();

        // All secondary files are located relative to the location of the primary descriptor.
        final String localTemporaryDirectory = localPrimaryDescriptorFile.getParent();
        final String checksumFunction = "sha-256";

        // Validate each tool file associated with the entry (Primary and secondary descriptors)
        for (ToolFile toolFile : allDescriptors) {

            // Get remote descriptor checksum
            Optional<Checksum> remoteDescriptorChecksum = Optional.empty();
            try {
                // The TRS endpoint only discovers published entries
                final FileWrapper remoteDescriptor = ga4ghv20api.toolsIdVersionsVersionIdTypeDescriptorRelativePathGet(type.toString(), ga4ghv20Path, versionID, toolFile.getPath());
                remoteDescriptorChecksum = remoteDescriptor.getChecksum()
                    .stream()
                    .filter(c -> c.getType().equals(checksumFunction))
                    .findFirst();
            } catch (io.dockstore.openapi.client.ApiException ex) {
                exceptionMessage(ex, "Unable to locate remote descriptor " + ga4ghv20Path, ENTRY_NOT_FOUND);
            }

            if (!remoteDescriptorChecksum.isEmpty()) {

                // Get local descriptor checksum
                Checksum localDescriptorChecksum = new Checksum();
                localDescriptorChecksum.setType(checksumFunction);
                try {
                    // if the toolFile.getPath() is absolute, it is converted to a relative path by File the constructor
                    final File localDescriptor = new File(localTemporaryDirectory, toolFile.getPath());
                    final String fileContents = FileUtils.readFileToString(localDescriptor, StandardCharsets.UTF_8);
                    final String fileChecksum = DigestUtils.sha256Hex(fileContents);
                    localDescriptorChecksum.setChecksum(fileChecksum);
                } catch (IOException ex) {
                    exceptionMessage(ex, "Unable to locate local descriptor at " + localTemporaryDirectory + "/" + toolFile.getPath(), IO_ERROR);
                }

                // verify checksums match
                if (!remoteDescriptorChecksum.get().equals(localDescriptorChecksum)) {
                    errorMessage(CHECKSUM_MISMATCH_MESSAGE + toolFile.getPath(), API_ERROR);
                }
            } else {
                // remote descriptor checksum is empty, notify the user but continue with launch
                err(CHECKSUM_NULL_MESSAGE + toolFile.getPath());
            }
        }

        out(CHECKSUM_VALIDATED_MESSAGE);
    }

    /**
     * Creates a working directory and downloads descriptor files
     * @param type CWL or WDL
     * @return Pair of downloaded primary descriptor and zip file
     */
    public LauncherFiles initializeWorkingDirectoryWithFiles(ToolDescriptor.TypeEnum type) {
        // Try to create a working directory
        File workingDir;
        try {
            workingDir = Files.createTempDir();
        } catch (IllegalStateException ex) {
            exceptionMessage(ex, "Could not create a temporary working directory.", IO_ERROR);
            throw new RuntimeException(ex);
        }
        out("Created temporary working directory at '" + workingDir.getAbsolutePath() + "'");

        File primaryDescriptor;
        File zipFile;
        if (!isLocalEntry) {
            // If not a local entry then download remote descriptors
            try {
                primaryDescriptor = abstractEntryClient.downloadTargetEntry(entry, type, true, workingDir);
                String[] parts = entry.split(":");
                String path = parts[0];
                String convertedName = path.replaceAll("/", "_") + ".zip";
                zipFile = new File(workingDir, convertedName);
                out("Successfully downloaded files for entry '" + path + "'");
            } catch (ApiException ex) {
                if (abstractEntryClient.getEntryType().equalsIgnoreCase("tool")) {
                    exceptionMessage(ex, "The tool entry does not exist. Did you mean to launch a local tool or a workflow?",
                            ENTRY_NOT_FOUND);
                } else {
                    exceptionMessage(ex, "The workflow entry does not exist. Did you mean to launch a local workflow or a tool?",
                            ENTRY_NOT_FOUND);
                }
                throw new RuntimeException(ex);
            } catch (IOException ex) {
                exceptionMessage(ex, "Problem downloading and unzipping entry.", IO_ERROR);
                throw new RuntimeException(ex);
            }
        } else {
            // For local entries zip the directory where the primary descriptor is located
            File primaryDescriptorBase = new File(entry);
            primaryDescriptor = new File(primaryDescriptorBase.getAbsolutePath());

            File parentFile = primaryDescriptor.getParentFile();
            if (parentFile == null) {
                errorMessage("Could not find parent directory of primary descriptor", GENERIC_ERROR);
            }
            // NOTE: Support for ZIP imports exists, but we decided to comment it out for now as it was causing some issues.
            //zipFile = zipDirectory(workingDir, parentFile);
            zipFile = null;
            out("Using local file '" + entry + "' as primary descriptor");
        }

        return new LauncherFiles(workingDir, primaryDescriptor, zipFile);
    }

    /**
     * Zips the given directoryToZip and returns the zip file
     * @param workingDir The working dir to place the zip file
     * @param directoryToZip The directoryToZip to zip
     * @return The zip file created
     */
    public File zipDirectory(File workingDir, File directoryToZip) {
        String zipFilePath = workingDir.getAbsolutePath() + "/directory.zip";
        try (FileOutputStream fos = new FileOutputStream(zipFilePath); ZipOutputStream zos = new ZipOutputStream(fos)) {
            zipFile(directoryToZip, "/", zos);
        } catch (IOException ex) {
            exceptionMessage(ex, "There was a problem zipping the directory '" + directoryToZip.getPath() + "'", IO_ERROR);
        } catch (Exception ex) {
            exceptionMessage(ex, "There was a problem zipping the directory '" + directoryToZip.getPath() + "'", GENERIC_ERROR);
        }
        return new File(zipFilePath);
    }

    /**
     * A helper function for zipping directories
     * @param fileToZip File being looked at (could be a directory)
     * @param fileName Name of file being looked at
     * @param zos Zip Output Stream
     * @throws IOException
     */
    public void zipFile(File fileToZip, String fileName, ZipOutputStream zos) throws IOException {
        if (fileToZip == null) {
            return;
        }
        if (fileToZip.isHidden()) {
            return;
        }
        if (fileToZip.isDirectory()) {
            if (fileName.endsWith("/") && !Objects.equals(fileName, "/")) {
                zos.putNextEntry(new ZipEntry(fileName.endsWith("/") ? fileName : fileName + "/"));
                zos.closeEntry();
            }
            File[] children = fileToZip.listFiles();
            for (File childFile : children) {
                if (Objects.equals(fileName, "/")) {
                    zipFile(childFile, childFile.getName(), zos);
                } else {
                    zipFile(childFile, fileName + "/" + childFile.getName(), zos);
                }
            }
            return;
        }

        try (FileInputStream fis = new FileInputStream(fileToZip)) {
            ZipEntry zipEntry = new ZipEntry(fileName);
            zos.putNextEntry(zipEntry);
            final int byteLength = 1024;
            byte[] bytes = new byte[byteLength];
            int length;
            while ((length = fis.read(bytes)) >= 0) {
                zos.write(bytes, 0, length);
            }
        }
    }

    /**
     * Common code used for executing an entry
     * @param workDir
     * @param launcherName
     * @throws ExecuteException
     * @throws RuntimeException
     */
    public void commonExecutionCode(File workDir, String launcherName) throws ExecuteException, RuntimeException {
        notificationsClient.sendMessage(NotificationsClient.RUN, true);

        if (abstractEntryClient.isWesCommand()) {
            System.out.println("Executing: WES request");
            launcher.executeEntry("", workDir);
        } else {

            List<String> runCommand = launcher.buildRunCommand();
            String joinedCommand = Joiner.on(" ").join(runCommand);
            System.out.println("Executing: " + joinedCommand);

            ImmutablePair<String, String> execute;
            int exitCode = 0;
            try {
                execute = launcher.executeEntry(joinedCommand, workDir);
                stdout = execute.getLeft();
                stderr = execute.getRight();
            } catch (RuntimeException ex) {
                LOG.error("Problem running launcher" + launcherName + ": ", ex);
                if (ex.getCause() instanceof ExecuteException) {
                    exitCode = ((ExecuteException)ex.getCause()).getExitValue();
                    throw new ExecuteException("problems running command: " + runCommand, exitCode);
                }
                notificationsClient.sendMessage(NotificationsClient.RUN, false);
                throw new RuntimeException("Could not run launcher", ex);
            } finally {
                System.out.println(launcherName + " exit code: " + exitCode);
            }
        }
    }
}
