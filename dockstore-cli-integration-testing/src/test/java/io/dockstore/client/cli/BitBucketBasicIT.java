package io.dockstore.client.cli;

import io.dockstore.common.BitBucketTest;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.FlushingSystemErrRule;
import io.dockstore.common.FlushingSystemOutRule;
import io.dockstore.common.Registry;
import io.dropwizard.testing.ResourceHelpers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.experimental.categories.Category;

@Category(BitBucketTest.class)
public class BitBucketBasicIT extends BaseIT {
    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();

    @Rule
    public final SystemOutRule systemOutRule = new FlushingSystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new FlushingSystemErrRule().enableLog().muteForSuccessfulTests();

    @Before
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate1(SUPPORT);
    }

    /**
     * Checks that you can properly publish and unpublish a Quay/Bitbucket entry
     */
    @Test
    public void testQuayAndBitbucketPublishAndUnpublishAentry() {
        // Publish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--entry",
            "quay.io/dockstoretestuser/quayandbitbucket", "--script" });

        final long count = testingPostgres
            .runSelectStatement("select count(*) from tool where name = 'quayandbitbucket' and ispublished='t'", long.class);
        Assert.assertEquals("there should be 1 registered", 1, count);

        // Unpublish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry",
            "quay.io/dockstoretestuser/quayandbitbucket", "--script" });

        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from tool where name = 'quayandbitbucket' and ispublished='t'", long.class);
        Assert.assertEquals("there should be 0 registered", 0, count2);
    }

    /**
     * This tests that you can refresh user data by refreshing a tool
     * ONLY WORKS if the current user in the database dump has no metadata, and on Github there is metadata (bio, location)
     * If the user has metadata, test will pass as long as the user's metadata isn't the same as Github already
     */
    @Test
    public void testRefreshingUserMetadata() {
        // Setup database

        // Refresh a tool
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "refresh", "--entry",
            "quay.io/dockstoretestuser/quayandbitbucket", "--script" });

        // Check that user has been updated
        // TODO: bizarrely, the new GitHub Java API library doesn't seem to handle bio
        //final long count = testingPostgres.runSelectStatement("select count(*) from enduser where location='Toronto' and bio='I am a test user'", long.class);
        final long count = testingPostgres.runSelectStatement("select count(*) from user_profile where location='Toronto'", long.class);
        Assert.assertEquals("One user should have this info now, there are " + count, 1, count);
    }

    /**
     * Check that refreshing an existing tool will not throw an error
     * Todo: Update test to check the outcome of a refresh
     */
    @Test
    public void testRefreshCorrectTool() {
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
    public void testQuayBitbucketManuallyRegisterDuplicate() {
        systemExit.expectSystemExitWithStatus(Client.API_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.QUAY_IO.name(), Registry.QUAY_IO.toString(), "--namespace", "dockstoretestuser", "--name", "quayandbitbucket",
            "--git-url", "git@bitbucket.org:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--script" });
    }

    /**
     * Ensures that you can't publish an automatically added Quay/Bitbucket entry with an alternate structure unless you change the Dockerfile and Dockstore.cwl locations
     */
    @Test
    public void testQuayBitbucketPublishAlternateStructure() {
        systemExit.expectSystemExitWithStatus(Client.API_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--entry",
            "quay.io/dockstoretestuser/quayandbitbucketalternate", "--script" });

        // TODO: change the tag tag locations of Dockerfile and Dockstore.cwl, now should be able to publish
    }

    /**
     * Checks that you can manually publish and unpublish a Quay/Bitbucket entry with an alternate structure, if the CWL and Dockerfile paths are defined properly
     */
    @Test
    public void testQuayBitbucketManualPublishAndUnpublishAlternateStructure() {
        // Manual Publish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.QUAY_IO.name(), Registry.QUAY_IO.toString(), "--namespace", "dockstoretestuser", "--name", "quayandbitbucketalternate",
            "--git-url", "git@bitbucket.org:DockstoreTestUser/quayandbitbucketalternate.git", "--git-reference", "master", "--toolname",
            "alternate", "--cwl-path", "/testDir/Dockstore.cwl", "--dockerfile-path", "/testDir/Dockerfile", "--script" });

        final long count = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'alternate' and ispublished='t'", long.class);

        Assert.assertEquals("there should be 1 entries, there are " + count, 1, count);

        // Unpublish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry",
            "quay.io/dockstoretestuser/quayandbitbucketalternate/alternate", "--script" });
        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'alternate' and ispublished='t'", long.class);

        Assert.assertEquals("there should be 0 entries, there are " + count2, 0, count2);

    }

    /**
     * Will test manually publishing and unpublishing a Dockerhub/Bitbucket entry with an alternate structure
     */
    @Test
    public void testDockerhubBitbucketAlternateStructure() {
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.DOCKER_HUB.name(), Registry.DOCKER_HUB.toString(), "--namespace", "dockstoretestuser", "--name",
            "dockerhubandbitbucket", "--git-url", "git@bitbucket.org:DockstoreTestUser/quayandbitbucketalternate.git", "--git-reference",
            "master", "--toolname", "alternate", "--cwl-path", "/testDir/Dockstore.cwl", "--dockerfile-path", "/testDir/Dockerfile",
            "--script" });

        final long count = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'alternate' and ispublished='t'", long.class);
        Assert.assertEquals("there should be 1 entry", 1, count);

        // Unpublish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry",
            "registry.hub.docker.com/dockstoretestuser/dockerhubandbitbucket/alternate", "--script" });

        final long count3 = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'alternate' and ispublished='f'", long.class);
        Assert.assertEquals("there should be 1 entry", 1, count3);

    }


    /**
     * Will test attempting to manually publish a Dockerhub/Bitbucket entry using incorrect CWL and/or dockerfile locations
     */
    @Ignore
    @Test
    public void testDockerhubBitbucketWrongStructure() {
        // Todo : Manual publish entry with wrong cwl and dockerfile locations, should not be able to manual publish
        systemExit.expectSystemExitWithStatus(Client.GENERIC_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.DOCKER_HUB.name(), Registry.DOCKER_HUB.toString(), "--namespace", "dockstoretestuser", "--name",
            "dockerhubandbitbucketalternate", "--git-url", "git@bitbucket.org:DockstoreTestUser/quayandbitbucketalterante.git",
            "--git-reference", "master", "--toolname", "alternate", "--cwl-path", "/Dockstore.cwl", "--dockerfile-path", "/Dockerfile",
            "--script" });
    }


}
