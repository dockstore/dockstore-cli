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
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class AbstractEntryClientTestIT {

    static final String FAKE_AWS_REGION = "space-jupyter-7";

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
        final String[] commandNames = {"", "launch", "status", "cancel", "service-info"};

        // has config file
        for (String command : commandNames) {
            String[] commandStatement;

            if (command.length() == 0) {
                commandStatement = new String[]{ "workflow", "wes", "--help"};
            } else {
                commandStatement = new String[]{ "workflow", "wes", command, "--help"};
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
    public void testAggregateAWSCredentialBadConfigPath() {

        AbstractEntryClient workflowClient = testAggregateHelper(null);

        String[] args = {
            "service-info",
            "--aws-region",
            FAKE_AWS_REGION,
            "--wes-auth",
            "aws",
            "myProfile",
            "--aws-config",
            "bad/path/to/nowehere"
        };
        WesCommandParser parser = new WesCommandParser();
        parser.jCommander.parse(args);

        systemExit.expectSystemExit();
        workflowClient.aggregateWesRequestData(parser);
        assertFalse("The config file doesn't exist", systemErrRule.getLog().isBlank());
    }

    @Test
    public void testAggregateAWSCredentialBadProfile() {

        AbstractEntryClient workflowClient = testAggregateHelper(null);

        String config = ResourceHelpers.resourceFilePath("fakeAwsCredentials");
        String[] args = {
            "service-info",
            "--aws-region",
            FAKE_AWS_REGION,
            "--wes-auth",
            "aws",
            "badProfile",
            "--aws-config",
            config
        };
        WesCommandParser parser = new WesCommandParser();
        parser.jCommander.parse(args);

        systemExit.expectSystemExit();
        workflowClient.aggregateWesRequestData(parser);
        assertFalse("The profile doesn't exist", systemErrRule.getLog().isBlank());
    }

    @Test
    public void testAggregateAWSCredentialNoRegion() {

        AbstractEntryClient workflowClient = testAggregateHelper(null);

        String config = ResourceHelpers.resourceFilePath("fakeAwsCredentials");
        String[] args = {
            "service-info",
            "--wes-url",
            "myUrl",
            "--wes-auth",
            "aws",
            "myProfile",
            "--aws-config",
            config
        };

        WesCommandParser parser = new WesCommandParser();
        parser.jCommander.parse(args);
        WesRequestData data = workflowClient.aggregateWesRequestData(parser);

        assertEquals("No AWS region should've been parsed, an empty string should be return in its place", "", data.getAwsRegion());
    }

    @Test
    public void testAggregateAWSCredentialCompleteCommand() {

        AbstractEntryClient workflowClient = testAggregateHelper(null);

        String config = ResourceHelpers.resourceFilePath("fakeAwsCredentials");
        String[] args = {
            "service-info",
            "--wes-url",
            "myUrl",
            "--wes-auth",
            "aws",
            "myProfile",
            "--aws-config",
            config,
            "--aws-region",
            "somewhere-in-space"
        };

        WesCommandParser parser = new WesCommandParser();
        parser.jCommander.parse(args);
        WesRequestData data = workflowClient.aggregateWesRequestData(parser);

        assertEquals("AWS access key should be parsed", "KEY", data.getAwsAccessKey());
        assertEquals("AWS secret key should be parsed", "SECRET_KEY", data.getAwsSecretKey());
        assertEquals("AWS region should be parsed", "somewhere-in-space", data.getAwsRegion());
        assertEquals("AWS region should be parsed", WesRequestData.CredentialType.AWS_PERMANENT_CREDENTIALS, data.getCredentialType());

    }

    @Test
    public void testAggregateAWSCredentialNoProfileAuth() {

        AbstractEntryClient workflowClient = testAggregateHelper(null);

        String config = ResourceHelpers.resourceFilePath("fakeAwsCredentials");
        String[] args = {
            "service-info",
            "--wes-url",
            "myUrl",
            "--wes-auth",
            "aws",
            "--aws-config",
            config,
            "--aws-region",
            "somewhere-in-space"
        };

        WesCommandParser parser = new WesCommandParser();
        parser.jCommander.parse(args);
        WesRequestData data = workflowClient.aggregateWesRequestData(parser);

        assertEquals("AWS region should be parsed", WesRequestData.CredentialType.NO_CREDENTIALS, data.getCredentialType());
    }

    @Test
    public void testAggregateBearerCredentialCompleteCommand() {

        AbstractEntryClient workflowClient = testAggregateHelper(null);
        
        String[] args = {
            "service-info",
            "--wes-url",
            "myUrl",
            "--wes-auth",
            "bearer",
            "myToken",
        };

        WesCommandParser parser = new WesCommandParser();
        parser.jCommander.parse(args);
        WesRequestData data = workflowClient.aggregateWesRequestData(parser);

        assertEquals("AWS access key should be parsed", "myToken", data.getBearerToken());
        assertEquals("AWS region should be parsed", WesRequestData.CredentialType.BEARER_TOKEN, data.getCredentialType());
    }

    @Test
    public void testAggregateBearerCredentialAWSConfigFile() {

        AbstractEntryClient workflowClient = testAggregateHelper("configWithAwsProfile");
        String config = ResourceHelpers.resourceFilePath("fakeAwsCredentials");

        String[] args = {
            "service-info",
            "--aws-config",
            config
        };

        WesCommandParser parser = new WesCommandParser();
        parser.jCommander.parse(args);
        WesRequestData data = workflowClient.aggregateWesRequestData(parser);

        assertEquals("AWS access key should be parsed", "KEY", data.getAwsAccessKey());
        assertEquals("AWS secret key should be parsed", "SECRET_KEY", data.getAwsSecretKey());
        assertEquals("AWS region should be parsed", "somewhere-in-space", data.getAwsRegion());
        assertEquals("AWS region should be parsed", WesRequestData.CredentialType.AWS_PERMANENT_CREDENTIALS, data.getCredentialType());

    }

}
