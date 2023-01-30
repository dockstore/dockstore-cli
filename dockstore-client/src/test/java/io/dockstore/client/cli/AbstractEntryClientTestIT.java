package io.dockstore.client.cli;

import io.dockstore.client.cli.nested.AbstractEntryClient;
import io.dockstore.client.cli.nested.WesCommandParser;
import io.dockstore.client.cli.nested.WesRequestData;
import io.dockstore.client.cli.nested.WorkflowClient;
import io.dropwizard.testing.ResourceHelpers;
import io.swagger.client.api.UsersApi;
import io.swagger.client.api.WorkflowsApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

import static io.dockstore.client.cli.Client.HELP;
import static io.dockstore.client.cli.Client.WORKFLOW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static software.amazon.awssdk.profiles.ProfileFileSystemSetting.AWS_CONFIG_FILE;
import static software.amazon.awssdk.profiles.ProfileFileSystemSetting.AWS_SHARED_CREDENTIALS_FILE;
import static uk.org.webcompere.systemstubs.SystemStubs.catchSystemExit;

@ExtendWith(SystemStubsExtension.class)
class AbstractEntryClientTestIT {

    static final String CONFIG_NO_CONTENT_RESOURCE = "configNoContent";

    @SystemStub
    public final SystemOut systemOutRule = new SystemOut();

    @SystemStub
    public final SystemErr systemErrRule = new SystemErr();

    @SystemStub
    private EnvironmentVariables environmentVariables;

