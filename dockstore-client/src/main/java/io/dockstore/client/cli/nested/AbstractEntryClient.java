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
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.ws.rs.core.HttpHeaders;

import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import com.spotify.docker.client.exceptions.DockerException;
import io.cwl.avro.CWL;
import io.dockstore.client.cli.CheckerClient;
import io.dockstore.client.cli.Client;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.Utilities;
import io.dockstore.common.WdlBridge;
import io.github.collaboratory.cwl.CWLClient;
import io.github.collaboratory.nextflow.NextflowClient;
import io.github.collaboratory.wdl.WDLClient;
import io.openapi.wes.client.api.WorkflowExecutionServiceApi;
import io.openapi.wes.client.model.RunId;
import io.openapi.wes.client.model.RunLog;
import io.openapi.wes.client.model.RunStatus;
import io.swagger.client.ApiException;
import io.swagger.client.model.Label;
import io.swagger.client.model.SourceFile;
import io.swagger.client.model.ToolDescriptor;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.configuration2.SubnodeConfiguration;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.parser.ParserException;
import wdl.draft3.parser.WdlParser;

import static io.dockstore.client.cli.ArgumentUtility.CONVERT;
import static io.dockstore.client.cli.ArgumentUtility.DOWNLOAD;
import static io.dockstore.client.cli.ArgumentUtility.LAUNCH;
import static io.dockstore.client.cli.ArgumentUtility.MAX_DESCRIPTION;
import static io.dockstore.client.cli.ArgumentUtility.containsHelpRequest;
import static io.dockstore.client.cli.ArgumentUtility.errorMessage;
import static io.dockstore.client.cli.ArgumentUtility.exceptionMessage;
import static io.dockstore.client.cli.ArgumentUtility.invalid;
import static io.dockstore.client.cli.ArgumentUtility.optVal;
import static io.dockstore.client.cli.ArgumentUtility.optVals;
import static io.dockstore.client.cli.ArgumentUtility.out;
import static io.dockstore.client.cli.ArgumentUtility.printFlagHelp;
import static io.dockstore.client.cli.ArgumentUtility.printHelpFooter;
import static io.dockstore.client.cli.ArgumentUtility.printHelpHeader;
import static io.dockstore.client.cli.ArgumentUtility.printLineBreak;
import static io.dockstore.client.cli.ArgumentUtility.printUsageHelp;
import static io.dockstore.client.cli.ArgumentUtility.reqVal;
import static io.dockstore.client.cli.Client.API_ERROR;
import static io.dockstore.client.cli.Client.CLIENT_ERROR;
import static io.dockstore.client.cli.Client.ENTRY_NOT_FOUND;
import static io.dockstore.client.cli.Client.IO_ERROR;
import static io.dockstore.common.DescriptorLanguage.CWL;
import static io.dockstore.common.DescriptorLanguage.NEXTFLOW;
import static io.dockstore.common.DescriptorLanguage.WDL;

/**
 * Handles the commands for a particular type of entry. (e.g. Workflows, Tools) Not a great abstraction, but enforces some structure for
 * now.
 * <p>
 * The goal here should be to gradually work toward an interface that removes those pesky command line arguments (List&lt;String&gt; args) from
 * implementing classes that do not need to reference to the command line arguments directly.
 * <p>
 * Note that many of these methods depend on a unique identifier for an entry called a path for workflows and tools.
 * For example, a tool path looks like quay.io/collaboratory/bwa-tool:develop wheras a workflow path looks like
 * collaboratory/bwa-workflow:develop
 *
 * @author dyuen
 */
public abstract class AbstractEntryClient<T> {
    private static final String WORKFLOW = "workflow";
    private static final Logger LOG = LoggerFactory.getLogger(AbstractEntryClient.class);
    protected boolean isAdmin = false;

    boolean isLocalEntry = false;

    private boolean isWesCommand = false;
    private String wesUri = null;
    private String wesAuth = null;

    static String getCleanedDescription(String description) {
        description = MoreObjects.firstNonNull(description, "");
        // strip control characters
        description = CharMatcher.javaIsoControl().removeFrom(description);
        if (description.length() > MAX_DESCRIPTION) {
            description = description.substring(0, MAX_DESCRIPTION - Client.PADDING) + "...";
        }
        return description;
    }

    String getWesUri() {
        return wesUri;
    }

    String getWesAuth() {
        return wesAuth;
    }

    public boolean isWesCommand() {
        return isWesCommand;
    }

    boolean isLocalEntry() {
        return isLocalEntry;
    }

    public CWL getCwlUtil() {
        // TODO: may be reactivated if we find a different way to read CWL into Java
        // String cwlrunner = CWLRunnerFactory.getCWLRunner();
        return new CWL(false, Utilities.parseConfig(getConfigFile()));
    }

    public abstract String getConfigFile();

    /**
     * Print help for this group of commands.
     */
    public void printGeneralHelp() {
        printHelpHeader();
        printUsageHelp(getEntryType().toLowerCase());
        out("Commands:");
        out("");
        out("  list             :  lists all the " + getEntryType() + "s published by the user");
        out("");
        out("  search           :  allows a user to search for all published " + getEntryType() + "s that match the criteria");
        out("");
        out("  publish          :  publish/unpublish a " + getEntryType() + " in the dockstore");
        out("");
        out("  info             :  print detailed information about a particular published " + getEntryType());
        out("");
        out("  " + CWL.getLowerShortName() + "              :  returns the Common Workflow Language " + getEntryType() + " definition for this entry");
        out("                      which enables integration with Global Alliance compliant systems");
        out("");
        out("  " + WDL.getLowerShortName() + "              :  returns the Workflow Descriptor Language definition for this Docker image");
        out("");
        out("  refresh          :  updates your list of " + getEntryType() + "s stored on Dockstore or an individual " + getEntryType());
        out("");
        out("  label            :  updates labels for an individual " + getEntryType() + "");
        out("");
        out("  star             :  star/unstar a " + getEntryType() + " in the dockstore");
        out("");
        out("  test_parameter   :  updates test parameter files for a version of a " + getEntryType() + "");
        out("");
        out("  " + CONVERT + "          :  utilities that allow you to convert file types");
        out("");
        out("  " + LAUNCH + "           :  launch " + getEntryType() + "s (locally)");
        out("");
        out("  " + DOWNLOAD + "         :  download " + getEntryType() + "s to the local directory");
        if (WORKFLOW.equalsIgnoreCase(getEntryType())) {
            out("");
            out("  wes              :  calls a Workflow Execution Schema API (WES) for a version of a " + getEntryType() + "");
        }

        printClientSpecificHelp();
        if (isAdmin) {
            printAdminHelp();
        }
        printLineBreak();
        printFlagHelp();
        printHelpFooter();
    }

    /**
     * Print help for commands specific to this client type.
     */
    protected abstract void printClientSpecificHelp();

    /**
     * A friendly description for the type of entry that this handles. Damn you type erasure.
     *
     * @return string to use in descriptions and help output
     */
    public abstract String getEntryType();

