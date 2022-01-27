package io.dockstore.client.cli;

import io.dockstore.client.cli.nested.WesFile;
import io.dropwizard.testing.ResourceHelpers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class WesFileIT {

    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();
    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog();

    @Test
    public void testFileSuffixNaming() {
        final String descriptorPath = ResourceHelpers.resourceFilePath("hello.wdl");

        for (int i = descriptorPath.length() - 2; i >= 0; i--) {
            final String desiredSuffix = descriptorPath.substring(i);

            // Skip absolute paths, that will be tested elsewhere
            if (!desiredSuffix.startsWith("/")) {
                WesFile wesFile = new WesFile(descriptorPath, null, desiredSuffix);
                assertEquals("The WesFile object name should be the same as the suffix", desiredSuffix, wesFile.getName());
            }

        }
    }

    @Test
    public void testFileSuffixBadNaming() {
        final String descriptorPath = ResourceHelpers.resourceFilePath("hello.wdl");

        final String desiredSuffix = "/resources/hello.wdl";
        WesFile wesFile = new WesFile(descriptorPath, null, desiredSuffix);
        systemExit.expectSystemExit();
        wesFile.getName();
        fail("Should have failed when given an absolute path");
    }

    @Test
    public void testFileSuffixBadNaming2() {
        final String descriptorPath = ResourceHelpers.resourceFilePath("hello.wdl");

        final String desiredSuffix = "/resources/badSuffix.wdl";
        WesFile wesFile = new WesFile(descriptorPath, null, desiredSuffix);
        systemExit.expectSystemExit();
        wesFile.getName();
        fail("Should have failed when given an absolute path");

    }

    @Test
    public void testFileSuffixBadNaming3() {
        final String descriptorPath = ResourceHelpers.resourceFilePath("hello.wdl");

        final String desiredSuffix = "resources/badSuffix.wdl";
        WesFile wesFile = new WesFile(descriptorPath, null, desiredSuffix);
        systemExit.expectSystemExit();
        wesFile.getName();
        fail("Should have failed when given an invalid suffix");
    }

    @Test
    public void testFilePrefix() {
        final String descriptorPath = ResourceHelpers.resourceFilePath("hello.wdl");

        for (int i = 1; i < descriptorPath.length(); i++) {
            final String removablePrefix = descriptorPath.substring(0, i);
            WesFile wesFile = new WesFile(descriptorPath, removablePrefix, null);
            final String expectedSuffix = descriptorPath.substring(removablePrefix.length()).replaceAll("^/+", "");
            assertEquals("We should be given all text content after the removed prefix, no leading slashes.", expectedSuffix, wesFile.getName());
        }
    }

    @Test
    public void testAllNull() {
        final String descriptorPath = ResourceHelpers.resourceFilePath("hello.wdl");

        WesFile wesFile = new WesFile(descriptorPath, null, null);
        assertEquals("The default File getName() functionality should be used if no additional constructor parameters are given",
            "hello.wdl", wesFile.getName());
    }
}
