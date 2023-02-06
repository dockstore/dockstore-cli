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

package io.dockstore.client.cli.nested;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.ws.rs.core.GenericType;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.google.common.base.Joiner;
import io.dockstore.client.cli.Client;
import io.dockstore.client.cli.JCommanderUtility;
import io.dockstore.client.cli.SwaggerUtility;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.SourceControl;
import io.openapi.wes.client.api.WorkflowExecutionServiceApi;
import io.swagger.client.ApiException;
import io.swagger.client.api.UsersApi;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.Label;
import io.swagger.client.model.PublishRequest;
import io.swagger.client.model.SourceFile;
import io.swagger.client.model.StarRequest;
import io.swagger.client.model.ToolDescriptor;
import io.swagger.client.model.User;
import io.swagger.client.model.Workflow;
import io.swagger.client.model.WorkflowVersion;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.dockstore.client.cli.ArgumentUtility.CONVERT;
import static io.dockstore.client.cli.ArgumentUtility.DESCRIPTION_HEADER;
import static io.dockstore.client.cli.ArgumentUtility.GIT_HEADER;
import static io.dockstore.client.cli.ArgumentUtility.LAUNCH;
import static io.dockstore.client.cli.ArgumentUtility.NAME_HEADER;
import static io.dockstore.client.cli.ArgumentUtility.boolWord;
import static io.dockstore.client.cli.ArgumentUtility.columnWidthsWorkflow;
import static io.dockstore.client.cli.ArgumentUtility.conditionalErrorMessage;
import static io.dockstore.client.cli.ArgumentUtility.containsHelpRequest;
import static io.dockstore.client.cli.ArgumentUtility.err;
import static io.dockstore.client.cli.ArgumentUtility.errorMessage;
import static io.dockstore.client.cli.ArgumentUtility.exceptionMessage;
import static io.dockstore.client.cli.ArgumentUtility.getGitRegistry;
import static io.dockstore.client.cli.ArgumentUtility.optVal;
import static io.dockstore.client.cli.ArgumentUtility.out;
import static io.dockstore.client.cli.ArgumentUtility.outFormatted;
import static io.dockstore.client.cli.ArgumentUtility.printHelpFooter;
import static io.dockstore.client.cli.ArgumentUtility.printHelpHeader;
import static io.dockstore.client.cli.ArgumentUtility.printLineBreak;
import static io.dockstore.client.cli.ArgumentUtility.reqVal;
import static io.dockstore.client.cli.Client.CLIENT_ERROR;
import static io.dockstore.client.cli.Client.COMMAND_ERROR;
import static io.dockstore.client.cli.Client.ENTRY_NOT_FOUND;
import static io.dockstore.client.cli.Client.HELP;
import static io.dockstore.client.cli.Client.IO_ERROR;
import static io.dockstore.client.cli.Client.WORKFLOW;
import static io.dockstore.client.cli.JCommanderUtility.printJCommanderHelp;
import static io.dockstore.client.cli.nested.ToolClient.VERSION_TAG;
import static io.dockstore.client.cli.nested.WesCommandParser.ENTRY;
import static io.dockstore.client.cli.nested.WesCommandParser.JSON;
import static java.lang.String.join;

/**
 * This stub will eventually implement all operations on the CLI that are specific to workflows.
 *
 * @author dyuen
 */
public class WorkflowClient extends AbstractEntryClient<Workflow> {

    public static final String BIOWORKFLOW = "bioworkflow";
    public static final String LAUNCH_COMMAND_NAME = LAUNCH;
    public static final String GITHUB_APP_COMMAND_ERROR = "Command not supported for GitHub App entries";
    public static final String UPDATE_WORKFLOW = "update_workflow";
    public static final String RESTUB = "restub";
    protected static final Logger LOG = LoggerFactory.getLogger(WorkflowClient.class);
    protected final WorkflowsApi workflowsApi;
    protected final UsersApi usersApi;
    protected final Client client;
    private final JCommander jCommander;
    private final CommandLaunch commandLaunch;

    private boolean isAppTool;

    public WorkflowClient(WorkflowsApi workflowApi, UsersApi usersApi, Client client, boolean isAdmin) {
        this.workflowsApi = workflowApi;
        this.usersApi = usersApi;
        this.client = client;
        this.isAdmin = isAdmin;
        this.jCommander = new JCommander();
        this.commandLaunch = new CommandLaunch();
        this.jCommander.addCommand(LAUNCH, commandLaunch);
    }

    private static void printWorkflowList(List<Workflow> workflows) {
        int[] maxWidths = columnWidthsWorkflow(workflows);

        int nameWidth = maxWidths[0] + Client.PADDING;
        int descWidth = maxWidths[1] + Client.PADDING;
        int gitWidth = maxWidths[2] + Client.PADDING;
        String format = "%-" + nameWidth + "s%-" + descWidth + "s%-" + gitWidth + "s%-16s";
        outFormatted(format, NAME_HEADER, DESCRIPTION_HEADER, GIT_HEADER, "PUBLISHED");

        for (Workflow workflow : workflows) {
            String gitUrl = "";

            if (workflow.getGitUrl() != null && !workflow.getGitUrl().isEmpty()) {
                gitUrl = workflow.getGitUrl();
            }

            String description = getCleanedDescription(workflow.getDescription());

            outFormatted(format, workflow.getFullWorkflowPath(), description, gitUrl, boolWord(workflow.isIsPublished()));
        }
    }

    public boolean isAppTool() {
        return isAppTool;
    }

    private void manualPublishHelp() {
        printHelpHeader();
        out(join(" ", "Usage: dockstore", getEntryType().toLowerCase(), MANUAL_PUBLISH, HELP));
        out(join(" ", "       dockstore", getEntryType().toLowerCase(), MANUAL_PUBLISH, "[parameters]"));
        out("");
        out("Description:");
        out("  Manually register a workflow in dockstore. If this is successful and the workflow is valid, then publish.");
        out("");
        out("Required parameters:");
        out("  --repository <repository>                            Name for the git repository");
        out("  --organization <organization>                        Organization for the git repo");
        out("  --git-version-control <git version control>          Either github, gitlab, or bitbucket");
        out("");
        out("Optional parameters:");
        out("  --workflow-path <workflow-path>                      Path for the descriptor file, defaults to /Dockstore.cwl");
        out("  --workflow-name <workflow-name>                      Workflow name, defaults to null");
        out("  --descriptor-type <descriptor-type>                  Descriptor type, defaults to " + DescriptorLanguage.CWL);
        out("  --test-parameter-path <test-parameter-path>          Path to default test parameter file, defaults to /test.json");

        printHelpFooter();
    }

