package io.dockstore.client.cli;

import io.dockstore.common.BitBucketTest;
import io.dockstore.common.CLICommonTestUtilities;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.Registry;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dropwizard.testing.ResourceHelpers;
import org.hibernate.Session;
import org.hibernate.context.internal.ManagedSessionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static uk.org.webcompere.systemstubs.SystemStubs.catchSystemExit;

@Tag(ConfidentialTest.NAME)
@Tag(BitBucketTest.NAME)
@ExtendWith(SystemStubsExtension.class)
class BitBucketBasicIT extends BaseIT {

    @SystemStub
    public final SystemOut systemOutRule = new SystemOut();

    @SystemStub
    public final SystemErr systemErrRule = new SystemErr();

    @BeforeEach
    @Override
    public void resetDBBetweenTests() throws Exception {
        CLICommonTestUtilities.cleanStatePrivate1(SUPPORT, testingPostgres, true);
    }

    @AfterEach
    public void preserveBitBucketTokens() {
        // used to allow us to use cacheBitbucketTokens outside of the web service
        DockstoreWebserviceApplication application = SUPPORT.getApplication();
        Session session = application.getHibernate().getSessionFactory().openSession();
        ManagedSessionContext.bind(session);
        CommonTestUtilities.cacheBitbucketTokens(SUPPORT);
    }

