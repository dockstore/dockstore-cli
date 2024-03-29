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
package io.dockstore.client.cli.nested;

import static org.mockito.Mockito.when;

import io.dockstore.client.cli.Client;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.openapi.client.ApiException;
import io.dockstore.openapi.client.api.ContainersApi;
import io.dockstore.openapi.client.api.ContainertagsApi;
import io.dockstore.openapi.client.api.UsersApi;
import io.dockstore.openapi.client.model.DockstoreTool;
import io.dockstore.openapi.client.model.SourceFile;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ToolClientTest {

    private static final String MISSING_TAG = "0.3.1";
    private static final String GOOD_TAG = "0.3.0";
    private static final String REPOSITORY = "registry.hub.docker.com/ucsctreehouse/fusion";
    private static final long CONTAINER_ID = 1L;

    private ContainersApi containersApi;
    private ContainertagsApi containertagsApi;
    private UsersApi usersApi;
    private Client client;

    @BeforeEach
    void setup() {
        containersApi = Mockito.mock(ContainersApi.class);
        containertagsApi = Mockito.mock(ContainertagsApi.class);
        usersApi = Mockito.mock(UsersApi.class);
        client = Mockito.mock(Client.class);
        DockstoreTool dockstoreTool = Mockito.mock(DockstoreTool.class);
        ApiException apiException = Mockito.mock(ApiException.class);
        when(apiException.getCode()).thenReturn(HttpStatus.SC_BAD_REQUEST);
        when(dockstoreTool.getId()).thenReturn(CONTAINER_ID);
        when(containersApi.primaryDescriptor(CONTAINER_ID, MISSING_TAG, DescriptorLanguage.CWL.toString())).thenThrow(apiException);
        when(containersApi.primaryDescriptor(CONTAINER_ID, null, DescriptorLanguage.CWL.toString())).thenThrow(apiException);
        when(containersApi.primaryDescriptor(CONTAINER_ID, GOOD_TAG, DescriptorLanguage.CWL.toString()))
            .thenReturn(Mockito.mock(SourceFile.class));
        when(containersApi.getPublishedContainerByToolPath(REPOSITORY, null)).thenReturn(dockstoreTool);
    }

    @Test
    void getDescriptorFromServerMissingTag() {
        ToolClient toolClient = new ToolClient(containersApi, containertagsApi, usersApi, client, false);
        boolean exceptionThrown = false;
        try {
            toolClient.getDescriptorFromServer(REPOSITORY + ":" + MISSING_TAG, DescriptorLanguage.CWL);
        } catch (Exception ex) {
            exceptionThrown = true;
        }
        Assertions.assertTrue(exceptionThrown);
    }

    @Test
    void getDescriptorFromServerNoTag() {
        ToolClient toolClient = new ToolClient(containersApi, containertagsApi, usersApi, client, false);
        boolean exceptionThrown = false;
        try {
            toolClient.getDescriptorFromServer(REPOSITORY, DescriptorLanguage.CWL);
        } catch (Exception ex) {
            exceptionThrown = true;
        }
        Assertions.assertTrue(exceptionThrown);
    }

    @Test
    void getDescriptorFromServerGoodTag() {
        ToolClient toolClient = new ToolClient(containersApi, containertagsApi, usersApi, client, false);
        SourceFile cwl = toolClient.getDescriptorFromServer(REPOSITORY + ":" + GOOD_TAG, DescriptorLanguage.CWL);
        Assertions.assertNotNull(cwl);
    }
}
