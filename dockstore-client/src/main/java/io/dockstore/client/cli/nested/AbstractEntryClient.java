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

import static io.dockstore.client.cli.ArgumentUtility.CONVERT;
import static io.dockstore.client.cli.ArgumentUtility.DOWNLOAD;
import static io.dockstore.client.cli.ArgumentUtility.LAUNCH;
import static io.dockstore.client.cli.ArgumentUtility.MAX_DESCRIPTION;
import static io.dockstore.client.cli.ArgumentUtility.conditionalErrorMessage;
import static io.dockstore.client.cli.ArgumentUtility.containsHelpRequest;
import static io.dockstore.client.cli.ArgumentUtility.err;
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
import static io.dockstore.client.cli.Client.COMMAND_ERROR;
import static io.dockstore.client.cli.Client.ENTRY_NOT_FOUND;
import static io.dockstore.client.cli.Client.GENERIC_ERROR;
import static io.dockstore.client.cli.Client.HELP;
import static io.dockstore.client.cli.Client.IO_ERROR;
import static io.dockstore.client.cli.Client.TOOL;
import static io.dockstore.client.cli.Client.VERSION;
import static io.dockstore.client.cli.Client.WORKFLOW;
import static io.dockstore.client.cli.Client.getGeneralFlags;
import static io.dockstore.client.cli.JCommanderUtility.displayJCommanderSuggestions;
import static io.dockstore.client.cli.JCommanderUtility.getUnknownParameter;
import static io.dockstore.client.cli.JCommanderUtility.wasErrorDueToUnknownParameter;
import static io.dockstore.client.cli.YamlVerifyUtility.YAML;
import static io.dockstore.client.cli.nested.WesCommandParser.ATTACH;
import static io.dockstore.client.cli.nested.WesCommandParser.COUNT;
import static io.dockstore.client.cli.nested.WesCommandParser.ENTRY;
import static io.dockstore.client.cli.nested.WesCommandParser.ID;
import static io.dockstore.client.cli.nested.WesCommandParser.INLINE_WORKFLOW;
import static io.dockstore.client.cli.nested.WesCommandParser.JSON;
import static io.dockstore.client.cli.nested.WesCommandParser.PAGE_TOKEN;
import static io.dockstore.client.cli.nested.WesCommandParser.VERBOSE;
import static io.dockstore.client.cli.nested.WesCommandParser.WES_URL;
import static io.dockstore.common.DescriptorLanguage.CWL;
import static io.dockstore.common.DescriptorLanguage.NEXTFLOW;
import static io.dockstore.common.DescriptorLanguage.WDL;
import static io.github.collaboratory.cwl.CWLClient.WES;
import static java.lang.String.join;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.MissingCommandException;
import com.beust.jcommander.ParameterException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InfoCmd;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import io.cwl.avro.CWL;
import io.dockstore.client.cli.CheckerClient;
import io.dockstore.client.cli.Client;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.Utilities;
import io.dockstore.common.WdlBridge;
import io.dockstore.openapi.client.ApiException;
import io.dockstore.openapi.client.api.Ga4Ghv20Api;
import io.dockstore.openapi.client.model.Label;
import io.dockstore.openapi.client.model.SourceFile;
import io.dockstore.openapi.client.model.ToolDescriptor;
import io.dockstore.openapi.client.model.ToolFile;
import io.github.collaboratory.cwl.CWLClient;
import io.github.collaboratory.nextflow.NextflowClient;
import io.github.collaboratory.wdl.WDLClient;
import io.openapi.wes.client.api.WorkflowExecutionServiceApi;
import io.openapi.wes.client.model.RunId;
import io.openapi.wes.client.model.RunListResponse;
import io.openapi.wes.client.model.RunLog;
import io.openapi.wes.client.model.RunStatus;
import io.openapi.wes.client.model.ServiceInfo;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.ArrayList;
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
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.parser.ParserException;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;
import wdl.draft3.parser.WdlParser;

/**
 * Handles the commands for a particular type of entry. (e.g. Workflows, Tools) Not a great abstraction, but enforces some structure for
 * now.
 * <p>
 * The goal here should be to gradually work toward an interface that removes those pesky command line arguments (List&lt;String&gt; args) from
 * implementing classes that do not need to reference to the command line arguments directly.
 * <p>
 * Note that many of these methods depend on a unique identifier for an entry called a path for workflows and tools.
 * For example, a tool path looks like quay.io/collaboratory/bwa-tool:develop whereas a workflow path looks like
 * collaboratory/bwa-workflow:develop
 *
 * @author dyuen
 */
public abstract class AbstractEntryClient<T> {
    public static final String CHECKSUM_NULL_MESSAGE = "Unable to validate local descriptor checksum. Please refresh the entry. Missing checksum for descriptor ";
    public static final String CHECKSUM_MISMATCH_MESSAGE = "Launch halted. Local checksum does not match remote checksum for ";
    public static final String CHECKSUM_VALIDATED_MESSAGE = "Checksums validated.";
    public static final String MULTIPLE_TEST_FILE_ERROR_MESSAGE = "If specifying a test parameter file, use either --json or --yaml, but not both.";

    public static final String INFO = "info";
    public static final String LIST = "list";
    public static final String SEARCH = "search";
    public static final String PUBLISH = "publish";
    public static final String STAR = "star";
    public static final String REFRESH = "refresh";
    public static final String LABEL = "label";
    public static final String MANUAL_PUBLISH = "manual_publish";
    public static final String VERIFY = "verify";
    public static final String TEST_PARAMETER = "test_parameter";
    public static final String NFL = "nfl";
    public static final String CWL_2_JSON = "cwl2json";
    public static final String CWL_2_YAML = "cwl2yaml";
    public static final String WDL_2_JSON = "wdl2json";
    public static final String ENTRY_2_JSON = "entry2json";
    public static final String STATUS = "status";
    public static final String LOGS = "logs";
    public static final String CANCEL = "cancel";
    public static final String SERVICE_INFO = "service-info";
    private static final Logger LOG = LoggerFactory.getLogger(AbstractEntryClient.class);
    protected boolean isAdmin = false;

    boolean isLocalEntry = false;
    boolean ignoreChecksums = false;

    private WesRequestData wesRequestData = null;

    static String getCleanedDescription(String description) {
        description = MoreObjects.firstNonNull(description, "");
        // strip control characters
        description = CharMatcher.javaIsoControl().removeFrom(description);
        if (description.length() > MAX_DESCRIPTION) {
            description = description.substring(0, MAX_DESCRIPTION - Client.PADDING) + "...";
        }
        return description;
    }

    public WesRequestData getWesRequestData() {
        return wesRequestData;
    }

    public void setWesRequestData(WesRequestData wrd) {
        this.wesRequestData = wrd;
    }

    boolean isLocalEntry() {
        return isLocalEntry;
    }

