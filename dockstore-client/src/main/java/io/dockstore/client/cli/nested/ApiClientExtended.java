package io.dockstore.client.cli.nested;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;
import java.util.TreeMap;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import io.openapi.wes.client.ApiClient;
import io.openapi.wes.client.ApiException;
import io.openapi.wes.client.Pair;
import org.apache.http.HttpStatus;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.MultiPart;
import uk.co.lucasweb.aws.v4.signer.HttpRequest;
import uk.co.lucasweb.aws.v4.signer.Signer;
import uk.co.lucasweb.aws.v4.signer.credentials.AwsCredentials;

import static io.dockstore.client.cli.ArgumentUtility.out;

public class ApiClientExtended extends ApiClient {

    static final String TIME_ZONE = "UTC";
    static final String DATA_FORMAT = "yyyyMMdd'T'HHmmss'Z'";
    static final String AWS_DATE_HEADER = "x-amz-date";
    static final String AWS_WES_SERVICE_NAME = "execute-api";

    final WesRequestData wesRequestData;
    private Signer.Builder awsAuthSignature = null;
    private HttpRequest awsHttpRequest = null;

    public ApiClientExtended(WesRequestData wesRequestData) {
        this.wesRequestData = wesRequestData;
    }

    public WesRequestData getWesRequestData() {
        return wesRequestData;
    }

    /**
     *
     * @param key Multi-part form body name
     * @return MediaType
     */
    private Optional<MediaType> getMediaType(Optional<String> key) {
        Optional<MediaType> mediaType = Optional.empty();
        if (key.isPresent()) {
            String keyWESParam = key.get().toLowerCase();
            switch (keyWESParam) {
            case "workflow_params":
            case "tags":
            case "workflow_engine_parameters":
                mediaType =  Optional.of(MediaType.APPLICATION_JSON_TYPE);
                break;
            case "workflow_attachment":
                mediaType =  Optional.of(MediaType.APPLICATION_OCTET_STREAM_TYPE);
                break;
            case "workflow_url":
            case "workflow_type":
            case "workflow_type_version":
                mediaType =  Optional.of(MediaType.TEXT_PLAIN_TYPE);
                break;
            default:
            }
        }
        return mediaType;
    }

    /**
     * ]
     * @param multiPart Multipart form
     * @param key Multi part form body name
     * @param formObject Form object that will be a body part of the multi part form
     */
    public void createBodyPart(MultiPart multiPart, String key, Object formObject) {
        Optional<MediaType> optMediaType = getMediaType(Optional.ofNullable(key));
        if (formObject instanceof File) {
            File file = (File)formObject;
            FormDataContentDisposition contentDisp = FormDataContentDisposition.name(key)
                    .fileName(file.getName()).size(file.length()).build();

            MediaType mediaType = optMediaType.orElse(MediaType.APPLICATION_OCTET_STREAM_TYPE);
            multiPart.bodyPart(new FormDataBodyPart(contentDisp, file, mediaType));
        } else {
            FormDataContentDisposition contentDisp = FormDataContentDisposition.name(key).build();

            MediaType mediaType = optMediaType.orElse(MediaType.TEXT_PLAIN_TYPE);
            multiPart.bodyPart(new FormDataBodyPart(contentDisp, formObject, mediaType));
        }
    }

    /**
     * Serialize the given Java object into string entity according the given
     * Content-Type (only JSON is supported for now).
     * @param obj Object
     * @param formParams Form parameters
     * @param contentType Context type
     * @return Entity
     * @throws ApiException API exception
     */
    @Override
    public Entity<?> serialize(Object obj, Map<String, Object> formParams, String contentType) throws ApiException {
        Entity<?> entity;
        if (contentType.startsWith("multipart/form-data")) {
            MultiPart multiPart = new MultiPart();
            for (Map.Entry<String, Object> param: formParams.entrySet()) {
                if (param.getValue() instanceof File) {
                    createBodyPart(multiPart, param.getKey(), param.getValue());
                } else if (param.getValue() instanceof List) {
                    List formListObject = (List)param.getValue();
                    for (int i = 0; i < formListObject.size(); i++) {
                        createBodyPart(multiPart, param.getKey(), formListObject.get(i));
                    }
                } else {
                    createBodyPart(multiPart, param.getKey(), param.getValue());
                }
            }
            entity = Entity.entity(multiPart, MediaType.MULTIPART_FORM_DATA_TYPE);
        } else if (contentType.startsWith("application/x-www-form-urlencoded")) {
            Form form = new Form();
            for (Map.Entry<String, Object> param: formParams.entrySet()) {
                form.param(param.getKey(), parameterToString(param.getValue()));
            }
            entity = Entity.entity(form, MediaType.APPLICATION_FORM_URLENCODED_TYPE);
        } else {
            // We let jersey handle the serialization
            entity = Entity.entity(obj, contentType);
        }
        return entity;
    }

