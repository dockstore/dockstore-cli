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

        if (bearerToken == null) {
            errorMessage("No WES API token found in config file and no WES API token entered on command line."
                + "Please add 'authorization: <type> <credentials> to config file in WES section or "
                + "use --wes-auth '<type> <credentials>' option on the command line if authorization credentials are needed", CLIENT_ERROR);
        }

        this.bearerToken = bearerToken;
        this.credentialType = CredentialType.BEARER_TOKEN;
    }

    /**
     * Credentials that use an AWS access key and AWS secret access key for authentication
     * @param awsAccessKey The permanent access key
     * @param awsSecretKey The permanent secret access key
     */
    public WesRequestData(String url, String awsAccessKey, String awsSecretKey, String region) {
        this(url);

        if (awsAccessKey == null) {
            errorMessage("No AWS access key found in config file and no AWS access key entered on command line."
                + "Please add 'authorization: <type> <credentials> to config file in WES section or "
                + "use --aws-access-key '<type> <credentials>' option on the command line if authorization credentials are needed", CLIENT_ERROR);
        }

        if (awsSecretKey == null) {
            errorMessage("No AWS secret access key found in config file and no AWS secret access key entered on command line."
                + "Please add 'authorization: <type> <credentials> to config file in WES section or "
                + "use --aws-secret-key '<type> <credentials>' option on the command line if authorization credentials are needed", CLIENT_ERROR);
        }

        if (region == null) {
            errorMessage("No AWS region key found in config file and no AWS region entered on command line."
                + "Please add 'region: <type> <credentials> to config file in WES section or "
                + "use --aws-region '<region>' option on the command line if region specifications are needed", CLIENT_ERROR);
        }

        this.awsAccessKey = awsAccessKey;
        this.awsSecretKey = awsSecretKey;
        this.region = region;
        this.credentialType = CredentialType.AWS_PERMANENT_CREDENTIALS;
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
            return this.region;
        } else {
            errorMessage("Unable to locate a AWS region, this credentials object is of type: " + credentialType.toString(), Client.COMMAND_ERROR);
            return null;
        }
    }
}
