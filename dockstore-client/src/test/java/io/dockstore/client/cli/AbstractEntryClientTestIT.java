package io.dockstore.client.cli;

import io.dockstore.client.cli.nested.AbstractEntryClient;
import io.dockstore.client.cli.nested.WesCommandParser;
import io.dockstore.client.cli.nested.WesRequestData;
import io.dockstore.client.cli.nested.WorkflowClient;
import io.dropwizard.testing.ResourceHelpers;
import io.swagger.client.api.UsersApi;
import io.swagger.client.api.WorkflowsApi;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class AbstractEntryClientTestIT {

    static final String CONFIG_NO_CONTENT_RESOURCE = "configNoContent";


    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();
    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog();
    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog();
    @Rule
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    /**
     * Tests the help messages for each of the WES command options
     */
    @Test
    public void testWESHelpMessages() {
        final String clientConfig = ResourceHelpers.resourceFilePath("clientConfig");
        final String[] commandNames = {"", "launch", "status", "cancel", "service-info"};

        // has config file
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

        // Empty config file
        for (String command : commandNames) {
            String[] commandStatement;

            if (command.length() == 0) {
                commandStatement = new String[]{ "workflow", "wes", "--help", "--config", CONFIG_NO_CONTENT_RESOURCE};
            } else {
                commandStatement = new String[]{ "workflow", "wes", command, "--help", "--config", CONFIG_NO_CONTENT_RESOURCE};
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
    public void testNoArgs() {

        AbstractEntryClient workflowClient = testAggregateHelper(null);

        String[] args = {};
        WesCommandParser parser = new WesCommandParser();
        parser.jCommander.parse(args);

        systemExit.expectSystemExit();
        workflowClient.aggregateWesRequestData(parser);
        assertFalse("The config file doesn't exist", systemErrRule.getLog().isBlank());
    }

    @Test
    public void testAggregateAWSCredentialBadProfile() {

        AbstractEntryClient workflowClient = testAggregateHelper("configNoContent");

        String[] args = {
            "service-info"
        };
        WesCommandParser parser = new WesCommandParser();
        parser.jCommander.parse(args);
        systemExit.expectSystemExit();
        workflowClient.aggregateWesRequestData(parser);
        assertFalse("There should be error logs", systemErrRule.getLog().isEmpty());
    }

    @Test
    public void testAggregateAWSCredentialNoRegion() {

        AbstractEntryClient workflowClient = testAggregateHelper("configWithAwsProfile");

        String[] args = {
            "service-info",
            "--wes-url",
            "myUrl"
        };

        String credentials  = ResourceHelpers.resourceFilePath("fakeAwsCredentials");
        String config = ResourceHelpers.resourceFilePath("fakeAwsConfig");
        environmentVariables.set("AWS_CONFIG_FILE", config);
        environmentVariables.set("AWS_CREDENTIAL_PROFILES_FILE", credentials);

        WesCommandParser parser = new WesCommandParser();
        parser.jCommander.parse(args);
        WesRequestData data = workflowClient.aggregateWesRequestData(parser);

        assertEquals("AWS region should be parsed", "REGION", data.getAwsRegion());
    }

    @Test
    public void testAggregateAWSCredentialCompleteCommand() {

        AbstractEntryClient workflowClient = testAggregateHelper("configWithAwsProfile");

        String[] args = {
            "service-info",
            "--wes-url",
            "myUrl"
        };

        String credentials  = ResourceHelpers.resourceFilePath("fakeAwsCredentials");
        String config = ResourceHelpers.resourceFilePath("fakeAwsConfig");
        environmentVariables.set("AWS_CONFIG_FILE", config);
        environmentVariables.set("AWS_CREDENTIAL_PROFILES_FILE", credentials);

        WesCommandParser parser = new WesCommandParser();
        parser.jCommander.parse(args);
        WesRequestData data = workflowClient.aggregateWesRequestData(parser);

        assertEquals("AWS access key should be parsed", "KEY", data.getAwsAccessKey());
        assertEquals("AWS secret key should be parsed", "SECRET_KEY", data.getAwsSecretKey());
        assertEquals("AWS region should be parsed", "REGION", data.getAwsRegion());
        assertEquals("AWS region should be parsed", WesRequestData.CredentialType.AWS_PERMANENT_CREDENTIALS, data.getCredentialType());

    }

    @Test
    public void testAggregateAWSCredentialCompleteCommandMalformed() {

        AbstractEntryClient workflowClient = testAggregateHelper("configWithAwsProfile");

        String[] args = {
            "service-info",
            "--wes-url",
            "myUrl"
        };

        String credentials  = ResourceHelpers.resourceFilePath("fakeMalformedAwsCredentials");
        String config = ResourceHelpers.resourceFilePath("fakeAwsConfig");
        environmentVariables.set("AWS_CONFIG_FILE", config);
        environmentVariables.set("AWS_CREDENTIAL_PROFILES_FILE", credentials);

        WesCommandParser parser = new WesCommandParser();
        parser.jCommander.parse(args);
        systemExit.expectSystemExit();
        workflowClient.aggregateWesRequestData(parser);
        assertFalse("There should be error logs", systemErrRule.getLog().isEmpty());
    }

    @Test
    public void testAggregateAWSCredentialNoProfileAuth() {

        AbstractEntryClient workflowClient = testAggregateHelper("configWithAwsProfileNoAuth");

        String[] args = {
            "service-info",
            "--wes-url",
            "myUrl"
        };

        String credentials  = ResourceHelpers.resourceFilePath("fakeAwsCredentials2");
        String config = ResourceHelpers.resourceFilePath("fakeAwsConfig");
        environmentVariables.set("AWS_CONFIG_FILE", config);
        environmentVariables.set("AWS_CREDENTIAL_PROFILES_FILE", credentials);

        WesCommandParser parser = new WesCommandParser();
        parser.jCommander.parse(args);
        WesRequestData data = workflowClient.aggregateWesRequestData(parser);

        assertEquals("AWS credential type should be set", WesRequestData.CredentialType.AWS_PERMANENT_CREDENTIALS, data.getCredentialType());
        assertEquals("AWS region should be parsed", "REGION2", data.getAwsRegion());

    }

    @Test
    public void testAggregateAWSCredentialNoProfileAuthDefault() {

        AbstractEntryClient workflowClient = testAggregateHelper("configWithAwsProfileNoAuth");

        String credentials  = ResourceHelpers.resourceFilePath("fakeAwsCredentials2");
        String config = ResourceHelpers.resourceFilePath("fakeAwsConfig");
        String[] args = {
            "service-info",
            "--wes-url",
            "myUrl"
        };

        environmentVariables.set("AWS_CONFIG_FILE", config);
        environmentVariables.set("AWS_CREDENTIAL_PROFILES_FILE", credentials);

        WesCommandParser parser = new WesCommandParser();
        parser.jCommander.parse(args);
        WesRequestData data = workflowClient.aggregateWesRequestData(parser);

        assertEquals("AWS access key should be parsed", "KEY2", data.getAwsAccessKey());
        assertEquals("AWS secret key should be parsed", "SECRET_KEY2", data.getAwsSecretKey());
        assertEquals("AWS region should be parsed", "REGION2", data.getAwsRegion());
        assertEquals("AWS region should be parsed", WesRequestData.CredentialType.AWS_PERMANENT_CREDENTIALS, data.getCredentialType());
    }

    @Test
    public void testAggregateBearerCredentialCompleteCommand() {

        AbstractEntryClient workflowClient = testAggregateHelper("configWithBearerToken");
        
        String[] args = {
            "service-info",
            "--wes-url",
            "myUrl"
        };

        WesCommandParser parser = new WesCommandParser();
        parser.jCommander.parse(args);
        WesRequestData data = workflowClient.aggregateWesRequestData(parser);

        assertEquals("Bearer token should be parsed", "myToken", data.getBearerToken());
        assertEquals("WES URL should be parsed", "myUrl", data.getUrl());
        assertEquals("AWS region should be parsed", WesRequestData.CredentialType.BEARER_TOKEN, data.getCredentialType());
    }

    @Test
    public void testAggregateBearerCredentialAWSConfigFile() {

        AbstractEntryClient workflowClient = testAggregateHelper("configWithAwsProfile");

        String[] args = {
            "service-info"
        };

        String credentials  = ResourceHelpers.resourceFilePath("fakeAwsCredentials");
        String config = ResourceHelpers.resourceFilePath("fakeAwsConfig");
        environmentVariables.set("AWS_CONFIG_FILE", config);
        environmentVariables.set("AWS_CREDENTIAL_PROFILES_FILE", credentials);

        WesCommandParser parser = new WesCommandParser();
        parser.jCommander.parse(args);
        WesRequestData data = workflowClient.aggregateWesRequestData(parser);

        assertEquals("AWS access key should be parsed", "KEY", data.getAwsAccessKey());
        assertEquals("AWS secret key should be parsed", "SECRET_KEY", data.getAwsSecretKey());
        assertEquals("AWS region should be parsed", "REGION", data.getAwsRegion());
        assertEquals("AWS region should be parsed", WesRequestData.CredentialType.AWS_PERMANENT_CREDENTIALS, data.getCredentialType());

    }

    @Test
    public void testAggregateAWSCredentialSessionToken() {

        AbstractEntryClient workflowClient = testAggregateHelper("configWithAwsSessionProfile");

        String[] args = {
            "service-info",
            "--wes-url",
            "myUrl"
        };

        String credentials  = ResourceHelpers.resourceFilePath("fakeAwsCredentials2");
        String config = ResourceHelpers.resourceFilePath("fakeAwsConfig");
        environmentVariables.set("AWS_CONFIG_FILE", config);
        environmentVariables.set("AWS_CREDENTIAL_PROFILES_FILE", credentials);

        WesCommandParser parser = new WesCommandParser();
        parser.jCommander.parse(args);
        WesRequestData data = workflowClient.aggregateWesRequestData(parser);

        assertEquals("AWS access key should be parsed", "KEY3", data.getAwsAccessKey());
        assertEquals("AWS secret key should be parsed", "SECRET_KEY3", data.getAwsSecretKey());
        assertEquals("AWS secret key should be parsed", "TOKEN3", data.getAwsSessionToken());
        assertEquals("AWS region should be parsed", "REGION3", data.getAwsRegion());
        assertEquals("AWS credentials type should be temporary", WesRequestData.CredentialType.AWS_TEMPORARY_CREDENTIALS, data.getCredentialType());

    }

}