    /**
     * A default implementation to process the commands that are common between types of entries. (i.e. both workflows and tools need to be
     * published and labelled)
     *
     * @param args          the arguments yet to be processed
     * @param activeCommand the current command that we're interested in
     * @return whether this interface handled the active command
     */
    public boolean processEntryCommands(List<String> args, String activeCommand) throws IOException, ApiException {
        if (null != activeCommand) {
            // see if it is a command specific to this kind of Entry
            boolean processed = processEntrySpecificCommands(args, activeCommand);
            if (processed) {
                return true;
            }

            final Optional<DescriptorLanguage> first = Arrays.stream((DescriptorLanguage.values()))
                .filter(lang -> activeCommand.equalsIgnoreCase(lang.getShortName())).findFirst();
            if (first.isPresent()) {
                descriptor(args, first.get());
                return true;
            }

            switch (activeCommand) {
            case "info":
                info(args);
                break;
            case "list":
                list(args);
                break;
            case "search":
                search(args);
                break;
            case "publish":
                publish(args);
                break;
            case "star":
                star(args);
                break;
            case "refresh":
                refresh(args);
                break;
            case "label":
                label(args);
                break;
            case "manual_publish":
                manualPublish(args);
                break;
            case "convert":
                convert(args);
                break;
            case LAUNCH:
                launch(args);
                break;
            case DOWNLOAD:
                download(args);
                break;
            case "verify":
                verify(args);
                break;
            case "test_parameter":
                testParameter(args);
                break;
            case "wes":
                isWesCommand = true;
                if (WORKFLOW.equalsIgnoreCase(getEntryType())) {
                    processWesCommands(args);
                } else {
                    errorMessage("WES API calls are only valid for workflows not tools.", CLIENT_ERROR);
                }
                break;
            default:
                return false;
            }
            return true;
        }
        return false;
    }

    /**
     * Handle search for an entry
     *
     * @param pattern a pattern, currently a subtring for searching
     */
    protected abstract void handleSearch(String pattern);

    /**
     * Handle the actual labelling
     *
     * @param entryPath  a unique identifier for an entry, called a path for workflows and tools
     * @param addsSet    the set of labels that we wish to add
     * @param removesSet the set of labels that we wish to delete
     */
    protected abstract void handleLabels(String entryPath, Set<String> addsSet, Set<String> removesSet);

    /**
     * Handle output for a type of entry
     *
     * @param entryPath a unique identifier for an entry, called a path for workflows and tools
     */
    protected abstract void handleInfo(String entryPath);

    /**
     * Refresh all entries of this type.
     */
    protected abstract void refreshAllEntries();

    /**
     * Refresh a specific entry of this type.
     *
     * @param toolpath a unique identifier for an entry, called a path for workflows and tools
     */
    protected abstract void refreshTargetEntry(String toolpath);

    /**
     * Download a specific entry of this type. Download into the current directory.
     *
     * @param type relevant for tools which have multiple languages
     * @param toolpath a unique identifier for an entry, called a path for workflows and tools
     * @param unzip unzip the entry after downloading
     * @return the path to the primary descriptor
     */
    public abstract File downloadTargetEntry(String toolpath, ToolDescriptor.TypeEnum type, boolean unzip) throws IOException;

    /**
     * Download a specific entry of this type.
     *
     * @param directory directory to unzip into
     * @param type relevant for tools which have multiple languages
     * @param toolpath a unique identifier for an entry, called a path for workflows and tools
     * @param unzip unzip the entry after downloading
     * @return the path to the primary descriptor
     */
    public abstract File downloadTargetEntry(String toolpath, ToolDescriptor.TypeEnum type, boolean unzip, File directory) throws IOException;

    /**
     * Grab the descriptor for an entry. TODO: descriptorType should probably be an enum, may need to play with generics to make it
     * dependent on the type of entry
     *
     * @param descriptorType type of descriptor
     * @param entry          a unique identifier for an entry, called a path for workflows and tools ex:
     *                       quay.io/collaboratory/seqware-bwa-workflow:develop for a tool
     */
    private void handleDescriptor(DescriptorLanguage descriptorType, String entry) {
        try {
            SourceFile file = getDescriptorFromServer(entry, descriptorType);

            if (file.getContent() != null && !file.getContent().isEmpty()) {
                out(file.getContent());
            } else {
                errorMessage("No " + descriptorType + " file found", Client.COMMAND_ERROR);
            }
        } catch (ApiException | IOException ex) {
            exceptionMessage(ex, "", Client.API_ERROR);
        }
    }

    /**
     * @param entryPath        a unique identifier for an entry, called a path for workflows and tools
     * @param newName          take entryPath and rename its most specific name (ex: toolName for tools) to newName
     * @param unpublishRequest true to publish, false to unpublish
     */
    protected abstract void handlePublishUnpublish(String entryPath, String newName, boolean unpublishRequest);

    /**
     * @param entryPath     a unique identifier for an entry, called a path for workflows and tools
     * @param unstarRequest true to star, false to unstar
     */
    protected abstract void handleStarUnstar(String entryPath, boolean unstarRequest);
    /**
     * Verify/Unverify an entry
     *
     * @param entry           a unique identifier for an entry, called a path for workflows and tools
     * @param versionName     the name of the version
     * @param verifySource    source of entry verification
     * @param unverifyRequest true to unverify, false to verify
     * @param isScript        true if called by script, false otherwise
     */
    protected abstract void handleVerifyUnverify(String entry, String versionName, String verifySource, boolean unverifyRequest,
            boolean isScript);

    /**
     * Adds/removes supplied test parameter paths for a given entry version
     *
     * @param entry          a unique identifier for an entry, called a path for workflows and tools
     * @param versionName    the name of the version
     * @param adds           set of test parameter paths to add (from git)
     * @param removes        set of test parameter paths to remove (from git)
     * @param descriptorType CWL or WDL
     * @param parentEntry    Entry path of parent, used for checker workflows. If not a checker will be equal to entry
     */
    protected abstract void handleTestParameter(String entry, String versionName, List<String> adds, List<String> removes,
            String descriptorType, String parentEntry);

    /**
     * List all of the entries published and unpublished for this user
     */
    protected abstract void handleListNonpublishedEntries();

    /**
     * List all of the entries starred and unstarred for this user
     */
    protected abstract void handleListUnstarredEntries();

    /**
     * List all of the published entries of this type for this user
     */
    protected abstract void handleList();

    /**
     * Process commands that are specific to this kind of entry (tools, workflows).
     *
     * @param args remaining command segment
     * @return true iff this handled the command
     */
    protected abstract boolean processEntrySpecificCommands(List<String> args, String activeCommand);

    /**
     * Manually publish a given entry
     *
     * @param args user's command-line arguments
     */
    protected abstract void manualPublish(List<String> args);

    public abstract SourceFile getDescriptorFromServer(String entry, DescriptorLanguage descriptorType) throws ApiException, IOException;

    /**
     * private helper methods
     */

    public void publish(List<String> args) {
        if (args.isEmpty()) {
            handleListNonpublishedEntries();
        } else if (containsHelpRequest(args)) {
            publishHelp();
        } else {
            String first = reqVal(args, "--entry");
            String entryname = optVal(args, "--entryname", null);
            final boolean unpublishRequest = args.contains("--unpub");
            handlePublishUnpublish(first, entryname, unpublishRequest);
        }
    }

    private void star(List<String> args) {
        if (args.isEmpty()) {
            handleListUnstarredEntries();
        } else if (containsHelpRequest(args)) {
            starHelp();
        } else {
            String first = reqVal(args, "--entry");
            final boolean unstarRequest = args.contains("--unstar");
            handleStarUnstar(first, unstarRequest);
        }
    }

    private void list(List<String> args) {
        if (containsHelpRequest(args)) {
            listHelp();
        } else {
            handleList();
        }
    }

    private void descriptor(List<String> args, DescriptorLanguage descriptorType) {
        if (args.isEmpty() || containsHelpRequest(args)) {
            descriptorHelp(descriptorType);
        } else {
            final String entry = reqVal(args, "--entry");
            handleDescriptor(descriptorType, entry);
        }
    }

    private void refresh(List<String> args) {
        if (containsHelpRequest(args)) {
            refreshHelp();
        } else if (!args.isEmpty()) {
            final String toolpath = reqVal(args, "--entry");
            refreshTargetEntry(toolpath);
        } else {
            // check user info after usage so that users can get usage without live webservice
            refreshAllEntries();
        }
    }

    private void info(List<String> args) {
        if (args.isEmpty() || containsHelpRequest(args)) {
            infoHelp();
        } else {
            String path = reqVal(args, "--entry");
            handleInfo(path);
        }
    }

