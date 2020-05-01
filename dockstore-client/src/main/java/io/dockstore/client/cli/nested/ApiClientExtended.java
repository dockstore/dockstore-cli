package io.dockstore.client.cli.nested;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.MediaType;

import io.openapi.wes.client.ApiClient;
import io.openapi.wes.client.ApiException;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.MultiPart;

public class ApiClientExtended extends ApiClient {

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

}
