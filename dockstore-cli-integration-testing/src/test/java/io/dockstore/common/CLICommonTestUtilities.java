/*
 *    Copyright 2018 OICR
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

package io.dockstore.common;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.google.common.hash.Hashing;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.ApiException;
import io.dockstore.openapi.client.model.DockstoreTool;
import io.dockstore.openapi.client.model.Tag;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.jdbi.TokenDAO;
import io.dropwizard.core.Application;
import io.dropwizard.testing.DropwizardTestSupport;
import io.dropwizard.testing.ResourceHelpers;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.io.FileUtils;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.context.internal.ManagedSessionContext;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author xliu
 */
public final class CLICommonTestUtilities {


    /**
     * confidential testing config, includes keys
     */
    public static final String CONFIDENTIAL_CONFIG_PATH;

    private static final Logger LOG = LoggerFactory.getLogger(CLICommonTestUtilities.class);

    static {
        String confidentialConfigPath = null;
        try {
            confidentialConfigPath = ResourceHelpers.resourceFilePath("dockstoreTest.yml");
        } catch (Exception e) {
            LOG.error("Confidential Dropwizard configuration file not found.", e);

        }
        CONFIDENTIAL_CONFIG_PATH = confidentialConfigPath;
    }

    private CLICommonTestUtilities() {

    }


    /**
     * Drops the database and recreates from migrations for non-confidential tests
     *
     * @param support reference to testing instance of the dockstore web service
     * @throws Exception
     */
    public static void dropAndCreateWithTestData(DropwizardTestSupport<DockstoreWebserviceConfiguration> support, boolean isNewApplication)
        throws Exception {
        dropAndCreateWithTestData(support, isNewApplication, CONFIDENTIAL_CONFIG_PATH);
    }

    public static void dropAndCreateWithTestData(DropwizardTestSupport<DockstoreWebserviceConfiguration> support, boolean isNewApplication,
        String dropwizardConfigurationFile) throws Exception {
        LOG.info("Dropping and Recreating the database with non-confidential test data");
        Application<DockstoreWebserviceConfiguration> application;
        if (isNewApplication) {
            application = support.newApplication();
        } else {
            application = support.getApplication();
        }
        application.run("db", "drop-all", "--confirm-delete-everything", dropwizardConfigurationFile);

        List<String> migrationList = Arrays
            .asList("1.3.0.generated", "1.3.1.consistency", "test", "1.4.0", "1.5.0", "test_1.5.0", "1.6.0", "1.7.0",
                    "1.8.0", "1.9.0", "1.10.0", "1.11.0", "1.12.0", "1.13.0", "1.14.0", "1.15.0");
        CommonTestUtilities.runMigration(migrationList, application, dropwizardConfigurationFile);
    }

    /**
     * Shared convenience method
     *
     * @return
     */
    public static ApiClient getWebClient(boolean authenticated, String username, TestingPostgres testingPostgres) {
        File configFile = FileUtils.getFile("src", "test", "resources", "config2");
        INIConfiguration parseConfig = Utilities.parseConfig(configFile.getAbsolutePath());
        ApiClient client = new ApiClient();
        client.setBasePath(parseConfig.getString(Constants.WEBSERVICE_BASE_PATH));
        if (authenticated) {
            client.addDefaultHeader("Authorization", "Bearer " + (testingPostgres
                .runSelectStatement("select content from token where tokensource='dockstore' and username= '" + username + "';",
                    String.class)));
        }
        return client;
    }

    /**
     * Shared convenience method for openApi Client
     *
     * @return
     */
    public static io.dockstore.openapi.client.ApiClient getOpenApiWebClient(boolean authenticated, String username, TestingPostgres testingPostgres) {
        File configFile = FileUtils.getFile("src", "test", "resources", "config2");
        INIConfiguration parseConfig = Utilities.parseConfig(configFile.getAbsolutePath());
        io.dockstore.openapi.client.ApiClient client = new io.dockstore.openapi.client.ApiClient();
        client.setBasePath(parseConfig.getString(Constants.WEBSERVICE_BASE_PATH));
        if (authenticated) {
            client.addDefaultHeader("Authorization", "Bearer " + (testingPostgres
                .runSelectStatement("select content from token where tokensource='dockstore' and username= '" + username + "';",
                    String.class)));
        }
        return client;
    }

    /**
     * Deletes BitBucket Tokens from Database
     *
     * @param testingPostgres reference to the testing instance of Postgres
     */
    public static void deleteBitBucketToken(TestingPostgres testingPostgres)  {
        LOG.info("Deleting BitBucket Token from Database");
        testingPostgres.runUpdateStatement("delete from token where tokensource = 'bitbucket.org'");
    }

