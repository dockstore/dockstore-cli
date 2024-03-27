package io.dockstore.client.cli;

import static io.dockstore.client.cli.Client.CONFIG;
import static io.dockstore.client.cli.Client.SCRIPT_FLAG;
import static io.dockstore.client.cli.Client.TOOL;
import static io.dockstore.client.cli.nested.AbstractEntryClient.INFO;
import static io.dockstore.client.cli.nested.AbstractEntryClient.MANUAL_PUBLISH;
import static io.dockstore.client.cli.nested.AbstractEntryClient.SEARCH;
import static io.dockstore.client.cli.nested.AbstractEntryClient.STAR;
import static io.dockstore.client.cli.nested.WesCommandParser.ENTRY;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.dockstore.client.cli.nested.ToolClient;
import io.dockstore.common.CLICommonTestUtilities;
import io.dockstore.common.Registry;
import io.dropwizard.testing.ResourceHelpers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

/**
 * @author gluu
 * @since 2020-02-28
 */
class StarToolIT extends BaseIT {

    public static final String ENTRY_PATH = Registry.DOCKER_HUB.getDockerPath() + "/dockstoretestuser/dockerhubandgithub/regular";

    @SystemStub
    public final SystemOut systemOutRule = new SystemOut();

    @SystemStub
    public final SystemErr systemErrRule = new SystemErr();

    @BeforeEach
    @Override
    public void resetDBBetweenTests() throws Exception {
        CLICommonTestUtilities.cleanStatePrivate1(SUPPORT, testingPostgres);
    }

    @Test
    void starTool() {
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, MANUAL_PUBLISH, "--registry",
                Registry.DOCKER_HUB.name(), Registry.DOCKER_HUB.toString(), "--namespace", "dockstoretestuser", "--name",
                "dockerhubandgithub", "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master",
                "--toolname", "regular", SCRIPT_FLAG });
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, STAR, ENTRY, ENTRY_PATH, SCRIPT_FLAG });
        assertTrue(systemOutRule.getText().contains("Successfully starred"));
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, STAR, "--unstar", ENTRY, ENTRY_PATH, SCRIPT_FLAG });
        assertTrue(systemOutRule.getText().contains("Successfully unstarred"));

        // hit up handleSearch
        systemOutRule.clear();
        Client.main(new String[]{ CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, SEARCH, "--pattern", "dockstoretestuser"});
        assertTrue(systemOutRule.getText().contains(ENTRY_PATH));

        // hit up handleListUnstarredEntries
        systemOutRule.clear();
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, STAR, SCRIPT_FLAG });
        assertTrue(systemOutRule.getText().contains(ToolClient.ALL_PUBLISHED_TOOLS));

        // hit up tool info while at it
        systemOutRule.clear();
        Client.main(new String[]{ CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, INFO, ENTRY, ENTRY_PATH });
        assertTrue(systemOutRule.getText().contains("git@github.com:DockstoreTestUser/dockstore-whalesay.git"));
    }
}
