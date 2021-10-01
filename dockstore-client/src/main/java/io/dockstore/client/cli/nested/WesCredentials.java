package io.dockstore.client.cli.nested;

import io.dockstore.client.cli.Client;

import static io.dockstore.client.cli.ArgumentUtility.errorMessage;

public class WesCredentials {

    public enum CredentialType {BEARER_TOKEN, PERMANENT_AWS_CREDENTIALS}

    // Type of credentials that are set
    private final CredentialType credentialType;

    // Generic bearer auth
    private String bearerToken;

    // AWS permanent credentials auth
    private String awsAccessKey;
    private String awsSecretKey;

    // TODO AWS temporary session credentials

    // TODO AWS profiles

    /**
     *
     * @param bearerToken
     */
    public WesCredentials(String bearerToken) {
        this.bearerToken = bearerToken;

        this.credentialType = CredentialType.BEARER_TOKEN;
    }

    public WesCredentials(String awsAccessKey, String awsSecretKey) {
        this.awsAccessKey = awsAccessKey;
        this.awsSecretKey = awsSecretKey;

        this.credentialType = CredentialType.PERMANENT_AWS_CREDENTIALS;
    }

    public CredentialType getCredentialType() {
        return credentialType;
    }

    public String getFirstToken() {
        switch (credentialType) {
        case BEARER_TOKEN:
            return this.bearerToken;
        case PERMANENT_AWS_CREDENTIALS:
            return this.awsAccessKey;
        default:
            errorMessage("Unable to locate a primary credentials token (bearer token, AWS access key)", Client.COMMAND_ERROR);
            return "";
        }
    }

    public String getSecondToken() {
        if (credentialType == CredentialType.PERMANENT_AWS_CREDENTIALS) {
            return this.awsSecretKey;
        } else {
            errorMessage("Unable to locate a secondary credentials token (AWS secret access key)", Client.COMMAND_ERROR);
            return "";
        }
    }
}
