package io.dockstore.client.cli;

import static io.dockstore.client.cli.ArgumentUtility.DOWNLOAD;
import static io.dockstore.client.cli.ArgumentUtility.LAUNCH;
import static io.dockstore.client.cli.ArgumentUtility.containsHelpRequest;
import static io.dockstore.client.cli.ArgumentUtility.errorMessage;
import static io.dockstore.client.cli.ArgumentUtility.exceptionMessage;
import static io.dockstore.client.cli.ArgumentUtility.invalid;
import static io.dockstore.client.cli.ArgumentUtility.optVal;
import static io.dockstore.client.cli.ArgumentUtility.out;
import static io.dockstore.client.cli.ArgumentUtility.printFlagHelp;
import static io.dockstore.client.cli.ArgumentUtility.printHelpFooter;
import static io.dockstore.client.cli.ArgumentUtility.printHelpHeader;
import static io.dockstore.client.cli.ArgumentUtility.printLineBreak;
import static io.dockstore.client.cli.ArgumentUtility.printUsageHelp;
import static io.dockstore.client.cli.ArgumentUtility.reqVal;
import static io.dockstore.client.cli.Client.CHECKER;
import static io.dockstore.client.cli.Client.HELP;
import static io.dockstore.client.cli.Client.TOOL;
import static io.dockstore.client.cli.Client.VERSION;
import static io.dockstore.client.cli.Client.WORKFLOW;
import static io.dockstore.client.cli.Client.getGeneralFlags;
import static io.dockstore.client.cli.nested.WesCommandParser.ENTRY;
import static io.dockstore.common.DescriptorLanguage.CWL;
import static io.dockstore.common.DescriptorLanguage.WDL;
import static java.lang.String.join;

import io.dockstore.client.cli.nested.WorkflowClient;
import io.dockstore.openapi.client.ApiException;
import io.dockstore.openapi.client.api.UsersApi;
import io.dockstore.openapi.client.api.WorkflowsApi;
import io.dockstore.openapi.client.model.Entry;
import io.dockstore.openapi.client.model.Workflow;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This implements all operations on the CLI that are specific to checkers
 * @author aduncan
 */
public class CheckerClient extends WorkflowClient {

    public static final String ADD = "add";
    public static final String UPDATE = "update";
    public static final String UPDATE_VERSION = "update_version";
    private static final Logger LOG = LoggerFactory.getLogger(CheckerClient.class);

    public CheckerClient(WorkflowsApi workflowApi, UsersApi usersApi, Client client, boolean isAdmin) {
        super(workflowApi, usersApi, client, isAdmin);
    }

    // If you add a command, please add it to possibleCommands
    @Override
    public void printGeneralHelp() {
        printHelpHeader();
        printUsageHelp(getEntryType().toLowerCase());

        // Checker client help
        out("Commands:");
        out("");
        out(join(" ", "  " + DOWNLOAD + "             :  Downloads all files associated with a", CHECKER, WORKFLOW + "."));
        out("");
        out(join(" ", "  " + LAUNCH + "               :  Launch a", CHECKER, WORKFLOW, "locally."));
        out("");
        out(join(" ", "  " + ADD + "                  :  Adds a", CHECKER, WORKFLOW, "to and existing", TOOL + "/" + WORKFLOW + "."));
        out("");
        out(join(" ", "  " + UPDATE + "               :  Updates an existing", CHECKER, WORKFLOW + "."));
        out("");
        out(join(" ", "  " + UPDATE_VERSION + "       :  Updates a specific version of an existing", CHECKER, WORKFLOW + "."));
        out("");
        out(join(" ", "  " + TEST_PARAMETER + "       :  Add/Remove test parameter files for a", CHECKER, WORKFLOW, "version."));
        out("");

        if (isAdmin) {
            printAdminHelp();
        }

        printLineBreak();
        printFlagHelp();
        printHelpFooter();
    }

    @Override
    public String getEntryType() {
        return "Checker";
    }

