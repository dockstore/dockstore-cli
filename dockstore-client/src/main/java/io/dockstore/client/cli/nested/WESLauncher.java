package io.dockstore.client.cli.nested;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.google.common.io.Files;
import io.dockstore.client.cli.SwaggerUtility;
import io.dockstore.common.DescriptorLanguage;
import io.openapi.wes.client.api.WorkflowExecutionServiceApi;
import io.openapi.wes.client.model.RunId;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.dockstore.client.cli.ArgumentUtility.exceptionMessage;
import static io.dockstore.client.cli.ArgumentUtility.out;
import static io.dockstore.client.cli.Client.GENERIC_ERROR;
import static io.dockstore.client.cli.Client.IO_ERROR;

public class WESLauncher extends BaseLauncher {
    private static final Logger LOG = LoggerFactory.getLogger(WESLauncher.class);
    private static final String TAGS = "{\"Client\":\"Dockstore\"}";
    private static final String WORKFLOW_TYPE_VERSION = "1.0";
    private WorkflowExecutionServiceApi clientWorkflowExecutionServiceApi;


    public WESLauncher(AbstractEntryClient abstractEntryClient, DescriptorLanguage language, boolean script) {
        super(abstractEntryClient, language, script);
        setLauncherName("WES");
    }

    /**
     * Creates a copy of the Workflow Execution Service (WES) API.
     */
    @Override
    public void initialize() {
        String wesUrl = abstractEntryClient.getWesUri();
        WesRequestData wesAuth = abstractEntryClient.getWesRequestData();
        clientWorkflowExecutionServiceApi = abstractEntryClient.getWorkflowExecutionServiceApi(wesUrl, wesAuth);
    }

    /**
     * Create a command to execute entry on the command line
     *
     * @return Command to run in list format
     */
    @Override
    public List<String> buildRunCommand() {
        return null;
    }

    /**
     * Provisions output files defined in the parameter file
     * @param stdout stdout of running entry
     * @param stderr stderr of running entry
     * @param wdlOutputTarget remote path to provision outputs files to (ex: s3://oicr.temp/testing-launcher/)
     */
    @Override
    public void provisionOutputFiles(String stdout, String stderr, String wdlOutputTarget) {
        // Don't provision output files; the WES endpoint will do this
    }

    @Override
    public ImmutablePair<String, String> executeEntry(String runCommand, File workingDir) throws RuntimeException {
        runWESCommand(this.originalParameterFile, this.primaryDescriptor, this.zippedEntry);
        //TODO return something better than this? or change the return....
        return new ImmutablePair<String, String>("", "");
    }

    protected List<File> addFilesToWorkflowAttachment(File zippedEntry, File tempDir) {
        List<File> workflowAttachment = new ArrayList<>();
        if (zippedEntry != null) {
            try {
                SwaggerUtility.unzipFile(zippedEntry, tempDir);
            } catch (IOException e) {
                exceptionMessage(e, "Unable to get workflow attachment files from zip file " + zippedEntry.getName()
                                + " Request not sent.", IO_ERROR);
            }
        }
        try {
            // A null fileFilter causes all files to be returned
            String[] fileFilter = null;
            Iterator it = FileUtils.iterateFiles(tempDir, fileFilter, true);
            while (it.hasNext()) {
                File afile = (File) it.next();
                workflowAttachment.add(afile);
            }
        } catch (Exception e) {
            exceptionMessage(e, "Unable to traverse directory " + tempDir.getName() + " to get workflow "
                    + "attachment files.", GENERIC_ERROR);
        }
        return workflowAttachment;
    }

    public void runWESCommand(String jsonInputFilePath, File localPrimaryDescriptorFile, File zippedEntry) {
        File workflowParams = new File(jsonInputFilePath);

        String languageType = this.languageType.toString().toUpperCase();

        // CWL uses workflow type version with a 'v' prefix, e.g 'v1.0', but WDL uses '1.0'
        String workflowTypeVersion = WORKFLOW_TYPE_VERSION;
        if ("CWL".equalsIgnoreCase(languageType)) {
            workflowTypeVersion = "v" + WORKFLOW_TYPE_VERSION;
        }

        String workflowURL = localPrimaryDescriptorFile.getName();

        final File tempDir = Files.createTempDir();
        List<File> workflowAttachment = addFilesToWorkflowAttachment(zippedEntry, tempDir);

        try {
            RunId response = clientWorkflowExecutionServiceApi.runWorkflow(workflowParams, languageType, workflowTypeVersion, TAGS,
                    "{}", workflowURL, workflowAttachment);
            String runID = response.getRunId();
            out("Launched WES run with id: " + runID);
            wesCommandSuggestions(runID);
        } catch (io.openapi.wes.client.ApiException e) {
            LOG.error("Error launching WES run", e);
        } finally {
            try {
                FileUtils.deleteDirectory(tempDir);
            } catch (IOException ioe) {
                LOG.error("Could not delete temporary directory" + tempDir + " for workflow attachment files", ioe);
            }
        }
    }

    /**
     * help text output
     */
    private void wesCommandSuggestions(String runId) {
        String wesEntryType = abstractEntryClient.getEntryType().toString().toLowerCase();
        out("");
        out("To get status for this run: dockstore " + wesEntryType + " wes status --id " + runId
                + " [--verbose][--wes-url <WES URL>][--wes-auth <auth>]");
        out("To cancel this run: dockstore " + wesEntryType + " wes cancel --id " + runId
                + " [--wes-url <WES URL>][--wes-auth <auth>]");
    }

}