    /**
     * Wrapper for dropping and recreating database from migrations and optionally deleting bitbucket tokens
     *
     * @param support reference to testing instance of the dockstore web service
     * @param testingPostgres reference to the testing instance of Postgres
     * @param needBitBucketToken if false the bitbucket token will be deleted
     * @throws Exception
     */
    public static void cleanStatePrivate1(DropwizardTestSupport<DockstoreWebserviceConfiguration> support,
        TestingPostgres testingPostgres, Boolean needBitBucketToken) throws Exception {
        LOG.info("Dropping and Recreating the database with confidential 1 test data");
        cleanStatePrivate1(support, CONFIDENTIAL_CONFIG_PATH);
        handleBitBucketTokens(support, testingPostgres, needBitBucketToken);
    }
    /**
     * Wrapper for dropping and recreating database from migrations for test confidential 1
     *
     * @param support reference to testing instance of the dockstore web service
     * @param testingPostgres reference to the testing instance of Postgres
     * @throws Exception
     */
    public static void cleanStatePrivate1(DropwizardTestSupport<DockstoreWebserviceConfiguration> support, TestingPostgres testingPostgres) throws Exception {
        cleanStatePrivate1(support, testingPostgres, false);
    }

    /**
     * Drops and recreates database from migrations for test confidential 1
     *
     * @param support    reference to testing instance of the dockstore web service
     * @param configPath
     * @throws Exception
     */
    private static void cleanStatePrivate1(DropwizardTestSupport<DockstoreWebserviceConfiguration> support, String configPath)
        throws Exception {
        Application<DockstoreWebserviceConfiguration> application = support.getApplication();
        application.run("db", "drop-all", "--confirm-delete-everything", configPath);

        List<String> migrationList = Arrays.asList("1.3.0.generated", "1.3.1.consistency");
        CommonTestUtilities.runMigration(migrationList, application, configPath);

        migrationList = Collections.singletonList(
                new File("../dockstore-webservice/src/main/resources/migrations.test.confidential1.xml").getAbsolutePath());
        runExternalMigration(migrationList, application, configPath);

        migrationList = Arrays.asList("1.4.0", "1.5.0");
        CommonTestUtilities.runMigration(migrationList, application, configPath);

        migrationList = Collections.singletonList(
                new File("../dockstore-webservice/src/main/resources/migrations.test.confidential1_1.5.0.xml").getAbsolutePath());
        runExternalMigration(migrationList, application, configPath);

        migrationList = Arrays.asList("1.6.0", "1.7.0", "1.8.0", "1.9.0", "1.10.0", "1.11.0", "1.12.0", "1.13.0", "1.14.0", "1.15.0");
        CommonTestUtilities.runMigration(migrationList, application, configPath);
    }

    /**
     * TODO: do not modify, should be deleted with next webservice release if the method can be made public
     * @param support
     * @param testingPostgres
     * @param needBitBucketToken
     */
    private static void handleBitBucketTokens(DropwizardTestSupport<DockstoreWebserviceConfiguration> support, TestingPostgres testingPostgres, boolean needBitBucketToken) {
        if (!needBitBucketToken) {
            deleteBitBucketToken(testingPostgres);
        } else {
            DockstoreWebserviceApplication application = support.getApplication();
            Session session = application.getHibernate().getSessionFactory().openSession();
            ManagedSessionContext.bind(session);
            //TODO restore bitbucket token from disk cache to reduce rate limit from busting cache with new access tokens
            SessionFactory sessionFactory = application.getHibernate().getSessionFactory();
            TokenDAO tokenDAO = new TokenDAO(sessionFactory);
            final List<Token> allBitBucketTokens = tokenDAO.findAllBitBucketTokens();
            Gson gson = new GsonBuilder().setPrettyPrinting().create();

            for (Token token : allBitBucketTokens) {
                try {
                    final String cacheCandidate = FileUtils.readFileToString(new File(CommonTestUtilities.BITBUCKET_TOKEN_CACHE + Hashing.sha256().hashString(token.getRefreshToken(), StandardCharsets.UTF_8) + ".json"),
                            StandardCharsets.UTF_8);
                    final Token cachedToken = gson.fromJson(cacheCandidate, Token.class);
                    if (cachedToken != null) {
                        testingPostgres.runUpdateStatement(
                                "update token set content = '" + cachedToken.getContent() + "', dbUpdateDate = '" + cachedToken.getDbUpdateDate().toLocalDateTime().toString() + "' where id = "
                                        + cachedToken.getId());
                    }
                } catch (IOException | UncheckedIOException e) {
                    // probably ok
                    LOG.debug("could not read bitbucket token", e);
                }
            }
        }
    }

