package io.dockstore.client.cli.nested;

import io.dockstore.client.cli.Client;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;

import static io.dockstore.client.cli.ArgumentUtility.errorMessage;
import static io.dockstore.client.cli.Client.CLIENT_ERROR;
import static io.dockstore.client.cli.nested.WesCommandParser.WES_URL;

public class WesRequestData {

    public enum CredentialType { NO_CREDENTIALS, BEARER_TOKEN, AWS_PERMANENT_CREDENTIALS, AWS_TEMPORARY_CREDENTIALS }

    // Type of credentials that are set
    private CredentialType credentialType;

    // WES URL
    private final String url;

    // Generic bearer auth
    private String bearerToken;

    // AWS permanent credentials auth
    private String awsAccessKey;
    private String awsSecretKey;
    private String awsSessionToken;
    private String region;

    // TODO AWS temporary session credentials

    // TODO AWS profiles

    /**
     * Credential-free request
     * @param url WES url
     */
    public WesRequestData(String url) {
        if (url == null || url.isEmpty()) {
            errorMessage("No WES URL found in config file and no WES URL entered on command line. Please add url: <url> to "
                + "config file in a WES section or use " + WES_URL + " <url> option on the command line", CLIENT_ERROR);
        }
        this.url = url;
        this.credentialType = CredentialType.NO_CREDENTIALS;
    }

    /**
     * Credentials that use a single API/bearer token for authentication
     * @param bearerToken The bearer token that will be used for authorization
     */
    public WesRequestData(String url, String bearerToken) {
        this(url);

        this.bearerToken = bearerToken;

        if (bearerToken == null) {
            this.credentialType = CredentialType.NO_CREDENTIALS;
        } else {
            this.credentialType = CredentialType.BEARER_TOKEN;
        }
    }

    public WesRequestData(String url, AwsCredentials credentials, String region) {
        this(url, credentials.accessKeyId(), credentials.secretAccessKey(), region);

        // Check if the credentials object uses a temporary session token
        if (credentials instanceof AwsSessionCredentials) {
            this.awsSessionToken = ((AwsSessionCredentials) credentials).sessionToken();
            this.credentialType = CredentialType.AWS_TEMPORARY_CREDENTIALS;
        }
    }

    /**
     * Credentials that use an AWS access key and AWS secret access key for authentication
     * @param awsAccessKey The permanent access key
     * @param awsSecretKey The permanent secret access key
     */
    public WesRequestData(String url, String awsAccessKey, String awsSecretKey, String region) {
        this(url);

        this.awsAccessKey = awsAccessKey;
        this.awsSecretKey = awsSecretKey;
        this.region = region;

        // AWS credentials are required to make a complete API call. If any of the parameters are null, throw an error for the user.
        if (awsAccessKey == null) {
            errorMessage("Unable to locate an AWS access key. Specify an AWS access key under your AWS profile in ~/.aws/credentials.", Client.COMMAND_ERROR);
        } else if (awsSecretKey == null) {
            errorMessage("Unable to locate an AWS secret key. Specify an AWS secret key under your AWS profile in ~/.aws/credentials.", Client.COMMAND_ERROR);
        } else if (region == null) {
            errorMessage("Unable to locate an AWS region. Specify an AWS region under your AWS profile in ~/.aws/config.", Client.COMMAND_ERROR);
        } else {
            this.credentialType = CredentialType.AWS_PERMANENT_CREDENTIALS;
        }
    }

    public CredentialType getCredentialType() {
        return credentialType;
    }

    public String getUrl() {
        return this.url;
    }

    public String getBearerToken() {
        if (credentialType == CredentialType.BEARER_TOKEN) {
            return this.bearerToken;
        } else {
            errorMessage("Unable to locate a bearer token this, credentials object is of type: " + credentialType.toString(), Client.COMMAND_ERROR);
            return null;
        }
    }

    public String getAwsAccessKey() {
        if (usesAwsCredentials()) {
            return this.awsAccessKey;
        } else {
            errorMessage("Unable to locate a AWS access key, this credentials object is of type: " + credentialType.toString(), Client.COMMAND_ERROR);
            return null;
        }
    }

    public String getAwsSecretKey() {
        if (usesAwsCredentials()) {
            return this.awsSecretKey;
        } else {
            errorMessage("Unable to locate a AWS secret access key, this credentials object is of type: " + credentialType.toString(), Client.COMMAND_ERROR);
            return null;
        }
    }

    public String getAwsSessionToken() {
        if (credentialType == CredentialType.AWS_TEMPORARY_CREDENTIALS) {
            return this.awsSessionToken;
        } else {
            errorMessage("Unable to locate a AWS session token, this credentials object is of type: " + credentialType.toString(), Client.COMMAND_ERROR);
            return null;
        }
    }

    public String getAwsRegion() {
        if (usesAwsCredentials()) {
            return this.region;
        } else {
            errorMessage("Unable to locate a AWS region, this credentials object is of type: " + credentialType.toString(), Client.COMMAND_ERROR);
            return null;
        }
    }

    public boolean usesAwsCredentials() {
        return this.credentialType == CredentialType.AWS_PERMANENT_CREDENTIALS || this.credentialType == CredentialType.AWS_TEMPORARY_CREDENTIALS;
    }

    public boolean requiresAwsSessionHeader() {
        return this.credentialType == CredentialType.AWS_TEMPORARY_CREDENTIALS;
    }

    public boolean hasCredentials() {
        return this.credentialType != CredentialType.NO_CREDENTIALS;
    }
}
