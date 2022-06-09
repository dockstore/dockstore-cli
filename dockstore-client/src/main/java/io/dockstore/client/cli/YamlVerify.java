package io.dockstore.client.cli;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import io.dockstore.client.cli.nested.WorkflowClient;
import io.dockstore.common.yaml.DockstoreYaml12;
import io.dockstore.common.yaml.DockstoreYamlHelper;
import io.dockstore.common.yaml.Service12;
import io.dockstore.common.yaml.YamlWorkflow;
import io.swagger.client.ApiException;
import io.swagger.client.api.UsersApi;
import io.swagger.client.api.WorkflowsApi;
import org.yaml.snakeyaml.Yaml;

import static com.google.api.client.util.Objects.equal;
import static io.dockstore.client.cli.ArgumentUtility.containsHelpRequest;
import static io.dockstore.client.cli.ArgumentUtility.out;
import static io.dockstore.client.cli.ArgumentUtility.printFlagHelp;
import static io.dockstore.client.cli.ArgumentUtility.printHelpFooter;
import static io.dockstore.client.cli.ArgumentUtility.printHelpHeader;
import static io.dockstore.client.cli.ArgumentUtility.printLineBreak;
import static io.dockstore.client.cli.ArgumentUtility.printUsageHelp;
import static io.dockstore.client.cli.ArgumentUtility.reqVal;

/*
    GENERAL STEPS:
    1. Verfiy that YAML syntax is valid
    2. Verify that all fields are named correctly
    3. Verify that all required fields are present
    4. Verify that all files exist
 */

public class YamlVerify extends WorkflowClient {

    public static final String ERROR_MESSAGE = "Your .dockstore.yml is invalid:\n";

    public static final String DOCKSTOREYML = ".dockstore.yml";
    public YamlVerify(WorkflowsApi workflowApi, UsersApi usersApi, Client client, boolean isAdmin) {
        super(workflowApi, usersApi, client, isAdmin);
    }

    @Override
    public void printGeneralHelp() {
        printHelpHeader();
        printUsageHelp(getEntryType().toLowerCase());

        // Checker client help
        out("Commands:");
        out("");
        out("  validate             :  Verifies that .dockstore.yml has the correct fields, and that all the required files are present");
        out("");

        printLineBreak();
        printFlagHelp();
        printHelpFooter();
    }

    @Override
    public String getEntryType() {
        return "yaml";
    }

    private void validateHelp() {
        printHelpHeader();
        out("Usage: dockstore " + getEntryType().toLowerCase() + " validate --help");
        out("       dockstore " + getEntryType().toLowerCase() + " validate [parameters]");
        out("");
        out("Description:");
        out("  Verifies that a .dockstore.yml file is valid and that all required files are present");
        out("");
        out("Required Parameters:");
        out("  --path <path>                                                          Complete entry path in computer (ex. /home/usr/test)");
        out("");
        printHelpFooter();
    }

    @Override
    public boolean processEntryCommands(List<String> args, String activeCommand) throws ApiException, IOException, ValidateYamlException {
        if (equal(activeCommand, "validate")) {
            validateChecker(args);
            return true;
        }
        return false;
    }


    // Determines if all the files referenced in a list of strings exist
    private static String filesExist(List<String> paths, String base) throws ValidateYamlException {
        String missingFiles = "";
        for (String path : paths) {
            Path pathToFile = Paths.get(base, path);
            if (!Files.exists(pathToFile)) {
                missingFiles += pathToFile.toString() + " does not exist\n";
            }
        }
        return missingFiles;
    }

