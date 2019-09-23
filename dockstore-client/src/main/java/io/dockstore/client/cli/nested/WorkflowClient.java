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
import java.util.ArrayList;
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
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.EnumUtils;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.dockstore.client.cli.ArgumentUtility.DESCRIPTION_HEADER;
import static io.dockstore.client.cli.ArgumentUtility.GIT_HEADER;
import static io.dockstore.client.cli.ArgumentUtility.NAME_HEADER;
import static io.dockstore.client.cli.ArgumentUtility.boolWord;
import static io.dockstore.client.cli.ArgumentUtility.columnWidthsWorkflow;
import static io.dockstore.client.cli.ArgumentUtility.containsHelpRequest;
import static io.dockstore.client.cli.ArgumentUtility.errorMessage;
import static io.dockstore.client.cli.ArgumentUtility.exceptionMessage;
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
import static io.dockstore.client.cli.Client.IO_ERROR;
import static io.dockstore.client.cli.JCommanderUtility.printJCommanderHelp;

/**
 * This stub will eventually implement all operations on the CLI that are
 * specific to workflows.
 *
 * @author dyuen
 */
public class WorkflowClient extends AbstractEntryClient<Workflow> {

    protected static final Logger LOG = LoggerFactory.getLogger(WorkflowClient.class);
    private static final String UPDATE_WORKFLOW = "update_workflow";
    protected final WorkflowsApi workflowsApi;
    protected final UsersApi usersApi;
    protected final Client client;
    private JCommander jCommander;
    private CommandLaunch commandLaunch;

    public WorkflowClient(WorkflowsApi workflowApi, UsersApi usersApi, Client client, boolean isAdmin) {
        this.workflowsApi = workflowApi;
        this.usersApi = usersApi;
        this.client = client;
        this.isAdmin = isAdmin;
        this.jCommander = new JCommander();
        this.commandLaunch = new CommandLaunch();
        this.jCommander.addCommand("launch", commandLaunch);
    }

    private static void printWorkflowList(List<Workflow> workflows) {
        int[] maxWidths = columnWidthsWorkflow(workflows);

        int nameWidth = maxWidths[0] + Client.PADDING;
        int descWidth = maxWidths[1] + Client.PADDING;
        int gitWidth = maxWidths[2] + Client.PADDING;
        String format = "%-" + nameWidth + "s%-" + descWidth + "s%-" + gitWidth + "s%-16s";
        outFormatted(format, NAME_HEADER, DESCRIPTION_HEADER, GIT_HEADER, "ON DOCKSTORE?");

        for (Workflow workflow : workflows) {
            String gitUrl = "";

            if (workflow.getGitUrl() != null && !workflow.getGitUrl().isEmpty()) {
                gitUrl = workflow.getGitUrl();
            }

            String description = getCleanedDescription(workflow.getDescription());

            outFormatted(format, workflow.getPath(), description, gitUrl, boolWord(workflow.isIsPublished()));
        }
    }

    private void manualPublishHelp() {
        printHelpHeader();
        out("Usage: dockstore " + getEntryType().toLowerCase() + " manual_publish --help");
        out("       dockstore " + getEntryType().toLowerCase() + " manual_publish [parameters]");
        out("");
        out("Description:");
        out("  Manually register an workflow in the dockstore. If this is successful and the workflow is valid, then publish.");
        out("");
        out("Required parameters:");
        out("  --repository <repository>                            Name for the git repository");
        out("  --organization <organization>                        Organization for the git repo");
        out("  --git-version-control <git version control>          Either github, gitlab, or bitbucket");
        out("");
        out("Optional parameters:");
        out("  --workflow-path <workflow-path>                      Path for the descriptor file, defaults to /Dockstore.cwl");
        out("  --workflow-name <workflow-name>                      Workflow name, defaults to null");
        out("  --descriptor-type <descriptor-type>                  Descriptor type, defaults to cwl");
        out("  --test-parameter-path <test-parameter-path>          Path to default test parameter file, defaults to /test.json");

        printHelpFooter();
    }

