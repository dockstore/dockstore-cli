package io.dockstore.client.cli;

import static io.dockstore.client.cli.Client.CONFIG;
import static io.dockstore.client.cli.Client.SCRIPT_FLAG;
import static io.dockstore.client.cli.Client.TOOL;
import static io.dockstore.client.cli.nested.AbstractEntryClient.MANUAL_PUBLISH;
import static io.dockstore.client.cli.nested.AbstractEntryClient.PUBLISH;
import static io.dockstore.client.cli.nested.WesCommandParser.ENTRY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static uk.org.webcompere.systemstubs.SystemStubs.catchSystemExit;

import io.dockstore.common.CLICommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.Registry;
import io.dockstore.common.SlowTest;
import io.dockstore.common.ToolTest;
import io.dropwizard.testing.ResourceHelpers;
import org.junit.experimental.categories.Category;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

@Tag(ConfidentialTest.NAME)
@Tag(ToolTest.NAME)
class QuayGitlabBasicIT extends BaseIT {

    @SystemStub
    public final SystemOut systemOutRule = new SystemOut();

    @SystemStub
    public final SystemErr systemErrRule = new SystemErr();

    @BeforeEach
    @Override
    public void resetDBBetweenTests() throws Exception {
        CLICommonTestUtilities.cleanStatePrivate1(SUPPORT, testingPostgres);
    }

    /*
     * Test Quay and Gitlab -
     * These tests are focused on testing entries created from Quay and Gitlab repositories
     */

    /**
     * Checks that the two Quay/Gitlab entries were automatically found
     */
    @Test
    void testQuayGitlabAutoRegistration() {
        // Need to add these to the db dump (db dump 1)

        final long count = testingPostgres.runSelectStatement(
            "select count(*) from tool where  registry = '" + Registry.QUAY_IO.getDockerPath() + "' and giturl like 'git@gitlab.com%'",
            long.class);
        assertEquals(2, count, "there should be 2 registered from Quay and Gitlab");
    }

    /**
     * Ensures that you can't publish an automatically added Quay/Gitlab entry with an alternate structure unless you change the Dockerfile and Dockstore.cwl locations
     */
    @Test
    void testQuayGitlabPublishAlternateStructure() throws Exception {
        int exitCode = catchSystemExit(() -> Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, PUBLISH, ENTRY,
                "quay.io/dockstoretestuser/quayandgitlabalternate", SCRIPT_FLAG }));
        assertEquals(Client.API_ERROR, exitCode);
    }

    /**
     * Checks that you can properly publish and unpublish a Quay/Gitlab entry
     */
    @Test
    void testQuayAndGitlabPublishAndUnpublishAnentry() {
        // Publish
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, PUBLISH, ENTRY,
            "quay.io/dockstoretestuser/quayandgitlab", SCRIPT_FLAG });

        final long count = testingPostgres
            .runSelectStatement("select count(*) from tool where name = 'quayandgitlab' and ispublished='t'", long.class);
        assertEquals(1, count, "there should be 1 registered");

        // Unpublish
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, PUBLISH, "--unpub", ENTRY,
            "quay.io/dockstoretestuser/quayandgitlab", SCRIPT_FLAG });

        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from tool where name = 'quayandgitlab' and ispublished='t'", long.class);
        assertEquals(0, count2, "there should be 0 registered");
    }

    /**
     * Checks that you can manually publish and unpublish a Quay/Gitlab entry with an alternate structure, if the CWL and Dockerfile paths are defined properly
     */
    @Test
    @Category(SlowTest.class)
    void testQuayGitlabManualPublishAndUnpublishAlternateStructure() {
        // Manual Publish
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, MANUAL_PUBLISH, "--registry",
            Registry.QUAY_IO.name(), Registry.QUAY_IO.toString(), "--namespace", "dockstoretestuser", "--name", "quayandgitlabalternate",
            "--git-url", "git@gitlab.com:dockstore.test.user/quayandgitlabalternate.git", "--git-reference", "master", "--toolname",
            "alternate", "--cwl-path", "/testDir/Dockstore.cwl", "--dockerfile-path", "/testDir/Dockerfile", SCRIPT_FLAG });

        final long count = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'alternate' and ispublished='t'", long.class);

        assertEquals(1, count, "there should be 1 entries, there are " + count);

        // Unpublish
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, PUBLISH, "--unpub", ENTRY,
            "quay.io/dockstoretestuser/quayandgitlabalternate/alternate", SCRIPT_FLAG });
        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'alternate' and ispublished='t'", long.class);

        assertEquals(0, count2, "there should be 0 entries, there are " + count2);

    }

    /**
     * Ensures that one cannot register an existing Quay/Gitlab entry if you don't give it an alternate toolname
     */
    @Test
    void testQuayGitlabManuallyRegisterDuplicate() throws Exception {
        int exitCode = catchSystemExit(() ->  Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), TOOL, MANUAL_PUBLISH, "--registry",
                Registry.QUAY_IO.name(), Registry.QUAY_IO.toString(), "--namespace", "dockstoretestuser", "--name", "quayandgitlab",
                "--git-url", "git@gitlab.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", SCRIPT_FLAG }));
        assertEquals(Client.API_ERROR, exitCode);
    }


}
