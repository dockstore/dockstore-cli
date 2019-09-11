/*
 *    Copyright 2018 OICR
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
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.UriBuilder;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import com.google.common.collect.Lists;
import com.google.common.io.Resources;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.Registry;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dropwizard.testing.DropwizardTestSupport;
import io.dropwizard.testing.ResourceHelpers;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.ContainersApi;
import io.swagger.client.api.ContainertagsApi;
import io.swagger.client.api.Ga4GhApi;
import io.swagger.client.api.Ga4Ghv1Api;
import io.swagger.client.api.HostedApi;
import io.swagger.client.api.MetadataApi;
import io.swagger.client.api.UsersApi;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.Config;
import io.swagger.client.model.DescriptorLanguageBean;
import io.swagger.client.model.DockstoreTool;
import io.swagger.client.model.Entry;
import io.swagger.client.model.MetadataV1;
import io.swagger.client.model.Permission;
import io.swagger.client.model.PublishRequest;
import io.swagger.client.model.RegistryBean;
import io.swagger.client.model.SharedWorkflows;
import io.swagger.client.model.SourceFile;
import io.swagger.client.model.StarRequest;
import io.swagger.client.model.Tag;
import io.swagger.client.model.Token;
import io.swagger.client.model.Tool;
import io.swagger.client.model.ToolDescriptor;
import io.swagger.client.model.ToolDockerfile;
import io.swagger.client.model.ToolVersion;
import io.swagger.client.model.ToolVersionV1;
import io.swagger.client.model.User;
import io.swagger.client.model.Workflow;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import static io.dockstore.common.DescriptorLanguage.CWL;
import static io.dockstore.webservice.TokenResourceIT.GITHUB_ACCOUNT_USERNAME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests the actual ApiClient generated via Swagger
 *
 * @author xliu
 */
@Category(ConfidentialTest.class)
public class SwaggerClientIT extends BaseIT {

    private static final String QUAY_IO_TEST_ORG_TEST6 = "quay.io/test_org/test6";
    private static final String REGISTRY_HUB_DOCKER_COM_SEQWARE_SEQWARE = "registry.hub.docker.com/seqware/seqware/test5";
    public static final DropwizardTestSupport<DockstoreWebserviceConfiguration> SUPPORT = new DropwizardTestSupport<>(
        DockstoreWebserviceApplication.class, CommonTestUtilities.CONFIDENTIAL_CONFIG_PATH);

    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testListUsersTools() throws ApiException {
        ApiClient client = getAdminWebClient();

        UsersApi usersApi = new UsersApi(client);
        User user = usersApi.getUser();

        List<DockstoreTool> tools = usersApi.userContainers(user.getId());
        assertEquals(2, tools.size());
    }

    @Test
    public void testFailedContainerRegistration() throws ApiException {
        ApiClient client = getWebClient();
        ContainersApi containersApi = new ContainersApi(client);
        List<DockstoreTool> containers = containersApi.allPublishedContainers(null, null, null, null, null);

        assertEquals(1, containers.size());

        UsersApi usersApi = new UsersApi(client);
        User user = usersApi.getUser();
        containers = usersApi.userContainers(user.getId());

        assertEquals(5, containers.size());

        // do some minor testing on pagination, majority of tests are in WorkflowIT.testPublishingAndListingOfPublished for now
        // TODO: better testing of pagination when we use it
        List<DockstoreTool> pagedToolsLowercase = containersApi.allPublishedContainers("0", 1, "test", "stars", "desc");
        assertEquals(1, pagedToolsLowercase.size());
        List<DockstoreTool> pagedToolsUppercase = containersApi.allPublishedContainers("0", 1, "TEST", "stars", "desc");
        assertEquals(1, pagedToolsUppercase.size());
        assertEquals(pagedToolsLowercase, pagedToolsUppercase);

        DockstoreTool container = containersApi.getContainerByToolPath("quay.io/test_org/test2", null);
        assertFalse(container.isIsPublished());

        long containerId = container.getId();

        PublishRequest pub = SwaggerUtility.createPublishRequest(true);
        thrown.expect(ApiException.class);
        containersApi.publish(containerId, pub);
    }

    @Test
    public void testToolLabelling() throws ApiException {
        ContainersApi userApi1 = new ContainersApi(getWebClient(true, false));
        ContainersApi userApi2 = new ContainersApi(getWebClient(false, false));

        DockstoreTool container = userApi1.getContainerByToolPath("quay.io/test_org/test2", null);
        assertFalse(container.isIsPublished());

        long containerId = container.getId();
        userApi1.updateLabels(containerId, "foo,spam,phone", "");
        container = userApi1.getContainerByToolPath("quay.io/test_org/test2", null);
        assertEquals(3, container.getLabels().size());
        thrown.expect(ApiException.class);
        userApi2.updateLabels(containerId, "foobar", "");
    }

    @Test
    public void testWorkflowLabelling() throws ApiException {
        // note db workflow seems to have no owner, so I need an admin user to label it
        WorkflowsApi userApi1 = new WorkflowsApi(getWebClient(true, true));
        WorkflowsApi userApi2 = new WorkflowsApi(getWebClient(false, false));

        Workflow workflow = userApi1.getWorkflowByPath("github.com/A/l", null, false);
        assertTrue(workflow.isIsPublished());

        long containerId = workflow.getId();

        userApi1.updateLabels(containerId, "foo,spam,phone", "");
        workflow = userApi1.getWorkflowByPath("github.com/A/l", null, false);
        assertEquals(3, workflow.getLabels().size());
        thrown.expect(ApiException.class);
        userApi2.updateLabels(containerId, "foobar", "");
    }

