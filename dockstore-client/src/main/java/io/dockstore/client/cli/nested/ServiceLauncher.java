package io.dockstore.client.cli.nested;

import java.util.List;

import io.dockstore.common.DescriptorLanguage;

public class ServiceLauncher extends BaseLauncher {

    public ServiceLauncher(AbstractEntryClient abstractEntryClient, DescriptorLanguage language, boolean script) {
        super(abstractEntryClient, language, script);
    }

    @Override
    public List<String> buildRunCommand() {
        return null;
    }

    @Override
    public void provisionOutputFiles(String stdout, String stderr, String wdlOutputTarget) {

    }
}