    private void updateWorkflowHelp() {
        printHelpHeader();
        out(join(" ", "Usage: dockstore", getEntryType().toLowerCase(), UPDATE_WORKFLOW, HELP));
        out(join(" ", "       dockstore", getEntryType().toLowerCase(), UPDATE_WORKFLOW, "[parameters]"));
        out("");
        out("Description:");
        out("  Update certain fields for a given workflow.");
        out("");
        out("Required Parameters:");
        out("  " + ENTRY + " <entry>                                              Complete workflow path in Dockstore (ex. github.com/collaboratory/seqware-bwa-workflow)");
        out("");
        out("Optional Parameters");
        out("  --descriptor-type <descriptor-type>                          Descriptor type of the given workflow.  Can only be altered if workflow is a STUB.");
        out("  --workflow-path <workflow-path>                              Path to default workflow descriptor location");
        out("  --default-version <default-version>                          Default branch name");
        out("  --default-test-parameter-path <default-test-parameter-path>  Default test parameter file path");
        printHelpFooter();
    }

    protected void versionTagHelp() {
        printHelpHeader();
        out(join(" ", "Usage: dockstore", getEntryType().toLowerCase(), VERSION_TAG, HELP));
        out(join(" ", "       dockstore", getEntryType().toLowerCase(), VERSION_TAG, "[parameters]"));
        out("");
        out("Description:");
        out("  Update certain fields for a given " + getEntryType().toLowerCase() + " version.");
        out("");
        out("Required Parameters:");
        out("  " + ENTRY + " <entry>                                      Complete " + getEntryType().toLowerCase()
            + " path in Dockstore (ex. quay.io/collaboratory/seqware-bwa-workflow)");
        out("  --name <name>                                        Name of the " + getEntryType().toLowerCase() + " version.");
        out("");
        out("Optional Parameters");
        out("  --workflow-path <workflow-path>                      Path to " + getEntryType().toLowerCase() + " descriptor");
        out("  --hidden <true/false>                                Hide the tag from public viewing, default false");
        printHelpFooter();
    }

    public WorkflowsApi getWorkflowsApi() {
        return workflowsApi;
    }

    @Override
    public String getEntryType() {
        return "Workflow";
    }

    @Override
    public void handleLabels(String entryPath, Set<String> addsSet, Set<String> removesSet) {
        // Try and update the labels for the given workflow
        try {
            Workflow workflow = findAndGetDockstoreWorkflowByPath(entryPath, null, false, true);
            long workflowId = workflow.getId();
            List<Label> existingLabels = workflow.getLabels();

            String combinedLabelString = generateLabelString(addsSet, removesSet, existingLabels);

            Workflow updatedWorkflow = workflowsApi.updateLabels(workflowId, combinedLabelString, "");

            List<Label> newLabels = updatedWorkflow.getLabels();
            if (!newLabels.isEmpty()) {
                out("The workflow has the following labels:");
                for (Label newLabel : newLabels) {
                    out(newLabel.getValue());
                }
            } else {
                out("The workflow has no labels.");
            }

        } catch (ApiException ex) {
            exceptionMessage(ex, "", Client.API_ERROR);
        }
    }

    @Override
    public String getConfigFile() {
        return client.getConfigFile();
    }

    @Override
    protected void printClientSpecificHelp() {
        out("");
        out("  " + MANUAL_PUBLISH + "   :  registers a Github, Gitlab or Bitbucket workflow in Dockstore and then attempts to " + PUBLISH);
        out("");
        out("  " + UPDATE_WORKFLOW + "  :  updates certain fields of a workflow");
        out("");
        out("  " + VERSION_TAG + "      :  updates an existing version tag of a workflow");
        out("");
        out("  restub           :  converts a full, unpublished workflow back to a stub");
        out("");
    }

    @Override
    public void handleEntry2json(List<String> args) throws ApiException, IOException {
        String commandName = ENTRY_2_JSON;
        String[] argv = args.toArray(new String[0]);
        String[] argv1 = {commandName};
        String[] both = ArrayUtils.addAll(argv1, argv);
        CommandEntry2json commandEntry2json = new CommandEntry2json();
        JCommander jc = new JCommander();
        jc.addCommand(commandName, commandEntry2json);
        jc.setProgramName("dockstore workflow " + CONVERT);
        try {
            jc.parse(both);
            if (commandEntry2json.help) {
                printJCommanderHelp(jc, "dockstore workflow " + CONVERT, commandName);
            } else {
                final String runString = convertWorkflow2Json(commandEntry2json.entry, true);
                out(runString);
            }
        } catch (ParameterException e1) {
            out(e1.getMessage());
            printJCommanderHelp(jc, "dockstore workflow " + CONVERT, commandName);
        }
    }

    private String convertWorkflow2Json(String entry, final boolean json) throws ApiException, IOException {
        // User may enter the version, so we have to extract the path
        String[] parts = entry.split(":");
        String path = parts[0];
        Workflow workflow = findAndGetDockstoreWorkflowByPath(path);
        String descriptor = workflow.getDescriptorType().getValue();
        LanguageClientInterface languageCLient = convertCLIStringToEnum(descriptor);
        return languageCLient.generateInputJson(entry, json);
    }

    /**
     * Try and get the workflow with the path (unauthenticated/authenticated bioworkflow, unauthenticated/authenticated apptool)
     *
     * @param path Path of the apptool or bioworkflow
     * @return
     */
    public Workflow findAndGetDockstoreWorkflowByPath(String path) {
        return findAndGetDockstoreWorkflowByPath(path, null, true, true);
    }

    public Workflow findAndGetDockstoreWorkflowByPath(String entryPath, String include, boolean searchUnauthenticated, boolean searchAppTool) {
        WebserviceWorkflowClient webserviceWorkflowClient = new WebserviceWorkflowClient(workflowsApi, include, searchUnauthenticated, searchAppTool);
        Workflow workflow = webserviceWorkflowClient.findAndGetDockstoreWorkflowByPath(entryPath);
        this.isAppTool = webserviceWorkflowClient.isFoundAppTool();
        return workflow;
    }