    @Test
    public void testSuccessfulManualImageRegistration() throws ApiException {
        ApiClient client = getAdminWebClient();
        ContainersApi containersApi = new ContainersApi(client);

        DockstoreTool c = getContainer();

        containersApi.registerManual(c);
    }

    private DockstoreTool getContainer() {
        DockstoreTool c = new DockstoreTool();
        c.setMode(DockstoreTool.ModeEnum.MANUAL_IMAGE_PATH);
        c.setName("seqware_full");
        c.setName("seqware");
        c.setGitUrl("https://github.com/denis-yuen/test1");
        c.setDefaultDockerfilePath("/Dockerfile");
        c.setDefaultCwlPath("/Dockstore.cwl");
        c.setRegistryString(Registry.DOCKER_HUB.toString());
        c.setIsPublished(true);
        c.setNamespace("seqware");
        c.setToolname("test5");
        c.setPrivateAccess(false);
        //c.setToolPath("registry.hub.docker.com/seqware/seqware/test5");
        Tag tag = new Tag();
        tag.setName("master");
        tag.setReference("refs/heads/master");
        tag.setValid(true);
        tag.setImageId("123456");
        tag.setVerified(false);
        tag.setVerifiedSource(null);
        // construct source files
        SourceFile fileCWL = new SourceFile();
        fileCWL.setContent("cwlstuff");
        fileCWL.setType(SourceFile.TypeEnum.DOCKSTORE_CWL);
        fileCWL.setPath("/Dockstore.cwl");
        fileCWL.setAbsolutePath("/Dockstore.cwl");
        List<SourceFile> files = new ArrayList<>();
        files.add(fileCWL);
        tag.setSourceFiles(files);
        SourceFile fileDockerFile = new SourceFile();
        fileDockerFile.setContent("dockerstuff");
        fileDockerFile.setType(SourceFile.TypeEnum.DOCKERFILE);
        fileDockerFile.setPath("/Dockerfile");
        fileDockerFile.setAbsolutePath("/Dockerfile");
        tag.getSourceFiles().add(fileDockerFile);
        SourceFile testParameterFile = new SourceFile();
        testParameterFile.setContent("testparameterstuff");
        testParameterFile.setType(SourceFile.TypeEnum.CWL_TEST_JSON);
        testParameterFile.setPath("/test1.json");
        testParameterFile.setAbsolutePath("/test1.json");
        tag.getSourceFiles().add(testParameterFile);
        SourceFile testParameterFile2 = new SourceFile();
        testParameterFile2.setContent("moretestparameterstuff");
        testParameterFile2.setType(SourceFile.TypeEnum.CWL_TEST_JSON);
        testParameterFile2.setPath("/test2.json");
        testParameterFile2.setAbsolutePath("/test2.json");
        tag.getSourceFiles().add(testParameterFile2);
        List<Tag> tags = new ArrayList<>();
        tags.add(tag);
        c.setWorkflowVersions(tags);

        return c;
    }

    @Test
    public void testFailedDuplicateManualImageRegistration() throws ApiException {
        ApiClient client = getAdminWebClient();
        ContainersApi containersApi = new ContainersApi(client);

        DockstoreTool c = getContainer();

        final DockstoreTool container = containersApi.registerManual(c);
        thrown.expect(ApiException.class);
        containersApi.registerManual(container);
    }

    @Test
    public void testGA4GHV1Path() throws IOException {
        // we need to explictly test the path rather than use the swagger generated client classes to enforce the path
        ApiClient client = getAdminWebClient();
        final String basePath = client.getBasePath();
        URL url = new URL(basePath + DockstoreWebserviceApplication.GA4GH_API_PATH + "/tools");
        final List<String> strings = Resources.readLines(url, Charset.forName("UTF-8"));
        assertTrue(strings.size() == 1 && strings.get(0).contains("CommandLineTool"));

        url = new URL(basePath + DockstoreWebserviceApplication.GA4GH_API_PATH + "/metadata");
        final List<String> metadataStrings = Resources.readLines(url, Charset.forName("UTF-8"));
        assertTrue(strings.size() == 1 && strings.get(0).contains("CommandLineTool"));
        assertTrue(metadataStrings.stream().anyMatch(s -> s.contains("friendly_name")));
    }

    @Test
    public void testGA4GHMetadata() throws ApiException {
        ApiClient client = getAdminWebClient();
        Ga4Ghv1Api toolApi = new Ga4Ghv1Api(client);
        final MetadataV1 metadata = toolApi.metadataGet();
        assertTrue(metadata.getFriendlyName().contains("Dockstore"));
    }

