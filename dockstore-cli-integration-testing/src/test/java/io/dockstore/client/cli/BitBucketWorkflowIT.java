package io.dockstore.client.cli;

import io.dockstore.common.BitBucketTest;
import io.dockstore.common.CLICommonTestUtilities;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.SourceControl;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dropwizard.testing.ResourceHelpers;
import org.hibernate.Session;
import org.hibernate.context.internal.ManagedSessionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

import static io.dockstore.client.cli.Client.CONFIG;
import static io.dockstore.client.cli.Client.SCRIPT_FLAG;
import static io.dockstore.client.cli.Client.WORKFLOW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag(BitBucketTest.NAME)
class BitBucketWorkflowIT extends BaseIT {

    @SystemStub
    public final SystemOut systemOutRule = new SystemOut();

    @SystemStub
    public final SystemErr systemErrRule = new SystemErr();

    @BeforeEach
    @Override
    public void resetDBBetweenTests() throws Exception {
        CLICommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres, true);
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
     * Tests that convert with valid imports will work (for WDL)
     */
    @Test
    void testRefreshAndConvertWithImportsWDL() {
        refreshByOrganizationReplacement(USER_2_USERNAME);
        Client.main(
            new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, "update_workflow", "--entry",
                SourceControl.BITBUCKET + "/dockstore_testuser2/dockstore-workflow", "--descriptor-type", "wdl",
                "--workflow-path", "/Dockstore.wdl", "--default-test-parameter-path", "/foo.json", SCRIPT_FLAG });

        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, "refresh", "--entry",
            SourceControl.BITBUCKET + "/dockstore_testuser2/dockstore-workflow", SCRIPT_FLAG });
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, "publish", "--entry",
            SourceControl.BITBUCKET + "/dockstore_testuser2/dockstore-workflow", SCRIPT_FLAG });

        Client.main(
            new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, "convert", "entry2json", "--entry",
                SourceControl.BITBUCKET + "/dockstore_testuser2/dockstore-workflow:wdl_import", SCRIPT_FLAG });
        assertTrue(systemOutRule.getText().contains("\"three_step.cgrep.pattern\": \"String\""));
    }

    /**
     * This tests manually publishing a Bitbucket workflow
     */
    @Test
    void testManualPublishBitbucket() {
        // manual publish
        Client.main(
            new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, "manual_publish", "--repository",
                "dockstore-workflow", "--organization", "dockstore_testuser2", "--git-version-control", "bitbucket", "--workflow-name",
                "testname", "--workflow-path", "/Dockstore.wdl", "--descriptor-type", "wdl", SCRIPT_FLAG });

        // Check for two valid versions (wdl_import and surprisingly, cwl_import)
        final long count = testingPostgres
            .runSelectStatement("select count(*) from workflowversion where valid='t' and (name='wdl_import' OR name='cwl_import')",
                long.class);
        assertEquals(2, count, "There should be a valid 'wdl_import' version and a valid 'cwl_import' version");

        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from workflowversion where lastmodified is null", long.class);
        assertEquals(0, count2, "All Bitbucket workflow versions should have last modified populated when manual published");

        // grab wdl file
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), WORKFLOW, "wdl", "--entry",
            SourceControl.BITBUCKET + "/dockstore_testuser2/dockstore-workflow/testname:wdl_import", SCRIPT_FLAG });
    }

    /**
     * This tests the dirty bit attribute for workflow versions with bitbucket
     */
    @Test
    void testBitbucketDirtyBit() {
        refreshByOrganizationReplacement(USER_2_USERNAME);

        // refresh individual that is valid
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--entry",
            SourceControl.BITBUCKET + "/dockstore_testuser2/dockstore-workflow", SCRIPT_FLAG });
        final long nullLastModifiedWorkflowVersions = testingPostgres
            .runSelectStatement("select count(*) from workflowversion where lastmodified is null", long.class);
        assertEquals(0, nullLastModifiedWorkflowVersions,
                "All Bitbucket workflow versions should have last modified populated after refreshing");
        // Check that no versions have a true dirty bit
        final long count = testingPostgres.runSelectStatement("select count(*) from workflowversion where dirtybit = true", long.class);
        assertEquals(0, count, "there should be no versions with dirty bit, there are " + count);

        // Edit workflow path for a version
        Client.main(new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "version_tag", "--entry",
            SourceControl.BITBUCKET + "/dockstore_testuser2/dockstore-workflow", "--name", "master", "--workflow-path",
            "/Dockstoredirty.cwl", SCRIPT_FLAG });

        // There should be on dirty bit
        final long count1 = testingPostgres.runSelectStatement("select count(*) from workflowversion where dirtybit = true", long.class);
        assertEquals(1, count1, "there should be 1 versions with dirty bit, there are " + count1);

        // Update default cwl
        Client.main(
            new String[] { CONFIG, ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "update_workflow", "--entry",
                SourceControl.BITBUCKET + "/dockstore_testuser2/dockstore-workflow", "--workflow-path", "/Dockstoreclean.cwl",
                SCRIPT_FLAG });

        // There should be 3 versions with new cwl
        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from workflowversion where workflowpath = '/Dockstoreclean.cwl'", long.class);
        assertEquals(4, count2, "there should be 4 versions with workflow path /Dockstoreclean.cwl, there are " + count2);

    }


}