    boolean getIgnoreChecksums() {
        return ignoreChecksums;
    }

    public CWL getCwlUtil() {
        // TODO: may be reactivated if we find a different way to read CWL into Java
        // String cwlrunner = CWLRunnerFactory.getCWLRunner();
        return new CWL(Utilities.parseConfig(getConfigFile()));
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
        out("  " + CONVERT + "          :  utilities that allow you to " + CONVERT + " file types");
        out("");
        out("  " + CWL + "              :  returns the Common Workflow Language " + getEntryType() + " definition for this entry");
        out("                      which enables integration with Global Alliance compliant systems");
        out("");
        out("  " + DOWNLOAD + "         :  " + DOWNLOAD + " " + getEntryType() + "s to the local directory");
        out("");
        out("  " + INFO + "             :  print detailed information about a particular published " + getEntryType());
        out("");
        out("  " + LABEL + "            :  updates labels for an individual " + getEntryType() + "");
        out("");
        out("  " + LAUNCH + "           :  launch " + getEntryType() + "s (locally)");
        out("");
        out("  " + LIST + "             :  lists all the " + getEntryType() + "s published by the user");
        out("");
        if (WORKFLOW.equalsIgnoreCase(getEntryType())) {
            out("  " + NFL + "              :  returns the Nextflow " + getEntryType() + " definition for this entry");
            out("");
        }
        out("  " + PUBLISH + "          :  publish/unpublish a " + getEntryType() + " in Dockstore");
        out("");
        out("  " + REFRESH + "          :  updates your list of " + getEntryType() + "s stored on Dockstore or an individual " + getEntryType());
        out("");
        out("  " + SEARCH + "           :  allows a user to search for all published " + getEntryType() + "s that match the criteria");
        out("");
        out("  " + STAR + "             :  " + STAR + "/unstar a " + getEntryType() + " in Dockstore");
        out("");
        out("  " + TEST_PARAMETER + "   :  updates test parameter files for a version of a " + getEntryType() + "");
        out("");
        out("  " + WDL + "              :  returns the Workflow Descriptor Language definition for this entry");
        if (WORKFLOW.equalsIgnoreCase(getEntryType())) {
            out("");
            out("  " + WES + "              :  calls a Workflow Execution Schema API (WES) for a version of a " + getEntryType() + "");
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

    protected abstract List<String> getClientSpecificCommands();

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
                .filter(lang -> activeCommand.equalsIgnoreCase(lang.toString())).findFirst();
            if (first.isPresent()) {
                descriptor(args, first.get());
                return true;
            }

            switch (activeCommand) {
            case INFO:
                info(args);
                break;
            case LIST:
                list(args);
                break;
            case SEARCH:
                search(args);
                break;
            case PUBLISH:
                publish(args);
                break;
            case STAR:
                star(args);
                break;
            case REFRESH:
                refresh(args);
                break;
            case LABEL:
                label(args);
                break;
            case MANUAL_PUBLISH:
                manualPublish(args);
                break;
            case CONVERT:
                convert(args);
                break;
            case LAUNCH:
                launch(args);
                break;
            case DOWNLOAD:
                download(args);
                break;
            case VERIFY:
                verify(args);
                break;
            case TEST_PARAMETER:
                testParameter(args);
                break;
            case WES:
                if (WORKFLOW.equalsIgnoreCase(getEntryType())) {
                    processWesCommands(args);
                } else {
                    errorMessage("WES API calls are only valid for workflows not tools.", CLIENT_ERROR);
                }
                break;
            default:
                List<String> possibleCommands = new ArrayList<>(
                        Arrays.asList(INFO, LIST, SEARCH, PUBLISH, STAR, REFRESH, LABEL, MANUAL_PUBLISH, CONVERT, LAUNCH, DOWNLOAD, VERIFY,
                                TEST_PARAMETER, WDL.toString(), CWL.toString()));

                if (WORKFLOW.equalsIgnoreCase(getEntryType())) {
                    possibleCommands.addAll(Arrays.asList(WES, NFL));
                }
                possibleCommands.addAll(getClientSpecificCommands());
                possibleCommands.addAll(getGeneralFlags());
                invalid(getEntryType().toLowerCase(), activeCommand, possibleCommands);
                return true;
            }
            return true;
        }
        return false;
    }

    /**
     * Handle search for an entry
     *
     * @param pattern a pattern, currently a substring for searching
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
     * Returns the versionID of the specified entry, or the default version if unspecified in the path
     *
     * @param entryPath path to either a tool or workflow
     */
    public abstract String getVersionID(String entryPath);

    /**
     * private helper methods
     */

    public void publish(List<String> args) {
        if (args.isEmpty()) {
            handleListNonpublishedEntries();
        } else if (containsHelpRequest(args)) {
            publishHelp();
        } else {
            String first = reqVal(args, ENTRY);
            final boolean unpublishRequest = args.contains("--unpub");

            // --new-entry-name is the desired parameter flag, but maintaining backwards compatibility for --entryname
            String newEntryName = optVal(args, "--new-entry-name", null);
            if (newEntryName == null && args.contains("--entryname")) {
                err("Dockstore CLI has deprecated the --entryname parameter and may remove it without warning. Please use --new-entry-name instead.");
                newEntryName = optVal(args, "--entryname", null);
            }

            // prevent specifying --unpub and --entryname together
            if (unpublishRequest && newEntryName != null) {
                errorMessage("Unable to specify both --unpub and --new-entry-name together. If trying to unpublish an entry,"
                    + " provide the entire entry path under the " + ENTRY + " parameter.", COMMAND_ERROR);
            } else {
                handlePublishUnpublish(first, newEntryName, unpublishRequest);
            }
        }
    }

    private void star(List<String> args) {
        if (args.isEmpty()) {
            handleListUnstarredEntries();
        } else if (containsHelpRequest(args)) {
            starHelp();
        } else {
            String first = reqVal(args, ENTRY);
            final boolean toStar = !args.contains("--unstar");
            handleStarUnstar(first, toStar);
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
            final String entry = reqVal(args, ENTRY);
            handleDescriptor(descriptorType, entry);
        }
    }

    private void refresh(List<String> args) {
        if (containsHelpRequest(args)) {
            refreshHelp();
        } else if (!args.isEmpty()) {
            final String toolpath = reqVal(args, ENTRY);
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
            String path = reqVal(args, ENTRY);
            handleInfo(path);
        }
    }

