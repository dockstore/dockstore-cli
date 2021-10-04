package io.dockstore.client.cli.nested;

import io.dockstore.client.cli.Client;

import static io.dockstore.client.cli.ArgumentUtility.errorMessage;

public class WesCredentials {

    public enum CredentialType {BEARER_TOKEN, AWS_PERMANENT_CREDENTIALS}

    // Type of credentials that are set
    private final CredentialType credentialType;

    // Generic bearer auth
    private String bearerToken;

    // AWS permanent credentials auth
    private String awsAccessKey;
    private String awsSecretKey;
    private String region;

    // TODO AWS temporary session credentials

    // TODO AWS profiles

    /**
     * Credentials that use a single API/bearer token for authentication
     * @param bearerToken The bearer token that will be used for authorization
     */
    public WesCredentials(String bearerToken) {
        this.bearerToken = bearerToken;

        this.credentialType = CredentialType.BEARER_TOKEN;
    }

    /**
     * Credentials that use an AWS access key and AWS secret access key for authentication
     * @param awsAccessKey The permanent access key
     * @param awsSecretKey The permanent secret access key
     */
    public WesCredentials(String awsAccessKey, String awsSecretKey, String region) {
        this.awsAccessKey = awsAccessKey;
        this.awsSecretKey = awsSecretKey;
        this.region = region;

        this.credentialType = CredentialType.AWS_PERMANENT_CREDENTIALS;
    }

    public CredentialType getCredentialType() {
        return credentialType;
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
            return this.region;
        } else {
            errorMessage("Unable to locate a AWS region, this credentials object is of type: " + credentialType.toString(), Client.COMMAND_ERROR);
            return null;
        }
    }
}
