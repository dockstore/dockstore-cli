package io.dockstore.client.cli;

import java.util.Map;
import java.util.TreeMap;

import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.HttpHeaders;

import io.dockstore.client.cli.nested.ApiClientExtended;
import io.dockstore.client.cli.nested.WesRequestData;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class ApiClientExtendedIT {

    // These are old keys that have been deleted.
    static final String WES_ENDPOINT = "https://eoof9s4bbe.execute-api.us-west-2.amazonaws.com/prod/ga4gh/wes/v1";
    static final String OLD_AWS_ACCESS_KEY = "AKIAZHZQVUMXJASEFDT4";
    static final String OLD_AWS_SECRET_KEY = "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";
    static final String AWS_REGION = "us-west-2";

    /**
     * This test isn't perfect, as validating that a one-way hash is correct (and works against an AWS endpoint) isn't very straightforward,
     * mainly because we don't have an easy source of truth. Instead, the inputs of a successful AWS request are provided,
     * and we confirm that the signing function is still producing the same signature.
     */
    @Test
    public void testAwsSigCalculation() {
        WesRequestData wrd = new WesRequestData(WES_ENDPOINT + "/service-info", OLD_AWS_ACCESS_KEY, OLD_AWS_SECRET_KEY, AWS_REGION);
        ApiClientExtended ace = new ApiClientExtended(wrd);

        final WebTarget target = ace.getHttpClient().target(wrd.getUrl());
        final String method = "GET";
        final Map<String, String> allHeaders = new TreeMap<>();
        allHeaders.put(HttpHeaders.ACCEPT, "*/*");
        allHeaders.put(HttpHeaders.USER_AGENT, "Swagger-Codegen/1.0.0/java");
        allHeaders.put("x-amz-date", "20211007T171738Z"); // Don't change this date setting

        final String expectedHeader = "AWS4-HMAC-SHA256 Credential=AKIAZHZQVUMXJASEFDT4/20211007/us-west-2/execute-api/aws4_request, SignedHeaders=accept;host;user-agent;x-amz-date, Signature=abc30f57ec627a809f6c3b4f738b2863ee8c242f8d4cdd653a37bf47e7bffc0f";
        final String newlyCalculatedHeader = ace.generateAwsSignature(target, method, allHeaders);

        assertEquals("The calculated header should match the original request that was made", expectedHeader, newlyCalculatedHeader);
    }

    @Test
    public void testSameSigForEmptyPayload() {
        WesRequestData wrd = new WesRequestData(WES_ENDPOINT + "/runs", OLD_AWS_ACCESS_KEY, OLD_AWS_SECRET_KEY, AWS_REGION);
        ApiClientExtended ace = new ApiClientExtended(wrd);

        final WebTarget target = ace.getHttpClient().target(wrd.getUrl());
        final String method = "GET";
        final Map<String, String> allHeaders = new TreeMap<>();
        allHeaders.put(HttpHeaders.ACCEPT, "*/*");
        allHeaders.put(HttpHeaders.USER_AGENT, "Swagger-Codegen/1.0.0/java");
        allHeaders.put("x-amz-date", "20211027T163015Z"); // Don't change this date setting

        final String expectedSignature = ace.generateAwsSignature(target, method, allHeaders); // This needs to be executed so the Jersey filter interceptor can generate the final header.
        final String fakeBodyChecksum = DigestUtils.sha256Hex("".getBytes());
        final String newlyCalculatedContentHeader = ace.generateAwsContentSignature(fakeBodyChecksum);

        assertEquals("The calculated header should match the original request that was made", expectedSignature, newlyCalculatedContentHeader);
    }

    @Test
    public void testAwsSigCalculationForBody() {
        WesRequestData wrd = new WesRequestData(WES_ENDPOINT + "/runs", OLD_AWS_ACCESS_KEY, OLD_AWS_SECRET_KEY, AWS_REGION);
        ApiClientExtended ace = new ApiClientExtended(wrd);

        final WebTarget target = ace.getHttpClient().target(wrd.getUrl());
        final String method = "POST";
        final Map<String, String> allHeaders = new TreeMap<>();
        allHeaders.put(HttpHeaders.ACCEPT, "*/*");
        allHeaders.put(HttpHeaders.USER_AGENT, "Swagger-Codegen/1.0.0/java");
        allHeaders.put("x-amz-date", "20211027T163927Z"); // Don't change this date setting

        final String expectedHeader = "AWS4-HMAC-SHA256 Credential=AKIAZHZQVUMXJASEFDT4/20211027/us-west-2/execute-api/aws4_request, SignedHeaders=accept;host;user-agent;x-amz-date, Signature=dab4ce2c5ff35cc2a99d57b03fb3ed0ece24630d97bfaf1676967b60b5171831";
        final String originalHeader = ace.generateAwsSignature(target, method, allHeaders); // This needs to be executed so the Jersey filter interceptor can generate the final header.

        // The checksum of a multipart body request.
        final String fakeBodyChecksum = "bf918205a9d2ce0cf8a0d1c7cc0eb2ecf42fdefc4ab387eaa1a7958edf26face";
        final String newlyCalculatedContentHeader = ace.generateAwsContentSignature(fakeBodyChecksum);

        assertNotEquals("The original header should not match the header after the body checksum has been calculated.", originalHeader, newlyCalculatedContentHeader);
        assertEquals("The calculated header should match the original request that was made", expectedHeader, newlyCalculatedContentHeader);
    }
}
