package io.dockstore.client.cli.nested;

public interface SingularityTest {

    String NAME = "io.dockstore.client.cli.nested.SingularityTest";

    default String getName() {
        return NAME;
    }
}