    @Override
    public boolean processEntryCommands(List<String> args, String activeCommand) throws ApiException {
        if (null != activeCommand) {
            switch (activeCommand) {
            case ADD:
                addChecker(args);
                break;
            case UPDATE:
                updateChecker(args);
                break;
            case DOWNLOAD:
                downloadChecker(args);
                break;
            case LAUNCH:
                launchChecker(args);
                break;
            case TEST_PARAMETER:
                testParameterChecker(args);
                break;
            case UPDATE_VERSION:
                updateVersionChecker(args);
                break;
            default:
                List<String> possibleCommands = new ArrayList<>();
                possibleCommands.addAll(Arrays.asList(ADD, UPDATE, DOWNLOAD, LAUNCH, TEST_PARAMETER, UPDATE_VERSION));
                possibleCommands.addAll(getGeneralFlags());
                invalid(getEntryType().toLowerCase(), activeCommand, possibleCommands);
                return true;
            }
            return true;
        }
        return false;
    }

    private void addChecker(List<String> args) {
        if (containsHelpRequest(args) || args.isEmpty()) {
            addCheckerHelp();
        } else {
            // Retrieve arguments
            String entryPath = reqVal(args, ENTRY);
            final String descriptorType = optVal(args, "--descriptor-type", CWL.toString()).toUpperCase();
            String descriptorPath = reqVal(args, "--descriptor-path");
            String inputParameterPath = optVal(args, "--input-parameter-path", null);

            // Check that descriptor type is valid
            if (!CWL.toString().equals(descriptorType) && !WDL.toString().equals(descriptorType)) {
                errorMessage("The given descriptor type " + descriptorType + " is not valid.",
                    Client.CLIENT_ERROR);
            }

            // Check that descriptor path is valid
            if (!descriptorPath.startsWith("/")) {
                errorMessage("Descriptor paths must be absolute paths.",
                    Client.CLIENT_ERROR);
            }

            // Check that input parameter path is valid
            if (inputParameterPath != null && !inputParameterPath.startsWith("/")) {
                errorMessage("Input parameter path paths must be absolute paths.",
                    Client.CLIENT_ERROR);
            }

            // Get entry from path
            Entry entry = getEntryFromPath(entryPath);

            // Register the checker workflow
            if (entry != null) {
                try {
                    workflowsApi.registerCheckerWorkflow(entry.getId(), descriptorPath, descriptorType, inputParameterPath);
                    out(join(" ", "A", CHECKER, WORKFLOW, "has been successfully added to entry with path", entryPath));
                } catch (ApiException ex) {
                    exceptionMessage(ex, join(" ", "There was a problem registering the", CHECKER, WORKFLOW + "."), Client.API_ERROR);
                }
            }
        }
    }

    private void addCheckerHelp() {
        printHelpHeader();
        out("Usage: dockstore " + getEntryType().toLowerCase() + " add " + HELP);
        out("       dockstore " + getEntryType().toLowerCase() + " add [parameters]");
        out("");
        out("Description:");
        out(join(" ", "  Add a", CHECKER, WORKFLOW, "to an existing", TOOL, "or", WORKFLOW + "."));
        out("");
        out("Required Parameters:");
        out("  " + ENTRY + " <entry>                                                          Complete entry path in the Dockstore (ex. quay.io/collaboratory/seqware-bwa-workflow)");
        out("  --descriptor-type <descriptor-type>                                      " + CWL + "/" + WDL + ", defaults to " + CWL);
        out("  --descriptor-path <descriptor-path>                                      Path to the main descriptor file.");
        out("");
        out("Optional Parameters:");
        out("  --input-parameter-path <input parameter path>                            Path to the input parameter path, defaults to that of the entry.");
        printHelpFooter();
    }

