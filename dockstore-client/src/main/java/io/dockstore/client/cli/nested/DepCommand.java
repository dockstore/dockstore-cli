package io.dockstore.client.cli.nested;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.MissingCommandException;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import io.dockstore.client.cli.ArgumentUtility;
import io.dockstore.client.cli.Client;
import io.dockstore.client.cli.JCommanderUtility;
import io.swagger.client.ApiClient;
import io.swagger.client.Configuration;
import io.swagger.client.api.MetadataApi;

import static io.dockstore.client.cli.ArgumentUtility.errorMessage;
import static io.dockstore.client.cli.Client.API_ERROR;
import static io.dockstore.client.cli.Client.CLIENT_ERROR;
import static io.dockstore.client.cli.Client.DEPS;
import static io.dockstore.client.cli.Client.HELP;
import static io.dockstore.client.cli.JCommanderUtility.displayJCommanderSuggestions;
import static io.dockstore.client.cli.JCommanderUtility.getUnknownParameter;
import static io.dockstore.client.cli.JCommanderUtility.wasErrorDueToUnknownParameter;

/**
 * @author gluu
 * @since 12/04/18
 */
public final class DepCommand {

    public static final String CLIENT_VERSION = "--client-version";
    public static final String PYTHON_VERSION = "--python-version";

    private DepCommand() {
    }

    /**
     * Handles when the deps command is called from the client
     *
     * @param args The command line arguments
     */
    public static boolean handleDepCommand(String[] args) {
        CommandDep commandDep = new CommandDep();
        JCommander jCommanderMain = new JCommander();
        JCommanderUtility.addCommand(jCommanderMain, DEPS, commandDep);
        try {
            jCommanderMain.parse(args);
        } catch (MissingCommandException e) {
            displayJCommanderSuggestions(jCommanderMain, e.getJCommander().getParsedCommand(), args[0], DEPS);
            return true;
        } catch (ParameterException e) {
            if (wasErrorDueToUnknownParameter(e.getMessage())) {
                String incorrectCommand = getUnknownParameter(e.getMessage());
                displayJCommanderSuggestions(jCommanderMain, e.getJCommander().getParsedCommand(), incorrectCommand, DEPS);
            } else {
                errorMessage(e.getMessage(), CLIENT_ERROR);
            }
            return true;
        }
        if (commandDep.help) {
            JCommanderUtility.printJCommanderHelp(jCommanderMain, "dockstore", DEPS);
        } else {
            ApiClient defaultApiClient;
            defaultApiClient = Configuration.getDefaultApiClient();
            MetadataApi metadataApi = new MetadataApi(defaultApiClient);
            String runnerDependencies = metadataApi
                    .getRunnerDependencies(commandDep.clientVersion, commandDep.pythonVersion, commandDep.runner, "text");
            if (runnerDependencies == null) {
                errorMessage("Could not get runner dependencies", API_ERROR);
            } else {
                ArgumentUtility.out(runnerDependencies);
            }

        }
        return true;
    }

    @Parameters(separators = "=", commandDescription = "Print cwltool runner dependencies")
    private static class CommandDep {
        @Parameter(names = CLIENT_VERSION, description = "Dockstore version")
        private String clientVersion = Client.getClientVersion();
        @Parameter(names = PYTHON_VERSION, description = "Python version")
        private String pythonVersion = "3";
        // @Parameter(names = "--runner", description = "tool/workflow runner. Available options: 'cwltool'")
        private String runner = "cwltool";
        @Parameter(names = HELP, description = "Prints help for " + DEPS, help = true)
        private boolean help = false;
    }
}
