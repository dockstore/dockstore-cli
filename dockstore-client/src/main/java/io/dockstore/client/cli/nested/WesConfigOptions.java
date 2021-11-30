package io.dockstore.client.cli.nested;

/**
 * This class defines a set of keys to read
 */
public final class WesConfigOptions {

    // The WES URL to be used for the WES request.
    public static final String URL_KEY = "url";

    // The type of authorization, and the value for the authorization.
    public static final String AUTHORIZATION_TYPE_KEY = "type";
    public static final String AUTHORIZATION_VALUE_KEY = "authorization";

    /**
     * AWS-specific values. These are only used in the case AUTHORIZATION_TYPE_KEY is set to "aws"
     */
    // The AWS region we are sending the WES request to.
    public static final String AWS_REGION_KEY = "region";

    // A path to an AWS credentials file. By default, AWS searches your home directory at '~/.aws/credentials'.
    public static final String AWS_CREDENTIALS_KEY = "config";

    // The AWS profile named 'default' is commonly used if another profile is not explicitly specified.
    public static final String AWS_DEFAULT_PROFILE_VALUE = "default";

    private WesConfigOptions() {

    }
}
