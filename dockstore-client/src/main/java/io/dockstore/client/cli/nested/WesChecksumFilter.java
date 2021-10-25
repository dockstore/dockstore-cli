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

@Provider
@Priority(Priorities.AUTHENTICATION)
public class WesChecksumFilter implements ClientRequestFilter {

    // This filter is a singleton, so the static variable shouldn't cause any issues, but this is not thread safe.
    private static ApiClientExtended clientExtended = null;

    /**
     * Injectable helper to look up appropriate {@link Provider}s
     * for our body parts.
     */
    @Context
    private Providers providers;

    /**
     * Sets the static variables for this filter.
     *
     * @param clientExtended The extended WES API client. We will call back to this client to get our final Authorization header.
     *
     */
    public static void setClientExtended(ApiClientExtended clientExtended) {
        WesChecksumFilter.clientExtended = clientExtended;
    }

    /**
     * This filter intercepts the jersey request before it is sent and attempts to calculate an AWS SigV4 Authorization header if needed.
     * If the request does not have a body, or if the static class variable clientExtended is not set, this filter will not do anything. This
     * Covers scenarios where the request has no payload and/or is not to an AWS endpoint.
     *
     * @param requestContext jersey requestContext
     * @throws IOException
     */
    @Override
    public void filter(ClientRequestContext requestContext) throws IOException {

        // If the request doesn't have an entity (no body content) we don't need to calculate a checksum based on body content.
        // If the extended client field is null, then we don't need to calculate a signature.
        if (requestContext.getEntity() == null || clientExtended == null) {
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

        // Close the buffer, nothing else should be written to it.
        buffer.close();

        // Calculate a sha256 of the content in the buffer
        byte[] content = buffer.toByteArray();
        String contentSha256 = DigestUtils.sha256Hex(content);
        String awsAuthHeader = clientExtended.generateAwsContentSignature(contentSha256);

        // Add this as the Authorization header to the request object
        requestContext.getHeaders().putSingle(HttpHeaders.AUTHORIZATION, awsAuthHeader);
    }
}
