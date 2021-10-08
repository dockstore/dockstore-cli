package io.dockstore.client.cli;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.dockstore.client.cli.nested.AbstractEntryClient;
import io.dockstore.client.cli.nested.WesRequestData;
import io.dockstore.client.cli.nested.WorkflowClient;
import io.dropwizard.testing.ResourceHelpers;
import io.swagger.client.api.UsersApi;
import io.swagger.client.api.WorkflowsApi;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class AbstractEntryClientTestIT {
    
    static final String FAKE_WES_URL = "veryveryfakeurl";

    static final String FAKE_AWS_ACCESS_KEY = "123456789";
    static final String FAKE_AWS_SECRET_KEY = "987654321";
    static final String FAKE_AWS_REGION = "space-jupyter-7";

    // These constants also match the data in clientConfig
    static final String FAKE_BEARER_TOKEN = "Bearer SmokeyTheBearToken";
    static final String BEARER_CONFIG_RESOURCE = "clientConfig";

    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();
    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog();
    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog();

    /**
     * Tests the help messages for each of the WES command options
     */
    @Test
    public void testWESHelpMessages() {
        final String clientConfig = ResourceHelpers.resourceFilePath("clientConfig");
        final String[] commandNames = {"", "launch", "status", "cancel", "service-info"};

        for (String command : commandNames) {
            String[] commandStatement;

            if (command.length() == 0) {
                commandStatement = new String[]{ "workflow", "wes", "--help", "--config", clientConfig };
            } else {
                commandStatement = new String[]{ "workflow", "wes", command, "--help", "--config", clientConfig };
            }

            Client.main(commandStatement);
            assertTrue("There are unexpected error logs", systemErrRule.getLog().isBlank());
        }
    }

    public AbstractEntryClient testAggregateHelper(String configPath) {
        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();

        if (configPath != null) {
            client.setConfigFile(ResourceHelpers.resourceFilePath(configPath));
        }

        AbstractEntryClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        return workflowClient;
    }

    @Test
    public void testAggregateAWSCredentialDataNoUrl() {

        AbstractEntryClient workflowClient = testAggregateHelper(null);

        // Tests complete command
        List<String> args = new ArrayList<>(Arrays.asList("workflow", "wes", "service-info", "--aws",
            "--aws-access-key", FAKE_AWS_ACCESS_KEY,
            "--aws-secret-key", FAKE_AWS_SECRET_KEY,
            "--aws-region", FAKE_AWS_REGION));
        systemExit.expectSystemExit();
        workflowClient.aggregateWesRequestData(args);
        assertFalse("No URL was passed in", systemErrRule.getLog().isBlank());
    }

    @Test
    public void testAggregateAWSCredentialDataNoSecret() {

        AbstractEntryClient workflowClient = testAggregateHelper(null);

        // Tests complete command
        List<String> args = new ArrayList<>(Arrays.asList("workflow", "wes", "service-info", "--aws",
            "--wes-url", FAKE_WES_URL,
            "--aws-access-key", FAKE_AWS_ACCESS_KEY,
            "--aws-region", FAKE_AWS_REGION));
        systemExit.expectSystemExit();
        workflowClient.aggregateWesRequestData(args);
        assertFalse("No secret was passed in", systemErrRule.getLog().isBlank());
    }

    @Test
    public void testAggregateAWSCredentialDataNoAccess() {

        AbstractEntryClient workflowClient = testAggregateHelper(null);

        // Tests complete command
        List<String> args = new ArrayList<>(Arrays.asList("workflow", "wes", "service-info", "--aws",
            "--wes-url", FAKE_WES_URL,
            "--aws-secret-key", FAKE_AWS_SECRET_KEY,
            "--aws-region", FAKE_AWS_REGION));
        systemExit.expectSystemExit();
        workflowClient.aggregateWesRequestData(args);
        assertFalse("No access was passed in", systemErrRule.getLog().isBlank());
    }

    @Test
    public void testAggregateAWSCredentialDataNoConfigFull() {

        AbstractEntryClient workflowClient = testAggregateHelper(null);

        // Tests complete command
        List<String> args = new ArrayList<>(Arrays.asList("workflow", "wes", "service-info", "--aws",
            "--wes-url", FAKE_WES_URL,
            "--aws-access-key", FAKE_AWS_ACCESS_KEY,
            "--aws-secret-key", FAKE_AWS_SECRET_KEY,
            "--aws-region", FAKE_AWS_REGION));

        WesRequestData requestData = workflowClient.aggregateWesRequestData(args);
        assertTrue("The data parsed should be AWS credentials", requestData.requiresAwsHeaders());
        assertEquals("The data should have parsed an access key", FAKE_AWS_ACCESS_KEY, requestData.getAwsAccessKey());
        assertEquals("The data should have parsed a secret key", FAKE_AWS_SECRET_KEY, requestData.getAwsSecretKey());
        assertEquals("The data should have parsed a region", FAKE_AWS_REGION, requestData.getAwsRegion());
        assertEquals("The data should have parsed a url", FAKE_WES_URL, requestData.getUrl());
        systemExit.expectSystemExit();
        requestData.getBearerToken();
        assertFalse("No Bearer token should be set", systemErrRule.getLog().isBlank());
    }

    @Test
    public void testAggregateApiCredentialDataWithNoConfig() {
        AbstractEntryClient workflowClient = testAggregateHelper(null);

        // Tests complete command
        List<String> args = new ArrayList<>(Arrays.asList("workflow", "wes", "service-info",
            "--wes-url", FAKE_WES_URL,
            "--wes-auth", FAKE_BEARER_TOKEN));

        WesRequestData requestData = workflowClient.aggregateWesRequestData(args);
        assertFalse("The data parsed should not be AWS credentials", requestData.requiresAwsHeaders());
        assertEquals("The data should have parsed a url", FAKE_WES_URL, requestData.getUrl());
        assertEquals("The data should have parsed a bearer token", FAKE_BEARER_TOKEN, requestData.getBearerToken());

        systemExit.expectSystemExit();
        requestData.getAwsAccessKey();
        assertFalse("No AWS credentials should be set", systemErrRule.getLog().isBlank());
    }

    @Test
    public void testAggregateApiCredentialDataWithConfigPartial1() {
        AbstractEntryClient workflowClient = testAggregateHelper(BEARER_CONFIG_RESOURCE);

        // Tests complete command
        List<String> args = new ArrayList<>(Arrays.asList("workflow", "wes", "service-info",
            "--wes-url", FAKE_WES_URL));

        WesRequestData requestData = workflowClient.aggregateWesRequestData(args);
        assertFalse("The data parsed should not be AWS credentials", requestData.requiresAwsHeaders());
        assertEquals("The data should have parsed a url", FAKE_WES_URL, requestData.getUrl());
        assertEquals("The data should have parsed a bearer token", FAKE_BEARER_TOKEN, requestData.getBearerToken());

        systemExit.expectSystemExit();
        requestData.getAwsAccessKey();
        assertFalse("No AWS credentials should be set", systemErrRule.getLog().isBlank());
    }

    @Test
    public void testAggregateApiCredentialDataWithConfigPartial2() {
        AbstractEntryClient workflowClient = testAggregateHelper(BEARER_CONFIG_RESOURCE);

        // Tests complete command
        List<String> args = new ArrayList<>(Arrays.asList("workflow", "wes", "service-info"));

        WesRequestData requestData = workflowClient.aggregateWesRequestData(args);
        assertFalse("The data parsed should not be AWS credentials", requestData.requiresAwsHeaders());
        assertEquals("The data should have parsed a url", FAKE_WES_URL, requestData.getUrl());
        assertEquals("The data should have parsed a bearer token", FAKE_BEARER_TOKEN, requestData.getBearerToken());

        systemExit.expectSystemExit();
        requestData.getAwsAccessKey();
        assertFalse("No AWS credentials should be set", systemErrRule.getLog().isBlank());
    }
}