    @Test
    public void testGA4GHListContainers() throws ApiException {
        ApiClient client = getAdminWebClient();
        Ga4Ghv1Api toolApi = new Ga4Ghv1Api(client);
        ContainersApi containersApi = new ContainersApi(client);
        // register one more to give us something to look at
        DockstoreTool c = getContainer();
        containersApi.registerManual(c);

        List<io.swagger.client.model.ToolV1> tools = toolApi.toolsGet(null, null, null, null, null, null, null, null, null);
        assertEquals(3, tools.size());

        // test a few constraints
        tools = toolApi.toolsGet(QUAY_IO_TEST_ORG_TEST6, null, null, null, null, null, null, null, null);
        assertEquals(1, tools.size());
        tools = toolApi.toolsGet(QUAY_IO_TEST_ORG_TEST6, Registry.QUAY_IO.toString(), null, null, null, null, null, null, null);
        assertEquals(1, tools.size());
        tools = toolApi.toolsGet(QUAY_IO_TEST_ORG_TEST6, Registry.DOCKER_HUB.toString(), null, null, null, null, null, null, null);
        assertEquals(0, tools.size());
    }

    @Test
    public void testGetSpecificTool() throws ApiException {
        ApiClient client = getAdminWebClient();
        Ga4Ghv1Api toolApi = new Ga4Ghv1Api(client);
        ContainersApi containersApi = new ContainersApi(client);
        // register one more to give us something to look at
        DockstoreTool c = getContainer();
        containersApi.registerManual(c);

        final io.swagger.client.model.ToolV1 tool = toolApi.toolsIdGet(REGISTRY_HUB_DOCKER_COM_SEQWARE_SEQWARE);
        assertNotNull(tool);
        assertEquals(tool.getId(), REGISTRY_HUB_DOCKER_COM_SEQWARE_SEQWARE);
        // get versions
        final List<ToolVersionV1> toolVersions = toolApi.toolsIdVersionsGet(REGISTRY_HUB_DOCKER_COM_SEQWARE_SEQWARE);
        assertEquals(1, toolVersions.size());

        final ToolVersionV1 master = toolApi.toolsIdVersionsVersionIdGet(REGISTRY_HUB_DOCKER_COM_SEQWARE_SEQWARE, "master");
        assertNotNull(master);
        try {
            final ToolVersionV1 foobar = toolApi.toolsIdVersionsVersionIdGet(REGISTRY_HUB_DOCKER_COM_SEQWARE_SEQWARE, "foobar");
            assertNotNull(foobar); // this should be unreachable
        } catch (ApiException e) {
            assertEquals(e.getCode(), HttpStatus.SC_NOT_FOUND);
        }
    }

    @Test
    public void testAddDuplicateTagsForTool() throws ApiException {
        ApiClient client = getAdminWebClient();
        Ga4Ghv1Api toolApi = new Ga4Ghv1Api(client);
        ContainersApi containersApi = new ContainersApi(client);
        ContainertagsApi containertagsApi = new ContainertagsApi(client);
        // register one more to give us something to look at
        DockstoreTool c = getContainer();
        final DockstoreTool dockstoreTool = containersApi.registerManual(c);

        io.swagger.client.model.ToolV1 tool = toolApi.toolsIdGet(REGISTRY_HUB_DOCKER_COM_SEQWARE_SEQWARE);
        assertNotNull(tool);
        assertEquals(tool.getId(), REGISTRY_HUB_DOCKER_COM_SEQWARE_SEQWARE);
        List<Tag> tags = containertagsApi.getTagsByPath(dockstoreTool.getId());
        assertEquals(1, tags.size());
        // register more tags
        Tag tag = new Tag();
        tag.setName("funky_tag");
        tag.setReference("funky_tag");
        containertagsApi.addTags(dockstoreTool.getId(), Lists.newArrayList(tag));
        tags = containertagsApi.getTagsByPath(dockstoreTool.getId());
        assertEquals(2, tags.size());
        // attempt to register duplicates (should fail)

        Tag secondTag = new Tag();
        secondTag.setName("funky_tag");
        secondTag.setReference("funky_tag");
        thrown.expect(ApiException.class);
        containertagsApi.addTags(dockstoreTool.getId(), Lists.newArrayList(secondTag));
    }

    @Test
    public void testGetVerifiedSpecificTool() throws ApiException {
        ApiClient client = getAdminWebClient();
        Ga4Ghv1Api toolApi = new Ga4Ghv1Api(client);
        ContainersApi containersApi = new ContainersApi(client);
        ContainertagsApi containertagsApi = new ContainertagsApi(client);
        // register one more to give us something to look at
        DockstoreTool c = getContainer();
        final DockstoreTool dockstoreTool = containersApi.registerManual(c);

        io.swagger.client.model.ToolV1 tool = toolApi.toolsIdGet(REGISTRY_HUB_DOCKER_COM_SEQWARE_SEQWARE);
        assertNotNull(tool);
        assertEquals(tool.getId(), REGISTRY_HUB_DOCKER_COM_SEQWARE_SEQWARE);
        List<Tag> tags = containertagsApi.getTagsByPath(dockstoreTool.getId());
        assertEquals(1, tags.size());
        Tag tag = tags.get(0);

        // verify master branch
        assertFalse(tag.isVerified());
        assertNull(tag.getVerifiedSource());

        containertagsApi.verifyToolTag(dockstoreTool.getId(), tag.getId());

        // check again
        tags = containertagsApi.getTagsByPath(dockstoreTool.getId());
        tag = tags.get(0);

        // The tag verification endpoint does nothing unless the extended TRS endpoint was used to verify
        assertFalse(tag.isVerified());
        assertNull(tag.getVerifiedSource());
    }