    private void updateChecker(List<String> args) {
        if (containsHelpRequest(args) || args.isEmpty()) {
            updateCheckerHelp();
        } else {
            // Retrieve arguments
            String entryPath = reqVal(args, ENTRY);

            // Get entry from path
            Entry entry = getEntryFromPath(entryPath);

            // Retrieve the checker workflow
            Workflow checkerWorkflow = getCheckerWorkflowFromEntry(entry, true);

            // Update the checker workflow
            if (entry != null && checkerWorkflow != null) {
                String descriptorPath = optVal(args, "--default-descriptor-path", checkerWorkflow.getWorkflowPath());
                String inputParameterPath = optVal(args, "--default-test-parameter-path", checkerWorkflow.getDefaultTestParameterFilePath());

                // Check that descriptor path is valid
                if (!descriptorPath.startsWith("/")) {
                    errorMessage("Descriptor paths must be absolute paths.",
                        Client.CLIENT_ERROR);
                }

                // Check that input parameter path is valid
                if (inputParameterPath != null && !inputParameterPath.startsWith("/")) {
                    errorMessage("Input parameter path paths must be absolute paths.",
                        Client.CLIENT_ERROR);
                }

                // Update fields
                checkerWorkflow.setWorkflowPath(descriptorPath);
                checkerWorkflow.setDefaultTestParameterFilePath(inputParameterPath);

                try {
                    // Update the checker workflow
                    workflowsApi.updateWorkflow(checkerWorkflow.getId(), checkerWorkflow);

                    // Refresh the checker workflow
                    workflowsApi.refresh1(checkerWorkflow.getId(), true);
                    out(join(" ", "The", CHECKER, WORKFLOW, "has been updated."));
                } catch (ApiException ex) {
                    exceptionMessage(ex, join(" ", "There was a problem updating the", CHECKER, WORKFLOW + "."), Client.API_ERROR);
                }
            }
        }
    }


    private void updateCheckerHelp() {
        printHelpHeader();
        out("Usage: dockstore " + getEntryType().toLowerCase() + " update " + HELP);
        out("       dockstore " + getEntryType().toLowerCase() + " update [parameters]");
        out("");
        out("Description:");
        out(join(" ", "  Update an existing", CHECKER, WORKFLOW, "associated with an entry."));
        out("");
        out("Required Parameters:");
        out("  " + ENTRY + " <entry>                                                          Complete entry path in the Dockstore (ex. quay.io/collaboratory/seqware-bwa-workflow:master)");
        out("");
        out("Optional Parameters:");
        out("  --default-test-parameter-path <input parameter path>                     Path to the input parameter path, defaults to that of the entry.");
        out("  --default-descriptor-path <descriptor-path>                              Path to the main descriptor file.");
        printHelpFooter();
    }

    private void downloadChecker(List<String> args) {
        if (containsHelpRequest(args) || args.isEmpty()) {
            downloadCheckerHelp();
        } else {
            // Get current directory
            String currentDirectory = Paths.get(".").toAbsolutePath().normalize().toString();

            // Retrieve arguments
            String entryPath = reqVal(args, ENTRY);
            String version = reqVal(args, VERSION);
            final boolean unzip = !args.contains("--zip");

            // Get entry from path
            Entry entry = getDockstoreEntry(entryPath);

            // Get checker workflow
            Workflow checkerWorkflow = getCheckerWorkflowFromEntry(entry, false);

            // Download files
            if (entry != null && checkerWorkflow != null) {
                try {
                    downloadTargetEntry(entryPath + ":" + version, null, unzip);
                    out("Files have been successfully downloaded to the current directory.");
                } catch (IOException ex) {
                    exceptionMessage(ex, "Problems downloading files to " + currentDirectory, Client.IO_ERROR);
                } catch (ApiException ex) {
                    exceptionMessage(ex, "Problems downloading files to " + currentDirectory, Client.API_ERROR);
                }
            }
        }
    }


    private void downloadCheckerHelp() {
        printHelpHeader();
        out(join(" ", "Usage: dockstore", getEntryType().toLowerCase(), DOWNLOAD, HELP));
        out(join(" ", "       dockstore", getEntryType().toLowerCase(), DOWNLOAD, "[parameters]"));
        out("");
        out("Description:");
        out(join(" ", "  Downloads all", CHECKER, WORKFLOW, "files for the given entry and stores them in the current directory."));
        out("");
        out("Required Parameters:");
        out("  " + ENTRY + " <entry>          Complete entry path in the Dockstore (ex. quay.io/collaboratory/seqware-bwa-workflow)");
        out("  " + VERSION + " <version>      Checker version");
        out("  --zip                    Keep the zip file rather than uncompress the files within");
        out("");
        printHelpFooter();
    }

