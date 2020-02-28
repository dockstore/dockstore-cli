/*
 *    Copyright 2017 OICR
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package io.dockstore.client.cli;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.ws.rs.core.GenericType;

import com.google.gson.Gson;
import io.swagger.client.ApiClient;
import io.swagger.client.model.PublishRequest;
import org.apache.commons.io.FileUtils;

public final class SwaggerUtility {

    private SwaggerUtility() {

    }

    public static <T> T getArbitraryURL(String url, GenericType<T> type, ApiClient client) {
        return client
            .invokeAPI(url, "GET", new ArrayList<>(), null, new HashMap<>(), new HashMap<>(), "application/zip", "application/zip",
                new String[] { "BEARER" }, type).getData();
    }

    public static void unzipFile(File zipFile, File unzipDirectory) throws IOException {
        unzipFile(zipFile, unzipDirectory, false);
    }

    public static void unzipFile(File zipFile, File unzipDirectory, boolean deleteZip) throws IOException {
        try (ZipFile zipFileActual = new ZipFile(zipFile)) {
            zipFileActual.stream().forEach((ZipEntry zipEntry) -> {
                if (!zipEntry.isDirectory()) {
                    String fileName = zipEntry.getName();
                    File newFile = new File(unzipDirectory, fileName);
                    try {
                        newFile.getParentFile().mkdirs();
                        FileUtils.copyInputStreamToFile(zipFileActual.getInputStream(zipEntry), newFile);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    } finally {
                        if (deleteZip) {
                            FileUtils.deleteQuietly(zipFile);
                        }
                    }
                }
            });
        }
    }

    /**
     * These serialization/deserialization hacks should not be necessary.
     * Why does this version of codegen remove the setters?
     * Probably because someone dun goof'd the restful implementation of publish
     * @param bool
     * @return
     */
    public static PublishRequest createPublishRequest(Boolean bool) {
        Map<String, Object> publishRequest = new HashMap<>();
        publishRequest.put("publish", bool);
        Gson gson = new Gson();
        String s = gson.toJson(publishRequest);
        return gson.fromJson(s, PublishRequest.class);
    }
}