    @Test
    public void testGetFiles() throws IOException, ApiException {
        ApiClient client = getAdminWebClient();
        Ga4Ghv1Api toolApi = new Ga4Ghv1Api(client);
        ContainersApi containersApi = new ContainersApi(client);
        // register one more to give us something to look at
        DockstoreTool c = getContainer();
        containersApi.registerManual(c);

        final ToolDockerfile toolDockerfile = toolApi
                .toolsIdVersionsVersionIdDockerfileGet("registry.hub.docker.com/seqware/seqware/test5", "master");
        assertTrue(toolDockerfile.getDockerfile().contains("dockerstuff"));
        ToolDescriptor cwl = toolApi
            .toolsIdVersionsVersionIdTypeDescriptorGet("cwl", "registry.hub.docker.com/seqware/seqware/test5", "master");
        assertTrue(cwl.getDescriptor().contains("cwlstuff"));

        // hit up the plain text versions
        final String basePath = client.getBasePath();
        String encodedID = "registry.hub.docker.com%2Fseqware%2Fseqware%2Ftest5";
        URL url = UriBuilder.fromPath(basePath)
                .path(DockstoreWebserviceApplication.GA4GH_API_PATH + "/tools/" + encodedID + "/versions/master/PLAIN_CWL/descriptor")
                .build().toURL();

        List<String> strings = Resources.readLines(url, Charset.forName("UTF-8"));
        assertTrue(strings.size() == 1 && strings.get(0).equals("cwlstuff"));

        //hit up the relative path version
        String encodedPath = "%2FDockstore.cwl";
        url = UriBuilder.fromPath(basePath)
                .path(DockstoreWebserviceApplication.GA4GH_API_PATH + "/tools/" + encodedID + "/versions/master/PLAIN_CWL/descriptor/" + encodedPath)
                .build().toURL();
        strings = Resources.readLines(url, Charset.forName("UTF-8"));
        assertTrue(strings.size() == 1 && strings.get(0).equals("cwlstuff"));

        // Get test files
        url = UriBuilder.fromPath(basePath)
                .path(DockstoreWebserviceApplication.GA4GH_API_PATH + "/tools/" + encodedID + "/versions/master/PLAIN_CWL/tests")
                .build().toURL();
        strings = Resources.readLines(url, Charset.forName("UTF-8"));
        assertTrue(strings.get(0).contains("testparameterstuff"));
        assertTrue(strings.get(1).contains("moretestparameterstuff"));
    }

    /**
     * This test should be removed once tag.setVerified is removed because verification should solely depend on the version's source files
     * @throws ApiException
     */
    @Test
    public void testVerifiedToolsViaGA4GH() throws ApiException {
        ApiClient client = getAdminWebClient();
        ContainersApi containersApi = new ContainersApi(client);
        Ga4GhApi ga4GhApi = new Ga4GhApi(client);
        // register one more to give us something to look at
        DockstoreTool c = getContainer();
        c.setIsPublished(true);
        final Tag tag = c.getWorkflowVersions().get(0);
        tag.setVerified(true);
        tag.setVerifiedSource("funky source");
        containersApi.registerManual(c);

        // hit up the plain text versions
        final String basePath = client.getBasePath();
        String encodedID = "registry.hub.docker.com%2Fseqware%2Fseqware%2Ftest5";
        Tool tool = ga4GhApi.toolsIdGet(encodedID);
        // Verifying the tag does nothing because the TRS verification endpoint was not used
        Assert.assertFalse(tool.isVerified());
        Assert.assertEquals("[]", tool.getVerifiedSource());

        // hit up a specific version
        ToolVersion master = ga4GhApi.toolsIdVersionsVersionIdGet(encodedID, "master");
        Assert.assertFalse(master.isVerified());
        Assert.assertEquals("[]", master.getVerifiedSource());
    }

    // Can't test publish repos that don't exist
    @Ignore
    public void testContainerRegistration() throws ApiException {
        ApiClient client = getWebClient();
        ContainersApi containersApi = new ContainersApi(client);
        List<DockstoreTool> containers = containersApi.allPublishedContainers(null, null, null, null, null);

        assertEquals(1, containers.size());

        UsersApi usersApi = new UsersApi(client);
        User user = usersApi.getUser();
        containers = usersApi.userContainers(user.getId());

        assertEquals(5, containers.size());

        DockstoreTool container = containersApi.getContainerByToolPath("quay.io/test_org/test5", null);
        assertFalse(container.isIsPublished());

        long containerId = container.getId();

        PublishRequest pub = SwaggerUtility.createPublishRequest(true);

        container = containersApi.publish(containerId, pub);
        assertTrue(container.isIsPublished());

        containers = containersApi.allPublishedContainers(null, null, null, null, null);
        assertEquals(2, containers.size());

        pub = SwaggerUtility.createPublishRequest(false);

        container = containersApi.publish(containerId, pub);
        assertFalse(container.isIsPublished());
    }

    @Test
    public void testContainerSearch() throws ApiException {
        ApiClient client = getWebClient();
        ContainersApi containersApi = new ContainersApi(client);
        List<DockstoreTool> containers = containersApi.allPublishedContainers(null, null, "test6", null, null);
        assertEquals(1, containers.size());
        assertEquals(containers.get(0).getPath(), QUAY_IO_TEST_ORG_TEST6);

        containers = containersApi.allPublishedContainers(null, null, "test52", null, null);
        assertTrue(containers.isEmpty());
    }