    private void label(List<String> args) {
        if (args.isEmpty() || containsHelpRequest(args)) {
            labelHelp();
        } else {
            final String toolpath = reqVal(args, ENTRY);
            final List<String> adds = optVals(args, "--add");
            final Set<String> addsSet = adds.isEmpty() ? new HashSet<>() : new HashSet<>(adds);
            final List<String> removes = optVals(args, "--remove");
            final Set<String> removesSet = removes.isEmpty() ? new HashSet<>() : new HashSet<>(removes);

            // Do a check on the input
            final String labelStringPattern = "^[a-zA-Z0-9]+(-[a-zA-Z0-9]+)*$";

            for (String add : addsSet) {
                if (!add.matches(labelStringPattern)) {
                    errorMessage("The following " + LABEL + " does not match the proper " + LABEL + " format : " + add, CLIENT_ERROR);
                } else if (removesSet.contains(add)) {
                    errorMessage("The following " + LABEL + " is present in both add and remove : " + add, CLIENT_ERROR);
                }
            }

            for (String remove : removesSet) {
                if (!remove.matches(labelStringPattern)) {
                    errorMessage("The following " + LABEL + " does not match the proper " + LABEL + " format : " + remove, CLIENT_ERROR);
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
        if (args.isEmpty() || (containsHelpRequest(args) && !args.contains(CWL_2_JSON) && !args.contains(WDL_2_JSON) && !args
                .contains(ENTRY_2_JSON))) {
            convertHelp(); // Display general help
        } else {
            final String cmd = args.remove(0);
            if (null != cmd) {
                switch (cmd) {
                case CWL_2_JSON:
                    cwl2json(args, true);
                    break;
                case CWL_2_YAML:
                    cwl2json(args, false);
                    break;
                case WDL_2_JSON:
                    wdl2json(args);
                    break;
                case ENTRY_2_JSON:
                    handleEntry2json(args);
                    break;
                default:
                    List<String> possibleCommands = new ArrayList<>();
                    possibleCommands.addAll(Arrays.asList(CWL_2_JSON, CWL_2_YAML, WDL_2_JSON, ENTRY_2_JSON));
                    possibleCommands.addAll(getGeneralFlags());
                    invalid(getEntryType().toLowerCase()  + " convert", cmd, possibleCommands);
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
            final scala.collection.immutable.List<String> wdlList = scala.jdk.javaapi.CollectionConverters.asScala(wdlDocuments).toList();
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
            args.add(0, VERIFY);
            String[] argsArray = new String[args.size()];
            argsArray = args.toArray(argsArray);
            Verify.handleVerifyCommand(argsArray, getEntryType().toLowerCase());
        } else {
            out("This command is only accessible to Admins.");
        }
    }

    protected void testParameter(List<String> args) {
        if (containsHelpRequest(args) || args.isEmpty()) {
            testParameterHelp();
        } else {
            String entry = reqVal(args, ENTRY);
            String version = reqVal(args, VERSION);
            String descriptorType = null;
            final List<String> adds = optVals(args, "--add");
            final List<String> removes = optVals(args, "--remove");

            String parentEntry = optVal(args, "--parent-entry", entry);

            if (getEntryType().equalsIgnoreCase(TOOL)) {
                descriptorType = reqVal(args, "--descriptor-type").toUpperCase();
                boolean validType = false;
                for (DescriptorLanguage type : DescriptorLanguage.values()) {
                    if (type.toString().equals(descriptorType) && !"none".equalsIgnoreCase(descriptorType)) {
                        validType = true;
                        break;
                    }
                }
                final String joinedLanguages = Joiner.on(',').join(Arrays.stream(DescriptorLanguage.values()).map(DescriptorLanguage::toString).collect(Collectors.toSet()));
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
     * Returns the correct path for use in TRS endpoints
     *
     * @param entryPath path to either a tool or workflow
     */
    public String getTrsId(String entryPath) {
        return entryPath;
    }

    /**
     * Get all tool descriptors associated with the entry type
     * @param type descriptor type, CWL, WDL, NFL ...
     * @param entryPath path to either a tool or workflow, the path for a workflow must have the #workflow/ prefix
     * @param versionID version we are fetching descriptors for
     */
    public List<ToolFile> getAllToolDescriptors(String type, String entryPath, String versionID) {
        final Ga4Ghv20Api ga4ghv20api = this.getClient().getGa4Ghv20Api();

        // get all the tool files and filter out anything not a descriptor
        try {
            return ga4ghv20api.toolsIdVersionsVersionIdTypeFilesGet(entryPath, type, versionID, null).stream()
                .filter(toolFile -> ToolFile.FileTypeEnum.SECONDARY_DESCRIPTOR.equals(toolFile.getFileType()) || ToolFile.FileTypeEnum.PRIMARY_DESCRIPTOR.equals(toolFile.getFileType()))
                .collect(Collectors.toList());
        } catch (io.dockstore.openapi.client.ApiException ex) {
            exceptionMessage(ex, "Unable to locate entry " + entryPath + ":" + versionID + " at TRS endpoint", Client.API_ERROR);
        }

        return null;
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
        if (FilenameUtils.getExtension(path).equalsIgnoreCase(CWL.toString()) || FilenameUtils.getExtension(path).equalsIgnoreCase(YAML) || FilenameUtils.getExtension(path).equalsIgnoreCase("yml")) {
            return Optional.of(CWL);
        } else if (FilenameUtils.getExtension(path).equalsIgnoreCase(WDL.toString())) {
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
            if (getEntryType().equalsIgnoreCase(TOOL)) {
                errorMessage("The tool file " + file.getPath() + " does not exist. Did you mean to " + LAUNCH + " a remote " + TOOL + " or a " + WORKFLOW + "?",
                    ENTRY_NOT_FOUND);
            } else {
                errorMessage("The workflow file " + file.getPath() + " does not exist. Did you mean to " + LAUNCH + " a remote " + WORKFLOW + " or a " + TOOL + "?",
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
                } else if (!cwlContentPresent && CWL.toString().equals(descriptor)) {
                    errorMessage("Entry file is not a valid CWL file.", CLIENT_ERROR);
                } else if (wdlContentPresent && WDL.toString().equals(descriptor)) {
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
                } else if (!wdlContentPresent && WDL.toString().equals(descriptor)) {
                    errorMessage("Entry file is not a valid WDL file.", CLIENT_ERROR);
                } else if (cwlContentPresent && CWL.toString().equals(descriptor)) {
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
            if (!args.contains(ENTRY)) {
                errorMessage("dockstore: missing required flag " + ENTRY, CLIENT_ERROR);
            }
            final String entry = reqVal(args, ENTRY);
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
        List<String> argsCopy = new ArrayList<>(args);
        String jsonFile = optVal(argsCopy, JSON, null);
        String yamlFile = optVal(argsCopy, "--yaml", null);
        conditionalErrorMessage((jsonFile != null) && (yamlFile != null),
                                MULTIPLE_TEST_FILE_ERROR_MESSAGE,
                                CLIENT_ERROR);
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
     */
    public WorkflowExecutionServiceApi getWorkflowExecutionServiceApi() {

        if (this.getWesRequestData() == null) {
            errorMessage("The WES request data object was not created. This must be populated to generate the client APIs", GENERIC_ERROR);
        }

        WorkflowExecutionServiceApi clientWorkflowExecutionServiceApi = new WorkflowExecutionServiceApi();

        // Uncomment this code when Swagger Codegen generates correct Java
        // for OpenApi 3.0 yaml with arrays of files
        //ApiClient wesApiClient = clientWorkflowExecutionServiceApi.getApiClient();

        // Done so we can override the Serialize method in ApiClient
        // Since Swagger Codegen does not create correct code for the
        // workflow attachment
        // Delete these next two lines when Swagger Codegen is fixed
        ApiClientExtended wesApiClient = new ApiClientExtended(wesRequestData);
        clientWorkflowExecutionServiceApi.setApiClient(wesApiClient);
        wesApiClient.getHttpClient().register(WesChecksumFilter.class);

        wesApiClient.setBasePath(wesRequestData.getUrl());

        // Add these headers to the http request. Are these needed?
        wesApiClient.addDefaultHeader("Accept", "*/*");
        wesApiClient.addDefaultHeader("Expect", "100-continue");
        // TODO Might want to override the default User Agent header with a custom one to make tracking WES requests easier.

        clientWorkflowExecutionServiceApi.setApiClient(wesApiClient);
        return clientWorkflowExecutionServiceApi;
    }

    /**
     * Attempts to launch a workflow (tools not currently supported) on a WES server
     * @param clientWorkflowExecutionServiceApi The WES API client
     * @param entry The path to the desired entry (i.e. github.com/myrepo/myworkflow:version1
     * @param inlineWorkflow Indicates that the workflow files will be inlined directly into the WES HTTP request
     * @param paramsPath Path to the parameter JSON file
     * @param filePaths Paths to any other required files for the WES execution
     * @param verbose Whether or not to print verbose info messages
     */
    abstract void wesLaunch(WorkflowExecutionServiceApi clientWorkflowExecutionServiceApi, String entry, boolean inlineWorkflow,
        String paramsPath, List<String> filePaths, boolean verbose);

    public void launchWithArgs(final String entry, final String localEntry, final String jsonRun, final String yamlRun, final String wdlOutput, final boolean ignoreChecksumFlag, final String uuid) {
        // Does nothing for tools.
    }

    /**
     *  This will attempt to retrieve the status of a workflow run
     * @param workflowId The ID of the workflow we are getting status info for
     * @param clientWorkflowExecutionServiceApi The API client
     */
    private void wesStatus(WorkflowExecutionServiceApi clientWorkflowExecutionServiceApi, final String workflowId) {
        try {
            RunStatus response = clientWorkflowExecutionServiceApi.getRunStatus(workflowId);
            ObjectMapper mapper = new ObjectMapper();
            out(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(response));
        } catch (io.openapi.wes.client.ApiException e) {
            LOG.error("Error getting brief WES run " + STATUS, e);
        } catch (JsonProcessingException e) {
            LOG.error("Unable to " + CONVERT + " WES response object to JSON", e);
        }
    }

    /**
     *  This will attempt to retrieve the status of a workflow run
     * @param workflowId The ID of the workflow we are getting status info for
     * @param clientWorkflowExecutionServiceApi The API client
     */
    private void wesRunLogs(WorkflowExecutionServiceApi clientWorkflowExecutionServiceApi, final String workflowId) {
        try {
            RunLog response = clientWorkflowExecutionServiceApi.getRunLog(workflowId);
            ObjectMapper mapper = new ObjectMapper();
            out(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(response));
        } catch (io.openapi.wes.client.ApiException e) {
            LOG.error("Error getting WES run " + LOGS, e);
        } catch (JsonProcessingException e) {
            LOG.error("Unable to " + CONVERT + " WES response object to JSON", e);
        }
    }

    /**
     * This will attempt to cancel a WES run
     * @param runId The ID of the run we are cancelling
     * @param clientWorkflowExecutionServiceApi The API client
     */
    private void wesCancel(WorkflowExecutionServiceApi clientWorkflowExecutionServiceApi, final String runId, boolean verbose) {
        try {
            RunId response = clientWorkflowExecutionServiceApi.cancelRun(runId);
            out("Cancelled run with id: " + response.toString());
        } catch (io.openapi.wes.client.ApiException e) {
            LOG.error("Error canceling WES run", e);
        }
    }

    /**
     * This will attempt to retrieve information regarding the WES server
     * @param clientWorkflowExecutionServiceApi The API client
     */
    private void wesServiceInfo(WorkflowExecutionServiceApi clientWorkflowExecutionServiceApi) {
        try {
            ServiceInfo response = clientWorkflowExecutionServiceApi.getServiceInfo();
            ObjectMapper mapper = new ObjectMapper();
            out(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(response));
        } catch (io.openapi.wes.client.ApiException e) {
            LOG.error("Error getting WES server info", e);
        } catch (JsonProcessingException e) {
            LOG.error("Unable to " + CONVERT + " WES response object to JSON", e);
        }
    }

    /**
     * This will attempt to retrieve information regarding the WES server
     * @param pageSize The number of entries to return
     * @param pageToken The returned page token from a previous call of ListRuns
     * @param clientWorkflowExecutionServiceApi The API client
     */
    private void wesListRuns(WorkflowExecutionServiceApi clientWorkflowExecutionServiceApi, int pageSize, String pageToken, boolean verbose) {
        try {
            if (verbose) {
                out(MessageFormat.format("Requesting latest {0} runs", pageSize));
            }
            RunListResponse response = clientWorkflowExecutionServiceApi.listRuns((long)pageSize, pageToken);
            ObjectMapper mapper = new ObjectMapper();
            out(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(response));
        } catch (io.openapi.wes.client.ApiException e) {
            LOG.error("Error getting WES Run List", e);
        } catch (JsonProcessingException e) {
            LOG.error("Unable to " + CONVERT + " WES response object to JSON", e);
        }
    }

    /**
     * Given the parsed command object, determine if we are to print help commands
     * @param wesCommandParser Parse commands
     * @return true if help was displayed, false otherwise
     */
    private boolean displayWesHelpWhenNecessary(WesCommandParser wesCommandParser) {
        // Print the main help section if 'dockstore workflow wes' was the command
        if (wesCommandParser.wesMain.isHelp() || wesCommandParser.jCommander.getParsedCommand() == null) {
            wesHelp();
            return true;
        } else if (wesCommandParser.commandLaunch.isHelp()) {
            wesLaunchHelp();
            return true;
        } else if (wesCommandParser.commandStatus.isHelp()) {
            wesStatusHelp();
            return true;
        } else if (wesCommandParser.commandRunLogs.isHelp()) {
            wesRunLogsHelp();
            return true;
        } else if (wesCommandParser.commandCancel.isHelp()) {
            wesCancelHelp();
            return true;
        } else if (wesCommandParser.commandServiceInfo.isHelp()) {
            wesServiceInfoHelp();
            return true;
        } else if (wesCommandParser.commandRunList.isHelp()) {
            wesRunListHelp();
            return true;
        }

        return false;
    }

    /**
     * Processes Workflow Execution Schema (WES) commands.
     *
     * @param args Arguments entered into the CLI
     */
    private void processWesCommands(final List<String> args) {
        WesCommandParser wesCommandParser = new WesCommandParser();
        // JCommander throws a parameter exception for invalid parameters. Catch this and print the error cleanly.
        try {
            wesCommandParser.jCommander.parse(args.toArray(new String[0]));
        } catch (MissingCommandException e) {
            // This catch is called when JCommander parse doesn't understand any command. This is for incorrect commands of the form:
            // dockstore workflow INCORRECT_COMMAND
            displayJCommanderSuggestions(wesCommandParser.jCommander, e.getJCommander().getParsedCommand(), args.get(0), WORKFLOW + " " + WES);
            return;
        } catch (ParameterException e) {
            if (wasErrorDueToUnknownParameter(e.getMessage())) {
                // This is for when JCommander parse understands part of the command. This is for incorrect commands of the form:
                // dockstore workflow CORRECT_COMMAND INCORRECT_COMMAND
                String incorrectCommand = getUnknownParameter(e.getMessage());
                // get command after the commands that were successfully read
                displayJCommanderSuggestions(wesCommandParser.jCommander, e.getJCommander().getParsedCommand(), incorrectCommand, join(" ", WORKFLOW, WES, e.getJCommander().getParsedCommand()));
                return;
            }
            errorMessage(e.getMessage(), CLIENT_ERROR);
        }

        final boolean helpDisplayed = displayWesHelpWhenNecessary(wesCommandParser);
        if (!helpDisplayed) {

            // Get any credentials that will be used in the following WES request
            final WesRequestData requestData = this.aggregateWesRequestData(wesCommandParser);
            setWesRequestData(requestData);
            WorkflowExecutionServiceApi clientWorkflowExecutionServiceApi = getWorkflowExecutionServiceApi();

            // All Wes commands are parsed as subclasses of WesMain. We need to extract which object was created to parse with jCommander
            // so we can get a global attribute.
            WesCommandParser.WesMain parsedCommand = (WesCommandParser.WesMain) wesCommandParser.jCommander.getCommands()
                .get(wesCommandParser.jCommander.getParsedCommand())
                .getObjects().get(0);

            // Print the WES URL and parsed credentials type
            if (parsedCommand.isVerbose()) {
                out("WES URL: " + wesRequestData.getUrl());
                out("Credentials type: " + wesRequestData.getCredentialType());
            }

            // Depending on the desired WES request, parse input parameters from the command line
            switch (wesCommandParser.jCommander.getParsedCommand()) {
            case LAUNCH:
                wesLaunch(clientWorkflowExecutionServiceApi,
                    wesCommandParser.commandLaunch.getEntry(),
                    wesCommandParser.commandLaunch.getInlineWorkflow(),
                    wesCommandParser.commandLaunch.getJson(),
                    wesCommandParser.commandLaunch.getAttachments(),
                    wesCommandParser.commandLaunch.isVerbose());
                break;
            case STATUS:
                wesStatus(clientWorkflowExecutionServiceApi,
                    wesCommandParser.commandStatus.getId());
                break;
            case LOGS:
                wesRunLogs(clientWorkflowExecutionServiceApi,
                    wesCommandParser.commandRunLogs.getId());
                break;
            case CANCEL:
                wesCancel(clientWorkflowExecutionServiceApi,
                    wesCommandParser.commandCancel.getId(),
                    wesCommandParser.commandCancel.isVerbose());
                break;
            case SERVICE_INFO:
                wesServiceInfo(clientWorkflowExecutionServiceApi);
                break;
            case LIST:
                wesListRuns(clientWorkflowExecutionServiceApi,
                    wesCommandParser.commandRunList.getPageSize(),
                    wesCommandParser.commandRunList.getPageToken(),
                    wesCommandParser.commandRunList.isVerbose());
                break;
            default:
                errorMessage("Unknown WES command.", CLIENT_ERROR);
                wesHelp();
            }
        }
    }

    /**
     * This will aggregate the WES request URI and credentials into a single object for use down the line
     * @param wesCommandParser The parsed command line arguments
     */
    public WesRequestData aggregateWesRequestData(final WesCommandParser wesCommandParser) {

        // Get the config file to see if credentials are there
        INIConfiguration config = Utilities.parseConfig(this.getConfigFile());
        SubnodeConfiguration configSubNode = config.getSection("WES");

        // Obtain the WES command object
        JCommander parsedCommand = wesCommandParser.jCommander
            .findCommandByAlias(wesCommandParser.jCommander.getParsedCommand());
        WesCommandParser.WesMain command = parsedCommand == null ?  new WesCommandParser.WesMain() : (WesCommandParser.WesMain) parsedCommand.getObjects().get(0);

        // Attempt to find the WES URL
        final String wesEndpointUrl = ObjectUtils.firstNonNull(
            command.getWesUrl(),
            configSubNode.getString(WesConfigOptions.URL_KEY));

        // Determine the authorization method used by the user
        final String authType = configSubNode.getString(WesConfigOptions.AUTHORIZATION_TYPE_KEY);

        // The auth value is either a bearer token or AWS profile
        final String authValue = configSubNode.getString(WesConfigOptions.AUTHORIZATION_VALUE_KEY);

        // Depending on the endpoint (AWS/non-AWS) we need to look for a different set of credentials
        final boolean isAwsWes = "aws".equals(authType);
        if (isAwsWes) {

            try {
                // Parse AWS credentials from the provided config file. If the config file path is null, we can read the config file from
                // the default home/.aws/credentials file.
                final String profileToRead = authValue != null ? authValue : WesConfigOptions.AWS_DEFAULT_PROFILE_VALUE;
                final ProfileCredentialsProvider credentialsProvider = ProfileCredentialsProvider.builder().profileName(profileToRead).build();
                final Region region = DefaultAwsRegionProviderChain.builder().profileName(profileToRead).build().getRegion();

                // Build and return the request data
                return new WesRequestData(wesEndpointUrl,
                    credentialsProvider.resolveCredentials(),
                        region.toString());

            } catch (IllegalArgumentException | SdkClientException e) {
                // Some potential reasons for this exception are:
                // 1) The path to the config file is invalid or 2) The profile doesn't exist or 3) The config file is malformed
                errorMessage(e.getMessage(), CLIENT_ERROR);
            }

            // Let the WesRequestData class handle missing credentials
            return new WesRequestData(wesEndpointUrl, null, null, null);
        } else {
            return new WesRequestData(wesEndpointUrl, authValue);
        }
    }

    /**
     * Prints a warning if Docker isn't running. Docker is not always needed. If a workflow or tool uses Docker and
     * it is not running, it fails with a cryptic error. This should make the problem more obvious.
     */
    void checkIfDockerRunning() {
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();

        try (DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder().dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig()).build();
                DockerClient instance = DockerClientImpl.getInstance(config, httpClient)) {
            InfoCmd infoCmd = instance.infoCmd(); // attempt to get information about docker
            infoCmd.exec();
        } catch (Exception e) {  // couldn't access docker, this library is wonderfully non-specific about exceptions
            String type = this.getEntryType().toLowerCase(); // TOOL or WORKFLOW
            out("WARNING: Docker is not running. If this " + type + " uses Docker, it will fail.");
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
            if (args.contains("--local-entry") && args.contains(ENTRY)) {
                errorMessage("You can only use one of --local-entry and " + ENTRY + " at a time. Please use " + HELP + " for more information.",
                        CLIENT_ERROR);
            } else if (args.contains("--local-entry")) {
                final String descriptor = optVal(args, "--descriptor", null);
                final String localFilePath = reqVal(args, "--local-entry");
                this.isLocalEntry = true;
                preValidateLaunchArguments(args);
                checkIfDockerRunning();
                checkEntryFile(localFilePath, args, descriptor);
            } else {
                if (!args.contains(ENTRY)) {
                    errorMessage("dockstore: missing required flag " + ENTRY, CLIENT_ERROR);
                }
                this.isLocalEntry = false;
                this.ignoreChecksums = args.contains("--ignore-checksums");

                preValidateLaunchArguments(args);
                checkIfDockerRunning();

                final String descriptor = optVal(args, "--descriptor", CWL.toString()).toUpperCase();
                if (CWL.toString().equals(descriptor)) {
                    try {
                        String entry = reqVal(args, ENTRY);
                        launchCwl(entry, args, false);
                    } catch (ApiException e) {
                        exceptionMessage(e, "API error launching workflow. Did you mean to use --local-entry instead of --entry?",
                                Client.API_ERROR);
                    } catch (IOException e) {
                        exceptionMessage(e, "IO error launching workflow. Did you mean to use --local-entry instead of --entry?",
                                Client.IO_ERROR);
                    }
                } else if (WDL.toString().equals(descriptor)) {
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
        String jsonRun = optVal(args, JSON, null);
        final String uuid = optVal(args, "--uuid", null);
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
        final String entry = reqVal(args, ENTRY);
        launchWdl(entry, args, isALocalEntry);
    }

    private void launchWdl(String entry, final List<String> args, boolean isALocalEntry) throws ApiException {
        final String yamlRun = optVal(args, "--yaml", null);
        String jsonRun = optVal(args, JSON, null);
        final String wdlOutputTarget = optVal(args, "--wdl-output-target", null);
        final String uuid = optVal(args, "--uuid", null);
        WDLClient client = new WDLClient(this);
        client.launch(entry, isALocalEntry, yamlRun, jsonRun, wdlOutputTarget, uuid);
    }

    private void launchNextflow(String entry, final List<String> args, boolean isALocalEntry) throws ApiException {
        final String json = reqVal(args, JSON);
        final String uuid = optVal(args, "--uuid", null);
        NextflowClient client = new NextflowClient(this);
        client.launch(entry, isALocalEntry, null, json, null, uuid);
    }

    private String convertEntry2Json(List<String> args, final boolean json) throws ApiException, IOException {
        final String entry = reqVal(args, ENTRY);
        final String descriptor = optVal(args, "--descriptor", CWL.toString()).toUpperCase();
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
        out(join(" ", "Usage: dockstore", getEntryType().toLowerCase(), WES, HELP));
        out(join(" ", "       dockstore", getEntryType().toLowerCase(), WES, LAUNCH, "[parameters]"));
        out(join(" ", "       dockstore", getEntryType().toLowerCase(), WES, STATUS, "[parameters]"));
        out(join(" ", "       dockstore", getEntryType().toLowerCase(), WES, LOGS, "[parameters]"));
        out(join(" ", "       dockstore", getEntryType().toLowerCase(), WES, CANCEL, "[parameters]"));
        out(join(" ", "       dockstore", getEntryType().toLowerCase(), WES, SERVICE_INFO, "[parameters]"));
        out(join(" ", "       dockstore", getEntryType().toLowerCase(), WES, "list [parameters]"));
        out("");
        out("Description:");
        out(" Sends a request to a Workflow Execution Service (WES) endpoint.");
        printWesHelpFooter();
        printHelpFooter();
    }

    private void printWesLaunchHelpBody() {
        out("");
        out("Description:");
        out("  Launch an entry on a WES endpoint.");
        out("");
        out("Required parameters:");
        out("  " + ENTRY + " <entry>                     Complete entry path in Dockstore (ex. quay.io/collaboratory/seqware-bwa-workflow:develop)");
        out("");
        out("Optional parameters:");
        out("  " + JSON + " <json file>                  JSON parameter file for the WES run. This may be reference an attached file");
        out("  " + ATTACH + " <path>, -a <path>          A list of paths to files that should be included in the WES request. (ex. -a <path1> <path2> OR -a <path1> -a <path2>)");
        out("  " + INLINE_WORKFLOW + "                   Inlines workflow files contents directly into the WES HTTP request. This is required for some WES server implementations.");
        out("");
    }

    private void wesLaunchHelp() {
        printHelpHeader();
        out(join(" ", "Usage: dockstore", getEntryType().toLowerCase(), WES, LAUNCH, HELP));
        out(join(" ", "       dockstore", getEntryType().toLowerCase(), WES, LAUNCH, "[parameters]"));
        printWesLaunchHelpBody();
        printWesHelpFooter();
        printHelpFooter();
    }

    private void wesStatusHelp() {
        printHelpHeader();
        out(join(" ", "Usage: dockstore", getEntryType().toLowerCase(), WES, STATUS, HELP));
        out(join(" ", "       dockstore", getEntryType().toLowerCase(), WES, STATUS, "[parameters]"));
        out("");
        out("Description:");
        out("  Status, gets the " + STATUS + " of a " + getEntryType() + ".");
        out("Required Parameters:");
        out("  " + ID + " <id>                           Id of a run at the WES endpoint, e.g. id returned from the " + LAUNCH + " command");
        out("");
        printWesHelpFooter();
        printHelpFooter();
    }

    private void wesRunLogsHelp() {
        printHelpHeader();
        out(join(" ", "Usage: dockstore", getEntryType().toLowerCase(), WES, LOGS, HELP));
        out(join(" ", "       dockstore", getEntryType().toLowerCase(), WES, LOGS, "[parameters]"));
        out("");
        out("Description:");
        out("  Logs, gets the " + VERBOSE + " run " + LOGS + " of a " + getEntryType() + ".");
        out("Required Parameters:");
        out("  " + ID + " <id>                           Id of a run at the WES endpoint, e.g. id returned from the " + LAUNCH + " command");
        out("");
        printWesHelpFooter();
        printHelpFooter();
    }

    private void wesCancelHelp() {
        printHelpHeader();
        out(join(" ", "Usage: dockstore", getEntryType().toLowerCase(), WES, CANCEL, HELP));
        out(join(" ", "       dockstore", getEntryType().toLowerCase(), WES, CANCEL, "[parameters]"));
        out("");
        out("Description:");
        out("  Cancels a " + getEntryType() + ".");
        out("Required Parameters:");
        out("  " + ID + " <id>                           Id of a run at the WES endpoint, e.g. id returned from the " + LAUNCH + " command");
        out("");
        printWesHelpFooter();
        printHelpFooter();
    }

    private void wesServiceInfoHelp() {
        printHelpHeader();
        out(join(" ", "Usage: dockstore", getEntryType().toLowerCase(), WES, SERVICE_INFO, HELP));
        out(join(" ", "       dockstore", getEntryType().toLowerCase(), WES, SERVICE_INFO));
        out("");
        out("Description:");
        out("  Returns descriptive information of the provided WES server. ");
        printWesHelpFooter();
        printHelpFooter();
    }

    private void wesRunListHelp() {
        printHelpHeader();
        out(join(" ", "Usage: dockstore", getEntryType().toLowerCase(), WES, "list", HELP));
        out(join(" ", "       dockstore", getEntryType().toLowerCase(), WES, "list"));
        out("");
        out("Description:");
        out("  Returns information about past runs. ");
        out("Optional Parameters:");
        out("  " + COUNT + "                           The number of runs to list.");
        out("  " + PAGE_TOKEN + "                      A page token provided from a previous list of runs.");
        out("");
        printWesHelpFooter();
        printHelpFooter();
    }

    private void printWesHelpFooter() {
        out("Global Optional Parameters:");
        out("  " + WES_URL + " <WES URL>                 URL where the WES request should be sent, e.g. 'http://localhost:8080/ga4gh/wes/v1'");
        out("");
        out("NOTE: Currently only WDL workflows are supported for WES requests. Further language support is under development.");
    }

    protected abstract void publishHelp();

    private void starHelp() {
        printHelpHeader();
        out(join(" ", "Usage: dockstore", getEntryType().toLowerCase(), STAR, HELP));
        out(join(" ", "       dockstore", getEntryType().toLowerCase(), STAR));
        out(join(" ", "       dockstore", getEntryType().toLowerCase(), STAR, "[parameters]"));
        out(join(" ", "       dockstore", getEntryType().toLowerCase(), STAR, "--unstar [parameters]"));
        out("");
        out("Description:");
        out("  Star/unstar a registered " + getEntryType() + ".");
        out("  No arguments will list the current and potential " + getEntryType() + "s to share.");
        out("Required Parameters:");
        out("  " + ENTRY + " <" + getEntryType() + ">             Complete " + getEntryType()
                + " path in Dockstore (ex. quay.io/collaboratory/seqware-bwa-workflow)");
        printHelpFooter();
    }

    private void listHelp() {
        printHelpHeader();
        out("Usage: dockstore " + getEntryType().toLowerCase() + " list " + HELP);
        out("       dockstore " + getEntryType().toLowerCase() + " list");
        out("");
        out("Description:");
        out("  lists all the " + getEntryType() + " published by the user.");
        printHelpFooter();
    }

    private void labelHelp() {
        printHelpHeader();
        out(join(" ", "Usage: dockstore", getEntryType().toLowerCase(), LABEL, HELP));
        out(join(" ", "       dockstore", getEntryType().toLowerCase(), LABEL, "[parameters]"));
        out("");
        out("Description:");
        out("  Add or remove labels from a given Dockstore " + getEntryType());
        out("");
        out("Required Parameters:");
        out("  " + ENTRY + " <entry>                             Complete " + getEntryType() + " path in Dockstore");
        out("");
        out("Optional Parameters:");
        out("  --add <label> (--add <label>)               Add given label(s)");
        out("  --remove <label> (--remove <label>)         Remove given label(s)");
        printHelpFooter();
    }

    protected void testParameterHelp() {
        printHelpHeader();
        out(join(" ", "Usage: dockstore", getEntryType().toLowerCase(), TEST_PARAMETER, HELP));
        out(join(" ", "       dockstore", getEntryType().toLowerCase(), TEST_PARAMETER, "[parameters]"));
        out("");
        out("Description:");
        out("  Add or remove test parameter files from a given Dockstore " + getEntryType() + " version");
        out("");
        out("Required Parameters:");
        out("  " + ENTRY + " <entry>                                                          Complete " + getEntryType()
                + " path in Dockstore (ex. quay.io/collaboratory/seqware-bwa-workflow)");
        out("  " + VERSION + " <version>                                                      " + getEntryType() + " version name");
        if (getEntryType().equalsIgnoreCase(TOOL)) {
            out("  --descriptor-type <descriptor-type>                                      " + CWL + "/" + WDL);
        }
        out("");
        out("Optional Parameters:");
        out("  --add <test parameter file> (--add <test parameter file>)               Path in Git repository of test parameter file(s) to add");
        out("  --remove <test parameter file> (--remove <test parameter file>)         Path in Git repository of test parameter file(s) to remove");
        printHelpFooter();
    }

    private void infoHelp() {
        printHelpHeader();
        out("Usage: dockstore " + getEntryType().toLowerCase() + " info " + HELP);
        out("       dockstore " + getEntryType().toLowerCase() + " info [parameters]");
        out("");
        out("Description:");
        out("  Get information related to a published " + getEntryType() + ".");
        out("");
        out("Required Parameters:");
        out("  " + ENTRY + " <entry>     The complete " + getEntryType()
                + " path in Dockstore (ex. quay.io/collaboratory/seqware-bwa-workflow)");
        printHelpFooter();
    }

    private void descriptorHelp(DescriptorLanguage descriptorType) {
        printHelpHeader();
        out("Usage: dockstore " + getEntryType().toLowerCase() + " " + descriptorType + " " + HELP);
        out("       dockstore " + getEntryType().toLowerCase() + " " + descriptorType + " [parameters]");
        out("");
        out("Description:");
        out("  Grab a " + descriptorType.toString().toUpperCase() + " document for a particular entry.");
        out("");
        out("Required parameters:");
        out("  " + ENTRY + " <entry>              Complete " + getEntryType()
                + " path in Dockstore (ex. quay.io/collaboratory/seqware-bwa-workflow:develop)");
        printHelpFooter();
    }

    private void refreshHelp() {
        printHelpHeader();
        out(join(" ", "Usage: dockstore", getEntryType().toLowerCase(), REFRESH, HELP));
        out(join(" ", "       dockstore", getEntryType().toLowerCase(), REFRESH));
        out(join(" ", "       dockstore", getEntryType().toLowerCase(), REFRESH, "[parameters]"));
        out("");
        out("Description:");
        out("  Refresh an individual " + getEntryType() + " or all your " + getEntryType() + ".");
        out("");
        out("Optional Parameters:");
        out("  " + ENTRY + " <entry>         Complete tool path in Dockstore (ex. quay.io/collaboratory/seqware-bwa-workflow)");
        printHelpFooter();
    }

    private void searchHelp() {
        printHelpHeader();
        out(join(" ", "Usage: dockstore", getEntryType().toLowerCase(), SEARCH, HELP));
        out(join(" ", "       dockstore", getEntryType().toLowerCase(), SEARCH, "[parameters]"));
        out("");
        out("Description:");
        out("  Search for published " + getEntryType() + " on Dockstore.");
        out("");
        out("Required Parameters:");
        out("  --pattern <pattern>         Pattern to " + SEARCH + " Dockstore with");
        printHelpFooter();
    }

    private void cwl2yamlHelp() {
        printHelpHeader();
        out(join(" ", "Usage: dockstore", getEntryType().toLowerCase(), CONVERT, HELP));
        out(join(" ", "       dockstore", getEntryType().toLowerCase(), CONVERT, CWL_2_YAML, "[parameters]"));
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
        out(join(" ", "Usage: dockstore", getEntryType().toLowerCase(), CONVERT, HELP));
        out(join(" ", "       dockstore", getEntryType().toLowerCase(), CONVERT, CWL_2_JSON, "[parameters]"));
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
        out(join(" ", "Usage: dockstore", getEntryType().toLowerCase(), CONVERT, HELP));
        out(join(" ", "       dockstore", getEntryType().toLowerCase(), CONVERT, WDL_2_JSON, "[parameters]"));
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
        out(join(" ", "Usage: dockstore", getEntryType().toLowerCase(), CONVERT, HELP));
        out(join(" ", "       dockstore", getEntryType().toLowerCase(), CONVERT, CWL_2_JSON, "[parameters]"));
        out(join(" ", "       dockstore", getEntryType().toLowerCase(), CONVERT, CWL_2_YAML, "[parameters]"));
        out(join(" ", "       dockstore", getEntryType().toLowerCase(), CONVERT, WDL_2_JSON, "[parameters]"));
        out(join(" ", "       dockstore", getEntryType().toLowerCase(), CONVERT, "entry2json [parameters]"));
        out("");
        out("Description:");
        out("  They allow you to " + CONVERT + " between file representations.");
        printHelpFooter();
    }

    private void entry2jsonHelp() {
        printHelpHeader();
        out(join(" ", "Usage: dockstore", getEntryType().toLowerCase(), CONVERT, ENTRY_2_JSON, HELP));
        out(join(" ", "       dockstore", getEntryType().toLowerCase(), CONVERT, ENTRY_2_JSON, "[parameters]"));
        out("");
        out("Description:");
        out("  Spit out a json run file for a given cwl document.");
        out("");
        out("Required parameters:");
        out("  " + ENTRY + " <entry>                Complete " + getEntryType().toLowerCase()
                + " path in Dockstore (ex. quay.io/collaboratory/seqware-bwa-workflow:develop)");
        out("  --descriptor <descriptor>      Type of descriptor language used. Defaults to cwl");
        printHelpFooter();
    }

    private void downloadHelp() {
        printHelpHeader();
        out(join(" ", "Usage: dockstore", getEntryType().toLowerCase(), DOWNLOAD, HELP));
        out(join(" ", "       dockstore", getEntryType().toLowerCase(), DOWNLOAD, "[parameters]"));
        out("");
        out("Description:");
        out("  Download an entry to the working directory.");
        out("");
        out("Required parameters:");
        out("  " + ENTRY + " <entry>          Complete entry path in Dockstore (ex. quay.io/collaboratory/seqware-bwa-workflow:develop)");
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
        out("  " + ENTRY + " <entry>                     Complete entry path in Dockstore (ex. quay.io/collaboratory/seqware-bwa-workflow:develop)");
        if (!(this instanceof CheckerClient)) {
            out("   OR");
            out("  --local-entry <local-entry>         Allows you to specify a full path to a local descriptor instead of an entry path");
        }
        out("");
        out("Optional parameters:");
        out("  " + JSON + " <json file>                  Parameters to the entry in Dockstore, one map for one run, an array of maps for multiple runs");
        out("  --yaml <yaml file>                  Parameters to the entry in Dockstore, one map for one run, an array of maps for multiple runs");
        out("  --descriptor <descriptor type>      Descriptor type used to " + LAUNCH + " " + WORKFLOW + ". Defaults to " + CWL.toString());
        if (!(this instanceof CheckerClient)) {
            out("  --local-entry                       Allows you to specify a full path to a local descriptor for " + ENTRY + " instead of an entry path");
        }
        out("  --wdl-output-target                 Allows you to specify a remote path to provision output files to ex: s3://oicr.temp/testing-launcher/");
        out("  --uuid                              Allows you to specify a uuid for 3rd party notifications");
        out("  --ignore-checksums                  Allows you to ignore validating checksums of each downloaded descriptor");
        out("");
    }

    protected void launchHelp() {
        printHelpHeader();
        out("Usage: dockstore " + getEntryType().toLowerCase() + " launch " + HELP);
        out("       dockstore " + getEntryType().toLowerCase() + " launch [parameters]");
        printLaunchHelpBody();
        printHelpFooter();
    }

    private void verifyHelp() {
        printHelpHeader();
        out(join(" ", "Usage: dockstore", getEntryType().toLowerCase(), VERIFY, HELP));
        out(join(" ", "       dockstore", getEntryType().toLowerCase(), VERIFY, "[parameters]"));
        out(join(" ", "       dockstore", getEntryType().toLowerCase(), VERIFY, "--unverify [parameters]"));
        out("");
        out("Description:");
        out("  Verify/unverify a version.");
        out("");
        out("Required parameters:");
        out("  " + ENTRY + " <entry>                              Complete entry path in Dockstore (ex. quay.io/collaboratory/seqware-bwa-workflow)");
        out("  " + VERSION + " <version>                          Version name");
        out("");
        out("Optional Parameters:");
        out("  --verified-source <verified-source>          Source of verification (Required to " + VERIFY + ").");
        printHelpFooter();
    }

    protected void printAdminHelp() {
        out("Admin Only Commands:");
        out("");
        out("  " + VERIFY + "           :  " + VERIFY + "/unverify a version");
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
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        try (FileInputStream fileInputStream = FileUtils.openInputStream(new File(yamlRun))) {
            Map<String, Object> map = yaml.load(fileInputStream);
            JSONObject jsonObject = new JSONObject(map);
            return jsonObject.toString();
        }
    }
}
