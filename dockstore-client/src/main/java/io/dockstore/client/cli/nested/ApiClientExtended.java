package io.dockstore.client.cli.nested;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import io.openapi.wes.client.ApiClient;
import io.openapi.wes.client.ApiException;
import io.openapi.wes.client.Pair;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.MultiPart;

public class ApiClientExtended extends ApiClient {

    final WesCredentials wesCredentials;

    public ApiClientExtended(WesCredentials wesCredentials) {
        this.wesCredentials = wesCredentials;
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

        Invocation.Builder invocationBuilder = target.request().accept(accept);

        for (Map.Entry<String, String> entry : headerParams.entrySet()) {
            String value = entry.getValue();
            if (value != null) {
                invocationBuilder = invocationBuilder.header(entry.getKey(), value);
            }
        }

        for (Map.Entry<String, String> entry : defaultHeaderMap.entrySet()) {
            String key = entry.getKey();
            if (!headerParams.containsKey(key)) {
                String value = entry.getValue();
                if (value != null) {
                    invocationBuilder = invocationBuilder.header(key, value);
                }
            }
        }

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
                throw new ApiException(500, "unknown method type " + method);
            }

            statusCode = response.getStatusInfo().getStatusCode();
            responseHeaders = buildResponseHeaders(response);

            if (response.getStatus() == Response.Status.NO_CONTENT.getStatusCode()) {
                return null;
            } else if (response.getStatusInfo().getFamily() == Response.Status.Family.SUCCESSFUL) {
                if (returnType == null)
                    return null;
                else
                    return deserialize(response, returnType);
            } else {
                String message = "error";
                String respBody = null;
                if (response.hasEntity()) {
                    try {
                        respBody = String.valueOf(response.readEntity(String.class));
                        message = respBody;
                    } catch (RuntimeException e) {
                        // e.printStackTrace();
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
                // it's not critical, since the response object is local in method invokeAPI; that's fine, just continue
            }
        }
    }


}