    protected Workflow getDockstoreWorkflowById(Long id) {
        // simply getting published descriptors does not require permissions
        Workflow workflow = null;
        try {
            workflow = workflowsApi.getPublishedWorkflow(id, null);
        } catch (ApiException e) {
            if (e.getResponseBody().contains("Entry not found")) {
                LOG.info("Unable to locate entry without credentials, trying again as authenticated user");
                workflow = workflowsApi.getWorkflow(id, null);
            }
        } finally {
            if (workflow == null) {
                errorMessage("No workflow found with id " + id, Client.ENTRY_NOT_FOUND);
            }
        }
        return workflow;
    }

    public File downloadTargetEntry(String toolpath, ToolDescriptor.TypeEnum type, boolean unzip) throws IOException {
        return downloadTargetEntry(toolpath, type, unzip, new File(System.getProperty("user.dir")));
    }

    /**
     * Disturbingly similar to WorkflowClient#downloadTargetEntry, could use cleanup refactoring
     *
     * @param toolpath  a unique identifier for an entry, called a path for workflows and tools
     * @param unzip     unzip the entry after downloading
     * @param directory directory to unzip descriptors into
     * @return A path to the primary descriptor if contents were unzipped, otherwise return a path to the zip file itself
     */
    public File downloadTargetEntry(String toolpath, ToolDescriptor.TypeEnum type, boolean unzip, File directory) throws IOException {
        String[] parts = toolpath.split(":");
        String path = parts[0];
        // match behaviour from getDescriptorFromServer, use master if no version is provided
        Workflow workflow = findAndGetDockstoreWorkflowByPath(path, "versions", true, true);
        String tag = getVersionID(toolpath);
        Optional<WorkflowVersion> first = workflow.getWorkflowVersions().stream().filter(foo -> foo.getName().equalsIgnoreCase(tag))
            .findFirst();

        if (first.isPresent()) {
            boolean isValid = first.get().isValid();
            if (!isValid) {
                errorMessage("Cannot use workflow version '" + first.get().getName() + "' because it is not valid. Please pick a"
                    + " workflow version that is recognized as valid by Dockstore.", CLIENT_ERROR);
            }
            Long versionId = first.get().getId();
            // https://github.com/dockstore/dockstore/issues/1712 client seems to use jersey logging which is not controlled from logback
            workflowsApi.getApiClient().setDebugging(false);
            byte[] arbitraryURL = SwaggerUtility
                .getArbitraryURL("/workflows/" + workflow.getId() + "/zip/" + versionId, new GenericType<byte[]>() {
                }, workflowsApi.getApiClient());
            workflowsApi.getApiClient().setDebugging(Client.DEBUG.get());
            File zipFile = new File(directory, zipFilename(workflow));
            FileUtils.writeByteArrayToFile(zipFile, arbitraryURL, false);

            // If we unzip the file, we can provide a path to the primary descriptor, otherwise just provide a path to the zip file
            if (unzip) {
                SwaggerUtility.unzipFile(zipFile, directory);
                Files.delete(zipFile.toPath());
                return new File(directory, first.get().getWorkflowPath());
            }
            return zipFile;
        } else {
            throw new RuntimeException("version not found");
        }
    }

    /**
     * Appends the #workflow/ prefix to the start of the provided path if necessary
     *
     * @param entryPath path to either a tool or workflow
     */
    @Override
    public String getTrsId(String entryPath) {
        if (isAppTool) {
            return entryPath;
        } else {
            return entryPath.startsWith("#workflow/") ? entryPath : "#workflow/" + entryPath;
        }
    }

    /**
     * Returns the version ID of the given workflow, falls back to the latest version
     *
     * @param entryPath Workflow path
     */
    @Override
    public String getVersionID(String entryPath) {
        final String[] parts = entryPath.split(":");

        final String versionID = parts.length > 1 ? parts[1] : "master";

        final Workflow workflow = findAndGetDockstoreWorkflowByPath(parts[0], "versions", true, true);

        // ensure workflow has version
        Optional<WorkflowVersion> first = workflow.getWorkflowVersions().stream().filter(foo -> foo.getName().equalsIgnoreCase(versionID))
            .findFirst();

        // if no master is present (for example, for hosted workflows), fail over to the latest descriptor
        if (first.isEmpty()) {
            first = workflow.getWorkflowVersions().stream().max(Comparator.comparing(WorkflowVersion::getLastModified));
            first.ifPresent(workflowVersion -> out(
                "Could not locate workflow with version '" + versionID + "'. Using last modified version '" + workflowVersion.getName()
                    + "' instead."));
        }

        return first.isEmpty() ? versionID : first.get().getName();
    }

    @Override
    public String zipFilename(Workflow workflow) {
        return workflow.getFullWorkflowPath().replaceAll("/", "_") + ".zip";
    }

    /**
     * @param args Arguments entered into the CLI
     */
    @Override
    public void launch(final List<String> args) {
        preValidateLaunchArguments(args);
        String[] argv = args.toArray(new String[0]);
        String[] argv1 = {LAUNCH_COMMAND_NAME};
        String[] both = ArrayUtils.addAll(argv1, argv);
        this.jCommander.parse(both);
        String entry = commandLaunch.entry;
        String localEntry = commandLaunch.localEntry;
        String jsonRun = commandLaunch.json;
        String yamlRun = commandLaunch.yaml;
        String wdlOutputTarget = commandLaunch.wdlOutputTarget;
        boolean ignoreChecksumFlag = commandLaunch.ignoreChecksums;
        String uuid = commandLaunch.uuid;

        if (this.commandLaunch.help) {
            JCommanderUtility.printJCommanderHelpLaunch(jCommander, "dockstore workflow", LAUNCH_COMMAND_NAME);
        } else {
            launchWithArgs(entry, localEntry, jsonRun, yamlRun, wdlOutputTarget, ignoreChecksumFlag, uuid);
        }
    }

