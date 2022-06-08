package io.dockstore.client.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import io.dockstore.client.cli.nested.WorkflowClient;
import io.dockstore.common.yaml.DockstoreYaml12;
import io.dockstore.common.yaml.DockstoreYamlHelper;
import io.dockstore.common.yaml.DockstoreYamlHelper.DockstoreYamlException;
import io.swagger.client.ApiException;
import io.swagger.client.api.UsersApi;
import io.swagger.client.api.WorkflowsApi;
import org.yaml.snakeyaml.Yaml;

import static com.google.api.client.util.Objects.equal;
import static io.dockstore.client.cli.ArgumentUtility.containsHelpRequest;
import static io.dockstore.client.cli.ArgumentUtility.errorMessage;
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

    private final String dockstoreYml = ".dockstore.yml";

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
    public boolean processEntryCommands(List<String> args, String activeCommand) throws ApiException, IOException, DockstoreYamlException {
        if (equal(activeCommand, "validate")) {
            validateChecker(args);
            return true;
        }
        return false;
    }

    /*
    private List<String> getFileNames(Map<String, Object> map) {
        List<Object>
    }
    */
    private boolean validateChecker(List<String> args) {
        if (containsHelpRequest(args) || args.isEmpty()) {
            validateHelp();
            return false;
        } else {
            // Retrieve arguments
            String path = reqVal(args, "--path");

            // Check that path type is valid

            // Check that descriptor path is valid
            if (!path.startsWith("/")) {
                errorMessage("path must be an absolute path.",
                    Client.CLIENT_ERROR);
            }

            Path workflowPath = Paths.get(path);
            if (!Files.isDirectory(workflowPath)) {
                errorMessage(path + " is not a valid directory",
                    Client.CLIENT_ERROR);
                return false;
            }
            Path dockstoreYmlPath = Paths.get(path, dockstoreYml);
            if (!Files.exists(dockstoreYmlPath)) {
                out(dockstoreYmlPath.toString() + " does not exist");
                return false;
            }
            Yaml yaml = new Yaml();
            Map<String, Object> data = null;
            try {
                data = yaml.load(Files.readString(dockstoreYmlPath));
                out(dockstoreYmlPath + " is a valid yaml file");
            } catch (Exception ex) {
                out(dockstoreYmlPath + " is not a valid yaml file\n" + ex.getMessage());
                return false;
            }
            try {
                // Running validate first, as if readAsDockstoreYaml12 is run first and a parameter is incorrect it is difficult to understand
                DockstoreYamlHelper.validateDockstoreYamlProperties(Files.readString(dockstoreYmlPath));
            } catch (Exception ex) {
                out(dockstoreYmlPath + " has the following errors\n" + ex.getMessage());
                return false;
            }
            DockstoreYaml12 dockstoreYaml12 = null;
            try {
                dockstoreYaml12 = DockstoreYamlHelper.readAsDockstoreYaml12(Files.readString(dockstoreYmlPath));
            } catch (Exception ex) {
                out(dockstoreYmlPath + " has the following errors\n" + ex.getMessage());
                return false;
            }
            return true;
            /*
            try {
                DockstoreYamlHelper.validateDockstoreYamlProperties(Files.readString(dockstoreYmlPath));
                DockstoreYaml12 dockstoreYaml12 = DockstoreYamlHelper.readAsDockstoreYaml12(Files.readString(dockstoreYmlPath));
                String missingFiles = "";
                System.out.println(data);
                System.out.println(dockstoreYaml12.getTools());
                System.out.println(dockstoreYaml12.getWorkflows());
            } catch (Exception ex) {
                errorMessage("Your .dockstore.yml has the following errors\n" + ex.getMessage(),
                    Client.CLIENT_ERROR);
            }
            for (String filePath: files) {
                String fileContent = this.readFileFromRepo(filePath, ref.getLeft(), repository);
                if (fileContent != null) {
                    SourceFile file = new SourceFile();
                    file.setAbsolutePath(filePath);
                    file.setPath(filePath);
                    file.setContent(fileContent);
                    file.setType(DescriptorLanguage.FileType.DOCKSTORE_SERVICE_OTHER);
                    version.getSourceFiles().add(file);
                } else {
                    // File not found or null
                    LOG.info("Could not find file " + filePath + " in repo " + repository);
                }
            }
            */
        }
    }
}