    // Determines if all the files in dockstoreYaml12 exist
    private static void allFilesExist(DockstoreYaml12 dockstoreYaml12, String basePath) throws ValidateYamlException {
        String missingFiles = "";

        // Check Workflows
        List<YamlWorkflow> workflows = dockstoreYaml12.getWorkflows();
        if (workflows != null) {
            for (YamlWorkflow workflow : workflows) {
                List<String> filePaths = workflow.getTestParameterFiles();
                filePaths.add(workflow.getPrimaryDescriptorPath());
                missingFiles += filesExist(filePaths, basePath);
            }
        }
        // Check Tools
        List<YamlWorkflow> tools = dockstoreYaml12.getTools();
        if (tools != null) {
            for (YamlWorkflow tool : tools) {
                List<String> filePaths = tool.getTestParameterFiles();
                filePaths.add(tool.getPrimaryDescriptorPath());
                missingFiles += filesExist(filePaths, basePath);
            }
        }

        // Check service
        Service12 service = dockstoreYaml12.getService();
        if (service != null) {
            missingFiles += filesExist(service.getFiles(), basePath);
        }
        if (!missingFiles.isBlank()) {
            throw new ValidateYamlException("Your file structure has the following errors:\n" + missingFiles);
        }
    }

    public static void dockstoreValidate(String path) throws ValidateYamlException {
        Path workflowPath = Paths.get(path);

        // Verify that the path is a valid directory
        if (!Files.isDirectory(workflowPath)) {
            throw new ValidateYamlException(ERROR_MESSAGE + workflowPath.toString() + " does not exist");
        }

        // Verify that .dockstore.yml exists
        Path dockstoreYmlPath = Paths.get(path, DOCKSTOREYML);
        if (!Files.exists(dockstoreYmlPath)) {
            throw new ValidateYamlException(ERROR_MESSAGE + dockstoreYmlPath.toString() + " does not exist");
        }

        // Verify that .dockstore.yml is non-empty
        File dockstoreYml = new File(dockstoreYmlPath.toString());
        if (dockstoreYml.length() == 0) {
            throw new ValidateYamlException(ERROR_MESSAGE + dockstoreYmlPath.toString() + " is empty");
        }

        // Verify that the Yaml is valid, however does not verify it is valid for dockstore
        Yaml yaml = new Yaml();
        Map<String, Object> data = null;
        try {
            data = yaml.load(Files.readString(dockstoreYmlPath));
            out(dockstoreYmlPath + " is a valid yaml file");
        } catch (Exception ex) {
            throw new ValidateYamlException(ERROR_MESSAGE + dockstoreYmlPath.toString() + " is not a valid yaml file:\n" + ex.getMessage());
        }

        // Running validate first, as if readAsDockstoreYaml12 is run first and a parameter is incorrect it is difficult to understand
        // Determine if any of the parameters names are incorrect (does not verify if all the parameters are there)
        try {

            DockstoreYamlHelper.validateDockstoreYamlProperties(Files.readString(dockstoreYmlPath));
        } catch (Exception ex) {
            throw new ValidateYamlException(ERROR_MESSAGE + dockstoreYmlPath.toString() + " has the following errors:\n" + ex.getMessage());
        }

        // Validates file dockstoreYaml in general
        DockstoreYaml12 dockstoreYaml12 = null;
        try {
            dockstoreYaml12 = DockstoreYamlHelper.readAsDockstoreYaml12(Files.readString(dockstoreYmlPath));
        } catch (Exception ex) {
            throw new ValidateYamlException(ERROR_MESSAGE + dockstoreYmlPath.toString() + " has the following errors:\n" + ex.getMessage());
        }

        // Determines if all referenced files exist
        allFilesExist(dockstoreYaml12, workflowPath.toString());

    }


    private void validateChecker(List<String> args) throws ValidateYamlException {
        if (containsHelpRequest(args) || args.isEmpty()) {
            validateHelp();
        } else {
            // Retrieve arguments
            String path = reqVal(args, "--path");
            dockstoreValidate(path);
            // Check that path type is valid

            // Check that descriptor path is valid

        }
    }
    public static class ValidateYamlException extends Exception {
        public ValidateYamlException(final String msg) {
            super(msg);
        }
    }
}