    /**
     * Invoke API by sending HTTP request with the given options.
     *
     * @param <T> Type
     * @param path The sub-path of the HTTP URL
     * @param method The request method, one of "GET", "POST", "PUT", "HEAD" and "DELETE"
     * @param queryParams The query parameters
     * @param body The request body object
     * @param headerParams The header parameters
     * @param formParams The form parameters
     * @param accept The request's Accept header
     * @param contentType The request's Content-Type header
     * @param authNames The authentications to apply
     * @param returnType The return type into which to deserialize the response
     * @return The response body in type of string
     * @throws ApiException API exception
     */
    @Override
    @SuppressWarnings("checkstyle:ParameterNumber")
    public <T> T invokeAPI(String path, String method, List<Pair> queryParams, Object body, Map<String, String> headerParams, Map<String, Object> formParams, String accept, String contentType, String[] authNames, GenericType<T> returnType) throws ApiException {
        updateParamsForAuth(authNames, queryParams, headerParams);

        // Not using `.target(this.basePath).path(path)` below,
        // to support (constant) query string in `path`, e.g. "/posts?draft=1"
        WebTarget target = httpClient.target(this.basePath + path);

        if (queryParams != null) {
            for (Pair queryParam : queryParams) {
                if (queryParam.getValue() != null) {
                    target = target.queryParam(queryParam.getName(), queryParam.getValue());
                }
            }
        }

        Invocation.Builder invocationBuilder = createInvocation(this.wesRequestData.requiresAwsHeaders(), target, method, headerParams);

        Entity<?> entity = serialize(body, formParams, contentType);

        Response response = null;

        try {
            if ("GET".equals(method)) {
                response = invocationBuilder.get();
            } else if ("POST".equals(method)) {
                response = invocationBuilder.post(entity);
            } else if ("PUT".equals(method)) {
                response = invocationBuilder.put(entity);
            } else if ("DELETE".equals(method)) {
                response = invocationBuilder.delete();
            } else if ("PATCH".equals(method)) {
                response = invocationBuilder.method("PATCH", entity);
            } else if ("HEAD".equals(method)) {
                response = invocationBuilder.head();
            } else {
                throw new ApiException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "unknown method type " + method);
            }

            statusCode = response.getStatusInfo().getStatusCode();
            responseHeaders = buildResponseHeaders(response);

            if (response.getStatus() == Response.Status.NO_CONTENT.getStatusCode()) {
                return null;
            } else if (response.getStatusInfo().getFamily() == Response.Status.Family.SUCCESSFUL) {
                if (returnType == null) {
                    return null;
                } else {
                    return deserialize(response, returnType);
                }
            } else {
                String message = "error";
                String respBody = null;
                if (response.hasEntity()) {
                    try {
                        respBody = String.valueOf(response.readEntity(String.class));
                        message = respBody;
                    } catch (RuntimeException e) {
                        out(e.getMessage()); // Not in original generated code, placing this here for checkstyle
                    }
                }
                throw new ApiException(
                    response.getStatus(),
                    message,
                    buildResponseHeaders(response),
                    respBody);
            }
        } finally {
            try {
                response.close();
            } catch (Exception e) {
                out(e.getMessage()); // Not in original generated code, placing this here for checkstyle
            }
        }
    }

    /**
     * We have 3 different sets of header values we need to consider
     *  1. The headerParams passed into the invokeApi function
     *  2. The defaultHeaderMap that is set when the Client is created
     *  3. The required AWS headers for AWS SigV4 signing
     *
     * @param headerParams The header parameters passed to the original invokeAPI function
     * @return A merged map of multiple headers.
     */
    private Map<String, String> mergeHeaders(boolean requiresAwsHeaders, Map<String, String> headerParams) {
        // We need the map to be sorted, as AWS requires header orders to be alphabetical
        Map<String, String> mergedHeaderMap = new TreeMap<>();

        // Note: If 2 maps have duplicate keys, the key/value pair of the last map merged into the
        // mergedMap is the one that will be kept, the rest will be clobbered.
        mergedHeaderMap.putAll(defaultHeaderMap);
        mergedHeaderMap.putAll(headerParams);

        if (requiresAwsHeaders) {
            // Calculate the date header
            DateFormat dateFormat = new SimpleDateFormat(DATA_FORMAT);
            dateFormat.setTimeZone(TimeZone.getTimeZone(TIME_ZONE));
            mergedHeaderMap.put(AWS_DATE_HEADER, dateFormat.format(new Date()));

            // Don't want to calculate our Authorization header off of another Authorization that will subsequently get overridden
            mergedHeaderMap.remove(HttpHeaders.AUTHORIZATION);

            // TODO decide how to handle this header. The 'Expect' header should only be sent for requests with a body, and might(?) impact signing based on how the 'Expect' header modifies how requests are sent
            // It is currently part of the 'defaultHeaderMap', and is set in AbstractEntryClient, and seems to work fine for non-AWS WES requests
            mergedHeaderMap.remove("Expect");
        }

        return mergedHeaderMap;
    }

    /**
     * This will set the appropriate variables so the final Authorization header may be calculated in a Jersey hook
     *
     * @param target The target endpoint
     * @param method The HTTP method (GET, POST, etc ...)
     * @param allHeaders A list of header parameters custom to this request
     * @return A string the should be set under the Authorization header for AWS HTTP requests
     */
    public void setAwsHeaderCalculationData(WebTarget target, String method, Map<String, String> allHeaders) {
        HttpRequest request = new HttpRequest(method, target.getUri());

        // Our signature object. We will add all necessary headers to this request that comprise the 'canonical' HTTP request.
        // This will then be signed alongside a hash of the body content (if there is a body).
        Signer.Builder authSignature = Signer.builder()
            // TODO this currently only supports permanent AWS credentials
            .awsCredentials(new AwsCredentials(this.wesRequestData.getAwsAccessKey(), this.wesRequestData.getAwsSecretKey()))
            .region(this.wesRequestData.getAwsRegion())
            .header(HttpHeaders.HOST, target.getUri().getHost()); // Have to manually set the Host header as it's required when signing

        // add all the headers to the signature object
        for (Map.Entry<String, String> mapEntry : allHeaders.entrySet()) {
            authSignature.header(mapEntry.getKey(), mapEntry.getValue());
        }

        // Not ideal, but we need to save some of the signature creation objects so we have the necessary information
        // to calculate the Authorization header for requests with a payload. This isn't calculable until we're already
        // making the request with jersey.
        setAwsAuthMetadata(authSignature, request);
    }

    /**
     * Sets metadata for calculating an AWS Authorization header later in the request process.
     *
     * @param authSignature The builder which creates the final request
     * @param request An HttpRequest object that holds the AWS service type and URI
     */
    private void setAwsAuthMetadata(Signer.Builder authSignature, HttpRequest request) {
        this.awsAuthSignature = authSignature;
        this.awsHttpRequest = request;
    }

    /**
     * Returns an AWS SigV4 String based on some request metadata and a sha256 of the payload content.
     *
     * @param contentSha256 A sha256 checksum of the request payload.
     * @return A SigV4 string that should be set as the Authorization header of an AWS HTTP request
     */
    public String generateAwsContentSignature(String contentSha256) {
        return awsAuthSignature.build(awsHttpRequest, AWS_WES_SERVICE_NAME, contentSha256).getSignature();
    }

    /**
     * Creates an Invocation.Builder that will be used to make a WES request. If the request is to be sent to an AWS endpoint
     * a SigV4 Authorization header needs to be calculated based on the canonical request (https://docs.aws.amazon.com/general/latest/gr/signature-version-4.html).
     * @param requiresAwsHeaders Boolean value indicating if this request requires AWS-specific headers
     * @param target The target endpoint
     * @param method The HTTP method (GET, POST, etc ...)
     * @param headerParams A list of header parameters custom to this request
     * @return An Invocation.Builder that can be used to make an HTTP request
     */
    private Invocation.Builder createInvocation(boolean requiresAwsHeaders, WebTarget target, String method, Map<String, String> headerParams) {

        Invocation.Builder invocationBuilder = target.request();

        // Merge all our different headers into a single object for easier handling then add them to the invocation
        final Map<String, String> mergedHeaderMap = mergeHeaders(requiresAwsHeaders, headerParams);
        for (Map.Entry<String, String> mapEntry : mergedHeaderMap.entrySet()) {
            invocationBuilder.header(mapEntry.getKey(), mapEntry.getValue());
        }

        // If this request has credentials and is to an AWS endpoint, we need to set some signature calculation data.
        // This will allow the jersey hook (WesChecksumFilter.java) to correctly calculate the Authorization header.
        if (this.wesRequestData.hasCredentials() && requiresAwsHeaders) {
            setAwsHeaderCalculationData(target, method, mergedHeaderMap);
        }

        // Point the jersey filter to this object so we can calculate the Authorization header if needed
        WesChecksumFilter.setClientExtended(this);

        // This invocation has no Authorization header set, that is handle in a Jersey hook
        return invocationBuilder;
    }


}