    @Test
    public void testHidingTags() throws ApiException {
        ApiClient client = getAdminWebClient();

        ContainersApi containersApi = new ContainersApi(client);
        // register one more to give us something to look at
        DockstoreTool c = getContainer();
        c.getWorkflowVersions().get(0).setHidden(true);
        c = containersApi.registerManual(c);

        assertEquals("should see one tag as an admin, saw " + c.getWorkflowVersions().size(), 1, c.getWorkflowVersions().size());

        ApiClient muggleClient = getWebClient();
        ContainersApi muggleContainersApi = new ContainersApi(muggleClient);
        final DockstoreTool registeredContainer = muggleContainersApi.getPublishedContainer(c.getId(), null);
        assertEquals("should see no tags as a regular user, saw " + registeredContainer.getWorkflowVersions().size(), 0,
            registeredContainer.getWorkflowVersions().size());
    }

    @Test
    public void testListTokens() throws ApiException {
        ApiClient client = getWebClient();

        UsersApi usersApi = new UsersApi(client);
        User user = usersApi.getUser();

        List<Token> tokens = usersApi.getUserTokens(user.getId());

        assertFalse(tokens.isEmpty());
    }


    @Test
    public void testStarUnpublishedTool() throws ApiException {
        ApiClient client = getWebClient(true, true);
        ContainersApi containersApi = new ContainersApi(client);
        DockstoreTool container = containersApi.getContainerByToolPath("quay.io/test_org/test1", null);
        long containerId = container.getId();
        assertEquals(1, containerId);

        containersApi.publish(containerId, SwaggerUtility.createPublishRequest(false));
        final ApiClient otherWebClient = getWebClient(GITHUB_ACCOUNT_USERNAME, testingPostgres);
        assertNotNull(new UsersApi(otherWebClient).getUser());
        boolean expectedFailure = false;
        try {
            // should not be able to star unpublished entries as a different user
            ContainersApi otherContainersApi = new ContainersApi(otherWebClient);
            otherContainersApi.starEntry(containerId, SwaggerUtility.createStarRequest(true));
        } catch (ApiException e) {
            expectedFailure = true;
        }
        assertTrue(expectedFailure);
    }

    /**
     * Try to star/unstar an unpublished tool
     *
     * @throws ApiException
     */
    @Test
    public void testStarringUnpublishedTool() throws ApiException {
        ApiClient apiClient = getWebClient();
        ContainersApi containersApi = new ContainersApi(apiClient);
        StarRequest request = SwaggerUtility.createStarRequest(true);
        try {
            containersApi.starEntry(1L, request);
            Assert.fail("Should've encountered problems for trying to star an unpublished tool");
        } catch (ApiException e) {
            Assert.assertTrue("Should've gotten a forbidden message", e.getMessage().contains("Forbidden"));
            Assert.assertEquals("Should've gotten a status message", HttpStatus.SC_FORBIDDEN, e.getCode());
        }
        try {
            containersApi.unstarEntry(1L);
            Assert.fail("Should've encountered problems for trying to unstar an unpublished tool");
        } catch (ApiException e) {
            Assert.assertTrue("Should've gotten a forbidden message", e.getMessage().contains("cannot unstar"));
            Assert.assertEquals("Should've gotten a status message", HttpStatus.SC_BAD_REQUEST, e.getCode());
        }
    }

    /**
     * Try to star/unstar an unpublished workflow
     *
     * @throws ApiException
     */
    @Test
    public void testStarringUnpublishedWorkflow() throws ApiException {
        ApiClient apiClient = getWebClient();
        WorkflowsApi workflowsApi = new WorkflowsApi(apiClient);
        ApiClient adminApiClient = getAdminWebClient();
        WorkflowsApi adminWorkflowsApi = new WorkflowsApi(adminApiClient);
        StarRequest request = SwaggerUtility.createStarRequest(true);
        PublishRequest publishRequest = SwaggerUtility.createPublishRequest(false);
        adminWorkflowsApi.publish(11l, publishRequest);
        try {
            workflowsApi.starEntry(11l, request);
            Assert.fail("Should've encountered problems for trying to star an unpublished workflow");
        } catch (ApiException e) {
            Assert.assertTrue("Should've gotten a forbidden message", e.getMessage().contains("Forbidden"));
            Assert.assertEquals("Should've gotten a status message", HttpStatus.SC_FORBIDDEN, e.getCode());
        }
        try {
            workflowsApi.unstarEntry(11l);
            Assert.fail("Should've encountered problems for trying to unstar an unpublished workflow");
        } catch (ApiException e) {
            Assert.assertTrue("Should've gotten a forbidden message", e.getMessage().contains("cannot unstar"));
            Assert.assertEquals("Should've gotten a status message", HttpStatus.SC_BAD_REQUEST, e.getCode());
        }
    }

    /**
     * This tests if a tool can be starred twice.
     * This test will pass if this action cannot be performed.
     *
     * @throws ApiException
     */
    @Test
    public void testStarStarredTool() throws ApiException {
        ApiClient client = getWebClient();
        ContainersApi containersApi = new ContainersApi(client);
        DockstoreTool container = containersApi.getContainerByToolPath("quay.io/test_org/test2", null);
        assertTrue("There should be at least one user of the workflow", container.getUsers().size() > 0);
        Assert.assertNotNull("Upon checkUser(), a container with lazy loaded users should still get users", container.getUsers());
        long containerId = container.getId();
        assertEquals(2, containerId);

        StarRequest request = SwaggerUtility.createStarRequest(true);
        containersApi.starEntry(containerId, request);
        List<User> starredUsers = containersApi.getStarredUsers(container.getId());
        Assert.assertEquals(1, starredUsers.size());
        starredUsers.forEach(user -> assertNull("User profile is not lazy loaded in starred users", user.getUserProfiles()));
        thrown.expect(ApiException.class);
        containersApi.starEntry(containerId, request);
    }


