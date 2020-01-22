package io.dockstore.client.cli;

import io.dockstore.client.cli.nested.SingularityTest;
import io.dropwizard.testing.ResourceHelpers;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Category(SingularityTest.class)
public class SingularityIT extends BaseIT {

    @Test
    public void runCwlWorkflow() {
        Client.main(new String[] {
            "--config",
            ResourceHelpers.resourceFilePath("config_for_singularity"),
            "workflow",
            "launch",
            "--entry",
            "github.com/dockstore-testing/md5sum-checker/_cwl_checker:1.0.1",
            "--json", ResourceHelpers.resourceFilePath("md5sum-wrapper-tool.json")
        });
    }
}