    @Override
    public void launchWithArgs(final String entry, final String localEntry, final String jsonRun, final String yamlRun, final String wdlOutput, final boolean ignoreChecksumFlag, final String uuid) {

        // trim the final slash on output if it is present, probably an error ( https://github.com/aws/aws-cli/issues/421 ) causes a double slash which can fail
        final String wdlOutputTarget = wdlOutput != null ? wdlOutput.replaceAll("/$", "") : null;

        if (this.commandLaunch.help) {
            JCommanderUtility.printJCommanderHelpLaunch(jCommander, "dockstore workflow", LAUNCH_COMMAND_NAME);
        } else {
            checkIfDockerRunning(); // print a warning message if Docker is not running
            if ((entry == null) != (localEntry == null)) {
                if (entry != null) {
                    this.isLocalEntry = false;
                    this.ignoreChecksums = ignoreChecksumFlag;
                    String[] parts = entry.split(":");
                    String path = parts[0];
                    try {
                        Workflow workflow = findAndGetDockstoreWorkflowByPath(path);
                        final Workflow.DescriptorTypeEnum descriptorType = workflow.getDescriptorType();
                        final String descriptor = descriptorType.getValue().toLowerCase();
                        LanguageClientInterface languageClientInterface = convertCLIStringToEnum(descriptor);
                        DescriptorLanguage language = DescriptorLanguage.convertShortStringToEnum(descriptor);
                        try {
                            switch (language) {
                            case CWL:
                                languageClientInterface.launch(entry, false, yamlRun, jsonRun, null, uuid);
                                break;
                            case WDL:
                            case NEXTFLOW:
                                conditionalErrorMessage((yamlRun != null), "--yaml is not supported please use " + JSON + " instead", CLIENT_ERROR);
                                languageClientInterface.launch(entry, false, null, jsonRun, wdlOutputTarget, uuid);
                                break;
                            default:
                                errorMessage("Workflow type not supported for launch: " + path, ENTRY_NOT_FOUND);
                                break;
                            }
                        } catch (Exception e) {
                            // in addition to its checked exceptions, languageClientInterface.launch() can throw a variety of RuntimeExceptions, handle them all here
                            exceptionMessage(e, "Could not launch entry", IO_ERROR);
                        }

                    } catch (ApiException e) {
                        exceptionMessage(e, "Could not get workflow: " + path, ENTRY_NOT_FOUND);
                    }
                } else {
                    this.isLocalEntry = true;
                    checkEntryFile(localEntry, jsonRun, yamlRun, wdlOutputTarget, uuid);
                }
            } else {
                out("You can only use one of --local-entry and --entry at a time.");
                JCommanderUtility.printJCommanderHelpLaunch(jCommander, "dockstore workflow", LAUNCH_COMMAND_NAME);
            }
        }
    }

    /**
     * Attempts to launch a workflow on a WES server
     * @param clientWorkflowExecutionServiceApi The WES API client
     * @param entry The path to the desired entry (i.e. github.com/myrepo/myworfklow:version1
     * @param inlineWorkflow Indicates that the workflow files will be inlined directly into the WES HTTP request
     * @param paramsPath Path to the parameter JSON file
     * @param filePaths Paths to any other required files for the WES execution
     * @param verbose Launching with verbose logs
     */
    void wesLaunch(WorkflowExecutionServiceApi clientWorkflowExecutionServiceApi, String entry, boolean inlineWorkflow, String paramsPath,
        List<String> filePaths, boolean verbose) {
        WesLauncher.launchWesCommand(clientWorkflowExecutionServiceApi, this, entry, inlineWorkflow, paramsPath, filePaths, verbose);
    }

    @Override
    public Client getClient() {
        return this.client;
    }

    /**
     * this function will check for the content and the extension of entry file
     *
     * @param entry relative path to local descriptor for either WDL/CWL tools or workflows this will either give back exceptionMessage and exit (if the content/extension/descriptor is invalid) OR
     *              proceed with launching the entry file (if it's valid)
     * @param uuid
     */
    private void checkEntryFile(String entry, String jsonRun, String yamlRun, String wdlOutputTarget, String uuid) {
        File file = new File(entry);
        Optional<DescriptorLanguage> optExt = checkFileExtension(file.getPath());     //file extension could be cwl,wdl or ""

        if (!file.exists() || file.isDirectory()) {
            errorMessage("The workflow file " + file.getPath() + " does not exist. Did you mean to " + LAUNCH + " a remote " + WORKFLOW + "?",
                ENTRY_NOT_FOUND);
        }
        Optional<LanguageClientInterface> languageCLientOptional = LanguageClientFactory.createLanguageCLient(this, optExt.get());
        LanguageClientInterface languageCLient = languageCLientOptional
            .orElseThrow(() -> new UnsupportedOperationException("language not supported yet"));
        // TODO: limitations of merged but non-cleaned up interface are apparent here
        try {
            DescriptorLanguage ext = optExt.get();
            switch (ext) {
            case CWL:
                languageCLient.launch(entry, true, yamlRun, jsonRun, null, uuid);
                break;
            case WDL:
                languageCLient.launch(entry, true, yamlRun, jsonRun, wdlOutputTarget, uuid);
                break;
            case NEXTFLOW:
                languageCLient.launch(entry, true, null, jsonRun, null, uuid);
                break;
            default:
                Optional<DescriptorLanguage> content = checkFileContent(file);             //check the file content (wdl,cwl or "")
                switch (content.get()) {
                case CWL:
                    out("This is a CWL file.. Please put an extension to the entry file name.");
                    out("Launching entry file as a CWL file..");
                    languageCLient.launch(entry, true, yamlRun, jsonRun, null, uuid);
                    break;
                case WDL:
                    out("This is a WDL file.. Please put an extension to the entry file name.");
                    out("Launching entry file as a WDL file..");
                    languageCLient.launch(entry, true, null, jsonRun, wdlOutputTarget, uuid);
                    break;
                case NEXTFLOW:
                    out("This is a Nextflow file.. Please put an extension to the entry file name.");
                    out("Launching entry file as a Nextflow file..");
                    languageCLient.launch(entry, true, null, jsonRun, null, uuid);
                    break;
                default:
                    errorMessage("Entry file is invalid. Please enter a valid CWL/WDL file with the correct extension on the file name.",
                        CLIENT_ERROR);
                }
            }
        } catch (ApiException e) {
            exceptionMessage(e, "API error launching entry", Client.API_ERROR);
        } catch (IOException e) {
            exceptionMessage(e, "IO error launching entry", IO_ERROR);
        }
    }

