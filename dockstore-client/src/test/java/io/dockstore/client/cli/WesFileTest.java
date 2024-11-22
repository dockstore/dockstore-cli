package io.dockstore.client.cli;

import static uk.org.webcompere.systemstubs.SystemStubs.catchSystemExit;

import io.dockstore.client.cli.nested.WesFile;
import io.dockstore.common.FlushingSystemErr;
import io.dockstore.common.FlushingSystemOut;
import io.dockstore.common.MuteForSuccessfulTests;
import io.dropwizard.testing.ResourceHelpers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

@ExtendWith(SystemStubsExtension.class)
@ExtendWith(MuteForSuccessfulTests.class)
public class WesFileTest {

    @SystemStub
    public final SystemOut systemOutRule = new FlushingSystemOut();

    @SystemStub
    public final SystemErr systemErrRule = new FlushingSystemErr();

    @Test
    void testFileSuffixNaming() {
        final String descriptorPath = ResourceHelpers.resourceFilePath("hello.wdl");

        for (int i = descriptorPath.length() - 2; i >= 0; i--) {
            final String desiredSuffix = descriptorPath.substring(i);

            // Skip absolute paths, that will be tested elsewhere
            if (!desiredSuffix.startsWith("/")) {
                WesFile wesFile = new WesFile(descriptorPath, null, desiredSuffix);
                Assertions.assertEquals(desiredSuffix, wesFile.getName(), "The WesFile object name should be the same as the suffix");
            }

        }
    }

    @Test
    void testFileSuffixBadNaming() throws Exception {
        final String descriptorPath = ResourceHelpers.resourceFilePath("hello.wdl");

        final String desiredSuffix = "/resources/hello.wdl";
        WesFile wesFile = new WesFile(descriptorPath, null, desiredSuffix);
        catchSystemExit(wesFile::getName);
        // Should have failed when given an absolute path
    }

    @Test
    void testFileSuffixBadNaming2() throws Exception {
        final String descriptorPath = ResourceHelpers.resourceFilePath("hello.wdl");

        final String desiredSuffix = "/resources/badSuffix.wdl";
        WesFile wesFile = new WesFile(descriptorPath, null, desiredSuffix);
        catchSystemExit(wesFile::getName);
        // Should have failed when given an absolute path
    }

    @Test
    void testFileSuffixBadNaming3() throws Exception {
        final String descriptorPath = ResourceHelpers.resourceFilePath("hello.wdl");

        final String desiredSuffix = "resources/badSuffix.wdl";
        WesFile wesFile = new WesFile(descriptorPath, null, desiredSuffix);
        catchSystemExit(wesFile::getName);
        // Should have failed when given an invalid suffix
    }

    @Test
    void testFilePrefix() {
        final String descriptorPath = ResourceHelpers.resourceFilePath("hello.wdl");

        for (int i = 1; i < descriptorPath.length(); i++) {
            final String removablePrefix = descriptorPath.substring(0, i);
            WesFile wesFile = new WesFile(descriptorPath, removablePrefix, null);
            final String expectedSuffix = descriptorPath.substring(removablePrefix.length()).replaceAll("^/+", "");
            Assertions.assertEquals(expectedSuffix, wesFile.getName(),
                    "We should be given all text content after the removed prefix, no leading slashes.");
        }
    }

    @Test
    void testAllNull() {
        final String descriptorPath = ResourceHelpers.resourceFilePath("hello.wdl");

        WesFile wesFile = new WesFile(descriptorPath, null, null);
        Assertions.assertEquals("hello.wdl", wesFile.getName(),
                "The default File getName() functionality should be used if no additional constructor parameters are given");
    }

    @Test
    void testRelativeDirectory() throws Exception {
        WesFile wesFile = new WesFile("/fake/path/to/file", "not/absolute", null);
        catchSystemExit(wesFile::getName);
        // Should have failed when given a relative directory path
    }
}
