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
import java.nio.charset.StandardCharsets;

import io.dockstore.client.cli.nested.ToolClient;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.TestUtility;
import io.dropwizard.testing.ResourceHelpers;
import io.swagger.client.ApiException;
import io.swagger.client.api.UsersApi;
import io.swagger.client.model.SourceFile;
import io.swagger.client.model.ToolDescriptor;
import io.swagger.client.model.User;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.spy;
import static org.powermock.api.mockito.PowerMockito.whenNew;

/**
 * These tests use mocking to simulate responses from GitHub, BitBucket, and Quay.io
 *
 * @author dyuen
 */
@PowerMockIgnore({ "org.apache.http.conn.ssl.*", "javax.net.ssl.*", "javax.crypto.*", "javax.management.*", "javax.net.*", "com.sun.org.apache.xerces.*", "javax.xml.*", "org.xml.*", "org.w3c.*", "org.apache.http.*" })
@RunWith(PowerMockRunner.class)
@PrepareForTest({ Client.class, ToolClient.class, UsersApi.class })
@Category(ConfidentialTest.class)
public class MockedIT {

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();

    private Client client;


    @Before
    public void clearDB() throws Exception {
        this.client = mock(Client.class);
        ToolClient toolClient = spy(new ToolClient(client, false));

        final UsersApi userApiMock = mock(UsersApi.class);
        when(client.getConfigFile()).thenReturn(TestUtility.getConfigFileLocation(true));
        when(userApiMock.getUser()).thenReturn(new User());
        whenNew(UsersApi.class).withAnyArguments().thenReturn(userApiMock);
        whenNew(ToolClient.class).withAnyArguments().thenReturn(toolClient);

        // mock return of a simple CWL file
        File sourceFile = new File(ResourceHelpers.resourceFilePath("dockstore-tool-linux-sort.cwl"));
        final String sourceFileContents = FileUtils.readFileToString(sourceFile, StandardCharsets.UTF_8);
        SourceFile file = mock(SourceFile.class);
        when(file.getContent()).thenReturn(sourceFileContents);
        doReturn(file).when(toolClient).getDescriptorFromServer("quay.io/collaboratory/dockstore-tool-linux-sort", DescriptorLanguage.CWL);
        when(file.getPath()).thenReturn(sourceFile.getAbsolutePath());

        // change getDescriptorFromServer to downloadTargetEntry
        doReturn(sourceFile).when(toolClient).downloadTargetEntry(eq("quay.io/collaboratory/dockstore-tool-linux-sort"),
            eq(ToolDescriptor.TypeEnum.CWL), eq(true), any(File.class));

        // mock return of a more complicated CWL file
        File sourceFileArrays = new File(ResourceHelpers.resourceFilePath("arrays.cwl"));
        final String sourceFileArraysContents = FileUtils.readFileToString(sourceFileArrays, StandardCharsets.UTF_8);
        SourceFile file2 = mock(SourceFile.class);
        when(file2.getContent()).thenReturn(sourceFileArraysContents);
        when(file2.getPath()).thenReturn(sourceFileArrays.getAbsolutePath());
        doReturn(file2).when(toolClient).getDescriptorFromServer("quay.io/collaboratory/arrays", DescriptorLanguage.CWL);

        // change getDescriptorFromServer to downloadTargetEntry
        doReturn(sourceFileArrays).when(toolClient).downloadTargetEntry(eq("quay.io/collaboratory/arrays"),
            eq(ToolDescriptor.TypeEnum.CWL), eq(true), any(File.class));

        FileUtils.deleteQuietly(new File("/tmp/wc1.out"));
        FileUtils.deleteQuietly(new File("/tmp/wc2.out"));
        FileUtils.deleteQuietly(new File("/tmp/example.bedGraph"));

        try {
            FileUtils.copyFile(new File(ResourceHelpers.resourceFilePath("example.bedGraph")), new File("./datastore/example.bedGraph"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @After
    public void clearFiles() {
        FileUtils.deleteQuietly(new File("/tmp/wc1.out"));
        FileUtils.deleteQuietly(new File("/tmp/wc2.out"));
        FileUtils.deleteQuietly(new File("/tmp/example.bedGraph"));
    }

    @Test
    public void runLaunchOneJson() throws IOException, ApiException {
        Client.main(new String[] { "--config", TestUtility.getConfigFileLocation(true), "tool", "launch", "--entry",
                "quay.io/collaboratory/dockstore-tool-linux-sort", "--json", ResourceHelpers.resourceFilePath("testOneRun.json"),
                "--script" });

        Assert.assertTrue("output should contain cwltool command", systemOutRule.getLog().contains("Executing: cwltool"));
    }

    // TODO: This is returning false positives, disabling for now until we add array support
    @Ignore
    public void runLaunchNJson() throws IOException {
        Client.main(new String[] { "--config", TestUtility.getConfigFileLocation(true), "tool", "launch", "--entry",
                "quay.io/collaboratory/dockstore-tool-linux-sort", "--json", ResourceHelpers.resourceFilePath("testMultipleRun.json"),
                "--script" });
    }

    /**
     * Tests local file input in arrays or as single files, output to local file
     *
     * @throws IOException
     * @throws ApiException
     */
    @Test
    public void runLaunchOneLocalArrayedJson() throws IOException, ApiException {
        Client.main(new String[] { "--config", TestUtility.getConfigFileLocation(true), "tool", "launch", "--entry",
                "quay.io/collaboratory/arrays", "--json", ResourceHelpers.resourceFilePath("testArrayLocalInputLocalOutput.json"),
                "--script" });

        Assert.assertTrue(new File("/tmp/example.bedGraph").exists());
        Assert.assertTrue("output should contain cwltool command", systemOutRule.getLog().contains("Executing: cwltool"));
    }

    /**
     * Tests local file input in arrays or as single files, output to local file
     *
     * @throws IOException
     * @throws ApiException
     */
    @Test
    public void runLaunchOneLocalArrayedJsonWithCache() throws IOException, ApiException {
        String configFileLocation = TestUtility.getConfigFileLocation(true, true, true);
        when(client.getConfigFile()).thenReturn(configFileLocation);

        Client.main(new String[] { "--clean-cache", "--config", configFileLocation, "--script" });
        // this is kind of redundant, it looks like we take the mocked config file no matter what
        Client.main(new String[] { "--config", configFileLocation, "tool", "launch", "--entry", "quay.io/collaboratory/arrays", "--json",
                ResourceHelpers.resourceFilePath("testArrayLocalInputLocalOutput.json"),
                "--script" });

        Assert.assertTrue(new File("/tmp/example.bedGraph").exists());
        Assert.assertTrue("output should contain cwltool command", systemOutRule.getLog().contains("Executing: cwltool"));
        systemOutRule.clearLog();

        // try again, things should be cached now
        Client.main(new String[] { "--config", configFileLocation, "tool", "launch", "--entry", "quay.io/collaboratory/arrays", "--json",
                ResourceHelpers.resourceFilePath("testArrayLocalInputLocalOutput.json"),
                "--script" });
        Assert.assertEquals("output should contain only hard linking", 6, StringUtils.countMatches(systemOutRule.getLog(), "hard-linking"));
        Assert.assertTrue("output should not contain warnings about skipping files", !systemOutRule.getLog().contains("skipping"));
    }

    /**
     * Tests http file input in arrays or as single files, output to local file and local array
     *
     * @throws IOException
     * @throws ApiException
     */
    @Test
    public void runLaunchOneHTTPArrayedJson() throws IOException, ApiException {
        Client.main(new String[] { "--config", TestUtility.getConfigFileLocation(true), "tool", "launch", "--entry",
                "quay.io/collaboratory/arrays", "--json", ResourceHelpers.resourceFilePath("testArrayHttpInputLocalOutput.json"),
                "--script" });

        Assert.assertTrue(new File("/tmp/wc1.out").exists());
        Assert.assertTrue(new File("/tmp/wc2.out").exists());
        Assert.assertTrue(new File("/tmp/example.bedGraph").exists());

        Assert.assertTrue("output should contain cwltool command", systemOutRule.getLog().contains("Executing: cwltool"));
    }
}