    private void launchChecker(List<String> args) {
        if (containsHelpRequest(args) || args.isEmpty()) {
            launchHelp();
        } else {
            // Retrieve arguments
            String entryPath = reqVal(args, ENTRY);

            // Properly handle versions
            String[] splitPath = entryPath.split(":");
            entryPath = splitPath[0];
            String version = null;
            if (splitPath.length > 1) {
                version = splitPath[1];
            }

            // Get entry from path
            Entry entry = getDockstoreEntry(entryPath);

            // Get checker workflow
            Workflow checkerWorkflow = getCheckerWorkflowFromEntry(entry, false);

            // Call parent launcher
            if (entry != null && checkerWorkflow != null) {
                // Readd entry path to call, but with checker workflow
                String checkerPath = checkerWorkflow.getFullWorkflowPath();
                if (version != null) {
                    checkerPath += ":" + version;
                }
                args.add(ENTRY);
                args.add(checkerPath);
                launch(args);
            }
        }
    }

    private void testParameterChecker(List<String> args) {
        if (containsHelpRequest(args) || args.isEmpty()) {
            testParameterHelp();
        } else {
            // Retrieve arguments
            String entryPath = reqVal(args, ENTRY);

            // Get entry from path
            Entry entry = getEntryFromPath(entryPath);

            // Get checker workflow
            Workflow checkerWorkflow = getCheckerWorkflowFromEntry(entry, true);

            // Add/remove test parameter files
            if (entry != null && checkerWorkflow != null) {
                // Add entry path to command, but with checker workflow
                args.add(ENTRY);
                args.add(checkerWorkflow.getFullWorkflowPath());

                // This is used by testParameter to properly display output/error messages
                args.add("--parent-entry");
                args.add(entryPath);

                // Call inherited test parameter function
                testParameter(args);
            }
        }
    }

    private void updateVersionChecker(List<String> args) {
        if (containsHelpRequest(args) || args.isEmpty()) {
            updateVersionCheckerHelp();
        } else {
            // Retrieve arguments
            String entryPath = reqVal(args, ENTRY);
            // Get entry from path
            Entry entry = getEntryFromPath(entryPath);
            // Get checker workflow
            Workflow checkerWorkflow = getCheckerWorkflowFromEntry(entry, true);
            // Update version
            if (entry != null && checkerWorkflow != null) {
                // Read entry path to call, but with checker workflow
                args.add(ENTRY);
                args.add(checkerWorkflow.getFullWorkflowPath());
                // Call inherited test parameter function
                versionTag(args);
            }
        }
    }

    /**
     * Retrieve an entry from the full path
     * @param entryPath Full path of the entry
     * @return entry
     */
    private Entry getEntryFromPath(String entryPath) {
        // Get entry from path
        Entry entry = null;
        try {
            entry = workflowsApi.getEntryByPath(entryPath);
        } catch (ApiException ex) {
            exceptionMessage(ex, "Could not find the entry with path" + entryPath, Client.API_ERROR);
        }
        return entry;
    }

    /**
     * Retrieve the checker workflow from an entry
     * @param entry The entry of interest
     * @param authRequired If true won't look for published workflows first, if false then it will
     * @return checker workflow
     */
    private Workflow getCheckerWorkflowFromEntry(Entry entry, boolean authRequired) {
        // Get checker workflow
        Workflow checkerWorkflow = null;
        if (entry != null) {
            if (entry.getCheckerId() == null) {
                errorMessage(join(" ", "The entry has no", CHECKER, WORKFLOW + "."),
                    Client.CLIENT_ERROR);
            } else {
                if (authRequired) {
                    checkerWorkflow = workflowsApi.getWorkflow(entry.getCheckerId(), null);
                } else {
                    checkerWorkflow = getDockstoreWorkflowById(entry.getCheckerId());
                }
            }
        }
        return checkerWorkflow;
    }

    /**
     * Retrieves a dockstore entry if either it is published or the user owns it
     * Note: Only use with commands that normally are use for published workflows, but can also be useful to the owner if the workflow is not published
     * @param path The complete entry path
     * @return Matching entry
     */
    private Entry getDockstoreEntry(String path) {
        // simply getting published descriptors does not require permissions
        Entry entry = null;
        try {
            entry = workflowsApi.getPublishedEntryByPath(path);
        } catch (ApiException e) {
            if (e.getResponseBody().contains("Entry not found")) {
                LOG.info("Unable to locate entry without credentials, trying again as authenticated user");
                entry = workflowsApi.getEntryByPath(path);
            }
        } finally {
            if (entry == null) {
                errorMessage("No entry found with path " + path, Client.ENTRY_NOT_FOUND);
            }
        }
        return entry;
    }
}
