package io.dockstore.client.cli;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.dockstore.client.cli.nested.AbstractEntryClient;
import io.dockstore.client.cli.nested.WesRequestData;
import io.dockstore.client.cli.nested.WorkflowClient;
import io.dropwizard.testing.ResourceHelpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import io.swagger.client.api.UsersApi;
import io.swagger.client.api.WorkflowsApi;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;

public class AbstractEntryClientTestIT {

    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();
    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog();
    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog();

    // These constants also match the data in clientConfigAws
    static final String FAKE_WES_URL = "veryveryfakeurl";
    static final String FAKE_AWS_ACCESS_KEY = "123456789";
    static final String FAKE_AWS_SECRET_KEY = "987654321";
    static final String FAKE_AWS_REGION = "space-jupyter-7";
    static final String AWS_CONFIG_RESOURCE = "clientConfigAws";

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

    @Test
    public void testAggregateCredentialDataNoConfig() {

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();

        // client.setConfigFile(ResourceHelpers.resourceFilePath("clientConfig"));

        AbstractEntryClient workflowClient = new WorkflowClient(api, usersApi, client, false);

        final List<String> command_starter_aws = Arrays.asList("workflow", "wes", "service-info", "--aws");
        final List<String> wes_url = Arrays.asList("--wes-url", FAKE_WES_URL);
        final List<String> aws_access = Arrays.asList("--aws-access-key", FAKE_AWS_ACCESS_KEY);
        final List<String> aws_secret = Arrays.asList("--aws-secret-key", FAKE_AWS_SECRET_KEY);
        final List<String> aws_region = Arrays.asList("--aws-region", FAKE_AWS_REGION);

        // Tests complete command
        List<String> args = new ArrayList<>(command_starter_aws);
        args.addAll(aws_access);
        args.addAll(aws_secret);
        args.addAll(aws_region);
        args.addAll(wes_url);

        WesRequestData requestData = workflowClient.aggregateWesRequestData(args);
        assertTrue("The data parsed should be AWS credentials", requestData.requiresAwsHeaders());
        assertEquals("The data should have parsed an access key", FAKE_AWS_ACCESS_KEY, requestData.getAwsAccessKey());
        assertEquals("The data should have parsed a secret key", FAKE_AWS_SECRET_KEY, requestData.getAwsSecretKey());
        assertEquals("The data should have parsed a region", FAKE_AWS_REGION, requestData.getAwsRegion());
        assertEquals("The data should have parsed a url", FAKE_WES_URL, requestData.getUrl());

        // Test incomplete commands, no url
        args = new ArrayList<>(command_starter_aws);
        args.addAll(aws_access);
        args.addAll(aws_secret);
        args.addAll(aws_region);
        systemExit.expectSystemExit();
        workflowClient.aggregateWesRequestData(args);
        assertFalse("No URL was passed in", systemErrRule.getLog().isBlank());

        // Test incomplete command, missing AWS creds
        args = new ArrayList<>(command_starter_aws);
        args.addAll(aws_access);
        args.addAll(aws_region);
        args.addAll(wes_url);
        systemExit.expectSystemExit();
        workflowClient.aggregateWesRequestData(args);
        assertFalse("No secret was passed in", systemErrRule.getLog().isBlank());

        // Test incomplete command, missing AWS region
        args = new ArrayList<>(command_starter_aws);
        args.addAll(aws_access);
        args.addAll(aws_secret);
        args.addAll(wes_url);
        systemExit.expectSystemExit();
        workflowClient.aggregateWesRequestData(args);
        assertFalse("No region was passed in", systemErrRule.getLog().isBlank());
    }

    @Test
    public void testAggregateCredentialDataWithConfig() {
        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();

        client.setConfigFile(ResourceHelpers.resourceFilePath(AWS_CONFIG_RESOURCE));

        AbstractEntryClient workflowClient = new WorkflowClient(api, usersApi, client, false);

        final List<String> command_starter_aws = Arrays.asList("workflow", "wes", "service-info", "--aws");
        final List<String> wes_url = Arrays.asList("--wes-url", FAKE_WES_URL);
        final List<String> aws_access = Arrays.asList("--aws-access-key", FAKE_AWS_ACCESS_KEY);
        final List<String> aws_secret = Arrays.asList("--aws-secret-key", FAKE_AWS_SECRET_KEY);
        final List<String> aws_region = Arrays.asList("--aws-region", FAKE_AWS_REGION);

        // Tests complete command
        List<String> args = new ArrayList<>(command_starter_aws);
        args.addAll(aws_access);
        args.addAll(aws_secret);
        args.addAll(aws_region);
        args.addAll(wes_url);
        WesRequestData requestData = workflowClient.aggregateWesRequestData(args);
        assertTrue("The data parsed should be AWS credentials", requestData.requiresAwsHeaders());
        assertEquals("The data should have parsed an access key", FAKE_AWS_ACCESS_KEY, requestData.getAwsAccessKey());
        assertEquals("The data should have parsed a secret key", FAKE_AWS_SECRET_KEY, requestData.getAwsSecretKey());
        assertEquals("The data should have parsed a region", FAKE_AWS_REGION, requestData.getAwsRegion());
        assertEquals("The data should have parsed a url", FAKE_WES_URL, requestData.getUrl());

        // Tests complete command with data from config
        args = new ArrayList<>(command_starter_aws);
        args.addAll(aws_access);
        args.addAll(aws_secret);
        args.addAll(aws_region);
        requestData = workflowClient.aggregateWesRequestData(args);
        assertTrue("The data parsed should be AWS credentials", requestData.requiresAwsHeaders());
        assertEquals("The data should have parsed an access key", FAKE_AWS_ACCESS_KEY, requestData.getAwsAccessKey());
        assertEquals("The data should have parsed a secret key", FAKE_AWS_SECRET_KEY, requestData.getAwsSecretKey());
        assertEquals("The data should have parsed a region", FAKE_AWS_REGION, requestData.getAwsRegion());
        assertEquals("The data should have parsed a url", FAKE_WES_URL, requestData.getUrl());

        // Tests complete command with data from config
        args = new ArrayList<>(command_starter_aws);
        args.addAll(aws_access);
        requestData = workflowClient.aggregateWesRequestData(args);
        assertTrue("The data parsed should be AWS credentials", requestData.requiresAwsHeaders());
        assertEquals("The data should have parsed an access key", FAKE_AWS_ACCESS_KEY, requestData.getAwsAccessKey());
        assertEquals("The data should have parsed a secret key", FAKE_AWS_SECRET_KEY, requestData.getAwsSecretKey());
        assertEquals("The data should have parsed a region", FAKE_AWS_REGION, requestData.getAwsRegion());
        assertEquals("The data should have parsed a url", FAKE_WES_URL, requestData.getUrl());

        // Tests complete command with data from config
        args = new ArrayList<>(command_starter_aws);
        requestData = workflowClient.aggregateWesRequestData(args);
        assertTrue("The data parsed should be AWS credentials", requestData.requiresAwsHeaders());
        assertEquals("The data should have parsed an access key", FAKE_AWS_ACCESS_KEY, requestData.getAwsAccessKey());
        assertEquals("The data should have parsed a secret key", FAKE_AWS_SECRET_KEY, requestData.getAwsSecretKey());
        assertEquals("The data should have parsed a region", FAKE_AWS_REGION, requestData.getAwsRegion());
        assertEquals("The data should have parsed a url", FAKE_WES_URL, requestData.getUrl());
    }
}