    /**
     * This tests if an already unstarred tool can be unstarred again.
     * This test will pass if this action cannot be performed.
     *
     * @throws ApiException
     */
    @Test
    public void testUnstarUnstarredTool() throws ApiException {
        ApiClient client = getWebClient();
        ContainersApi containersApi = new ContainersApi(client);
        DockstoreTool container = containersApi.getContainerByToolPath("quay.io/test_org/test2", null);
        Assert.assertNotNull("Upon checkUser(), a container with lazy loaded users should still get users", container.getUsers());
        long containerId = container.getId();
        assertEquals(2, containerId);
        thrown.expect(ApiException.class);
        containersApi.unstarEntry(containerId);
    }

    /**
     * This tests if a workflow can be starred twice.
     * This test will pass if this action cannot be performed.
     *
     * @throws ApiException
     */
    @Test
    public void testStarStarredWorkflow() throws ApiException {
        ApiClient client = getWebClient();
        WorkflowsApi workflowsApi = new WorkflowsApi(client);
        Workflow workflow = workflowsApi.getPublishedWorkflowByPath("github.com/A/l", null, false);
        long workflowId = workflow.getId();
        assertEquals(11, workflowId);
        StarRequest request = SwaggerUtility.createStarRequest(true);
        workflowsApi.starEntry(workflowId, request);
        List<User> starredUsers = workflowsApi.getStarredUsers(workflow.getId());
        Assert.assertEquals(1, starredUsers.size());
        starredUsers.forEach(user -> assertNull("User profile is not lazy loaded in starred users", user.getUserProfiles()));
        thrown.expect(ApiException.class);
        workflowsApi.starEntry(workflowId, request);
    }

    /**
     * This tests if an already unstarred workflow can be unstarred again.
     * This test will pass if this action cannot be performed.
     *
     * @throws ApiException
     */
    @Test
    public void testUnstarUnstarredWorkflow() throws ApiException {
        ApiClient client = getWebClient();
        WorkflowsApi workflowApi = new WorkflowsApi(client);
        Workflow workflow = workflowApi.getPublishedWorkflowByPath("github.com/A/l", null, false);
        long workflowId = workflow.getId();
        assertEquals(11, workflowId);
        thrown.expect(ApiException.class);
        workflowApi.unstarEntry(workflowId);
    }

    /**
     * This tests many combinations of starred tools would be returned in the same order
     * This test will pass if the order returned is always the same
     *
     * @throws ApiException
     */
    @Test
    public void testStarredToolsOrder() throws ApiException {
        ApiClient apiClient = getAdminWebClient();
        UsersApi usersApi = new UsersApi(apiClient);
        ContainersApi containersApi = new ContainersApi(apiClient);
        List<Long> containerIds1 = Arrays.asList((long)1, (long)2, (long)3, (long)4, (long)5);
        List<Long> containerIds2 = Arrays.asList((long)1, (long)3, (long)5, (long)2, (long)4);
        List<Long> containerIds3 = Arrays.asList((long)2, (long)4, (long)1, (long)3, (long)5);
        List<Long> containerIds4 = Arrays.asList((long)5, (long)4, (long)3, (long)2, (long)1);
        starring(containerIds1, containersApi, usersApi);
        starring(containerIds2, containersApi, usersApi);
        starring(containerIds3, containersApi, usersApi);
        starring(containerIds4, containersApi, usersApi);
    }

    @Test
    public void testEnumMetadataEndpoints() throws ApiException {
        ApiClient apiClient = getWebClient();
        MetadataApi metadataApi = new MetadataApi(apiClient);
        final List<RegistryBean> dockerRegistries = metadataApi.getDockerRegistries();
        final List<DescriptorLanguageBean> descriptorLanguages = metadataApi.getDescriptorLanguages();
        assertNotNull(dockerRegistries);
        assertNotNull(descriptorLanguages);
    }

    @Test
    public void testCacheMetadataEndpoint() throws ApiException{
        ApiClient apiClient = getWebClient();
        MetadataApi metadataApi = new MetadataApi(apiClient);
        final Map<String, Object> cachePerformance = metadataApi.getCachePerformance();
        assertNotNull(cachePerformance);
    }