    @Override
    public void handleInfo(String entryPath) {
        try {
            Workflow workflow = findAndGetDockstoreWorkflowByPath(entryPath, null, true, true);
            if (workflow == null || !workflow.isIsPublished()) {
                errorMessage("This workflow is not published.", COMMAND_ERROR);
            } else {
                Date lastUpdated = new Date(workflow.getLastUpdated());

                String description = workflow.getDescription();
                if (description == null) {
                    description = "";
                }

                String author = workflow.getAuthor();
                if (author == null) {
                    author = "";
                }

                String date = lastUpdated.toString();

                out(workflow.getFullWorkflowPath());
                out("");
                out("DESCRIPTION:");
                out(description);
                out("AUTHOR:");
                out(author);
                out("DATE UPLOADED:");
                out(date);
                out("WORKFLOW VERSIONS");

                List<WorkflowVersion> workflowVersions = workflow.getWorkflowVersions();
                int workflowVersionsSize = workflowVersions.size();
                StringBuilder builder = new StringBuilder();
                if (workflowVersionsSize > 0) {
                    builder.append(workflowVersions.get(0).getName());
                    for (int i = 1; i < workflowVersionsSize; i++) {
                        builder.append(", ").append(workflowVersions.get(i).getName());
                    }
                }

                out(builder.toString());

                out("GIT REPO:");
                if (Objects.equals(workflow.getMode(), Workflow.ModeEnum.HOSTED)) {
                    out("Dockstore.org");
                } else {
                    out(workflow.getGitUrl());
                }
            }

        } catch (ApiException ex) {
            exceptionMessage(ex, "Could not find workflow", Client.API_ERROR);
        }
    }

