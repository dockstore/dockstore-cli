package io.dockstore.client.cli.nested;

import io.dockstore.client.cli.Client;

import static io.dockstore.client.cli.ArgumentUtility.errorMessage;
import static io.dockstore.client.cli.Client.CLIENT_ERROR;

public class WesRequestData {

    public enum CredentialType { NO_CREDENTIALS, BEARER_TOKEN, AWS_PERMANENT_CREDENTIALS }

    // Type of credentials that are set
    private CredentialType credentialType;

    // WES URL
    private final String url;

    // Generic bearer auth
    private String bearerToken;

    // AWS permanent credentials auth
    private String awsAccessKey;
    private String awsSecretKey;
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
                + "config file in a WES section or use --wes-url <url> option on the command line", CLIENT_ERROR);
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

        // The credentials that were passed in are null, so we are assuming they are within an authorized environment (such as an EC2 instance)
        // This also assumes there is no scenario where just one of the keys is sufficient.
        if (awsAccessKey == null || awsSecretKey == null) {
            this.credentialType = CredentialType.NO_CREDENTIALS;
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
        if (credentialType == CredentialType.AWS_PERMANENT_CREDENTIALS) {
            return this.awsAccessKey;
        } else {
            errorMessage("Unable to locate a AWS access key, this credentials object is of type: " + credentialType.toString(), Client.COMMAND_ERROR);
            return null;
        }
    }

    public String getAwsSecretKey() {
        if (credentialType == CredentialType.AWS_PERMANENT_CREDENTIALS) {
            return this.awsSecretKey;
        } else {
            errorMessage("Unable to locate a AWS secret access key, this credentials object is of type: " + credentialType.toString(), Client.COMMAND_ERROR);
            return null;
        }
    }

    public String getAwsRegion() {
        if (credentialType == CredentialType.AWS_PERMANENT_CREDENTIALS) {
            return this.region == null ? "" : this.region; // regions don't need to be specified if you are in an AWS environment
        } else {
            errorMessage("Unable to locate a AWS region, this credentials object is of type: " + credentialType.toString(), Client.COMMAND_ERROR);
            return null;
        }
    }

    public boolean requiresAwsHeaders() {
        return this.credentialType == CredentialType.AWS_PERMANENT_CREDENTIALS;
    }

    public boolean hasCredentials() {
        return this.credentialType != CredentialType.NO_CREDENTIALS;
    }
}