    private void label(List<String> args) {
        if (args.isEmpty() || containsHelpRequest(args)) {
            labelHelp();
        } else {
            final String toolpath = reqVal(args, "--entry");
            final List<String> adds = optVals(args, "--add");
            final Set<String> addsSet = adds.isEmpty() ? new HashSet<>() : new HashSet<>(adds);
            final List<String> removes = optVals(args, "--remove");
            final Set<String> removesSet = removes.isEmpty() ? new HashSet<>() : new HashSet<>(removes);

            // Do a check on the input
            final String labelStringPattern = "^[a-zA-Z0-9]+(-[a-zA-Z0-9]+)*$";

            for (String add : addsSet) {
                if (!add.matches(labelStringPattern)) {
                    errorMessage("The following label does not match the proper label format : " + add, CLIENT_ERROR);
                } else if (removesSet.contains(add)) {
                    errorMessage("The following label is present in both add and remove : " + add, CLIENT_ERROR);
                }
            }

            for (String remove : removesSet) {
                if (!remove.matches(labelStringPattern)) {
                    errorMessage("The following label does not match the proper label format : " + remove, CLIENT_ERROR);
                }
            }
            handleLabels(toolpath, addsSet, removesSet);
        }
    }

    /*
    Generate label string given add set, remove set, and existing labels
      */
    String generateLabelString(Set<String> addsSet, Set<String> removesSet, List<Label> existingLabels) {
        Set<String> newLabelSet = new HashSet<>();

        // Get existing labels and store in a List
        for (Label existingLabel : existingLabels) {
            newLabelSet.add(existingLabel.getValue());
        }

        // Add new labels to the List of labels
        for (String add : addsSet) {
            final String label = add.toLowerCase();
            newLabelSet.add(label);
        }
        // Remove labels from the list of labels
        for (String remove : removesSet) {
            final String label = remove.toLowerCase();
            newLabelSet.remove(label);
        }

        return Joiner.on(",").join(newLabelSet);
    }

    private void search(List<String> args) {
        if (args.isEmpty() || containsHelpRequest(args)) {
            searchHelp();
        } else {
            String pattern = reqVal(args, "--pattern");
            handleSearch(pattern);
        }
    }

    private void convert(final List<String> args) throws ApiException, IOException {
        if (args.isEmpty() || (containsHelpRequest(args) && !args.contains("cwl2json") && !args.contains("wdl2json") && !args
                .contains("entry2json") && !args.contains("entry2tsv"))) {
            convertHelp(); // Display general help
        } else {
            final String cmd = args.remove(0);
            if (null != cmd) {
                switch (cmd) {
                case "cwl2json":
                    cwl2json(args, true);
                    break;
                case "cwl2yaml":
                    cwl2json(args, false);
                    break;
                case "wdl2json":
                    wdl2json(args);
                    break;
                case "entry2json":
                    handleEntry2json(args);
                    break;
                case "entry2tsv":
                    handleEntry2tsv(args);
                    break;
                default:
                    invalid(cmd);
                    break;
                }
            }
        }
    }

    private void cwl2json(final List<String> args, boolean json) throws ApiException {
        if (args.isEmpty() || containsHelpRequest(args)) {
            if (json) {
                cwl2jsonHelp();
            } else {
                cwl2yamlHelp();
            }
        } else {
            final String cwlPath = reqVal(args, "--cwl");

            final ImmutablePair<String, String> output = getCwlUtil().parseCWL(cwlPath);

            // do not continue to convert to json if cwl is invalid
            if (!validateCWL(cwlPath)) {
                return;
            }

            try {
                final Map<String, Object> runJson = getCwlUtil().extractRunJson(output.getLeft());
                if (json) {
                    final Gson gson = io.cwl.avro.CWL.getTypeSafeCWLToolDocument();
                    out(gson.toJson(runJson));
                } else {
                    Yaml yaml = new Yaml();
                    out(yaml.dumpAs(runJson, null, DumperOptions.FlowStyle.BLOCK));
                }
            } catch (CWL.GsonBuildException ex) {
                exceptionMessage(ex, "There was an error creating the CWL GSON instance.", API_ERROR);
            } catch (JsonParseException ex) {
                exceptionMessage(ex, "The JSON file provided is invalid.", API_ERROR);
            }
        }
    }

    private void wdl2json(final List<String> args) throws ApiException {
        if (args.isEmpty() || containsHelpRequest(args)) {
            wdl2jsonHelp();
        } else {
            // Will eventually need to update this to use wdltool
            final String wdlPath = reqVal(args, "--wdl");
            File wdlFile = new File(wdlPath);
            final List<String> wdlDocuments = Lists.newArrayList(wdlFile.getAbsolutePath());
            final scala.collection.immutable.List<String> wdlList = scala.collection.JavaConversions.asScalaBuffer(wdlDocuments).toList();
            WdlBridge wdlBridge = new WdlBridge();
            try {
                String inputs = wdlBridge.getParameterFile(wdlFile.getAbsolutePath());
                out(inputs);
            } catch (WdlParser.SyntaxError ex) {
                throw new ApiException(ex);
            }
        }
    }

    public void handleEntry2json(List<String> args) throws ApiException, IOException {
        if (args.isEmpty() || containsHelpRequest(args)) {
            entry2jsonHelp();
        } else {
            final String runString = convertEntry2Json(args, true);
            out(runString);
        }
    }

    public void handleEntry2tsv(List<String> args) throws ApiException, IOException {
        if (args.isEmpty() || containsHelpRequest(args)) {
            entry2tsvHelp();
        } else {
            final String runString = convertEntry2Json(args, false);
            out(runString);
        }
    }

    /**
     * this function will validate CWL file
     * using this command: cwltool --non-strict --validate &lt;file_path&gt;
     *
     * @param cwlFilePath a path to the cwl file to be validated
     */
    private boolean validateCWL(String cwlFilePath) {
        final String[] s = { "cwltool", "--non-strict", "--validate", cwlFilePath };
        try {
            io.cwl.avro.Utilities.executeCommand(Joiner.on(" ").join(Arrays.asList(s)), false,  com.google.common.base.Optional.absent(),  com.google.common.base.Optional.absent());
            return true;
        } catch (RuntimeException e) {
            // when invalid, executeCommand will throw a RuntimeException
            return false;
        } catch (Exception e) {
            throw new RuntimeException("An unexpected exception unrelated to validation has occurred");
        }
    }


    private void verify(List<String> args) {
        if (isAdmin) {
            if (containsHelpRequest(args) || args.isEmpty()) {
                verifyHelp();
            } else {
                /*
                String entry = reqVal(args, "--entry");
                String version = reqVal(args, "--version");
                String verifySource = optVal(args, "--verified-source", null);

                final boolean unverifyRequest = args.contains("--unverify");
                final boolean isScript = SCRIPT.get();
                handleVerifyUnverify(entry, version, verifySource, unverifyRequest, isScript);
                 */
                out("This command is currently deprecated. Use the extended TRS webservice endpoint instead.");
            }
        } else {
            out("This command is only accessible to Admins.");
        }
    }

    protected void testParameter(List<String> args) {
        if (containsHelpRequest(args) || args.isEmpty()) {
            testParameterHelp();
        } else {
            String entry = reqVal(args, "--entry");
            String version = reqVal(args, "--version");
            String descriptorType = null;
            final List<String> adds = optVals(args, "--add");
            final List<String> removes = optVals(args, "--remove");

            String parentEntry = optVal(args, "--parent-entry", entry);

            if (getEntryType().equalsIgnoreCase("tool")) {
                descriptorType = reqVal(args, "--descriptor-type");
                descriptorType = descriptorType.toLowerCase();
                boolean validType = false;
                for (DescriptorLanguage type : DescriptorLanguage.values()) {
                    if (type.toString().equalsIgnoreCase(descriptorType) && !"none".equalsIgnoreCase(descriptorType)) {
                        validType = true;
                        break;
                    }
                }
                final String joinedLanguages = Joiner.on(',').join(Arrays.stream(DescriptorLanguage.values()).map(DescriptorLanguage::getLowerShortName).collect(Collectors.toSet()));
                if (!validType) {
                    errorMessage("Only " + joinedLanguages + " are valid descriptor types", CLIENT_ERROR);
                }
            }

            handleTestParameter(entry, version, adds, removes, descriptorType, parentEntry);
        }
    }

