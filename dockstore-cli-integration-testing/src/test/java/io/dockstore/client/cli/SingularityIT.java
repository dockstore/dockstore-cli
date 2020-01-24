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
            "github.com/DockstoreTestUser2/md5sum-checker/testname",
            "--json", ResourceHelpers.resourceFilePath("md5sum-wrapper-tool.json")
        });
    }
}