    /**
     * Checks that you can properly publish and unpublish a Quay/Bitbucket entry
     */
    @Test
    void testQuayAndBitbucketPublishAndUnpublishAentry() {
        // Publish
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--entry",
            "quay.io/dockstoretestuser/quayandbitbucket", "--script" });

        final long count = testingPostgres
            .runSelectStatement("select count(*) from tool where name = 'quayandbitbucket' and ispublished='t'", long.class);
        assertEquals(1, count, "there should be 1 registered");

        // Unpublish
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry",
            "quay.io/dockstoretestuser/quayandbitbucket", "--script" });

        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from tool where name = 'quayandbitbucket' and ispublished='t'", long.class);
        assertEquals(0, count2, "there should be 0 registered");
    }

    /**
     * This tests that you can refresh user data by refreshing a tool
     * ONLY WORKS if the current user in the database dump has no metadata, and on Github there is metadata (bio, location)
     * If the user has metadata, test will pass as long as the user's metadata isn't the same as Github already
     */
    @Test
    void testRefreshingUserMetadata() {
        // Setup database

        // Refresh a tool
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "refresh", "--entry",
            "quay.io/dockstoretestuser/quayandbitbucket", "--script" });

        // Check that user has been updated
        // TODO: bizarrely, the new GitHub Java API library doesn't seem to handle bio
        //final long count = testingPostgres.runSelectStatement("select count(*) from enduser where location='Toronto' and bio='I am a test user'", long.class);
        final long count = testingPostgres.runSelectStatement("select count(*) from user_profile where location='Toronto'", long.class);
        assertEquals(1, count, "One user should have this info now, there are " + count);
    }

    /**
     * Check that refreshing an existing tool will not throw an error
     * Todo: Update test to check the outcome of a refresh
     */
    @Test
    void testRefreshCorrectTool() {
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "refresh", "--entry",
            "quay.io/dockstoretestuser/quayandbitbucket", "--script" });
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.DOCKER_HUB.name(), Registry.DOCKER_HUB.toString(), "--namespace", "dockstoretestuser", "--name",
            "dockerhubandbitbucket", "--git-url", "git@bitbucket.org:dockstoretestuser/dockstore-whalesay.git", "--git-reference", "master",
            "--toolname", "regular", "--script" });
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "refresh", "--entry",
            "registry.hub.docker.com/dockstoretestuser/dockerhubandbitbucket/regular", "--script" });
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.DOCKER_HUB.name(), Registry.DOCKER_HUB.toString(), "--namespace", "dockstoretestuser", "--name", "dockerhubandgithub",
            "--git-url", "git@github.com:dockstoretestuser/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "regular",
            "--script" });
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "refresh", "--entry",
            "registry.hub.docker.com/dockstoretestuser/dockerhubandgithub/regular", "--script" });
    }

    /**
     * Ensures that one cannot register an existing Quay/Bitbucket entry if you don't give it an alternate toolname
     */
    @Test
    void testQuayBitbucketManuallyRegisterDuplicate() throws Exception {
        int exitCode = catchSystemExit(() -> Client.main(
                new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
                        Registry.QUAY_IO.name(), Registry.QUAY_IO.toString(), "--namespace", "dockstoretestuser", "--name",
                        "quayandbitbucket", "--git-url", "git@bitbucket.org:DockstoreTestUser/dockstore-whalesay.git", "--git-reference",
                        "master", "--script" }));
        assertEquals(Client.API_ERROR, exitCode);
    }

    /**
     * Ensures that you can't publish an automatically added Quay/Bitbucket entry with an alternate structure unless you change the Dockerfile and Dockstore.cwl locations
     */
    @Test
    void testQuayBitbucketPublishAlternateStructure() throws Exception {
        int exitCode = catchSystemExit(() -> Client.main(
                new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--entry",
                        "quay.io/dockstoretestuser/quayandbitbucketalternate", "--script" }));
        assertEquals(Client.API_ERROR, exitCode);
        // TODO: change the tag tag locations of Dockerfile and Dockstore.cwl, now should be able to publish
    }

    /**
     * Checks that you can manually publish and unpublish a Quay/Bitbucket entry with an alternate structure, if the CWL and Dockerfile paths are defined properly
     */
    @Test
    void testQuayBitbucketManualPublishAndUnpublishAlternateStructure() {
        // Manual Publish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.QUAY_IO.name(), Registry.QUAY_IO.toString(), "--namespace", "dockstoretestuser", "--name", "quayandbitbucketalternate",
            "--git-url", "git@bitbucket.org:DockstoreTestUser/quayandbitbucketalternate.git", "--git-reference", "master", "--toolname",
            "alternate", "--cwl-path", "/testDir/Dockstore.cwl", "--dockerfile-path", "/testDir/Dockerfile", "--script" });

        final long count = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'alternate' and ispublished='t'", long.class);

        assertEquals(1, count, "there should be 1 entries, there are " + count);

        // Unpublish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry",
            "quay.io/dockstoretestuser/quayandbitbucketalternate/alternate", "--script" });
        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'alternate' and ispublished='t'", long.class);

        assertEquals(0, count2, "there should be 0 entries, there are " + count2);

    }

    /**
     * Will test manually publishing and unpublishing a Dockerhub/Bitbucket entry with an alternate structure
     */
    @Test
    void testDockerhubBitbucketAlternateStructure() {
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.DOCKER_HUB.name(), Registry.DOCKER_HUB.toString(), "--namespace", "dockstoretestuser", "--name",
            "dockerhubandbitbucket", "--git-url", "git@bitbucket.org:DockstoreTestUser/quayandbitbucketalternate.git", "--git-reference",
            "master", "--toolname", "alternate", "--cwl-path", "/testDir/Dockstore.cwl", "--dockerfile-path", "/testDir/Dockerfile",
            "--script" });

        final long count = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'alternate' and ispublished='t'", long.class);
        assertEquals(1, count, "there should be 1 entry");

        // Unpublish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry",
            "registry.hub.docker.com/dockstoretestuser/dockerhubandbitbucket/alternate", "--script" });

        final long count3 = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'alternate' and ispublished='f'", long.class);
        assertEquals(1, count3, "there should be 1 entry");

    }


    /**
     * Will test attempting to manually publish a Dockerhub/Bitbucket entry using incorrect CWL and/or dockerfile locations
     */
    @Disabled("probably broken with changes to manual publish")
    @Test
    void testDockerhubBitbucketWrongStructure() throws Exception {
        // Todo : Manual publish entry with wrong cwl and dockerfile locations, should not be able to manual publish
        int exitCode = catchSystemExit(() -> Client.main(
                new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
                        Registry.DOCKER_HUB.name(), Registry.DOCKER_HUB.toString(), "--namespace", "dockstoretestuser", "--name",
                        "dockerhubandbitbucketalternate", "--git-url", "git@bitbucket.org:DockstoreTestUser/quayandbitbucketalterante.git",
                        "--git-reference", "master", "--toolname", "alternate", "--cwl-path", "/Dockstore.cwl", "--dockerfile-path",
                        "/Dockerfile", "--script" }));
        assertEquals(Client.GENERIC_ERROR, exitCode);
    }


    /**
     * Checks that you can manually publish and unpublish a Dockerhub/Bitbucket duplicate if different toolnames are set (but same Path)
     */
    @Test
    void testDockerhubBitbucketManualRegistrationDuplicates() {
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.DOCKER_HUB.name(), Registry.DOCKER_HUB.toString(), "--namespace", "dockstoretestuser", "--name",
            "dockerhubandbitbucket", "--git-url", "git@bitbucket.org:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master",
            "--toolname", "regular", "--script" });

        final long count = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'regular' and ispublished='t'", long.class);

        assertEquals(1, count, "there should be 1 entry");

        // Add duplicate entry with different toolname
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.DOCKER_HUB.name(), Registry.DOCKER_HUB.toString(), "--namespace", "dockstoretestuser", "--name",
            "dockerhubandbitbucket", "--git-url", "git@bitbucket.org:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master",
            "--toolname", "regular2", "--script" });

        // Unpublish the duplicate entrys
        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname like 'regular%' and ispublished='t'", long.class);
        assertEquals(2, count2, "there should be 2 entries");

        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry",
            "registry.hub.docker.com/dockstoretestuser/dockerhubandbitbucket/regular", "--script" });
        final long count3 = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'regular2' and ispublished='t'", long.class);

        assertEquals(1, count3, "there should be 1 entry");

        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry",
            "registry.hub.docker.com/dockstoretestuser/dockerhubandbitbucket/regular2", "--script" });
        final long count4 = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname like 'regular%' and ispublished='t'", long.class);

        assertEquals(0, count4, "there should be 0 entries");

    }

    /*
     * Test dockerhub and bitbucket -
     * These tests are focused on testing entrys created from Dockerhub and Bitbucket repositories
     */

    /**
     * Tests manual registration and unpublishing of a Dockerhub/Bitbucket entry
     */
    @Test
    void testDockerhubBitbucketManualRegistration() {
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.DOCKER_HUB.name(), Registry.DOCKER_HUB.toString(), "--namespace", "dockstoretestuser", "--name",
            "dockerhubandbitbucket", "--git-url", "git@bitbucket.org:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master",
            "--toolname", "regular", "--script" });

        final long count = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'regular' and ispublished='t'", long.class);

        assertEquals(1, count, "there should be 1 entries, there are " + count);

        // Unpublish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry",
            "registry.hub.docker.com/dockstoretestuser/dockerhubandbitbucket/regular", "--script" });
        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'regular' and ispublished='t'", long.class);

        assertEquals(0, count2, "there should be 0 entries, there are " + count2);
    }

    /*
     * Test Quay and Bitbucket -
     * These tests are focused on testing entries created from Quay and Bitbucket repositories
     */

    /**
     * Checks that the two Quay/Bitbucket entrys were automatically found
     */
    @Test
    void testQuayBitbucketAutoRegistration() {

        final long count = testingPostgres.runSelectStatement(
            "select count(*) from tool where registry = '" + Registry.QUAY_IO.getDockerPath() + "' and giturl like 'git@bitbucket.org%'",
            long.class);
        assertEquals(2, count, "there should be 2 registered from Quay and Bitbucket");
    }

}