    private void updateWorkflowHelp() {
        printHelpHeader();
        out("Usage: dockstore " + getEntryType().toLowerCase() + " " + UPDATE_WORKFLOW + " --help");
        out("       dockstore " + getEntryType().toLowerCase() + " " + UPDATE_WORKFLOW + " [parameters]");
        out("");
        out("Description:");
        out("  Update certain fields for a given workflow.");
        out("");
        out("Required Parameters:");
        out("  --entry <entry>                                              Complete workflow path in the Dockstore (ex. github.com/collaboratory/seqware-bwa-workflow)");
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
        out("Usage: dockstore " + getEntryType().toLowerCase() + " version_tag --help");
        out("       dockstore " + getEntryType().toLowerCase() + " version_tag [parameters]");
        out("");
        out("Description:");
        out("  Update certain fields for a given " + getEntryType().toLowerCase() + " version.");
        out("");
        out("Required Parameters:");
        out("  --entry <entry>                                      Complete " + getEntryType().toLowerCase() + " path in the Dockstore (ex. quay.io/collaboratory/seqware-bwa-workflow)");
        out("  --name <name>                                        Name of the " + getEntryType().toLowerCase() + " version.");
        out("");
        out("Optional Parameters");
        out("  --workflow-path <workflow-path>                      Path to " + getEntryType().toLowerCase() + " descriptor");
        out("  --hidden <true/false>                                Hide the tag from public viewing, default false");
        printHelpFooter();
    }

    @Override
    public String getEntryType() {
        return "Workflow";
    }