    protected void refreshAllEntries() {
        try {
            User user = usersApi.getUser();
            if (user == null) {
                throw new RuntimeException("User not found");
            }

            // add user to all workflows
            final List<Workflow> updatedWorkflows = usersApi.addUserToDockstoreWorkflows(user.getId(), "").stream()
                // Skip hosted workflows
                .filter(workflow -> StringUtils.isNotEmpty(workflow.getGitUrl()))
                .map(workflow -> {
                    out(MessageFormat.format("Refreshing {0}", workflow.getFullWorkflowPath()));
                    try {
                        return workflowsApi.refresh(workflow.getId(), true);
                    } catch (ApiException ex) {
                        err(ex.getMessage());
                        return null;
                    } catch (Exception ex) {
                        exceptionMessage(ex, "", 0);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
            printLineBreak();
            printWorkflowList(updatedWorkflows);
        } catch (ApiException ex) {
            exceptionMessage(ex, "", Client.API_ERROR);
        }
    }

    @Override
    protected void refreshTargetEntry(String path) {
        try {
            Workflow workflow = findAndGetDockstoreWorkflowByPath(path, null, false, true);
            if (isAppTool) {
                errorMessage("GitHub Apps entries cannot be refreshed", COMMAND_ERROR);
            }
            final Long workflowId = workflow.getId();
            out("Refreshing workflow...");
            Workflow updatedWorkflow = workflowsApi.refresh(workflowId, true);
            List<Workflow> workflowList = new ArrayList<>();
            workflowList.add(updatedWorkflow);
            out("YOUR UPDATED WORKFLOW");
            printLineBreak();
            printWorkflowList(workflowList);
        } catch (ApiException ex) {
            errorMessage(ex.getMessage(), Client.API_ERROR);
        }
    }

    @Override
    protected void handlePublishUnpublish(String entryPath, String newName, boolean unpublishRequest) {
        Workflow existingWorkflow = null;
        boolean isPublished = false;

        // Cannot be making an unpublish request with a specified new name
        assert (!(unpublishRequest && newName != null));

        try {
            existingWorkflow = findAndGetDockstoreWorkflowByPath(entryPath, null, false, true);
            isPublished = existingWorkflow.isIsPublished();
        } catch (ApiException ex) {
            exceptionMessage(ex, "Unable to " + (unpublishRequest ? "unpublish " : PUBLISH + " ") + entryPath, Client.API_ERROR);
        }

        if (unpublishRequest) {
            if (isPublished) {
                publish(false, entryPath);
            } else {
                out("The following workflow is already unpublished: " + entryPath);
            }
        } else {
            if (newName == null) {
                if (isPublished) {
                    out("The following workflow is already published: " + entryPath);
                } else {
                    publish(true, entryPath);
                }
            } else if (!workflowExists(entryPath + "/" + newName)) {
                try {
                    // path should be represented as repository organization and name (ex. dockstore/dockstore-ui2)
                    final Workflow newWorkflow = workflowsApi.manualRegister(
                        getGitRegistry(existingWorkflow.getGitUrl()),
                        existingWorkflow.getOrganization() + "/" + existingWorkflow.getRepository(),
                        existingWorkflow.getWorkflowPath(),
                        newName,
                        existingWorkflow.getDescriptorType().toString(),
                        existingWorkflow.getDefaultTestParameterFilePath()
                    );

                    final String completeEntryPath = entryPath + "/" + newName;

                    out("Successfully registered " + completeEntryPath);

                    workflowsApi.refresh(newWorkflow.getId(), true);
                    publish(true, completeEntryPath);
                } catch (ApiException ex) {
                    exceptionMessage(ex, "Unable to " + PUBLISH + " " + entryPath + "/" + newName, Client.API_ERROR);
                }
            } else {
                out("The following workflow is already registered: " + entryPath + "/" + newName);
            }
        }
    }

    private boolean workflowExists(String entryPath) {
        try {
            workflowsApi.getWorkflowByPath(entryPath, BIOWORKFLOW, null);
            return true;
        } catch (ApiException ex) {
            return false;
        }
    }

    @Override
    protected void publishHelp() {
        printHelpHeader();
        out(join(" ", "Usage: dockstore", getEntryType().toLowerCase(), PUBLISH, HELP));
        out(join(" ", "       dockstore", getEntryType().toLowerCase(), PUBLISH));
        out(join(" ", "       dockstore", getEntryType().toLowerCase(), PUBLISH, "[parameters]"));
        out(join(" ", "       dockstore", getEntryType().toLowerCase(), PUBLISH, "--unpub [parameters]"));
        out("");
        out("Description:");
        out("  Publish/unpublish a registered " + getEntryType() + ".");
        out("  No arguments will list the current and potential " + getEntryType() + "s to share.");
        out("Required Parameters:");
        out("  " + ENTRY + " <entry>                      Complete " + getEntryType()
            + " path in Dockstore (ex. quay.io/collaboratory/seqware-bwa-workflow)");
        out("Optional Parameters:");
        out("  --new-entry-name <new-workflow-name>         New name to give the workflow specified by --entry. This will register and " + PUBLISH + " a new copy of the workflow with the given name.");
        printHelpFooter();
    }

    @Override
    protected void handleListNonpublishedEntries() {
        try {
            // check user info after usage so that users can get usage without live webservice
            User user = usersApi.getUser();
            if (user == null) {
                errorMessage("User not found", Client.CLIENT_ERROR);
            }
            List<Workflow> workflows = usersApi.userWorkflows(user.getId()).stream()
                .filter(workflow -> workflow.getMode() != Workflow.ModeEnum.STUB).collect(Collectors.toList());

            out("YOUR AVAILABLE WORKFLOWS");
            printLineBreak();
            printWorkflowList(workflows);
        } catch (ApiException ex) {
            exceptionMessage(ex, "", Client.API_ERROR);
        }
    }

    @Override
    protected void handleListUnstarredEntries() {
        try {
            List<Workflow> workflows = workflowsApi.allPublishedWorkflows(null, null, null, null, null, false, null);
            out("ALL PUBLISHED WORKFLOWS");
            printLineBreak();
            printWorkflowList(workflows);
        } catch (ApiException ex) {
            exceptionMessage(ex, "", Client.API_ERROR);
        }
    }

    public void publish(boolean publish, String entry) {
        String action = PUBLISH;
        if (!publish) {
            action = "unpublish";
        }

        try {
            Workflow workflow = findAndGetDockstoreWorkflowByPath(entry, null, false, true);
            PublishRequest pub = SwaggerUtility.createPublishRequest(publish);
            workflow = workflowsApi.publish(workflow.getId(), pub);

            if (workflow != null) {
                out("Successfully " + action + "ed  " + entry);
            } else {
                errorMessage("Unable to " + action + " workflow " + entry, COMMAND_ERROR);
            }
        } catch (ApiException ex) {
            exceptionMessage(ex, "Unable to " + action + " workflow " + entry, Client.API_ERROR);
        }
    }

    /**
     * Interacts with API to star/unstar a workflow
     *
     * @param entry the workflow or tool
     * @param star  true to star, false to unstar
     */
    @Override
    protected void handleStarUnstar(String entry, boolean star) {
        String action = star ? STAR : "unstar";
        try {
            Workflow workflow = findAndGetDockstoreWorkflowByPath(entry, null, true, true);
            StarRequest request = new StarRequest();
            request.setStar(star);
            workflowsApi.starEntry(workflow.getId(), request);
            out("Successfully " + action + "red " + entry);
        } catch (ApiException ex) {
            exceptionMessage(ex, "Unable to " + action + " workflow " + entry, Client.API_ERROR);
        }
    }

    @Override
    protected void handleSearch(String pattern) {
        try {
            List<Workflow> workflows = workflowsApi.allPublishedWorkflows(null, null, pattern, null, null, false, null);

            out("MATCHING WORKFLOWS");
            printLineBreak();
            printWorkflowList(workflows);
        } catch (ApiException ex) {
            exceptionMessage(ex, "", Client.API_ERROR);
        }
    }

    @Override
    protected void handleList() {
        try {
            // check user info after usage so that users can get usage without live webservice
            User user = usersApi.getUser();
            if (user == null) {
                errorMessage("User not found", Client.CLIENT_ERROR);
            }
            List<Workflow> workflows = usersApi.userPublishedWorkflows(user.getId());
            printWorkflowList(workflows);
        } catch (ApiException ex) {
            exceptionMessage(ex, "", Client.API_ERROR);
        }
    }

    protected List<String> getClientSpecificCommands() {
        List<String> possibleCommands = new ArrayList<String>();
        possibleCommands.addAll(Arrays.asList(UPDATE_WORKFLOW, VERSION_TAG, RESTUB));
        return possibleCommands;
    }

    // If adding command, please update getClientSpecificCommands()
    @Override
    public boolean processEntrySpecificCommands(List<String> args, String activeCommand) {
        if (null != activeCommand) {
            switch (activeCommand) {
            case UPDATE_WORKFLOW:
                updateWorkflow(args);
                break;
            case VERSION_TAG:
                versionTag(args);
                break;
            case RESTUB:
                restub(args);
                break;
            default:
                return false;
            }
            return true;
        }
        return false;
    }

    @Override
    public void manualPublish(final List<String> args) {
        if (args.isEmpty() || containsHelpRequest(args)) {
            manualPublishHelp();
        } else {
            final String repository = reqVal(args, "--repository");
            final String organization = reqVal(args, "--organization");
            final String gitVersionControl = reqVal(args, "--git-version-control");

            final String workflowPath = optVal(args, "--workflow-path", "/Dockstore.cwl");
            final String descriptorType = optVal(args, "--descriptor-type", DescriptorLanguage.CWL.toString()).toUpperCase();
            final String testParameterFile = optVal(args, "--test-parameter-path", "/test.json");

            // Check if valid input
            if (!DescriptorLanguage.CWL.toString().equals(descriptorType) && !DescriptorLanguage.WDL.toString().equals(descriptorType)) {
                errorMessage("Please ensure that the descriptor type is either cwl or wdl.", Client.CLIENT_ERROR);
            }

            if (!FilenameUtils.getExtension(workflowPath).equalsIgnoreCase(descriptorType)) {
                errorMessage("Please ensure that the given workflow path '" + workflowPath + "' is of type " + descriptorType
                    + " and has the file extension " + descriptorType.toLowerCase(), CLIENT_ERROR);
            }

            String workflowname = optVal(args, "--workflow-name", null);

            if (workflowname != null && workflowname.startsWith("_")) {
                errorMessage("Workflow names cannot start with an underscore.", Client.CLIENT_ERROR);
            }

            // Make new workflow object
            String path = Joiner.on("/").skipNulls().join(organization, repository, workflowname);

            Workflow workflow = null;

            if (workflowname == null) {
                workflowname = "";
            }

            // Try and register
            try {
                workflow = workflowsApi
                    .manualRegister(gitVersionControl, organization + "/" + repository, workflowPath, workflowname, descriptorType,
                        testParameterFile);
                if (workflow != null) {
                    workflow = workflowsApi.refresh(workflow.getId(), true);
                } else {
                    errorMessage("Unable to register " + path, COMMAND_ERROR);
                }
            } catch (ApiException ex) {
                exceptionMessage(ex, "Error when trying to register " + path, Client.API_ERROR);
            }

            // Check if valid
            boolean valid = false;
            if (workflow != null) {
                for (WorkflowVersion workflowVersion : workflow.getWorkflowVersions()) {
                    if (workflowVersion.isValid()) {
                        valid = true;
                        break;
                    }
                }

                if (valid) {
                    // Valid so try and publish
                    PublishRequest pub = SwaggerUtility.createPublishRequest(true);
                    try {
                        workflowsApi.publish(workflow.getId(), pub);
                        out("Successfully registered and published the given workflow.");
                    } catch (ApiException ex) {
                        // Unable to publish but has registered
                        exceptionMessage(ex, "Successfully registered " + path + ", however it is not valid to publish.", Client.API_ERROR);
                    }
                } else {
                    // Not valid to publish, but has been registered
                    errorMessage("The workflow has been registered, however it is not valid to publish.", Client.API_ERROR);
                }
            }

        }
    }

    private void updateWorkflow(List<String> args) {
        if (args.isEmpty() || containsHelpRequest(args)) {
            updateWorkflowHelp();
        } else {
            final String entry = reqVal(args, ENTRY);
            try {
                Workflow workflow = findAndGetDockstoreWorkflowByPath(entry, "versions", false, true);
                if (isAppTool) {
                    errorMessage(GITHUB_APP_COMMAND_ERROR, COMMAND_ERROR);
                }
                long workflowId = workflow.getId();

                String descriptorType = optVal(args, "--descriptor-type", workflow.getDescriptorType().getValue());
                String workflowDescriptorPath = optVal(args, "--workflow-path", workflow.getWorkflowPath());
                String defaultVersion = optVal(args, "--default-version", workflow.getDefaultVersion());
                String defaultTestJsonPath = optVal(args, "--default-test-parameter-path", workflow.getDefaultTestParameterFilePath());

                if (workflow.getMode() == io.swagger.client.model.Workflow.ModeEnum.STUB) {

                    // Check if valid input
                    if (!"cwl".equalsIgnoreCase(descriptorType) && !"wdl".equalsIgnoreCase(descriptorType)) {
                        errorMessage("Please ensure that the descriptor type is either cwl or wdl.", Client.CLIENT_ERROR);
                    }

                    workflow.setDescriptorType(Workflow.DescriptorTypeEnum.fromValue(descriptorType.toUpperCase()));
                } else if (!descriptorType.equalsIgnoreCase(workflow.getDescriptorType().getValue())) {
                    errorMessage(
                        "You cannot change the descriptor type of a FULL workflow. Revert it to a STUB if you wish to change descriptor type.",
                        Client.CLIENT_ERROR);
                }

                workflow.setWorkflowPath(workflowDescriptorPath);
                workflow.setDefaultTestParameterFilePath(defaultTestJsonPath);

                if (!EnumUtils.isValidEnum(SourceControl.class, workflow.getSourceControlProvider())) {
                    errorMessage("The source control type is not valid.", Client.CLIENT_ERROR);
                }

                // If valid version
                boolean updateVersionSuccess = false;
                final boolean newVersion = !Objects.equals(workflow.getDefaultVersion(), defaultVersion);
                for (WorkflowVersion workflowVersion : workflow.getWorkflowVersions()) {
                    if (workflowVersion.getName().equals(defaultVersion)) {
                        workflow.setDefaultVersion(defaultVersion);
                        updateVersionSuccess = true;
                        break;
                    }
                }

                if (workflow.getWorkflowVersions().isEmpty()) {
                    // also remember to clear out default version
                    workflow.setDefaultVersion(null);
                } else if (!updateVersionSuccess && defaultVersion != null) {
                    out("Not a valid workflow version.");
                    out("Valid versions include:");
                    for (WorkflowVersion workflowVersion : workflow.getWorkflowVersions()) {
                        out(workflowVersion.getReference());
                    }
                    errorMessage("Please enter a valid version.", Client.CLIENT_ERROR);
                }

                workflowsApi.updateWorkflow(workflowId, workflow);
                if (newVersion) { // Update default version separately, see https://github.com/dockstore/dockstore/issues/3563
                    workflowsApi.updateWorkflowDefaultVersion(workflowId, defaultVersion);
                }
                workflowsApi.refresh(workflowId, true);
                out("The workflow has been updated.");
            } catch (ApiException ex) {
                exceptionMessage(ex, "", Client.API_ERROR);
            }
        }
    }

    @Override
    protected void handleTestParameter(String entry, String versionName, List<String> adds, List<String> removes, String descriptorType,
        String parentEntry) {
        try {
            Workflow workflow = findAndGetDockstoreWorkflowByPath(entry, null, false, true);
            if (isAppTool) {
                errorMessage("Cannot update test parameter files of GitHub App entries",
                    COMMAND_ERROR);
            }
            long workflowId = workflow.getId();

            if (adds.size() > 0) {
                workflowsApi.addTestParameterFiles(workflowId, adds, "", versionName);
            }

            if (removes.size() > 0) {
                workflowsApi.deleteTestParameterFiles(workflowId, removes, versionName);
            }

            if (adds.size() > 0 || removes.size() > 0) {
                workflowsApi.refresh(workflow.getId(), true);
                out(join(" ", "The test parameter files for version", versionName, "of",
                        getEntryType().toLowerCase(), parentEntry, "have been updated."));
            } else {
                out("Please provide at least one test parameter file to add or remove.");
            }

        } catch (ApiException ex) {
            exceptionMessage(ex, "There was an error updating the test parameter files for " + parentEntry + " version " + versionName,
                Client.API_ERROR);
        }
    }

    protected void versionTag(List<String> args) {
        if (args.isEmpty() || containsHelpRequest(args)) {
            versionTagHelp();
        } else {
            final String entry = reqVal(args, ENTRY);
            final String name = reqVal(args, "--name");

            try {
                Workflow workflow = findAndGetDockstoreWorkflowByPath(entry, "versions", false, true);
                if (this.isAppTool) {
                    errorMessage(GITHUB_APP_COMMAND_ERROR, COMMAND_ERROR);
                }
                List<WorkflowVersion> workflowVersions = workflow.getWorkflowVersions();

                for (WorkflowVersion workflowVersion : workflowVersions) {
                    if (workflowVersion.getName().equals(name)) {
                        final Boolean hidden = Boolean.valueOf(optVal(args, "--hidden", workflowVersion.isHidden().toString()));
                        final String workflowPath = optVal(args, "--workflow-path", workflowVersion.getWorkflowPath());

                        // Check that workflow path matches with the workflow descriptor type
                        if (!workflowPath.toLowerCase().endsWith(workflow.getDescriptorType().getValue().toLowerCase())) {
                            errorMessage("Please ensure that the workflow path uses the file extension " + workflow.getDescriptorType(),
                                Client.CLIENT_ERROR);
                        }

                        workflowVersion.setHidden(hidden);
                        workflowVersion.setWorkflowPath(workflowPath);

                        List<WorkflowVersion> newVersions = new ArrayList<>();
                        newVersions.add(workflowVersion);

                        workflowsApi.updateWorkflowVersion(workflow.getId(), newVersions);
                        workflowsApi.refresh(workflow.getId(), true);
                        out("Workflow Version " + name + " has been updated.");
                        break;
                    }
                }

            } catch (ApiException ex) {
                exceptionMessage(ex, "Could not find workflow", Client.API_ERROR);
            }
        }
    }

    private void restub(List<String> args) {
        if (args.isEmpty() || args.contains(HELP) || args.contains("-h")) {
            restubHelp();
        } else {
            try {
                final String entry = reqVal(args, ENTRY);
                Workflow workflow = findAndGetDockstoreWorkflowByPath(entry, null, false, true);
                if (this.isAppTool) {
                    errorMessage(GITHUB_APP_COMMAND_ERROR, COMMAND_ERROR);
                }

                if (workflow.isIsPublished()) {
                    errorMessage("Cannot restub a published workflow. Please unpublish if you wish to restub.", Client.CLIENT_ERROR);
                }

                if (workflow.getMode() == io.swagger.client.model.Workflow.ModeEnum.STUB) {
                    errorMessage("The given workflow is already a stub.", Client.CLIENT_ERROR);
                }

                workflowsApi.restub(workflow.getId());
                out("The workflow " + workflow.getPath() + " has been converted back to a stub.");
            } catch (ApiException ex) {
                exceptionMessage(ex, "", Client.API_ERROR);
            }
        }
    }

    private void restubHelp() {
        printHelpHeader();
        out("Usage: dockstore workflow restub " + HELP);
        out("       dockstore workflow restub [parameters]");
        out("");
        out("Description:");
        out("  Converts a full, unpublished workflow back to a stub.");
        out("");
        out("Required Parameters:");
        out("  " + ENTRY + " <entry>                       Complete workflow path in Dockstore (ex. quay.io/collaboratory/seqware-bwa-workflow)");
        out("");
        printHelpFooter();
    }

    public SourceFile getDescriptorFromServer(String entry, DescriptorLanguage descriptorType) throws ApiException {
        String[] parts = entry.split(":");

        String path = parts[0];

        // Workflows are git repositories, so a master is likely to exist (if null passed then dockstore will look for latest tag, which is special to quay tools)
        String version = (parts.length > 1) ? parts[1] : "master";
        SourceFile file = new SourceFile();
        // simply getting published descriptors does not require permissions
        Workflow workflow = findAndGetDockstoreWorkflowByPath(path);

        boolean valid = false;
        for (WorkflowVersion workflowVersion : workflow.getWorkflowVersions()) {
            if (workflowVersion.isValid()) {
                valid = true;
                break;
            }
        }

        if (valid) {
            try {
                file = workflowsApi.primaryDescriptor(workflow.getId(), version, descriptorType.toString());
            } catch (ApiException ex) {
                if (ex.getCode() == HttpStatus.SC_BAD_REQUEST) {
                    // TODO: "No descriptor found" should not trigger the below
                    exceptionMessage(ex, "Invalid version", Client.API_ERROR);
                } else {
                    exceptionMessage(ex, "No " + descriptorType + " file found.", Client.API_ERROR);
                }
            }
        } else {
            errorMessage("No workflow found with path " + entry, Client.API_ERROR);
        }
        return file;
    }

    @Parameters(separators = "=", commandDescription = "Spit out a json run file for a given entry.")
    private static class CommandEntry2json {

        @Parameter(names = ENTRY, description = "Complete workflow path in Dockstore (ex. NCI-GDC/gdc-dnaseq-cwl/GDC_DNASeq:master)", required = true)
        private String entry;
        @Parameter(names = HELP, description = "Prints help for entry2json command", help = true)
        private boolean help = false;
    }

    @Parameters(separators = "=", commandDescription = "Launch an entry locally or remotely.")
    private static class CommandLaunch {

        @Parameter(names = "--local-entry", description = "Allows you to specify a full path to a local descriptor instead of an entry path")
        private String localEntry;
        @Parameter(names = ENTRY, description = "Complete workflow path in Dockstore (ex. NCI-GDC/gdc-dnaseq-cwl/GDC_DNASeq:master)")
        private String entry;
        @Parameter(names = JSON, description = "Parameters to the entry in Dockstore, one map for one run, an array of maps for multiple runs")
        private String json;
        @Parameter(names = "--yaml", description = "Parameters to the entry in Dockstore, one map for one run, an array of maps for multiple runs")
        private String yaml;
        @Parameter(names = "--wdl-output-target", description = "Allows you to specify a remote path to provision outputs files to (ex: s3://oicr.temp/testing-launcher/")
        private String wdlOutputTarget;
        @Parameter(names = "--ignore-checksums", description = "Allows you to ignore validating checksums of each downloaded descriptor")
        private boolean ignoreChecksums;
        @Parameter(names = HELP, description = "Prints help for " + LAUNCH + " command", help = true)
        private boolean help = false;
        @Parameter(names = "--uuid", description = "Allows you to specify a uuid for 3rd party notifications")
        private String uuid;
        @Parameter(names = "--aws", description = "Indicates this command is to an AWS endpoint")
        private boolean isAws = false;
    }

}
