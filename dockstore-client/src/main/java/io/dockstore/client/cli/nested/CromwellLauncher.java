package io.dockstore.client.cli.nested;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.FileProvisioning;
import io.dockstore.common.Utilities;
import io.dockstore.common.WdlBridge;
import io.github.collaboratory.cwl.CWLClient;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import wdl.draft3.parser.WdlParser;

import static io.dockstore.client.cli.ArgumentUtility.errorMessage;
import static io.dockstore.client.cli.Client.IO_ERROR;

/**
 * This is a base class for clients that launch workflows with Cromwell
 */
public class CromwellLauncher extends BaseLauncher {
    // If the Cromwell version is changed be sure to update the table
    // in the documentation at advanced-topics/advanced-features.rst:317
    protected static final String DEFAULT_CROMWELL_VERSION = "44";
    protected Map<String, List<FileProvisioning.FileInfo>> outputMap;
    protected List<String>  cromwellExtraParameters;
    protected List<String>  cromwellVmOptions;

    public CromwellLauncher(AbstractEntryClient abstractEntryClient, DescriptorLanguage language, boolean script) {
        super(abstractEntryClient, language, script);
        setLauncherName("Cromwell");
    }

    /**
     * Creates a local copy of the Cromwell JAR (May have to download from the GitHub).
     * Uses the default version unless a version is specified in the Dockstore config.
     */
    @Override
    public void initialize() {
        // initialize cromwell location from ~/.dockstore/config
        INIConfiguration config = Utilities.parseConfig(abstractEntryClient.getConfigFile());
        cromwellVmOptions = (List)(config.getList("cromwell-vm-options"));
        cromwellExtraParameters = (List)(config.getList("cromwell-extra-parameters"));


        String cromwellVersion = config.getString("cromwell-version", DEFAULT_CROMWELL_VERSION);
        String cromwellLocation =
                "https://github.com/broadinstitute/cromwell/releases/download/" + cromwellVersion + "/cromwell-" + cromwellVersion + ".jar";
        if (!Objects.equals(DEFAULT_CROMWELL_VERSION, cromwellVersion)) {
            System.out.println("Running with Cromwell " + cromwellVersion + " , Dockstore tests with " + DEFAULT_CROMWELL_VERSION);
        }

        // grab the cromwell jar if needed
        String libraryLocation =
                System.getProperty("user.home") + File.separator + ".dockstore" + File.separator + "libraries" + File.separator;
        URL cromwellURL;
        String cromwellFileName;
        try {
            cromwellURL = new URL(cromwellLocation);
            cromwellFileName = new File(cromwellURL.toURI().getPath()).getName();
        } catch (MalformedURLException | URISyntaxException e) {
            throw new RuntimeException("Could not create " + launcherName + " location", e);
        }
        String cromwellTarget = libraryLocation + cromwellFileName;
        File cromwellTargetFile = new File(cromwellTarget);
        if (!cromwellTargetFile.exists()) {
            try {
                FileUtils.copyURLToFile(cromwellURL, cromwellTargetFile);
            } catch (IOException e) {
                throw new RuntimeException("Could not download " + launcherName + " location", e);
            }
        }
        executionFile = cromwellTargetFile;
    }

    @Override
    public List<String> buildRunCommand() {
        List<String> arguments = new ArrayList<>();
        arguments.add("java");

        if (cromwellVmOptions.size() > 0) {
            List<String> cromwellVmOptionsParsed = cromwellVmOptions.stream().map(string -> string.split(","))
                    .flatMap(Arrays::stream).map(String::trim)
                    .collect(Collectors.toList());
            arguments.addAll(cromwellVmOptionsParsed);
        }

        // Cromwell help specifies the 'run' command line format as: run [options] workflow-source
        Collections.addAll(arguments, "-jar", executionFile.getAbsolutePath(), "run", "--inputs", provisionedParameterFile.getAbsolutePath());

        // NOTE: Support for ZIP imports exists, but we decided to comment it out for now as it was causing some issues.
        //Collections.addAll(arguments, "--imports", zippedEntry.getAbsolutePath());

        // There are currently issues with Cromwell 44 automatic type detection.  If it's CWL, the flag must be added.
        // https://github.com/broadinstitute/cromwell/issues/5085
        // Remove these 3 lines once the type can be detected again.
        if (languageType == DescriptorLanguage.CWL) {
            Collections.addAll(arguments, "--type", "cwl");
        }

        if (cromwellExtraParameters.size() > 0) {
            List<String> cromwellExtraParametersParsed = cromwellExtraParameters.stream().map(string -> string.split(","))
                    .flatMap(Arrays::stream).map(String::trim)
                    .collect(Collectors.toList());
            arguments.addAll(cromwellExtraParametersParsed);
        }

        // Add workflow source file
        arguments.add(primaryDescriptor.getAbsolutePath());

        return arguments;
    }

    @Override
    public void provisionOutputFiles(String stdout, String stderr, String wdlOutputTarget) {
        if (Objects.equals(languageType, DescriptorLanguage.WDL)) {
            handleWDLOutputProvisioning(stdout, stderr, wdlOutputTarget);
        } else if (Objects.equals(languageType, DescriptorLanguage.CWL)) {
            handleCWLOutputProvisioning(stdout, stderr);
        }
    }

