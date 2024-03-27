/*
 *    Copyright 2017 OICR
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package io.dockstore.client.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.codahale.metrics.Gauge;
import io.dockstore.common.CLICommonTestUtilities;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.MuteForSuccessfulTests;
import io.dockstore.common.SourceControl;
import io.dockstore.common.TestingPostgres;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.api.UsersApi;
import io.dockstore.openapi.client.api.WorkflowsApi;
import io.dockstore.openapi.client.model.Repository;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dropwizard.testing.DropwizardTestSupport;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.TimeUnit;
import org.jline.utils.Log;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

/**
 * Base integration test class
 * A default configuration that cleans the database between tests
 */
@ExtendWith(SystemStubsExtension.class)
@ExtendWith(MuteForSuccessfulTests.class)
@ExtendWith(BaseIT.TestStatus.class)
@Tag(ConfidentialTest.NAME)
class BaseIT {

    public static final String INSTALLATION_ID = "1179416";
    public static final String ADMIN_USERNAME = "admin@admin.com";
    public static final String USER_1_USERNAME = "DockstoreTestUser";
    public static final String USER_2_USERNAME = "DockstoreTestUser2";
    public static final DropwizardTestSupport<DockstoreWebserviceConfiguration> SUPPORT = new DropwizardTestSupport<>(
        DockstoreWebserviceApplication.class, CLICommonTestUtilities.CONFIDENTIAL_CONFIG_PATH);
    protected static TestingPostgres testingPostgres;
    static final String OTHER_USERNAME = "OtherUser";
    final String curatorUsername = "curator@curator.com";

    @BeforeAll
    public static void dropAndRecreateDB() throws Exception {
        CommonTestUtilities.dropAndRecreateNoTestData(SUPPORT);
        SUPPORT.before();
        testingPostgres = new TestingPostgres(SUPPORT);
    }

    public static void assertNoMetricsLeaks(DropwizardTestSupport<DockstoreWebserviceConfiguration> support) throws InterruptedException {
        SortedMap<String, Gauge> gauges = support.getEnvironment().metrics().getGauges();
        int active = (int)gauges.get("io.dropwizard.db.ManagedPooledDataSource.hibernate.active").getValue();
        int waiting = (int)gauges.get("io.dropwizard.db.ManagedPooledDataSource.hibernate.waiting").getValue();
        if (active != 0 || waiting != 0) {
            // Waiting 10 seconds to see if active connection disappears
            TimeUnit.SECONDS.sleep(10);
            active = (int)gauges.get("io.dropwizard.db.ManagedPooledDataSource.hibernate.active").getValue();
            waiting = (int)gauges.get("io.dropwizard.db.ManagedPooledDataSource.hibernate.waiting").getValue();
            assertEquals(0, active, "There should be no active connections");
            assertEquals(0, waiting, "There should be no waiting connections");
        }
    }

    @AfterAll
    public static void afterClass() {
        SUPPORT.after();
    }

    /**
     * the following were migrated from SwaggerClientIT and can be eventually merged. Note different config file used
     */

    protected static ApiClient getWebClient(String username, TestingPostgres testingPostgresParameter) {
        return CLICommonTestUtilities.getWebClient(true, username, testingPostgresParameter);
    }

    protected void refreshByOrganizationReplacement(final String username, Set<String> whitelist) {
        final ApiClient webClient = getWebClient(username, testingPostgres);
        final WorkflowsApi workflowsApi = new WorkflowsApi(webClient);
        refreshByOrganizationReplacement(workflowsApi, webClient, whitelist);
    }

    protected void refreshByOrganizationReplacement(WorkflowsApi workflowApi, ApiClient apiClient, Set<String> whitelist) {
        UsersApi openUsersApi = new UsersApi(apiClient);
        for (SourceControl control : SourceControl.values()) {
            List<String> userOrganizations = openUsersApi.getUserOrganizations(control.name());
            for (String org : userOrganizations) {
                List<Repository> userOrganizationRepositories = openUsersApi.getUserOrganizationRepositories(control.name(), org);
                for (Repository repo : userOrganizationRepositories) {
                    String pathName = control + "/" + repo.getPath();
                    Log.debug("checking: " + pathName);
                    if (whitelist.contains(pathName)) {
                        workflowApi.manualRegister(control.name(), repo.getPath(), "/Dockstore.cwl", "",
                                DescriptorLanguage.CWL.getShortName(), "");
                    }
                }
            }
        }
    }

    @AfterEach
    public void after() throws InterruptedException {
        assertNoMetricsLeaks(SUPPORT);
    }

    @BeforeEach
    public void resetDBBetweenTests() throws Exception {
        CLICommonTestUtilities.dropAndCreateWithTestData(SUPPORT, false);
    }

    public static class TestStatus implements org.junit.jupiter.api.extension.TestWatcher {
        @Override
        public void testSuccessful(ExtensionContext context) {
            System.out.printf("Test successful: %s%n", context.getTestMethod().get());
        }

        @Override
        public void testFailed(ExtensionContext context, Throwable cause) {
            System.out.printf("Test failed: %s%n", context.getTestMethod().get());
        }
    }
}
