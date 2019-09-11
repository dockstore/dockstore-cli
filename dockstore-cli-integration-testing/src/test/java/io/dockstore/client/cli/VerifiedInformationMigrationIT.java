package io.dockstore.client.cli;

import java.util.Arrays;
import java.util.List;

import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.SourceControl;
import io.dockstore.common.TestingPostgres;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dropwizard.Application;
import io.dropwizard.testing.DropwizardTestSupport;
import io.dropwizard.testing.ResourceHelpers;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

/**
 * @author gluu
 * @since 19/07/18
 */
public class VerifiedInformationMigrationIT {

    public static final DropwizardTestSupport<DockstoreWebserviceConfiguration> SUPPORT = new DropwizardTestSupport<>(
            DockstoreWebserviceApplication.class, CommonTestUtilities.CONFIDENTIAL_CONFIG_PATH);
    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();
    @Rule
    public TestRule watcher = new TestWatcher() {
        protected void starting(Description description) {
            System.out.println("Starting test: " + description.getMethodName());
        }
    };

    protected static TestingPostgres testingPostgres;

    @BeforeClass
    public static void dumpDBAndCreateSchema() {
        SUPPORT.before();
        testingPostgres = new TestingPostgres(SUPPORT);
    }

    @AfterClass
    public static void afterClass() {
        SUPPORT.after();
    }

    @Test
    public void toolVerifiedInformationMigrationTest() {
        Application<DockstoreWebserviceConfiguration> application = SUPPORT.getApplication();
        try {
            application.run("db", "drop-all", "--confirm-delete-everything", CommonTestUtilities.CONFIDENTIAL_CONFIG_PATH);
            List<String> migrationList = Arrays.asList("1.3.0.generated", "1.3.1.consistency", "test", "1.4.0");
            CommonTestUtilities.runMigration(migrationList, application, CommonTestUtilities.CONFIDENTIAL_CONFIG_PATH);
        } catch (Exception e) {
            Assert.fail("Could not run migrations up to 1.4.0");
        }



        testingPostgres.runUpdateStatement("UPDATE tag SET verified='t' where name='fakeName'");

        // Run full 1.5.0 migration
        try {
            List<String> migrationList = Arrays.asList("1.5.0");
            CommonTestUtilities.runMigration(migrationList, application, CommonTestUtilities.CONFIDENTIAL_CONFIG_PATH);
        } catch (Exception e) {
            Assert.fail("Could not run 1.5.0 migration");
        }

        final long afterMigrationVerifiedCount = testingPostgres
                .runSelectStatement("select count(*) from sourcefile_verified",
                        long.class);
        Assert.assertEquals("There should be 2 entries in sourcefile_verified after the migration but got: " + afterMigrationVerifiedCount, 5, afterMigrationVerifiedCount);
    }

    @Test
    public void workflowVerifiedInformationMigrationTest() {
        Application<DockstoreWebserviceConfiguration> application = SUPPORT.getApplication();
        try {
            application.run("db", "drop-all", "--confirm-delete-everything", CommonTestUtilities.CONFIDENTIAL_CONFIG_PATH);
            List<String> migrationList = Arrays.asList("1.3.0.generated", "1.3.1.consistency", "1.4.0", "testworkflow");
            CommonTestUtilities.runMigration(migrationList, application, CommonTestUtilities.CONFIDENTIAL_CONFIG_PATH);
        } catch (Exception e) {
            Assert.fail("Could not run migrations up to 1.4.0");
        }


        testingPostgres.runUpdateStatement("UPDATE workflowversion SET verified='t' where name='master'");

        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "verify", "--entry",
                SourceControl.GITHUB.toString() + "/testWorkflow/testWorkflow", "--verified-source",
                "Docker testing group", "--version", "master", "--script" });

        // Run full 1.5.0 migration
        try {
            List<String> migrationList = Arrays.asList("1.5.0");
            CommonTestUtilities.runMigration(migrationList, application, CommonTestUtilities.CONFIDENTIAL_CONFIG_PATH);
        } catch (Exception e) {
            Assert.fail("Could not run 1.5.0 migration");
        }

        final long afterMigrationVerifiedCount = testingPostgres
                .runSelectStatement("select count(*) from sourcefile_verified", long.class);
        Assert.assertEquals("There should be 2 entries in sourcefile_verified after the migration but got: " + afterMigrationVerifiedCount, 2,
                afterMigrationVerifiedCount);
    }
}