    /**
     * Handles output file provisioning for WDL
     * @param stdout
     * @param stderr
     * @param wdlOutputTarget
     */
    private void handleWDLOutputProvisioning(String stdout, String stderr, String wdlOutputTarget) {
        String alteredStdout = stdout.replaceAll("(?m)^", "\t");
        String alteredStderr = stderr.replaceAll("(?m)^", "\t");
        Gson gson = new Gson();
        String jsonString = null;
        try {
            jsonString = abstractEntryClient.fileToJSON(originalParameterFile);
        } catch (IOException ex) {
            errorMessage(ex.getMessage(), IO_ERROR);
        }
        Map<String, Object> inputJson = gson.fromJson(jsonString, HashMap.class);

        outputIntegrationOutput(workingDirectory, alteredStdout, alteredStderr, launcherName);
        // capture the output and provision it
        if (wdlOutputTarget != null) {
            // TODO: this is very hacky, look for a runtime option or start cromwell as a server and communicate via REST
            // grab values from output JSON
            Map<String, String> outputJson = parseOutputObjectFromCromwellStdout(alteredStdout, new Gson());

            System.out.println("Provisioning your output files to their final destinations");
            List<String> outputFiles = null;
            try {
                WdlBridge wdlBridge = new WdlBridge();
                outputFiles = wdlBridge.getOutputFiles(primaryDescriptor.getAbsolutePath());
            } catch (WdlParser.SyntaxError ex) {
                errorMessage(ex.getMessage(), IO_ERROR);
            }
            List<ImmutablePair<String, FileProvisioning.FileInfo>> outputList = new ArrayList<>();
            for (String outFile : outputFiles) {
                // find file path from output
                final File resultFile = new File(outputJson.get(outFile));
                FileProvisioning.FileInfo new1 = new FileProvisioning.FileInfo();
                new1.setUrl(wdlOutputTarget + "/" + outFile);
                new1.setLocalPath(resultFile.getAbsolutePath());
                if (inputJson.containsKey(outFile + ".metadata")) {
                    byte[] metadatas = Base64.getDecoder().decode((String)inputJson.get(outFile + ".metadata"));
                    new1.setMetadata(new String(metadatas, StandardCharsets.UTF_8));
                }
                System.out.println("Uploading: " + outFile + " from " + resultFile + " to : " + new1.getUrl());
                outputList.add(ImmutablePair.of(resultFile.getAbsolutePath(), new1));
            }
            fileProvisioning.uploadFiles(outputList);
        } else {
            System.out.println("Output files left in place");
        }
    }

    /**
     * Handles output file provisioning for CWL
     * @param stdout
     * @param stderr
     */
    private void handleCWLOutputProvisioning(String stdout, String stderr) {
        // Display output information
        outputIntegrationOutput(workingDirectory, stdout,
                stderr, launcherName);

        // Grab outputs object from Cromwell output (TODO: This is incredibly fragile)
        String outputPrefix = "Succeeded";
        int startIndex = stdout.indexOf("\n{\n", stdout.indexOf(outputPrefix));
        int endIndex = stdout.indexOf("\n}\n", startIndex) + 2;
        String bracketContents = stdout.substring(startIndex, endIndex).trim();
        if (bracketContents.isEmpty()) {
            throw new RuntimeException("No " + launcherName + " output");
        }
        Map<String, Object> outputJson = new Gson().fromJson(bracketContents, HashMap.class);

        // Find the name of the workflow that is used as a suffix for workflow output IDs
        startIndex = stdout.indexOf("Pre-Processing ");
        endIndex = stdout.indexOf("\n", startIndex);
        String temporaryWorkflowPath = stdout.substring(startIndex, endIndex).trim();
        String[] splitPath = temporaryWorkflowPath.split("/");
        String workflowName = splitPath[splitPath.length - 1];

        // Create a list of pairs of output ID and FileInfo objects used for uploading files
        List<ImmutablePair<String, FileProvisioning.FileInfo>> outputList = CWLClient
                .registerOutputFiles(outputMap, (Map<String, Object>)outputJson.get("outputs"), workflowName + ".");

        // Provision output files
        fileProvisioning.uploadFiles(outputList);
    }

    /**
     * Retrieves the output object from the Cromwell stdout
     * TODO: There has to be a better way to do this!
     * @param stdout Output from Cromwell Run
     * @param gson Gson object
     * @return Object for Cromwell output
     */
    public Map<String, String> parseOutputObjectFromCromwellStdout(String stdout, Gson gson) {
        String outputPrefix = "Final Outputs:";
        int prefixLocation = stdout.indexOf(outputPrefix);
        if (prefixLocation == -1) {
            throw new RuntimeException("Unexpected output format from cromwell");
        }

        int startIndex = stdout.indexOf("{\n", prefixLocation);
        int endIndex = stdout.indexOf("}\n", startIndex) + 2;

        if (startIndex == -1 || endIndex == -1) {
            throw new RuntimeException("Could not parse cromwell output");
        }
        String bracketContents = stdout.substring(startIndex, endIndex).trim();

        if (bracketContents.isEmpty()) {
            throw new RuntimeException("No cromwell output");
        }

        return gson.fromJson(bracketContents, HashMap.class);
    }

    public void setOutputMap(Map<String, List<FileProvisioning.FileInfo>> outputMap) {
        this.outputMap = outputMap;
    }
}
