package io.dockstore.common;

import io.dockstore.client.cli.nested.WesRequestData;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class WesRequestDataTest {

    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();
    @Rule
    public final SystemErrRule systemErrRule = new FlushingSystemErrRule().enableLog();
    @Rule
    public final SystemOutRule systemOutRule = new FlushingSystemOutRule().enableLog();

    @Test
    public void testNoCredentials() {
        WesRequestData wrd = new WesRequestData("myFakeUri");
        assertSame("If only a URI was passed, there should ne no credentials", wrd.getCredentialType(),
            WesRequestData.CredentialType.NO_CREDENTIALS);
        assertTrue("There are unexpected error logs", systemErrRule.getLog().isBlank());

        systemExit.expectSystemExit();
        wrd.getBearerToken();
        assertFalse("There should be error logs as no token exists but there aren't", systemErrRule.getLog().isBlank());
    }

    @Test
    public void testBearerTokenCredentials() {
        WesRequestData wrd = new WesRequestData("myFakeUri", "myfaketoken");
        assertSame("If a URI and single token was passed, this should be a interpreted as a bearer token", wrd.getCredentialType(),
            WesRequestData.CredentialType.BEARER_TOKEN);
        assertTrue("There are unexpected error logs", systemErrRule.getLog().isBlank());

        wrd.getBearerToken();
        assertTrue("There should be no error logs as a token exists", systemErrRule.getLog().isBlank());

        systemExit.expectSystemExit();
        wrd.getAwsAccessKey();
        assertFalse("There should be error logs as no access key exists but there aren't", systemErrRule.getLog().isBlank());
    }

    @Test
    public void testAwsPermanentCredentials() {
        WesRequestData wrd = new WesRequestData("myFakeUri", "myFakeAccessKey", "myFakeSecretKey", "myFakeRegion");
        assertSame("If AWS credentials were passed (access key, secret key, rgion), this should be interpreted as permanent credentials", wrd.getCredentialType(),
            WesRequestData.CredentialType.AWS_PERMANENT_CREDENTIALS);
        assertTrue("There are unexpected error logs", systemErrRule.getLog().isBlank());

        wrd.getAwsAccessKey();
        wrd.getAwsSecretKey();
        wrd.getAwsRegion();
        assertTrue("There should be no error logs for accessing any of the AWS credentials", systemErrRule.getLog().isBlank());

        systemExit.expectSystemExit();
        wrd.getBearerToken();
        assertFalse("There should be error logs as no token exists but there aren't", systemErrRule.getLog().isBlank());

    }

    @Test
    public void testNullURI() {
        systemExit.expectSystemExit();
        WesRequestData wrd = new WesRequestData(null);
        assertFalse("A null URI should not be accepted", systemErrRule.getLog().isBlank());

    }

    @Test
    public void testEmptyURI() {
        systemExit.expectSystemExit();
        WesRequestData wrd = new WesRequestData("");
        assertFalse("An empty URI should not be accepted", systemErrRule.getLog().isBlank());
    }

    @Test
    public void testNullCredentials1() {
        systemExit.expectSystemExit();
        WesRequestData wrd = new WesRequestData("myFakeUri", null);
        assertFalse("WES request object should report no credentials", wrd.hasCredentials());
        wrd.getBearerToken();
        assertFalse("A null bearer token should not be requested", systemErrRule.getLog().isBlank());
    }

    @Test
    public void testNullCredentials2() {
        systemExit.expectSystemExit();
        WesRequestData wrd = new WesRequestData("myFakeUri", null, null, null);
        assertFalse("WES request object should report no credentials", wrd.hasCredentials());
        wrd.getAwsSecretKey();
        assertFalse("Null AWS credentials should not be requested", systemErrRule.getLog().isBlank());

    }

    @Test
    public void testNullCredentials3() {
        systemExit.expectSystemExit();
        WesRequestData wrd = new WesRequestData("myFakeUri", null, "whatIfIPassInJustOne?", null);
        assertFalse("WES request object should report no credentials", wrd.hasCredentials());
        wrd.getAwsAccessKey();
        assertFalse("Null AWS credentials should not be requested", systemErrRule.getLog().isBlank());
    }

    @Test
    public void testNullRegion() {
        systemExit.expectSystemExit();
        WesRequestData wrd = new WesRequestData("myFakeUri", "whatIfIPassInJustOne?", "howAboutTwo?", null);
        assertTrue("WES request object should report credentials", wrd.hasCredentials());
        assertFalse("Null AWS regions should not be requested", systemErrRule.getLog().isBlank());
    }

    @Test
    public void testCorrectResponses() {
        final String uri = "myFakeUri";
        final String bearerToken = "myFakeBearerToken";
        final String awsAccessKey = "12345678";
        final String awsSecretKey = "87654321";
        final String awsRegion = "space-mars-1";

        WesRequestData wrd = new WesRequestData(uri);
        assertSame("URIs should match", wrd.getUrl(), uri);

        wrd = new WesRequestData(uri, bearerToken);
        assertSame("URIs should match", wrd.getUrl(), uri);
        assertSame("Bearer token should match", wrd.getBearerToken(), bearerToken);

        wrd = new WesRequestData(uri, awsAccessKey, awsSecretKey, awsRegion);
        assertSame("URIs should match", wrd.getUrl(), uri);
        assertSame("Access key should match", wrd.getAwsAccessKey(), awsAccessKey);
        assertSame("Secret key should match", wrd.getAwsSecretKey(), awsSecretKey);
        assertSame("Region should match", wrd.getAwsRegion(), awsRegion);
    }
}
