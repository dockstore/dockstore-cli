package io.dockstore.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import io.dockstore.provision.ProvisionInterface;
import java.io.File;
import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class FileProvisionTest {

    @Test
    void testFindSupportedTargetPath() {
        ProvisionInterface s3Mock = Mockito.mock(ProvisionInterface.class);
        when(s3Mock.schemesHandled()).thenReturn(new HashSet<>(List.of("s3")));
        ProvisionInterface httpMock = Mockito.mock(ProvisionInterface.class);
        when(httpMock.schemesHandled()).thenReturn(new HashSet<>(List.of("http")));

        assertEquals(
                FileProvisioning.findSupportedTargetPath(Arrays.asList(s3Mock, httpMock), Arrays.asList("s3://something", "http://something")),
                Optional.of(new ImmutablePair<>("s3://something", "s3")));

        assertEquals(
                FileProvisioning.findSupportedTargetPath(Arrays.asList(s3Mock, httpMock), Arrays.asList("http://something", "s3://something")),
                Optional.of(new ImmutablePair<>("http://something", "http")));

        assertEquals(FileProvisioning.findSupportedTargetPath(Arrays.asList(s3Mock, httpMock), List.of("gcs://something")),
                Optional.empty());
    }

    @Test
    void testFindPluginName() {
        String s3Class = "io.dockstore.provision.S3Plugin$S3Provision";
        String dosClass = "io.dockstore.provision.DOSPlugin$DOSPreProvision";

        assertEquals("io.dockstore.provision.S3Plugin", FileProvisioning.findPluginName(s3Class));
        assertEquals("io.dockstore.provision.DOSPlugin", FileProvisioning.findPluginName(dosClass));
    }

    @Test
    void testCreateFileURISpaces() {
        //verifies that creation of URI for input file provisioning can encode paths with space characters
        String encodedPath = "src/test/resources/testDirectory%20With%20Spaces/hello.txt";
        File inputFile = FileUtils.getFile("src", "test", "resources", "testDirectory With Spaces", "hello.txt");
        assertEquals(URI.create(encodedPath), FileProvisioning.createURIFromUnencodedPath(inputFile.getPath()));
    }
}