    @Override
    public void handleLabels(String entryPath, Set<String> addsSet, Set<String> removesSet) {
        // Try and update the labels for the given workflow
        try {
            Workflow workflow = workflowsApi.getWorkflowByPath(entryPath, null, false);
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
        out("  manual_publish   :  registers a Github, Gitlab or Bitbucket workflow in the dockstore and then attempts to publish");
        out("");
        out("  " + UPDATE_WORKFLOW + "  :  updates certain fields of a workflow");
        out("");
        out("  version_tag      :  updates an existing version tag of a workflow");
        out("");
        out("  restub           :  converts a full, unpublished workflow back to a stub");
        out("");
    }

    @Override
    public void handleEntry2json(List<String> args) throws ApiException, IOException {
        String commandName = "entry2json";
        String[] argv = args.toArray(new String[0]);
        String[] argv1 = { commandName };
        String[] both = ArrayUtils.addAll(argv1, argv);
        CommandEntry2json commandEntry2json = new CommandEntry2json();
        JCommander jc = new JCommander();
        jc.addCommand(commandName, commandEntry2json);
        jc.setProgramName("dockstore workflow convert");
        try {
            jc.parse(both);
            if (commandEntry2json.help) {
                printJCommanderHelp(jc, "dockstore workflow convert", commandName);
            } else {
                final String runString = convertWorkflow2Json(commandEntry2json.entry, true);
                out(runString);
            }
        } catch (ParameterException e1) {
            out(e1.getMessage());
            printJCommanderHelp(jc, "dockstore workflow convert", commandName);
        }
    }

    @Override
    public void handleEntry2tsv(List<String> args) throws ApiException, IOException {
        String commandName = "entry2tsv";
        String[] argv = args.toArray(new String[0]);
        String[] argv1 = { commandName };
        String[] both = ArrayUtils.addAll(argv1, argv);
        CommandEntry2tsv commandEntry2tsv = new CommandEntry2tsv();
        JCommander jc = new JCommander();
        jc.addCommand(commandName, commandEntry2tsv);
        jc.setProgramName("dockstore workflow convert");
        try {
            jc.parse(both);
            if (commandEntry2tsv.help) {
                printJCommanderHelp(jc, "dockstore workflow convert", commandName);
            } else {
                final String runString = convertWorkflow2Json(commandEntry2tsv.entry, false);
                out(runString);
            }
        } catch (ParameterException e1) {
            out(e1.getMessage());
            printJCommanderHelp(jc, "dockstore workflow convert", commandName);
        }
    }

    private String convertWorkflow2Json(String entry, final boolean json) throws ApiException, IOException {
        // User may enter the version, so we have to extract the path
        String[] parts = entry.split(":");
        String path = parts[0];
        Workflow workflow = getDockstoreWorkflowByPath(path);
        String descriptor = workflow.getDescriptorType().getValue();
        LanguageClientInterface languageCLient = convertCLIStringToEnum(descriptor);
        return languageCLient.generateInputJson(entry, json);
    }

    private Workflow getDockstoreWorkflowByPath(String path) {
        // simply getting published descriptors does not require permissions
        Workflow workflow = null;
        try {
            workflow = workflowsApi.getPublishedWorkflowByPath(path, null, false);
        } catch (ApiException e) {
            if (e.getResponseBody().contains("Entry not found")) {
                LOG.info("Unable to locate entry without credentials, trying again as authenticated user");
                workflow = workflowsApi.getWorkflowByPath(path, null, false);
            }
        } finally {
            if (workflow == null) {
                errorMessage("No workflow found with path " + path, Client.ENTRY_NOT_FOUND);
            }
        }
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
     * @param toolpath a unique identifier for an entry, called a path for workflows and tools
     * @param unzip unzip the entry after downloading
     * @param directory directory to unzip descriptors into
     * @return path to the primary descriptor
     */
    public File downloadTargetEntry(String toolpath, ToolDescriptor.TypeEnum type, boolean unzip, File directory) throws IOException {
        String[] parts = toolpath.split(":");
        String path = parts[0];
        // match behaviour from getDescriptorFromServer, use master if no version is provided
        String tag = (parts.length > 1) ? parts[1] : "master";
        Workflow workflow = getDockstoreWorkflowByPath(path);
        Optional<WorkflowVersion> first = workflow.getWorkflowVersions().stream().filter(foo -> foo.getName().equalsIgnoreCase(tag))
            .findFirst();
        // if no master is present (for example, for hosted workflows), fail over to the latest descriptor
        if (first.isEmpty()) {
            first = workflow.getWorkflowVersions().stream().max(Comparator.comparing(WorkflowVersion::getLastModified));
            first.ifPresent(workflowVersion -> System.out.println("Could not locate workflow with version '" + tag + "'. Using last modified version '"
                    + workflowVersion.getName() + "' instead."));
        }

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
            if (unzip) {
                SwaggerUtility.unzipFile(zipFile, directory);
            }
            return new File(directory, first.get().getWorkflowPath());
        } else {
            throw new RuntimeException("version not found");
        }
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
        String commandName = "launch";
        preValidateLaunchArguments(args);
        String[] argv = args.toArray(new String[0]);
        String[] argv1 = { commandName };
        String[] both = ArrayUtils.addAll(argv1, argv);
        this.jCommander.parse(both);
        String entry = commandLaunch.entry;
        String localEntry = commandLaunch.localEntry;
        String jsonRun = commandLaunch.json;
        String yamlRun = commandLaunch.yaml;
        String wdlOutputTarget = commandLaunch.wdlOutputTarget;
        String uuid = commandLaunch.uuid;


        // trim the final slash on output if it is present, probably an error ( https://github.com/aws/aws-cli/issues/421 ) causes a double slash which can fail
        wdlOutputTarget = wdlOutputTarget != null ? wdlOutputTarget.replaceAll("/$", "") : null;

        if (this.commandLaunch.help) {
            JCommanderUtility.printJCommanderHelpLaunch(jCommander, "dockstore workflow", commandName);
        } else {
            checkIfDockerRunning(); // print a warning message if Docker is not running
            if ((entry == null) != (localEntry == null)) {
                if (entry != null) {
                    this.isLocalEntry = false;
                    String[] parts = entry.split(":");
                    String path = parts[0];
                    try {
                        Workflow workflow = getDockstoreWorkflowByPath(path);
                        final Workflow.DescriptorTypeEnum descriptorType = workflow.getDescriptorType();
                        final String descriptor = descriptorType.getValue().toLowerCase();
                        LanguageClientInterface languageClientInterface = convertCLIStringToEnum(descriptor);
                        DescriptorLanguage language = DescriptorLanguage.convertShortStringToEnum(descriptor);

                        switch (language) {
                        case CWL:
                            if (!(yamlRun != null ^ jsonRun != null)) {
                                errorMessage("One of  --json, --yaml, and --tsv is required", CLIENT_ERROR);
                            } else {
                                try {
                                    languageClientInterface.launch(entry, false, yamlRun, jsonRun, null, uuid);
                                } catch (IOException e) {
                                    errorMessage("Could not launch entry", IO_ERROR);
                                }
                            }
                            break;
                        case WDL:
                        case NEXTFLOW:
                            if (jsonRun == null) {
                                errorMessage("dockstore: missing required flag " + "--json", Client.CLIENT_ERROR);
                            } else {
                                try {
                                    languageClientInterface.launch(entry, false, null, jsonRun, wdlOutputTarget, uuid);
                                } catch (Exception e) {
                                    errorMessage("Could not launch entry", IO_ERROR);
                                }
                            }
                            break;
                        default:
                            errorMessage("Workflow type not supported for launch: " + path, ENTRY_NOT_FOUND);
                            break;
                        }
                    } catch (ApiException e) {
                        errorMessage("Could not get workflow: " + path, ENTRY_NOT_FOUND);
                    }
                } else {
                    this.isLocalEntry = true;
                    checkEntryFile(localEntry, jsonRun, yamlRun, wdlOutputTarget, uuid);
                }
            } else {
                out("You can only use one of --local-entry and --entry at a time.");
                JCommanderUtility.printJCommanderHelpLaunch(jCommander, "dockstore workflow", commandName);
            }
        }
    }

    @Override
    public Client getClient() {
        return this.client;
    }

    /**
     * this function will check for the content and the extension of entry file
     *
     * @param entry relative path to local descriptor for either WDL/CWL tools or workflows
     *              this will either give back exceptionMessage and exit (if the content/extension/descriptor is invalid)
     *              OR proceed with launching the entry file (if it's valid)
     * @param uuid
     */
    private void checkEntryFile(String entry, String jsonRun, String yamlRun, String wdlOutputTarget, String uuid) {
        File file = new File(entry);
        Optional<DescriptorLanguage> optExt = checkFileExtension(file.getPath());     //file extension could be cwl,wdl or ""

        if (!file.exists() || file.isDirectory()) {
            errorMessage("The workflow file " + file.getPath() + " does not exist. Did you mean to launch a remote workflow?",
                    ENTRY_NOT_FOUND);
        }
        Optional<LanguageClientInterface> languageCLientOptional = LanguageClientFactory.createLanguageCLient(this, optExt.get());
        LanguageClientInterface languageCLient = languageCLientOptional.orElseThrow(() -> new UnsupportedOperationException("language not supported yet"));
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
            Workflow workflow = workflowsApi.getPublishedWorkflowByPath(entryPath, null, false);
            if (workflow == null || !workflow.isIsPublished()) {
                errorMessage("This workflow is not published.", COMMAND_ERROR);
            } else {
                Date lastUpdated = Date.from(workflow.getLastUpdated().toInstant());

                String description = workflow.getDescription();
                if (description == null) {
                    description = "";
                }

                String author = workflow.getAuthor();
                if (author == null) {
                    author = "";
                }

                String date = lastUpdated.toString();

                out(workflow.getPath());
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

            out("Refreshing all workflows...");
            List<Workflow> workflows = usersApi.refreshWorkflows(user.getId());

            out("YOUR UPDATED WORKFLOWS");
            printLineBreak();
            printWorkflowList(workflows);
        } catch (ApiException ex) {
            exceptionMessage(ex, "", Client.API_ERROR);
        }
    }

    @Override
    protected void refreshTargetEntry(String path) {
        try {
            Workflow workflow = workflowsApi.getWorkflowByPath(path, null, false);
            final Long workflowId = workflow.getId();
            out("Refreshing workflow...");
            Workflow updatedWorkflow = workflowsApi.refresh(workflowId);
            List<Workflow> workflowList = new ArrayList<>();
            workflowList.add(updatedWorkflow);
            out("YOUR UPDATED WORKFLOW");
            printLineBreak();
            printWorkflowList(workflowList);
        } catch (ApiException ex) {
            exceptionMessage(ex, "", Client.API_ERROR);
        }
    }

    @Override
    protected void handlePublishUnpublish(String entryPath, String newName, boolean unpublishRequest) {
        Workflow existingWorkflow;
        boolean isPublished = false;
        try {
            existingWorkflow = workflowsApi.getWorkflowByPath(entryPath, null, false);
            isPublished = existingWorkflow.isIsPublished();
        } catch (ApiException ex) {
            exceptionMessage(ex, "Unable to publish/unpublish " + newName, Client.API_ERROR);
        }
        if (unpublishRequest) {
            if (isPublished) {
                publish(false, entryPath);
            } else {
                out("This workflow is already unpublished.");
            }
        } else {
            if (newName == null) {
                if (isPublished) {
                    out("This workflow is already published.");
                } else {
                    publish(true, entryPath);
                }
            } else {
                //for workflows method currently doesn't work with --entryname flag
                errorMessage("Parameter '--entryname' not valid for workflows. See `workflow publish --help` for more information.", CLIENT_ERROR);
            }
        }
    }

    @Override
    protected void publishHelp() {
        printHelpHeader();
        out("Usage: dockstore " + getEntryType().toLowerCase() + " publish --help");
        out("       dockstore " + getEntryType().toLowerCase() + " publish");
        out("       dockstore " + getEntryType().toLowerCase() + " publish [parameters]");
        out("       dockstore " + getEntryType().toLowerCase() + " publish --unpub [parameters]");
        out("");
        out("Description:");
        out("  Publish/unpublish a registered " + getEntryType() + ".");
        out("  No arguments will list the current and potential " + getEntryType() + "s to share.");
        out("Required Parameters:");
        out("  --entry <entry>             Complete " + getEntryType()
                + " path in the Dockstore (ex. quay.io/collaboratory/seqware-bwa-workflow)");
        //TODO add support for optional parameter '--entryname'
        printHelpFooter();
    }

    @Override
    protected void handleVerifyUnverify(String entry, String versionName, String verifySource, boolean unverifyRequest, boolean isScript) {
        // TODO: Implement this with extended TRS endpoint
        /*
        boolean toOverwrite = true;
        try {
            Workflow workflow = workflowsApi.getWorkflowByPath(entry, null, false);
            List<WorkflowVersion> versions = workflow.getWorkflowVersions();

            final Optional<WorkflowVersion> first = versions.stream().filter((WorkflowVersion u) -> u.getName().equals(versionName))
                    .findFirst();

            WorkflowVersion versionToUpdate;
            if (first.isEmpty()) {
                errorMessage(versionName + " is not a valid version for " + entry, Client.CLIENT_ERROR);
            }
            versionToUpdate = first.get();

            VerifyRequest verifyRequest = new VerifyRequest();
            if (unverifyRequest) {
                verifyRequest = SwaggerUtility.createVerifyRequest(false, null);
            } else {
                // Check if already has been verified
                if (versionToUpdate.isVerified() && !isScript) {
                    Scanner scanner = new Scanner(System.in, "utf-8");
                    out("The version " + versionName + " has already been verified by \'" + versionToUpdate.getVerifiedSource() + "\'");
                    out("Would you like to overwrite this with \'" + verifySource + "\'? (y/n)");
                    String overwrite = scanner.nextLine();
                    if ("y".equalsIgnoreCase(overwrite)) {
                        verifyRequest = SwaggerUtility.createVerifyRequest(true, verifySource);
                    } else {
                        toOverwrite = false;
                    }
                } else {
                    verifyRequest = SwaggerUtility.createVerifyRequest(true, verifySource);
                }
            }

            if (toOverwrite) {
                List<WorkflowVersion> result = workflowsApi.verifyWorkflowVersion(workflow.getId(), versionToUpdate.getId(), verifyRequest);

                if (unverifyRequest) {
                    out("Version " + versionName + " has been unverified.");
                } else {
                    out("Version " + versionName + " has been verified by \'" + verifySource + "\'");
                }
            }
        } catch (ApiException ex) {
            exceptionMessage(ex, "Unable to " + (unverifyRequest ? "unverify" : "verify") + " version " + versionName, Client.API_ERROR);
        }
         */
    }

    @Override
    protected void handleListNonpublishedEntries() {
        try {
            // check user info after usage so that users can get usage without live webservice
            User user = usersApi.getUser();
            if (user == null) {
                errorMessage("User not found", Client.CLIENT_ERROR);
            }
            List<Workflow> workflows = usersApi.userWorkflows(user.getId()).stream().filter(workflow ->
                workflow.getMode() != Workflow.ModeEnum.STUB).collect(Collectors.toList());

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
            List<Workflow> workflows = workflowsApi.allPublishedWorkflows(null, null, null, null, null, false);
            out("ALL PUBLISHED WORKFLOWS");
            printLineBreak();
            printWorkflowList(workflows);
        } catch (ApiException ex) {
            exceptionMessage(ex, "", Client.API_ERROR);
        }
    }

    public void publish(boolean publish, String entry) {
        String action = "publish";
        if (!publish) {
            action = "unpublish";
        }

        try {
            Workflow workflow = workflowsApi.getWorkflowByPath(entry, null, false);
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
        String action = "star";
        if (!star) {
            action = "unstar";
        }
        try {
            Workflow workflow = workflowsApi.getPublishedWorkflowByPath(entry, null, false);
            if (star) {
                StarRequest request = SwaggerUtility.createStarRequest(true);
                workflowsApi.starEntry(workflow.getId(), request);
            } else {
                workflowsApi.unstarEntry(workflow.getId());
            }
            out("Successfully " + action + "red  " + entry);
        } catch (ApiException ex) {
            exceptionMessage(ex, "Unable to " + action + " workflow " + entry, Client.API_ERROR);
        }
    }

    @Override
    protected void handleSearch(String pattern) {
        try {
            List<Workflow> workflows = workflowsApi.allPublishedWorkflows(null, null, pattern, null, null, false);

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

    @Override
    public boolean processEntrySpecificCommands(List<String> args, String activeCommand) {
        if (null != activeCommand) {
            switch (activeCommand) {
            case UPDATE_WORKFLOW:
                updateWorkflow(args);
                break;
            case "version_tag":
                versionTag(args);
                break;
            case "restub":
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
            final String descriptorType = optVal(args, "--descriptor-type", "cwl");
            final String testParameterFile = optVal(args, "--test-parameter-path", "/test.json");

            // Check if valid input
            if (!"cwl".equalsIgnoreCase(descriptorType) && !"wdl".equalsIgnoreCase(descriptorType)) {
                errorMessage("Please ensure that the descriptor type is either cwl or wdl.", Client.CLIENT_ERROR);
            }

            if (!workflowPath.endsWith(descriptorType)) {
                errorMessage("Please ensure that the given workflow path '" + workflowPath + "' is of type " + descriptorType
                        + " and has the file extension " + descriptorType, Client.CLIENT_ERROR);
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
                        .manualRegister(gitVersionControl, organization + "/" + repository, workflowPath, workflowname, descriptorType, testParameterFile);
                if (workflow != null) {
                    workflow = workflowsApi.refresh(workflow.getId());
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
            final String entry = reqVal(args, "--entry");
            try {
                Workflow workflow = workflowsApi.getWorkflowByPath(entry, null, false);
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
                workflowsApi.refresh(workflowId);
                out("The workflow has been updated.");
            } catch (ApiException ex) {
                exceptionMessage(ex, "", Client.API_ERROR);
            }
        }
    }

    @Override
    protected void handleTestParameter(String entry, String versionName, List<String> adds, List<String> removes, String descriptorType, String parentEntry) {
        try {
            Workflow workflow = workflowsApi.getWorkflowByPath(entry, null, false);
            long workflowId = workflow.getId();

            if (adds.size() > 0) {
                workflowsApi.addTestParameterFiles(workflowId, adds, "", versionName);
            }

            if (removes.size() > 0) {
                workflowsApi.deleteTestParameterFiles(workflowId, removes, versionName);
            }

            if (adds.size() > 0 || removes.size() > 0) {
                workflowsApi.refresh(workflow.getId());
                out("The test parameter files for version " + versionName + " of " + getEntryType().toLowerCase() + " " + parentEntry + " have been updated.");
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
            final String entry = reqVal(args, "--entry");
            final String name = reqVal(args, "--name");

            try {
                Workflow workflow = workflowsApi.getWorkflowByPath(entry, null, false);
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
                        workflowsApi.refresh(workflow.getId());
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
        if (args.isEmpty() || args.contains("--help") || args.contains("-h")) {
            restubHelp();
        } else {
            try {
                final String entry = reqVal(args, "--entry");
                Workflow workflow = workflowsApi.getWorkflowByPath(entry, null, false);

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
        out("Usage: dockstore workflow restub --help");
        out("       dockstore workflow restub [parameters]");
        out("");
        out("Description:");
        out("  Converts a full, unpublished workflow back to a stub.");
        out("");
        out("Required Parameters:");
        out("  --entry <entry>                       Complete workflow path in the Dockstore (ex. quay.io/collaboratory/seqware-bwa-workflow)");
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
        Workflow workflow = getDockstoreWorkflowByPath(path);

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
        @Parameter(names = "--entry", description = "Complete workflow path in the Dockstore (ex. NCI-GDC/gdc-dnaseq-cwl/GDC_DNASeq:master)", required = true)
        private String entry;
        @Parameter(names = "--help", description = "Prints help for entry2json command", help = true)
        private boolean help = false;
    }

    @Parameters(separators = "=", commandDescription = "Spit out a tsv run file for a given entry.")
    private static class CommandEntry2tsv {
        @Parameter(names = "--entry", description = "Complete workflow path in the Dockstore (ex. NCI-GDC/gdc-dnaseq-cwl/GDC_DNASeq:master)", required = true)
        private String entry;
        @Parameter(names = "--help", description = "Prints help for entry2json command", help = true)
        private boolean help = false;
    }

    @Parameters(separators = "=", commandDescription = "Launch an entry locally or remotely.")
    private static class CommandLaunch {
        @Parameter(names = "--local-entry", description = "Allows you to specify a full path to a local descriptor instead of an entry path")
        private String localEntry;
        @Parameter(names = "--entry", description = "Complete workflow path in the Dockstore (ex. NCI-GDC/gdc-dnaseq-cwl/GDC_DNASeq:master)")
        private String entry;
        @Parameter(names = "--json", description = "Parameters to the entry in the dockstore, one map for one run, an array of maps for multiple runs")
        private String json;
        @Parameter(names = "--yaml", description = "Parameters to the entry in the dockstore, one map for one run, an array of maps for multiple runs")
        private String yaml;
        @Parameter(names = "--wdl-output-target", description = "Allows you to specify a remote path to provision outputs files to (ex: s3://oicr.temp/testing-launcher/")
        private String wdlOutputTarget;
        @Parameter(names = "--help", description = "Prints help for launch command", help = true)
        private boolean help = false;
        @Parameter(names = "--uuid", description = "Allows you to specify a uuid for 3rd party notifications")
        private String uuid;
    }

}
