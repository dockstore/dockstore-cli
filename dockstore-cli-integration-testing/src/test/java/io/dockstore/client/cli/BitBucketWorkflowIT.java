package io.dockstore.client.cli;

import io.dockstore.common.BitBucketTest;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.FlushingSystemErrRule;
import io.dockstore.common.FlushingSystemOutRule;
import io.dockstore.common.SourceControl;
import io.dropwizard.testing.ResourceHelpers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@Category(BitBucketTest.class)
public class BitBucketWorkflowIT extends BaseIT {
    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();

    @Rule
    public final SystemOutRule systemOutRule = new FlushingSystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new FlushingSystemErrRule().enableLog().muteForSuccessfulTests();

    @Before
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres, true);
    }

    /**
     * Tests that convert with valid imports will work (for WDL)
     */
    @Test
    public void testRefreshAndConvertWithImportsWDL() {
        refreshByOrganizationReplacement(USER_2_USERNAME);
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "update_workflow", "--entry",
                SourceControl.BITBUCKET.toString() + "/dockstore_testuser2/dockstore-workflow", "--descriptor-type", "wdl",
                "--workflow-path", "/Dockstore.wdl", "--default-test-parameter-path", "/foo.json", "--script" });

        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--entry",
            SourceControl.BITBUCKET.toString() + "/dockstore_testuser2/dockstore-workflow", "--script" });
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--entry",
            SourceControl.BITBUCKET.toString() + "/dockstore_testuser2/dockstore-workflow", "--script" });

        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "convert", "entry2json", "--entry",
                SourceControl.BITBUCKET.toString() + "/dockstore_testuser2/dockstore-workflow:wdl_import", "--script" });
        assertTrue(systemOutRule.getLog().contains("\"three_step.cgrep.pattern\": \"String\""));
    }

    /**
     * This tests manually publishing a Bitbucket workflow
     */
    @Test
    public void testManualPublishBitbucket() {
        // manual publish
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "manual_publish", "--repository",
                "dockstore-workflow", "--organization", "dockstore_testuser2", "--git-version-control", "bitbucket", "--workflow-name",
                "testname", "--workflow-path", "/Dockstore.wdl", "--descriptor-type", "wdl", "--script" });

        // Check for two valid versions (wdl_import and surprisingly, cwl_import)
        final long count = testingPostgres
            .runSelectStatement("select count(*) from workflowversion where valid='t' and (name='wdl_import' OR name='cwl_import')",
                long.class);
        assertEquals("There should be a valid 'wdl_import' version and a valid 'cwl_import' version", 2, count);

        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from workflowversion where lastmodified is null", long.class);
        assertEquals("All Bitbucket workflow versions should have last modified populated when manual published", 0, count2);

        // grab wdl file
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "wdl", "--entry",
            SourceControl.BITBUCKET.toString() + "/dockstore_testuser2/dockstore-workflow/testname:wdl_import", "--script" });
    }

    /**
     * This tests the dirty bit attribute for workflow versions with bitbucket
     */
    @Test
    public void testBitbucketDirtyBit() {
        refreshByOrganizationReplacement(USER_2_USERNAME);

        // refresh individual that is valid
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--entry",
            SourceControl.BITBUCKET.toString() + "/dockstore_testuser2/dockstore-workflow", "--script" });
        final long nullLastModifiedWorkflowVersions = testingPostgres
            .runSelectStatement("select count(*) from workflowversion where lastmodified is null", long.class);
        assertEquals("All Bitbucket workflow versions should have last modified populated after refreshing", 0,
            nullLastModifiedWorkflowVersions);
        // Check that no versions have a true dirty bit
        final long count = testingPostgres.runSelectStatement("select count(*) from workflowversion where dirtybit = true", long.class);
        assertEquals("there should be no versions with dirty bit, there are " + count, 0, count);

        // Edit workflow path for a version
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "version_tag", "--entry",
            SourceControl.BITBUCKET.toString() + "/dockstore_testuser2/dockstore-workflow", "--name", "master", "--workflow-path",
            "/Dockstoredirty.cwl", "--script" });

        // There should be on dirty bit
        final long count1 = testingPostgres.runSelectStatement("select count(*) from workflowversion where dirtybit = true", long.class);
        assertEquals("there should be 1 versions with dirty bit, there are " + count1, 1, count1);

        // Update default cwl
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "update_workflow", "--entry",
                SourceControl.BITBUCKET.toString() + "/dockstore_testuser2/dockstore-workflow", "--workflow-path", "/Dockstoreclean.cwl",
                "--script" });

        // There should be 3 versions with new cwl
        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from workflowversion where workflowpath = '/Dockstoreclean.cwl'", long.class);
        assertEquals("there should be 4 versions with workflow path /Dockstoreclean.cwl, there are " + count2, 4, count2);

    }


}
