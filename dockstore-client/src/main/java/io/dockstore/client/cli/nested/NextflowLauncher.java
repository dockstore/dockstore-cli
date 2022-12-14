package io.dockstore.client.cli.nested;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.NextflowUtilities;
import io.dockstore.common.Utilities;
import org.apache.commons.configuration2.INIConfiguration;

public class NextflowLauncher extends BaseLauncher {

    public NextflowLauncher(AbstractEntryClient abstractEntryClient, DescriptorLanguage language, boolean script) {
        super(abstractEntryClient, language, script);
        setLauncherName("Nextflow");
    }

    @Override
    public void initialize() {
        INIConfiguration config = Utilities.parseConfig(abstractEntryClient.getConfigFile());
        executionFile = NextflowUtilities.getNextflowTargetFile(config);
    }

    @Override
    public List<String> buildRunCommand() {
        ArrayList<String> command = new ArrayList<>(
                Arrays.asList("java", "--add-opens java.management/com.sun.jmx.mbeanserver=ALL-UNNAMED", "-jar", executionFile.getAbsolutePath(), "run", "-with-docker", "--outdir", workingDirectory,
                        "-work-dir", workingDirectory));
        if (originalParameterFile != null) {
            command.addAll(Arrays.asList("-params-file", originalParameterFile));
        }
        command.add(primaryDescriptor.getAbsolutePath());
        return command;
    }

    @Override
    public void provisionOutputFiles(String stdout, String stderr, String wdlOutputTarget) {
        outputIntegrationOutput(workingDirectory, stdout,
                stderr, launcherName);
    }
}