    /**
     * Tests the help messages for each of the WES command options
     */
    @Test
    void testWESHelpMessages() {
        final String clientConfig = ResourceHelpers.resourceFilePath("clientConfig");
        final String[] commandNames = {"", "launch", "status", "cancel", "service-info", "logs", "list"};

        // has config file
        for (String command : commandNames) {
            String[] commandStatement;

            if (command.length() == 0) {
                commandStatement = new String[]{ WORKFLOW, "wes", HELP, "--config", clientConfig };
            } else {
                commandStatement = new String[]{ WORKFLOW, "wes", command, HELP, "--config", clientConfig };
            }

            Client.main(commandStatement);
            assertTrue(systemErrRule.getText().isBlank(), "There are unexpected error logs");
        }

        // Empty config file
        for (String command : commandNames) {
            String[] commandStatement;

            if (command.length() == 0) {
                commandStatement = new String[]{ WORKFLOW, "wes", HELP, "--config", CONFIG_NO_CONTENT_RESOURCE};
            } else {
                commandStatement = new String[]{ WORKFLOW, "wes", command, HELP, "--config", CONFIG_NO_CONTENT_RESOURCE};
            }

            Client.main(commandStatement);
            assertTrue(systemErrRule.getText().isBlank(), "There are unexpected error logs");
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
    void testNoArgs() throws Exception {

        AbstractEntryClient workflowClient = testAggregateHelper(null);

        String[] args = {};
        WesCommandParser parser = new WesCommandParser();
        parser.jCommander.parse(args);

        int exitCode = catchSystemExit(() -> workflowClient.aggregateWesRequestData(parser));
        assertNotEquals(0, exitCode);
        assertFalse(systemErrRule.getText().isBlank(), "The config file doesn't exist");
    }

    @Test
    void testAggregateAWSCredentialBadProfile() throws Exception {

        AbstractEntryClient workflowClient = testAggregateHelper("configNoContent");

        String[] args = {
            "service-info"
        };
        WesCommandParser parser = new WesCommandParser();
        parser.jCommander.parse(args);
        int exitCode = catchSystemExit(() -> workflowClient.aggregateWesRequestData(parser));
        assertNotEquals(0, exitCode);
        assertFalse(systemErrRule.getText().isEmpty(), "There should be error logs");
    }

    @Test
    void testAggregateAWSCredentialNoRegion() {

        AbstractEntryClient workflowClient = testAggregateHelper("configWithAwsProfile");

        String[] args = {
            "service-info",
            "--wes-url",
            "myUrl"
        };

        String credentials  = ResourceHelpers.resourceFilePath("fakeAwsCredentials");
        String config = ResourceHelpers.resourceFilePath("fakeAwsConfig");
        environmentVariables.set(AWS_CONFIG_FILE.name(), config);
        environmentVariables.set(AWS_SHARED_CREDENTIALS_FILE.name(), credentials);

        WesCommandParser parser = new WesCommandParser();
        parser.jCommander.parse(args);
        WesRequestData data = workflowClient.aggregateWesRequestData(parser);

        assertEquals("REGION", data.getAwsRegion(), "AWS region should be parsed");
    }

    @Test
    void testAggregateAWSCredentialCompleteCommand() {

        AbstractEntryClient workflowClient = testAggregateHelper("configWithAwsProfile");

        String[] args = {
            "service-info",
            "--wes-url",
            "myUrl"
        };

        String credentials  = ResourceHelpers.resourceFilePath("fakeAwsCredentials");
        String config = ResourceHelpers.resourceFilePath("fakeAwsConfig");
        environmentVariables.set(AWS_CONFIG_FILE.name(), config);
        environmentVariables.set(AWS_SHARED_CREDENTIALS_FILE.name(), credentials);

        WesCommandParser parser = new WesCommandParser();
        parser.jCommander.parse(args);
        WesRequestData data = workflowClient.aggregateWesRequestData(parser);

        assertEquals("KEY", data.getAwsAccessKey(), "AWS access key should be parsed");
        assertEquals("SECRET_KEY", data.getAwsSecretKey(), "AWS secret key should be parsed");
        assertEquals("REGION", data.getAwsRegion(), "AWS region should be parsed");
        assertEquals(WesRequestData.CredentialType.AWS_PERMANENT_CREDENTIALS, data.getCredentialType(),
                "AWS region should be parsed");

    }

    @Test
    void testAggregateAWSCredentialCompleteCommandMalformed() throws Exception {

        AbstractEntryClient workflowClient = testAggregateHelper("configWithAwsProfile");

        String[] args = {
            "service-info",
            "--wes-url",
            "myUrl"
        };

        String credentials  = ResourceHelpers.resourceFilePath("fakeMalformedAwsCredentials");
        String config = ResourceHelpers.resourceFilePath("fakeAwsConfig");
        environmentVariables.set(AWS_CONFIG_FILE.name(), config);
        environmentVariables.set(AWS_SHARED_CREDENTIALS_FILE.name(), credentials);

        WesCommandParser parser = new WesCommandParser();
        parser.jCommander.parse(args);
        int exitCode = catchSystemExit(() -> workflowClient.aggregateWesRequestData(parser));
        assertNotEquals(0, exitCode);
        assertFalse(systemErrRule.getText().isEmpty(), "There should be error logs");
    }

    @Test
    void testAggregateAWSCredentialNoProfileAuth() {

        AbstractEntryClient workflowClient = testAggregateHelper("configWithAwsProfileNoAuth");

        String[] args = {
            "service-info",
            "--wes-url",
            "myUrl"
        };

        String credentials  = ResourceHelpers.resourceFilePath("fakeAwsCredentials2");
        String config = ResourceHelpers.resourceFilePath("fakeAwsConfig");
        environmentVariables.set(AWS_CONFIG_FILE.name(), config);
        environmentVariables.set(AWS_SHARED_CREDENTIALS_FILE.name(), credentials);

        WesCommandParser parser = new WesCommandParser();
        parser.jCommander.parse(args);
        WesRequestData data = workflowClient.aggregateWesRequestData(parser);

        assertEquals(WesRequestData.CredentialType.AWS_PERMANENT_CREDENTIALS, data.getCredentialType(),
                "AWS credential type should be set");
        assertEquals("REGION2", data.getAwsRegion(), "AWS region should be parsed");

    }

    @Test
    void testAggregateAWSCredentialNoProfileAuthDefault() {

        AbstractEntryClient workflowClient = testAggregateHelper("configWithAwsProfileNoAuth");

        String credentials  = ResourceHelpers.resourceFilePath("fakeAwsCredentials2");
        String config = ResourceHelpers.resourceFilePath("fakeAwsConfig");
        String[] args = {
            "service-info",
            "--wes-url",
            "myUrl"
        };

        environmentVariables.set(AWS_CONFIG_FILE.name(), config);
        environmentVariables.set(AWS_SHARED_CREDENTIALS_FILE.name(), credentials);

        WesCommandParser parser = new WesCommandParser();
        parser.jCommander.parse(args);
        WesRequestData data = workflowClient.aggregateWesRequestData(parser);

        assertEquals("KEY2", data.getAwsAccessKey(), "AWS access key should be parsed");
        assertEquals("SECRET_KEY2", data.getAwsSecretKey(), "AWS secret key should be parsed");
        assertEquals("REGION2", data.getAwsRegion(), "AWS region should be parsed");
        assertEquals(WesRequestData.CredentialType.AWS_PERMANENT_CREDENTIALS, data.getCredentialType(),
                "AWS region should be parsed");
    }

    @Test
    void testAggregateBearerCredentialCompleteCommand() {

        AbstractEntryClient workflowClient = testAggregateHelper("configWithBearerToken");
        
        String[] args = {
            "service-info",
            "--wes-url",
            "myUrl"
        };

        WesCommandParser parser = new WesCommandParser();
        parser.jCommander.parse(args);
        WesRequestData data = workflowClient.aggregateWesRequestData(parser);

        assertEquals("myToken", data.getBearerToken(), "Bearer token should be parsed");
        assertEquals("myUrl", data.getUrl(), "WES URL should be parsed");
        assertEquals(WesRequestData.CredentialType.BEARER_TOKEN, data.getCredentialType(), "AWS region should be parsed");
    }

    @Test
    void testAggregateBearerCredentialAWSConfigFile() {

        AbstractEntryClient workflowClient = testAggregateHelper("configWithAwsProfile");

        String[] args = {
            "service-info"
        };

        String credentials  = ResourceHelpers.resourceFilePath("fakeAwsCredentials");
        String config = ResourceHelpers.resourceFilePath("fakeAwsConfig");
        environmentVariables.set(AWS_CONFIG_FILE.name(), config);
        environmentVariables.set(AWS_SHARED_CREDENTIALS_FILE.name(), credentials);

        WesCommandParser parser = new WesCommandParser();
        parser.jCommander.parse(args);
        WesRequestData data = workflowClient.aggregateWesRequestData(parser);

        assertEquals("KEY", data.getAwsAccessKey(), "AWS access key should be parsed");
        assertEquals("SECRET_KEY", data.getAwsSecretKey(), "AWS secret key should be parsed");
        assertEquals("REGION", data.getAwsRegion(), "AWS region should be parsed");
        assertEquals(WesRequestData.CredentialType.AWS_PERMANENT_CREDENTIALS, data.getCredentialType(),
                "AWS region should be parsed");

    }

    @Test
    void testAggregateAWSCredentialSessionToken() {

        AbstractEntryClient workflowClient = testAggregateHelper("configWithAwsSessionProfile");

        String[] args = {
            "service-info",
            "--wes-url",
            "myUrl"
        };

        String credentials  = ResourceHelpers.resourceFilePath("fakeAwsCredentials2");
        String config = ResourceHelpers.resourceFilePath("fakeAwsConfig");
        environmentVariables.set(AWS_CONFIG_FILE.name(), config);
        environmentVariables.set(AWS_SHARED_CREDENTIALS_FILE.name(), credentials);

        WesCommandParser parser = new WesCommandParser();
        parser.jCommander.parse(args);
        WesRequestData data = workflowClient.aggregateWesRequestData(parser);

        assertEquals("KEY3", data.getAwsAccessKey(), "AWS access key should be parsed");
        assertEquals("SECRET_KEY3", data.getAwsSecretKey(), "AWS secret key should be parsed");
        assertEquals("TOKEN3", data.getAwsSessionToken(), "AWS session token should be parsed");
        assertEquals("REGION3", data.getAwsRegion(), "AWS region should be parsed");
        assertEquals(WesRequestData.CredentialType.AWS_TEMPORARY_CREDENTIALS, data.getCredentialType(),
                "AWS credentials type should be temporary");

    }

}
