package io.dockstore.client.cli.nested;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;
import javax.ws.rs.ext.Providers;

import org.apache.commons.codec.digest.DigestUtils;
import uk.co.lucasweb.aws.v4.signer.HttpRequest;
import uk.co.lucasweb.aws.v4.signer.Signer;

@Provider
@Priority(Priorities.AUTHENTICATION)
public class WesChecksumFilter implements ClientRequestFilter {

    // This filter is a singleton, so these static variables shouldn't cause any issues, but this is not thread safe.
    public static Signer.Builder signatureBuilder = null;
    public static HttpRequest httpRequest = null;
    public static String serviceName = null;

    /**
     * Injectable helper to look up appropriate {@link Provider}s
     * for our body parts.
     */
    @Context
    private Providers providers;

    @Override
    public void filter(ClientRequestContext requestContext) throws IOException {

        // If the request doesn't have an entity (no body content) we don't need to calculate a checksum
        if (requestContext.getEntity() == null) {
            return;
        }

        // Get the message body writer based on the entity type
        final MessageBodyWriter bodyWriter = providers.getMessageBodyWriter(
            requestContext.getEntity().getClass(),
            requestContext.getEntity().getClass(),
            requestContext.getEntityAnnotations(),
            requestContext.getMediaType());

        // Write the Entity to a byte stream using the MessageBodyWriter for our entity type
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        bodyWriter.writeTo(requestContext.getEntity(),
            requestContext.getEntity().getClass(),
            requestContext.getEntity().getClass(),
            requestContext.getEntityAnnotations(),
            requestContext.getMediaType(),
            requestContext.getHeaders(),
            buffer);

        // Close the buffer
        buffer.close();

        // Calculate the sha256 of the content
        byte[] data = buffer.toByteArray();
        String contentSha256 = DigestUtils.sha256Hex(data);
        String AwsAuthHeader = signatureBuilder.build(httpRequest, serviceName, contentSha256).getSignature();

        // Add this as the Authorization header to the request object
        requestContext.getHeaders().putSingle(HttpHeaders.AUTHORIZATION, AwsAuthHeader);
    }
}
