package io.dockstore.client.cli.nested;

public interface SingularityTest {

    String NAME = "io.dockstore.common.SingularityTest";

    default String getName() {
        return NAME;
    }
}
