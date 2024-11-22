package io.dockstore.client.cli.nested;

import static io.dockstore.client.cli.Client.API_ERROR;
import static io.dockstore.client.cli.Client.HELP;
import static io.dockstore.client.cli.Client.TOOL;
import static io.dockstore.client.cli.nested.AbstractEntryClient.VERIFY;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.dockstore.client.cli.ArgumentUtility;
import io.dockstore.client.cli.JCommanderUtility;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.Configuration;
import io.dockstore.openapi.client.api.ExtendedGa4GhApi;

/**
 * @author gluu
 * @since 2019-11-25
 */
final class Verify {
    private Verify() {
    }

    /**
     * Handles when the deps command is called from the client
     *
     * @param args The command line arguments
     */
    static void handleVerifyCommand(String[] args, String entryType) {
        VerifyCommand verifyCommand = new VerifyCommand();
        JCommander jCommanderMain = new JCommander();
        JCommanderUtility.addCommand(jCommanderMain, VERIFY, verifyCommand);
        jCommanderMain.parse(args);
        if (verifyCommand.help) {
            JCommanderUtility.printJCommanderHelp(jCommanderMain, "dockstore " + entryType, VERIFY);
        } else {
            ApiClient defaultApiClient;
            defaultApiClient = Configuration.getDefaultApiClient();
            ExtendedGa4GhApi extendedGa4GhApi = new ExtendedGa4GhApi(defaultApiClient);
            final String stringObjectMap = extendedGa4GhApi.verifyTestParameterFilePost(verifyCommand.descriptorType, verifyCommand.trsId,
                    verifyCommand.versionId, verifyCommand.filePath, verifyCommand.platform, verifyCommand.platformVersion,
                    !verifyCommand.unverify, verifyCommand.metadata);
            if (stringObjectMap == null) {
                ArgumentUtility.errorMessage(String.join(" ", "Could not", VERIFY, TOOL), API_ERROR);
            } else {
                Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
                ArgumentUtility.out(gson.toJson(stringObjectMap));
            }
        }
    }

    @Parameters(commandDescription = "Old-stype verification for tools, check out metrics!")
    private static class VerifyCommand {
        @Parameter(names = "--descriptor-type", description = "Descriptor Type (CWL, WDL, NFL)", required = true)
        private String descriptorType;
        @Parameter(names = "--trs-id", description = "TRS ID", required = true)
        private String trsId;
        @Parameter(names = "--version-id", description = "Version ID", required = true)
        private String versionId;
        @Parameter(names = "--file-path", description = "Path of the test JSON relative to the primary descriptor file", required = true)
        private String filePath;
        @Parameter(names = "--platform", description = "Platform to report on", required = true)
        private String platform;
        @Parameter(names = "--platform-version", description = "Version of the platform to report on", required = true)
        private String platformVersion;
        @Parameter(names = "--unverify", description = "Use flag to unverify")
        private boolean unverify;
        @Parameter(names = "--metadata", description = "Additional information on the verification (notes, explanation)", required = true)
        private String metadata;
        @Parameter(names = HELP, description = "Prints help for " + VERIFY, help = true)
        private boolean help = false;
    }
}