    private static void runExternalMigration(List<String> migrationList, Application<DockstoreWebserviceConfiguration> application,
        String configPath) {
        migrationList.forEach(migration -> {
            try {
                application.run("db", "migrate", configPath, "--migrations", migration);
            } catch (Exception e) {
                Assert.fail();
            }
        });
    }

    /**
     * Wrapper for dropping and recreating database from migrations for test confidential 2 and optionally deleting BitBucket tokens
     *
     * @param support reference to testing instance of the dockstore web service
     * @throws Exception
     */
    public static void cleanStatePrivate2(DropwizardTestSupport<DockstoreWebserviceConfiguration> support, boolean isNewApplication,
        TestingPostgres testingPostgres, boolean needBitBucketToken) throws Exception {
        LOG.info("Dropping and Recreating the database with confidential 2 test data");
        cleanStatePrivate2(support, CONFIDENTIAL_CONFIG_PATH, isNewApplication);
        handleBitBucketTokens(support, testingPostgres, needBitBucketToken);
    }

    /**
     * Wrapper for dropping and recreating database from migrations for test confidential 2
     *
     * @param support reference to testing instance of the dockstore web service
     * @throws Exception
     */
    public static void cleanStatePrivate2(DropwizardTestSupport<DockstoreWebserviceConfiguration> support, boolean isNewApplication, TestingPostgres testingPostgres)
        throws Exception {
        cleanStatePrivate2(support, isNewApplication, testingPostgres, false);
        // TODO: You can uncomment the following line to disable GitLab tool and workflow discovery
        // getTestingPostgres(SUPPORT).runUpdateStatement("delete from token where tokensource = 'gitlab.com'");
    }

    /**
     * Drops and recreates database from migrations for test confidential 2
     *
     * @param support    reference to testing instance of the dockstore web service
     * @param configPath
     * @throws Exception
     */
    private static void cleanStatePrivate2(DropwizardTestSupport<DockstoreWebserviceConfiguration> support, String configPath,
        boolean isNewApplication) throws Exception {
        Application<DockstoreWebserviceConfiguration> application;
        if (isNewApplication) {
            application = support.newApplication();
        } else {
            application = support.getApplication();
        }
        application.run("db", "drop-all", "--confirm-delete-everything", configPath);

        List<String> migrationList = Arrays.asList("1.3.0.generated", "1.3.1.consistency");
        CommonTestUtilities.runMigration(migrationList, application, configPath);

        migrationList = Collections.singletonList(
                new File("../dockstore-webservice/src/main/resources/migrations.test.confidential2.xml").getAbsolutePath());
        runExternalMigration(migrationList, application, configPath);


        migrationList = Arrays.asList("1.4.0", "1.5.0");
        CommonTestUtilities.runMigration(migrationList, application, configPath);

        migrationList = Collections.singletonList(
                new File("../dockstore-webservice/src/main/resources/migrations.test.confidential2_1.5.0.xml").getAbsolutePath());
        runExternalMigration(migrationList, application, configPath);

        migrationList = Arrays.asList("1.6.0", "1.7.0", "1.8.0", "1.9.0", "1.10.0", "1.11.0", "1.12.0", "1.13.0", "1.14.0", "1.15.0");
        CommonTestUtilities.runMigration(migrationList, application, configPath);
    }

    public static void checkToolList(String log) {
        Assert.assertTrue(log.contains("NAME"));
        Assert.assertTrue(log.contains("DESCRIPTION"));
        Assert.assertTrue(log.contains("GIT REPO"));
    }

    /**
     * This method will create and register a new container for testing
     *
     * @return DockstoreTool
     * @throws ApiException comes back from a web service error
     */
    public static DockstoreTool getContainer() {
        DockstoreTool c = new DockstoreTool();
        c.setMode(DockstoreTool.ModeEnum.MANUAL_IMAGE_PATH);
        c.setName("testUpdatePath");
        c.setGitUrl("https://github.com/DockstoreTestUser2/dockstore-tool-imports");
        c.setDefaultDockerfilePath("/Dockerfile");
        c.setDefaultCwlPath("/dockstore.cwl");
        c.setRegistryString(Registry.DOCKER_HUB.getDockerPath());
        c.setIsPublished(false);
        c.setNamespace("testPath");
        c.setToolname("test5");
        c.setPath("quay.io/dockstoretestuser2/dockstore-tool-imports");
        Tag tag = new Tag();
        tag.setName("1.0");
        tag.setReference("master");
        tag.setValid(true);
        tag.setImageId("123456");
        tag.setCwlPath(c.getDefaultCwlPath());
        tag.setWdlPath(c.getDefaultWdlPath());
        List<Tag> tags = new ArrayList<>();
        tags.add(tag);
        c.setTags(tags);
        return c;
    }
}