    /**
     * this function will check the content of the entry file if it's a valid cwl/wdl file
     *
     * @param content the file content, Type File
     * @return Type -> Type.CWL if file content is CWL
     * Type.WDL if file content is WDL
     * Type.NONE if file content is neither WDL nor CWL
     */
    Optional<DescriptorLanguage> checkFileContent(File content) {
        for (DescriptorLanguage type : DescriptorLanguage.values()) {
            Optional<LanguageClientInterface> languageCLient = LanguageClientFactory.createLanguageCLient(this, type);
            if (languageCLient.isPresent()) {
                Boolean check = languageCLient.get().check(content);
                if (check) {
                    return Optional.of(type);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * this function will check the extension of the entry file (cwl/wdl)
     *
     * @param path the file path, Type String
     * @return Type -> Type.CWL if file extension is CWL
     * Type.WDL if file extension is WDL
     * Type.NONE if file extension is neither WDL nor CWL, could be no extension or some other random extension(e.g .txt)
     */
    Optional<DescriptorLanguage> checkFileExtension(String path) {
        if (FilenameUtils.getExtension(path).equalsIgnoreCase(CWL.getLowerShortName()) || FilenameUtils.getExtension(path).equalsIgnoreCase("yaml") || FilenameUtils.getExtension(path).equalsIgnoreCase("yml")) {
            return Optional.of(CWL);
        } else if (FilenameUtils.getExtension(path).equalsIgnoreCase(WDL.getLowerShortName())) {
            return Optional.of(WDL);
        } else if (path.endsWith("nextflow.config")) {
            return Optional.of(NEXTFLOW);
        }
        return Optional.empty();
    }

    /**
     * this function will check for the content and the extension of entry file
     * for launch simplification, trying to reduce the use '--descriptor' when launching
     *
     * @param localFilePath relative path to local descriptor for either WDL/CWL tools or workflows
     *                      this will either give back exceptionMessage and exit (if the content/extension/descriptor is invalid)
     *                      OR proceed with launching the entry file (if it's valid)
     */
    public void checkEntryFile(String localFilePath, List<String> argsList, String descriptor) {
        String invalidWorkflowMessage = "Entry file is invalid. Please enter a valid workflow file with the correct extension on the file name.";

        File file = new File(localFilePath);
        Optional<DescriptorLanguage> optExt = checkFileExtension(file.getPath());     //file extension could be cwl,wdl or ""

        if (!file.exists() || file.isDirectory()) {
            if (getEntryType().equalsIgnoreCase("tool")) {
                errorMessage("The tool file " + file.getPath() + " does not exist. Did you mean to launch a remote tool or a workflow?",
                    ENTRY_NOT_FOUND);
            } else {
                errorMessage("The workflow file " + file.getPath() + " does not exist. Did you mean to launch a remote workflow or a tool?",
                    ENTRY_NOT_FOUND);
            }
        }

        Optional<DescriptorLanguage> optContent = checkFileContent(file);             //check the file content (wdl,cwl or "")

        if (optExt.isPresent()) {
            DescriptorLanguage ext = optExt.get();
            final boolean cwlContentPresent = optContent.isPresent() && optContent.get().equals(CWL);
            final boolean wdlContentPresent = optContent.isPresent() && optContent.get().equals(WDL);
            final boolean nextflowContentPresent = optContent.isPresent() && optContent.get().equals(NEXTFLOW);
            if (CWL.equals(ext)) {
                if (cwlContentPresent) {
                    // do not continue to check file if the cwl is invalid
                    if (!validateCWL(localFilePath)) {
                        return;
                    }
                    try {
                        launchCwl(localFilePath, argsList, true);
                    } catch (ApiException e) {
                        exceptionMessage(e, "API error launching entry", Client.API_ERROR);
                    } catch (IOException e) {
                        exceptionMessage(e, "IO error launching entry", IO_ERROR);
                    }
                } else if (!cwlContentPresent && descriptor == null) {
                    //extension is cwl but the content is not cwl
                    out("Entry file is ambiguous, please re-enter command with '--descriptor <descriptor>' at the end");
                } else if (!cwlContentPresent && CWL.getLowerShortName().equals(descriptor)) {
                    errorMessage("Entry file is not a valid CWL file.", CLIENT_ERROR);
                } else if (wdlContentPresent && WDL.getLowerShortName().equals(descriptor)) {
                    out("This is a WDL file.. Please put the correct extension to the entry file name.");
                    out("Launching entry file as a WDL file..");
                    try {
                        launchWdl(argsList, true);
                    } catch (ApiException e) {
                        exceptionMessage(e, "API error launching entry", Client.API_ERROR);
                    } catch (IOException e) {
                        exceptionMessage(e, "IO error launching entry", IO_ERROR);
                    }
                } else {
                    errorMessage(invalidWorkflowMessage, CLIENT_ERROR);
                }
            } else if (WDL.equals(ext)) {
                if (wdlContentPresent) {
                    try {
                        launchWdl(localFilePath, argsList, true);
                    } catch (ApiException e) {
                        exceptionMessage(e, "API error launching entry", Client.API_ERROR);
                    }
                } else if (!wdlContentPresent && descriptor == null) {
                    //extension is wdl but the content is not wdl
                    out("Entry file is ambiguous, please re-enter command with '--descriptor <descriptor>' at the end");
                } else if (!wdlContentPresent && WDL.getLowerShortName().equals(descriptor)) {
                    errorMessage("Entry file is not a valid WDL file.", CLIENT_ERROR);
                } else if (cwlContentPresent && CWL.getLowerShortName().equals(descriptor)) {
                    out("This is a CWL file.. Please put the correct extension to the entry file name.");
                    out("Launching entry file as a CWL file..");
                    try {
                        launchCwl(localFilePath, argsList, true);
                    } catch (ApiException e) {
                        exceptionMessage(e, "API error launching entry", Client.API_ERROR);
                    } catch (IOException e) {
                        exceptionMessage(e, "IO error launching entry", IO_ERROR);
                    }
                } else {
                    errorMessage(invalidWorkflowMessage, CLIENT_ERROR);
                }
            } else if (NEXTFLOW.equals(ext)) {
                // TODO: better error handling as with CWL and WDL
                if (nextflowContentPresent) {
                    try {
                        launchNextflow(localFilePath, argsList, true);
                    } catch (ApiException e) {
                        exceptionMessage(e, "API error launching entry", Client.API_ERROR);
                    }
                } else if (!nextflowContentPresent && descriptor == null) {
                    //extension is wdl but the content is not nextflow
                    out("Entry file is ambiguous, please re-enter command with '--descriptor <descriptor>' at the end");
                } else {
                    errorMessage(invalidWorkflowMessage, CLIENT_ERROR);
                }
            }
        } else if (optContent.isPresent()) {
            DescriptorLanguage content = optContent.get();
            //no extension given
            if (CWL.equals(content)) {
                out("This is a CWL file.. Please put an extension to the entry file name.");
                out("Launching entry file as a CWL file..");
                try {
                    launchCwl(localFilePath, argsList, true);
                } catch (ApiException e) {
                    exceptionMessage(e, "API error launching entry", Client.API_ERROR);
                } catch (IOException e) {
                    exceptionMessage(e, "IO error launching entry", IO_ERROR);
                }
            } else if (WDL.equals(content)) {
                out("This is a WDL file.. Please put an extension to the entry file name.");
                out("Launching entry file as a WDL file..");
                try {
                    launchWdl(localFilePath, argsList, true);
                } catch (ApiException e) {
                    exceptionMessage(e, "API error launching entry", Client.API_ERROR);
                }
            } else {
                errorMessage(invalidWorkflowMessage, CLIENT_ERROR);
            }
        }
        if (optContent.isEmpty() && optExt.isEmpty()) {
            // neither is present
            errorMessage(invalidWorkflowMessage, CLIENT_ERROR);
        }
    }

    /**
     * download tools and workflows.
     *
     * @param args Arguments entered into the CLI
     */
    public void download(final List<String> args) {
        if (args.isEmpty() || containsHelpRequest(args)) {
            downloadHelp();
        } else {
            if (!args.contains("--entry")) {
                errorMessage("dockstore: missing required flag --entry", CLIENT_ERROR);
            }
            final String entry = reqVal(args, "--entry");
            final boolean unzip = !args.contains("--zip");
            try {
                downloadTargetEntry(entry, null, unzip);
            } catch (ApiException e) {
                exceptionMessage(e, "error downloading workflow, perhaps an incorrect ID?",
                    Client.API_ERROR);
            } catch (IOException e) {
                exceptionMessage(e, "error downloading workflow, perhaps you're out of space or do not have permissions?",
                    Client.API_ERROR);
            }
        }
    }

    /**
     * Validates that any JSON and/or YAML files being passed in are syntactically valid.
     * If there is any error other than invalid syntax, the error is ignored and expected to be handled later.
     * Because prevalidation occurs prior to launch, the args need to be preserved for the later handling.
     *
     * @param args
     */
    void preValidateLaunchArguments(List<String> args) {
        // Create a copy of args for prevalidation since optVals removes args from list
        List<String> argsCopy = new java.util.ArrayList(args);
        String jsonFile = optVal(argsCopy, "--json", null);
        String yamlFile = optVal(argsCopy, "--yaml", null);
        if (jsonFile != null) {
            try {
                fileToJSON(jsonFile);
            } catch (ParserException ex) {
                errorMessage("Could not launch, syntax error in json file: " + jsonFile, CLIENT_ERROR);
            } catch (Exception e) {
                // Log error, but let existing code handle
                LOG.error("Error prevalidating input file: " + jsonFile, e);
            }
        }
        if (yamlFile != null) {
            try {
                fileToJSON(yamlFile);
            } catch (ParserException ex) {
                errorMessage("Could not launch, syntax error in yaml file: " + yamlFile, CLIENT_ERROR);
            } catch (Exception e) {
                // Log error, but let existing code handle
                LOG.error("Error prevalidating input file: " + yamlFile, e);
            }
        }
    }


    /**
     * Creates a WES API object and sets the endpoint.
     *
     * @param wesUrl URL to WES endpoint
     */
    WorkflowExecutionServiceApi getWorkflowExecutionServiceApi(String wesUrl, String wesCred) {
        WorkflowExecutionServiceApi clientWorkflowExecutionServiceApi = new WorkflowExecutionServiceApi();

        // Uncomment this code when Swagger Codegen generates correct Java
        // for OpenApi 3.0 yaml with arrays of files
        //ApiClient wesApiClient = clientWorkflowExecutionServiceApi.getApiClient();

        // Done so we can override the Serialize method in ApiClient
        // Since Swagger Codegen does not create correct code for the
        // workflow attachment
        // Delete these next two lines when Swagger Codegen is fixed
        ApiClientExtended wesApiClient = new ApiClientExtended();
        clientWorkflowExecutionServiceApi.setApiClient(wesApiClient);

        INIConfiguration config = Utilities.parseConfig(this.getConfigFile());
        SubnodeConfiguration configSubNode = null;
        try {
            configSubNode = config.getSection("WES");
        } catch (Exception e) {
            out("Could not get WES section from config file");
        }

        String wesEndpointUrl = ObjectUtils.firstNonNull(wesUrl, configSubNode.getString("url"));
        if (wesEndpointUrl == null || wesEndpointUrl.isEmpty()) {
            errorMessage("No WES URL found in config file and no WES URL entered on command line. Please add url: <url> to "
                    + "config file in a WES section or use --wes-url <url> option on the command line", CLIENT_ERROR);
        } else {
            out("WES endpoint url is: " + wesEndpointUrl);
            wesApiClient.setBasePath(wesEndpointUrl);
        }

        /*
         * Setup authentication credentials for the WES URL
         */
        String wesAuthorizationCredentials = ObjectUtils.firstNonNull(wesCred, configSubNode.getString("authorization"));
        if (wesAuthorizationCredentials == null) {
            out("Could not set Authorization header. Authorization key not found in config file and not provided on the command line. "
                    + "Please add 'authorization: <type> <credentials> to config file in WES section or "
                    + "use --wes-auth '<type> <credentials>' option on the command line if authorization credentials are needed");
        } else {
            wesApiClient.addDefaultHeader(HttpHeaders.AUTHORIZATION, wesAuthorizationCredentials);
        }

        // Add these headers to the http request. Are these needed?
        wesApiClient.addDefaultHeader("Accept", "*/*");
        wesApiClient.addDefaultHeader("Expect", "100-continue");

        clientWorkflowExecutionServiceApi.setApiClient(wesApiClient);
        return clientWorkflowExecutionServiceApi;
    }

    /**
     * Processes Workflow Execution Schema (WES) commands.
     *
     * @param args Arguments entered into the CLI
     */
    private void processWesCommands(final List<String> args) {
        this.wesUri = optVal(args, "--wes-url", null);
        this.wesAuth = optVal(args, "--wes-auth", null);

        if (args.isEmpty() || (args.size() == 1 && containsHelpRequest(args))) {
            wesHelp();
        } else {
            final String cmd = args.remove(0);
            switch (cmd) {
            case "launch":
                if (args.isEmpty() || containsHelpRequest(args)) {
                    wesLaunchHelp();
                } else {
                    launch(args);
                }
                break;
            case "status":
                if (args.isEmpty() || containsHelpRequest(args)) {
                    wesStatusHelp();
                } else {
                    WorkflowExecutionServiceApi clientWorkflowExecutionServiceApi = getWorkflowExecutionServiceApi(getWesUri(), getWesAuth());
                    String workflowId = reqVal(args, "--id");
                    out("Getting status of WES workflow");
                    if (args.contains("--verbose")) {
                        try {
                            RunLog response = clientWorkflowExecutionServiceApi.getRunLog(workflowId);
                            out("Verbose run status is: " + response.toString());
                        } catch (io.openapi.wes.client.ApiException e) {
                            LOG.error("Error getting verbose WES run status", e);
                        }
                    } else {
                        try {
                            RunStatus response = clientWorkflowExecutionServiceApi.getRunStatus(workflowId);
                            out("Brief run status is: " + response.toString());
                        } catch (io.openapi.wes.client.ApiException e) {
                            LOG.error("Error getting brief WES run status", e);
                        }
                    }
                }
                break;
            case "cancel":
                if (args.isEmpty() || containsHelpRequest(args)) {
                    wesCancelHelp();
                } else {
                    WorkflowExecutionServiceApi clientWorkflowExecutionServiceApi = getWorkflowExecutionServiceApi(getWesUri(), getWesAuth());
                    out("Canceling WES workflow");
                    String workflowId = reqVal(args, "--id");
                    try {
                        RunId response = clientWorkflowExecutionServiceApi.cancelRun(workflowId);
                        out("Cancelled run with id: " + response.toString());
                    } catch (io.openapi.wes.client.ApiException e) {
                        LOG.error("Error canceling WES run", e);
                    }
                }
                break;
            default:
                invalid(cmd);
                break;
            }
        }

    }

    /**
     * Prints a warning if Docker isn't running. Docker is not always needed. If a workflow or tool uses Docker and
     * it is not running, it fails with a cryptic error. This should make the problem more obvious.
     */
    void checkIfDockerRunning() {
        try (DockerClient docker = DefaultDockerClient.fromEnv().build()) {
            docker.info();  // attempt to get information about docker
        } catch (DockerException | DockerCertificateException e) {  // couldn't access docker
            String type = this.getEntryType().toLowerCase(); // "tool" or "workflow"
            out("WARNING: Docker is not running. If this " + type + " uses Docker, it will fail.");
        } catch (InterruptedException e) {  // something else went wrong
            LOG.error("Check for Docker failed", e);
        }
    }

    /**
     * Launches tools. Overridden for workflows.
     *
     * @param args Arguments entered into the CLI
     */
    public void launch(final List<String> args) {
        if (args.isEmpty() || containsHelpRequest(args)) {
            launchHelp();
        } else {
            if (args.contains("--local-entry") && args.contains("--entry")) {
                errorMessage("You can only use one of --local-entry and --entry at a time. Please use --help for more information.",
                        CLIENT_ERROR);
            } else if (args.contains("--local-entry")) {
                final String descriptor = optVal(args, "--descriptor", null);
                final String localFilePath = reqVal(args, "--local-entry");
                this.isLocalEntry = true;
                preValidateLaunchArguments(args);
                checkIfDockerRunning();
                checkEntryFile(localFilePath, args, descriptor);
            } else {
                if (!args.contains("--entry")) {
                    errorMessage("dockstore: missing required flag --entry", CLIENT_ERROR);
                }
                this.isLocalEntry = false;
                preValidateLaunchArguments(args);
                checkIfDockerRunning();

                final String descriptor = optVal(args, "--descriptor", CWL.getLowerShortName());
                if (descriptor.equals(CWL.getLowerShortName())) {
                    try {
                        String entry = reqVal(args, "--entry");
                        launchCwl(entry, args, false);
                    } catch (ApiException e) {
                        exceptionMessage(e, "API error launching workflow. Did you mean to use --local-entry instead of --entry?",
                                Client.API_ERROR);
                    } catch (IOException e) {
                        exceptionMessage(e, "IO error launching workflow. Did you mean to use --local-entry instead of --entry?",
                                Client.IO_ERROR);
                    }
                } else if (descriptor.equals(WDL.getLowerShortName())) {
                    try {
                        launchWdl(args, false);
                    } catch (ApiException e) {
                        exceptionMessage(e, "API error launching workflow. Did you mean to use --local-entry instead of --entry?",
                                Client.API_ERROR);
                    } catch (IOException e) {
                        exceptionMessage(e, "IO error launching workflow. Did you mean to use --local-entry instead of --entry?",
                                Client.IO_ERROR);
                    }
                }
            }

        }
    }

    /**
     * Create a path for the type of entry this handles
     * @param entry
     * @return create filename
     */
    public abstract String zipFilename(T entry);

    private void launchCwl(String entry, final List<String> args, boolean isALocalEntry) throws ApiException, IOException {
        final String yamlRun = optVal(args, "--yaml", null);
        String jsonRun = optVal(args, "--json", null);
        final String uuid = optVal(args, "--uuid", null);

        if (!(yamlRun != null ^ jsonRun != null)) {
            errorMessage("One of  --json or --yaml is required", CLIENT_ERROR);
        }
        CWLClient client = new CWLClient(this);
        client.launch(entry, isALocalEntry, yamlRun, jsonRun, null, uuid);
    }

    /**
     * Returns the first path that is not null and is not remote (YAML preference)
     *
     * @param yamlRun The yaml file path
     * @param jsonRun The json file path
     * @return Path to first not null file
     */
    public String getFirstNotNullParameterFile(String yamlRun, String jsonRun) {
        Optional<String> s = Stream.of(yamlRun, jsonRun).filter(Objects::nonNull).findFirst();
        if (s.isPresent() && Paths.get(s.get()).toFile().exists()) {
            // convert relative path to absolute path
            return s.get();
        } else {
            return "";
        }
    }

    void writeSourceFilesToDisk(File tempDir, List<SourceFile> result, List<SourceFile> files) throws IOException {
        for (SourceFile sourceFile : files) {
            File tempDescriptor = new File(tempDir.getAbsolutePath(), sourceFile.getPath());
            // ensure that the parent directory exists
            tempDescriptor.getParentFile().mkdirs();
            Files.asCharSink(tempDescriptor, StandardCharsets.UTF_8).write(sourceFile.getContent());
            result.add(sourceFile);
        }
    }

    private void launchWdl(final List<String> args, boolean isALocalEntry) throws IOException, ApiException {
        final String entry = reqVal(args, "--entry");
        launchWdl(entry, args, isALocalEntry);
    }

    private void launchWdl(String entry, final List<String> args, boolean isALocalEntry) throws ApiException {
        final String yamlRun = optVal(args, "--yaml", null);
        String jsonRun = optVal(args, "--json", null);
        if (!(yamlRun != null ^ jsonRun != null)) {
            errorMessage("dockstore: Missing required flag: one of --json or --yaml", CLIENT_ERROR);
        }
        final String wdlOutputTarget = optVal(args, "--wdl-output-target", null);
        final String uuid = optVal(args, "--uuid", null);
        WDLClient client = new WDLClient(this);
        client.launch(entry, isALocalEntry, yamlRun, jsonRun, wdlOutputTarget, uuid);
    }

    private void launchNextflow(String entry, final List<String> args, boolean isALocalEntry) throws ApiException {
        final String json = reqVal(args, "--json");
        final String uuid = optVal(args, "--uuid", null);
        NextflowClient client = new NextflowClient(this);
        client.launch(entry, isALocalEntry, null, json, null, uuid);
    }

    private String convertEntry2Json(List<String> args, final boolean json) throws ApiException, IOException {
        final String entry = reqVal(args, "--entry");
        final String descriptor = optVal(args, "--descriptor", CWL.getLowerShortName());
        LanguageClientInterface languageCLient = convertCLIStringToEnum(descriptor);
        return languageCLient.generateInputJson(entry, json);
    }

    LanguageClientInterface convertCLIStringToEnum(String descriptor) {
        final DescriptorLanguage descriptorLanguage = DescriptorLanguage.convertShortStringToEnum(descriptor);
        Optional<LanguageClientInterface> languageCLient = LanguageClientFactory.createLanguageCLient(this, descriptorLanguage);
        return languageCLient.orElseThrow(() -> new UnsupportedOperationException("language not supported yet"));
    }

    /**
     * Loads docker images from file system if there are any
     */
    void loadDockerImages() {
        INIConfiguration config = Utilities.parseConfig(this.getConfigFile());
        String dockerImageDirectory = config.getString("docker-images");
        if (!StringUtils.isBlank(dockerImageDirectory)) {
            Path directoryPath = Paths.get(dockerImageDirectory);
            Supplier<Stream<Path>> list = () -> {
                try {
                    return java.nio.file.Files.list(directoryPath);
                } catch (NotDirectoryException e) {
                    System.out.println("The specified Docker image directory is a file: " + directoryPath.toAbsolutePath());
                } catch (NoSuchFileException e) {
                    System.out.println("The specified Docker image directory not found: " + directoryPath.toAbsolutePath());
                } catch (IOException e) {
                    // Not able to find a situation in which this occurs
                    System.out.println("Something is wrong with the specified Docker image directory: " + directoryPath.toAbsolutePath());
                    System.out.println(e.toString());
                }
                return Stream.empty();
            };
            if (list.get().count() == 0) {
                System.out.println("There are no files in the docker image directory: " + directoryPath.toAbsolutePath());
            } else {
                System.out.println("Loading docker images...");
                list.get().forEach(path -> Utilities.executeCommand("docker load -i \"" + path + "\"", System.out, System.err));
            }
        } else {
            LOG.info("No docker image directory specified in Dockstore config file");
        }
    }

    public abstract Client getClient();



    /**
     * help text output
     */
    private void wesHelp() {
        printHelpHeader();
        out("Commands:");
        out("");
        out("Usage: dockstore " + getEntryType().toLowerCase() + " wes --help");
        out("       dockstore " + getEntryType().toLowerCase() + " wes launch [parameters]");
        out("       dockstore " + getEntryType().toLowerCase() + " wes status [parameters]");
        out("       dockstore " + getEntryType().toLowerCase() + " wes cancel [parameters]");
        out("");
        out("Description:");
        out(" Sends a request to a Workflow Execution Service (WES) endpoint.");
        printWesHelpFooter();
        printHelpFooter();
    }

    private void wesLaunchHelp() {
        printHelpHeader();
        out("Usage: dockstore " + getEntryType().toLowerCase() + " wes launch --help");
        out("       dockstore " + getEntryType().toLowerCase() + " wes launch [parameters]");
        printLaunchHelpBody();
        printWesHelpFooter();
        printHelpFooter();
    }

    private void wesStatusHelp() {
        printHelpHeader();
        out("Usage: dockstore " + getEntryType().toLowerCase() + " wes status --help");
        out("       dockstore " + getEntryType().toLowerCase() + " wes status [parameters]");
        out("");
        out("Description:");
        out("  Status, gets the status of a " + getEntryType() + ".");
        out("Required Parameters:");
        out("  --id <id>                           Id of a run at the WES endpoint, e.g. id returned from the launch command");
        out("Optional Parameters:");
        out("  --verbose                           Provide extra status information");
        out("");
        printWesHelpFooter();
        printHelpFooter();
    }

    private void wesCancelHelp() {
        printHelpHeader();
        out("Usage: dockstore " + getEntryType().toLowerCase() + " wes cancel --help");
        out("       dockstore " + getEntryType().toLowerCase() + " wes cancel [parameters]");
        out("");
        out("Description:");
        out("  Cancels a " + getEntryType() + ".");
        out("Required Parameters:");
        out("  --id <id>                           Id of a run at the WES endpoint, e.g. id returned from the launch command");
        out("");
        printWesHelpFooter();
        printHelpFooter();
    }

    private void printWesHelpFooter() {
        out("Global Optional Parameters:");
        out("  --wes-url <WES URL>                 URL where the WES request should be sent, e.g. 'http://localhost:8080/ga4gh/wes/v1'");
        out("  --wes-auth <auth>                   Authorization credentials for the WES endpoint, e.g. 'Bearer 12345'");
        out("");
        out("NOTE: WES SUPPORT IS IN BETA AT THIS TIME. RESULTS MAY BE UNPREDICTABLE.");
    }

    protected abstract void publishHelp();

    private void starHelp() {
        printHelpHeader();
        out("Usage: dockstore " + getEntryType().toLowerCase() + " star --help");
        out("       dockstore " + getEntryType().toLowerCase() + " star");
        out("       dockstore " + getEntryType().toLowerCase() + " star [parameters]");
        out("       dockstore " + getEntryType().toLowerCase() + " star --unstar [parameters]");
        out("");
        out("Description:");
        out("  Star/unstar a registered " + getEntryType() + ".");
        out("  No arguments will list the current and potential " + getEntryType() + "s to share.");
        out("Required Parameters:");
        out("  --entry <" + getEntryType() + ">             Complete " + getEntryType()
                + " path in the Dockstore (ex. quay.io/collaboratory/seqware-bwa-workflow)");
        printHelpFooter();
    }

    private void listHelp() {
        printHelpHeader();
        out("Usage: dockstore " + getEntryType().toLowerCase() + " list --help");
        out("       dockstore " + getEntryType().toLowerCase() + " list");
        out("");
        out("Description:");
        out("  lists all the " + getEntryType() + " published by the user.");
        printHelpFooter();
    }

    private void labelHelp() {
        printHelpHeader();
        out("Usage: dockstore " + getEntryType().toLowerCase() + " label --help");
        out("       dockstore " + getEntryType().toLowerCase() + " label [parameters]");
        out("");
        out("Description:");
        out("  Add or remove labels from a given Dockstore " + getEntryType());
        out("");
        out("Required Parameters:");
        out("  --entry <entry>                             Complete " + getEntryType() + " path in the Dockstore");
        out("");
        out("Optional Parameters:");
        out("  --add <label> (--add <label>)               Add given label(s)");
        out("  --remove <label> (--remove <label>)         Remove given label(s)");
        printHelpFooter();
    }

    protected void testParameterHelp() {
        printHelpHeader();
        out("Usage: dockstore " + getEntryType().toLowerCase() + " test_parameter --help");
        out("       dockstore " + getEntryType().toLowerCase() + " test_parameter [parameters]");
        out("");
        out("Description:");
        out("  Add or remove test parameter files from a given Dockstore " + getEntryType() + " version");
        out("");
        out("Required Parameters:");
        out("  --entry <entry>                                                          Complete " + getEntryType()
                + " path in the Dockstore (ex. quay.io/collaboratory/seqware-bwa-workflow)");
        out("  --version <version>                                                      " + getEntryType() + " version name");
        if (getEntryType().equalsIgnoreCase("tool")) {
            out("  --descriptor-type <descriptor-type>                                      CWL/WDL");
        }
        out("");
        out("Optional Parameters:");
        out("  --add <test parameter file> (--add <test parameter file>)               Path in Git repository of test parameter file(s) to add");
        out("  --remove <test parameter file> (--remove <test parameter file>)         Path in Git repository of test parameter file(s) to remove");
        printHelpFooter();
    }

    private void infoHelp() {
        printHelpHeader();
        out("Usage: dockstore " + getEntryType().toLowerCase() + " info --help");
        out("       dockstore " + getEntryType().toLowerCase() + " info [parameters]");
        out("");
        out("Description:");
        out("  Get information related to a published " + getEntryType() + ".");
        out("");
        out("Required Parameters:");
        out("  --entry <entry>     The complete " + getEntryType()
                + " path in the Dockstore (ex. quay.io/collaboratory/seqware-bwa-workflow)");
        printHelpFooter();
    }

    private void descriptorHelp(DescriptorLanguage descriptorType) {
        printHelpHeader();
        out("Usage: dockstore " + getEntryType().toLowerCase() + " " + descriptorType + " --help");
        out("       dockstore " + getEntryType().toLowerCase() + " " + descriptorType + " [parameters]");
        out("");
        out("Description:");
        out("  Grab a " + descriptorType.toString().toUpperCase() + " document for a particular entry.");
        out("");
        out("Required parameters:");
        out("  --entry <entry>              Complete " + getEntryType()
                + " path in the Dockstore (ex. quay.io/collaboratory/seqware-bwa-workflow:develop)");
        printHelpFooter();
    }

    private void refreshHelp() {
        printHelpHeader();
        out("Usage: dockstore " + getEntryType().toLowerCase() + " refresh --help");
        out("       dockstore " + getEntryType().toLowerCase() + " refresh");
        out("       dockstore " + getEntryType().toLowerCase() + " refresh [parameters]");
        out("");
        out("Description:");
        out("  Refresh an individual " + getEntryType() + " or all your " + getEntryType() + ".");
        out("");
        out("Optional Parameters:");
        out("  --entry <entry>         Complete tool path in the Dockstore (ex. quay.io/collaboratory/seqware-bwa-workflow)");
        printHelpFooter();
    }

    private void searchHelp() {
        printHelpHeader();
        out("Usage: dockstore " + getEntryType().toLowerCase() + " search --help");
        out("       dockstore " + getEntryType().toLowerCase() + " search [parameters]");
        out("");
        out("Description:");
        out("  Search for published " + getEntryType() + " on Dockstore.");
        out("");
        out("Required Parameters:");
        out("  --pattern <pattern>         Pattern to search Dockstore with");
        printHelpFooter();
    }

    private void cwl2yamlHelp() {
        printHelpHeader();
        out("Usage: dockstore " + getEntryType().toLowerCase() + " " + CONVERT + " --help");
        out("       dockstore " + getEntryType().toLowerCase() + " " + CONVERT + " cwl2yaml [parameters]");
        out("");
        out("Description:");
        out("  Spit out a yaml run file for a given cwl document.");
        out("");
        out("Required parameters:");
        out("  --cwl <file>                Path to cwl file");
        printHelpFooter();
    }

    private void cwl2jsonHelp() {
        printHelpHeader();
        out("Usage: dockstore " + getEntryType().toLowerCase() + " " + CONVERT + " --help");
        out("       dockstore " + getEntryType().toLowerCase() + " " + CONVERT + " cwl2json [parameters]");
        out("");
        out("Description:");
        out("  Spit out a json run file for a given cwl document.");
        out("");
        out("Required parameters:");
        out("  --cwl <file>                Path to cwl file");
        printHelpFooter();
    }

    private void wdl2jsonHelp() {
        printHelpHeader();
        out("Usage: dockstore " + getEntryType().toLowerCase() + " " + CONVERT + " --help");
        out("       dockstore " + getEntryType().toLowerCase() + " " + CONVERT + " wdl2json [parameters]");
        out("");
        out("Description:");
        out("  Spit out a json run file for a given wdl document.");
        out("");
        out("Required parameters:");
        out("  --wdl <file>                Path to wdl file");
        printHelpFooter();
    }

    private void convertHelp() {
        printHelpHeader();
        out("Usage: dockstore " + getEntryType().toLowerCase() + " " + CONVERT + " --help");
        out("       dockstore " + getEntryType().toLowerCase() + " " + CONVERT + " cwl2json [parameters]");
        out("       dockstore " + getEntryType().toLowerCase() + " " + CONVERT + " cwl2yaml [parameters]");
        out("       dockstore " + getEntryType().toLowerCase() + " " + CONVERT + " wdl2json [parameters]");
        out("       dockstore " + getEntryType().toLowerCase() + " " + CONVERT + " entry2json [parameters]");
        out("       dockstore " + getEntryType().toLowerCase() + " " + CONVERT + " entry2tsv [parameters]");
        out("");
        out("Description:");
        out("  They allow you to convert between file representations.");
        printHelpFooter();
    }

    private void entry2tsvHelp() {
        printHelpHeader();
        out("Usage: dockstore " + getEntryType().toLowerCase() + " " + CONVERT + " entry2tsv --help");
        out("       dockstore " + getEntryType().toLowerCase() + " " + CONVERT + " entry2tsv [parameters]");
        out("");
        out("Description:");
        out("  Spit out a tsv run file for a given cwl document.");
        out("");
        out("Required parameters:");
        out("  --entry <entry>                Complete " + getEntryType().toLowerCase()
                + " path in the Dockstore (ex. quay.io/collaboratory/seqware-bwa-workflow:develop)");
        printHelpFooter();
    }

    private void entry2jsonHelp() {
        printHelpHeader();
        out("Usage: dockstore " + getEntryType().toLowerCase() + " " + CONVERT + " entry2json --help");
        out("       dockstore " + getEntryType().toLowerCase() + " " + CONVERT + " entry2json [parameters]");
        out("");
        out("Description:");
        out("  Spit out a json run file for a given cwl document.");
        out("");
        out("Required parameters:");
        out("  --entry <entry>                Complete " + getEntryType().toLowerCase()
                + " path in the Dockstore (ex. quay.io/collaboratory/seqware-bwa-workflow:develop)");
        out("  --descriptor <descriptor>      Type of descriptor language used. Defaults to cwl");
        printHelpFooter();
    }

    private void downloadHelp() {
        printHelpHeader();
        out("Usage: dockstore " + getEntryType().toLowerCase() + " download --help");
        out("       dockstore " + getEntryType().toLowerCase() + " download [parameters]");
        out("");
        out("Description:");
        out("  Download an entry to the working directory.");
        out("");
        out("Required parameters:");
        out("  --entry <entry>          Complete entry path in the Dockstore (ex. quay.io/collaboratory/seqware-bwa-workflow:develop)");
        out("");
        out("Optional parameters:");
        out("  --zip               Keep the zip file rather than uncompress the files within");
        printHelpFooter();
    }

    private void printLaunchHelpBody() {
        out("");
        out("Description:");
        out("  Launch an entry locally.");
        out("");
        out("Required parameters:");
        out("  --entry <entry>                     Complete entry path in the Dockstore (ex. quay.io/collaboratory/seqware-bwa-workflow:develop)");
        if (!(this instanceof CheckerClient)) {
            out("   OR");
            out("  --local-entry <local-entry>         Allows you to specify a full path to a local descriptor instead of an entry path");
        }
        out("");
        out("Optional parameters:");
        out("  --json <json file>                  Parameters to the entry in the dockstore, one map for one run, an array of maps for multiple runs");
        out("  --yaml <yaml file>                  Parameters to the entry in the dockstore, one map for one run, an array of maps for multiple runs");
        out("  --descriptor <descriptor type>      Descriptor type used to launch workflow. Defaults to " + CWL.getLowerShortName());
        if (!(this instanceof CheckerClient)) {
            out("  --local-entry                       Allows you to specify a full path to a local descriptor for --entry instead of an entry path");
        }
        out("  --wdl-output-target                 Allows you to specify a remote path to provision output files to ex: s3://oicr.temp/testing-launcher/");
        out("  --uuid                              Allows you to specify a uuid for 3rd party notifications");
        out("");
    }

    protected void launchHelp() {
        printHelpHeader();
        out("Usage: dockstore " + getEntryType().toLowerCase() + " launch --help");
        out("       dockstore " + getEntryType().toLowerCase() + " launch [parameters]");
        printLaunchHelpBody();
        printHelpFooter();
    }

    private void verifyHelp() {
        printHelpHeader();
        out("Usage: dockstore " + getEntryType().toLowerCase() + " verify --help");
        out("       dockstore " + getEntryType().toLowerCase() + " verify [parameters]");
        out("       dockstore " + getEntryType().toLowerCase() + " verify --unverify [parameters]");
        out("");
        out("Description:");
        out("  Verify/unverify a version.");
        out("");
        out("Required parameters:");
        out("  --entry <entry>                              Complete entry path in the Dockstore (ex. quay.io/collaboratory/seqware-bwa-workflow)");
        out("  --version <version>                          Version name");
        out("");
        out("Optional Parameters:");
        out("  --verified-source <verified-source>          Source of verification (Required to verify).");
        printHelpFooter();
    }

    protected void printAdminHelp() {
        out("Admin Only Commands:");
        out("");
        out("  verify           :  Verify/unverify a version");
        out("");
    }



    /**
     * Reads a file whose format is either YAML or JSON and makes a JSON string out of the contents
     * @param yamlRun string representation of the yaml content
     * @throws ParserException if the JSON or YAML is not syntactically valid
     * @throws IOException
     * @return json string representation of the yaml content
     */
    public String fileToJSON(String yamlRun) throws IOException {
        Yaml yaml = new Yaml();
        try (FileInputStream fileInputStream = FileUtils.openInputStream(new File(yamlRun))) {
            Map<String, Object> map = yaml.load(fileInputStream);
            JSONObject jsonObject = new JSONObject(map);
            return jsonObject.toString();
        }
    }
}