    @Test
    public void testRSSPlusSiteMap() throws ApiException, IOException, ParserConfigurationException, SAXException {
        ApiClient apiClient = getWebClient();
        MetadataApi metadataApi = new MetadataApi(apiClient);
        String rssFeed = metadataApi.rssFeed();
        String sitemap = metadataApi.sitemap();
        assertTrue("rss feed should be valid xml with at least 2 entries", rssFeed.contains("http://localhost/containers/quay.io/test_org/test6") && rssFeed.contains("http://localhost/workflows/github.com/A/l"));

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setValidating(true);
        factory.setIgnoringElementContentWhitespace(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        try (InputStream stream = IOUtils.toInputStream(rssFeed, StandardCharsets.UTF_8)) {
            Document doc = builder.parse(stream);
            assertTrue("XML is not valid", doc.getStrictErrorChecking());
        }

        assertTrue("sitemap with testing data should have at least 2 entries", sitemap.split("\n").length >= 2 && sitemap.contains("http://localhost/containers/quay.io/test_org/test6") && sitemap.contains("http://localhost/workflows/github.com/A/l"));
    }

    @Test
    public void testDuplicateHostedWorkflowCreationNull() {
        registerHostedWorkflow(null);
    }

    @Test
    public void testDuplicateHostedWorkflowCreation() {
        registerHostedWorkflow("");
    }

    private void registerHostedWorkflow(String s) {
        final ApiClient userWebClient = getWebClient(true, true);
        final HostedApi userHostedApi = new HostedApi(userWebClient);
        userHostedApi.createHostedWorkflow("hosted1", s, "cwl", s, null);
        thrown.expect(ApiException.class);
        userHostedApi.createHostedWorkflow("hosted1", s, "cwl", s, null);
    }

    @Test
    public void testDuplicateHostedToolCreation() {
        final ApiClient userWebClient = getWebClient(true, true);
        final HostedApi userHostedApi = new HostedApi(userWebClient);
        userHostedApi.createHostedTool("hosted1", Registry.QUAY_IO.toString().toLowerCase(), CWL.getLowerShortName(), "dockstore.org", null);
        thrown.expect(ApiException.class);
        userHostedApi.createHostedTool("hosted1", Registry.QUAY_IO.toString().toLowerCase(), CWL.getLowerShortName(), "dockstore.org", null);
    }

    @Test
    public void testUploadZip() {
        final ApiClient webClient = getWebClient();
        final HostedApi hostedApi = new HostedApi(webClient);
        final Workflow hostedWorkflow = hostedApi.createHostedWorkflow("hosted", "something", "wdl", "something", null);
        // Created workflow, no versions
        Assert.assertEquals(0, hostedWorkflow.getWorkflowVersions().size());
        final String smartseqZip = ResourceHelpers.resourceFilePath("smartseq.zip");
        final Workflow updatedWorkflow = hostedApi.addZip(hostedWorkflow.getId(), new File(smartseqZip));
        // A version should now exist.
        Assert.assertEquals(1, updatedWorkflow.getWorkflowVersions().size());
    }

    /**
     * Test that the config endpoint doesn't fail and validates one random property
     */
    @Test
    public void testConfig() {
        final ApiClient webClient = getWebClient();
        final MetadataApi metadataApi = new MetadataApi(webClient);
        final Config config = metadataApi.getConfig();
        Assert.assertEquals("read:org,user:email", config.getGitHubScope());
    }

    /**
     * Tests workflow sharing/permissions.
     *
     * A longish method, but since we need to set up hosted workflows
     * to do the sharing, but don't want to do that with the other tests,
     * it seemed better to do the setup and variations all in this one method.
     */
    @Test
    public void testSharing()  {
        // Setup for sharing
        final ApiClient user1WebClient = getWebClient(true, true); // Admin user
        final ApiClient user2WebClient = getWebClient(true, false);
        final HostedApi user1HostedApi = new HostedApi(user1WebClient);
        final HostedApi user2HostedApi = new HostedApi(user2WebClient);
        final WorkflowsApi user1WorkflowsApi = new WorkflowsApi(user1WebClient);
        final WorkflowsApi user2WorkflowsApi = new WorkflowsApi(user2WebClient);
        final WorkflowsApi anonWorkflowsApi = new WorkflowsApi(getAnonymousWebClient());
        final UsersApi users2Api = new UsersApi(user2WebClient);
        final User user2 = users2Api.getUser();

        List<SharedWorkflows> sharedWorkflows;
        SharedWorkflows firstShared;
        SharedWorkflows secondShared;

        // Create two hosted workflows
        final Workflow hostedWorkflow1 = user1HostedApi.createHostedWorkflow("hosted1", null, "cwl", null, null);
        final Workflow hostedWorkflow2 = user1HostedApi.createHostedWorkflow("hosted2", null, "wdl", null, null);

        final String fullWorkflowPath1 = hostedWorkflow1.getFullWorkflowPath();
        final String fullWorkflowPath2 = hostedWorkflow2.getFullWorkflowPath();

        // User 2 should have no workflows shared with
        Assert.assertEquals(user2WorkflowsApi.sharedWorkflows().size(), 0);

        // User 2 should not be able to read user 1's hosted workflow
        try {
            user2WorkflowsApi.getWorkflowByPath(fullWorkflowPath1, null, false);
            Assert.fail("User 2 should not have rights to hosted workflow");
        } catch (ApiException e) {
            Assert.assertEquals(403, e.getCode());
        }

        // User 1 shares workflow with user 2 as a reader
        shareWorkflow(user1WorkflowsApi, user2.getUsername(), fullWorkflowPath1, Permission.RoleEnum.READER);

        // User 2 should now have 1 workflow shared with
        sharedWorkflows = user2WorkflowsApi.sharedWorkflows();
        Assert.assertEquals(1, sharedWorkflows.size());

        firstShared = sharedWorkflows.get(0);
        Assert.assertEquals(SharedWorkflows.RoleEnum.READER, firstShared.getRole());
        Assert.assertEquals(fullWorkflowPath1, firstShared.getWorkflows().get(0).getFullWorkflowPath());

        // User 2 can now read the hosted workflow (will throw exception if it fails).
        user2WorkflowsApi.getWorkflowByPath(fullWorkflowPath1, null, false);
        user2WorkflowsApi.getWorkflow(hostedWorkflow1.getId(), null);

        // But User 2 cannot edit the hosted workflow
        try {
            user2HostedApi.editHostedWorkflow(hostedWorkflow1.getId(), Collections.emptyList());
            Assert.fail("User 2 can unexpectedly edit a readonly workflow");
        } catch (ApiException ex) {
            Assert.assertEquals(403, ex.getCode());
        }

        // Now give write permission to user 2
        shareWorkflow(user1WorkflowsApi, user2.getUsername(), fullWorkflowPath1, Permission.RoleEnum.WRITER);
        // Edit should now work!
        final Workflow workflow = user2HostedApi.editHostedWorkflow(hostedWorkflow1.getId(), Collections.singletonList(createCwlWorkflow()));

        // Deleting the version should not fail
        user2HostedApi.deleteHostedWorkflowVersion(hostedWorkflow1.getId(), workflow.getWorkflowVersions().get(0).getId().toString());

        // Publishing the workflow should fail
        final PublishRequest publishRequest = SwaggerUtility.createPublishRequest(true);
        try {
            user2WorkflowsApi.publish(hostedWorkflow1.getId(), publishRequest);
            Assert.fail("User 2 can unexpectedly publish a read/write workflow");
        } catch (ApiException ex) {
            Assert.assertEquals(403, ex.getCode());
        }

        // Give Owner permission to user 2
        shareWorkflow(user1WorkflowsApi, user2.getUsername(), fullWorkflowPath1, Permission.RoleEnum.OWNER);

        // Should be able to publish
        user2WorkflowsApi.publish(hostedWorkflow1.getId(), publishRequest);
        checkAnonymousUser(anonWorkflowsApi, hostedWorkflow1);

        // Next, User 1 shares a second workflow with user 2 as a reader only
        shareWorkflow(user1WorkflowsApi, user2.getUsername(), fullWorkflowPath2, Permission.RoleEnum.READER);
        sharedWorkflows = user2WorkflowsApi.sharedWorkflows();

        // User 2 should now have one workflow shared from user 1 and one from user 3
        Assert.assertEquals(2, sharedWorkflows.size());

        firstShared = sharedWorkflows
                        .stream()
                        .filter(shared -> shared.getRole() == SharedWorkflows.RoleEnum.OWNER)
                        .findFirst().orElse(null);
        secondShared = sharedWorkflows
                        .stream()
                        .filter(shared -> shared.getRole() == SharedWorkflows.RoleEnum.READER)
                        .findFirst().orElse(null);

        Assert.assertEquals(SharedWorkflows.RoleEnum.OWNER, firstShared.getRole());
        Assert.assertEquals(fullWorkflowPath1, firstShared.getWorkflows().get(0).getFullWorkflowPath());

        Assert.assertEquals(SharedWorkflows.RoleEnum.READER, secondShared.getRole());
        Assert.assertEquals(fullWorkflowPath2, secondShared.getWorkflows().get(0).getFullWorkflowPath());
    }

    private void shareWorkflow(WorkflowsApi workflowsApi, String user, String path, Permission.RoleEnum role) {
        final Permission permission = new Permission();
        permission.setEmail(user);
        permission.setRole(role);
        workflowsApi.addWorkflowPermission(path, permission, false);
    }

    private void checkAnonymousUser(WorkflowsApi anonWorkflowsApi, Workflow hostedWorkflow) {
        try {
            anonWorkflowsApi.getWorkflowByPath(hostedWorkflow.getFullWorkflowPath(), null, false);
            Assert.fail("Anon user should not have rights to " + hostedWorkflow.getFullWorkflowPath());
        } catch (ApiException ex) {
            Assert.assertEquals(401, ex.getCode());
        }
    }

    private SourceFile createCwlWorkflow() {
        SourceFile fileCWL = new SourceFile();
        fileCWL.setContent("class: Workflow\ncwlVersion: v1.0"); // Need this for CWLHandler:isValidWorkflow
        fileCWL.setType(SourceFile.TypeEnum.DOCKSTORE_CWL);
        fileCWL.setPath("/Dockstore.cwl");
        fileCWL.setAbsolutePath("/Dockstore.cwl");
        return fileCWL;
    }

    private void starring(List<Long> containerIds, ContainersApi containersApi, UsersApi usersApi)
            throws ApiException {
        StarRequest request = SwaggerUtility.createStarRequest(true);
        containerIds.forEach(containerId -> {
            try {
                containersApi.starEntry(containerId, request);
            } catch (ApiException e) {
                fail("Couldn't star entry");
            }
        });
        List<Entry> starredTools = usersApi.getStarredTools();
        for (int i = 0; i < 5; i++) {
            Long id = starredTools.get(i).getId();
            assertEquals("Wrong order of starred tools returned, should be in ascending order.  Got" + id + ". Should be " + i + 1,
                (long)id, i + 1);
        }
        containerIds.parallelStream().forEach(containerId -> {
            try {
                containersApi.unstarEntry(containerId);
            } catch (ApiException e) {
                fail("Couldn't unstar entry");
            }
        });
    }
}
