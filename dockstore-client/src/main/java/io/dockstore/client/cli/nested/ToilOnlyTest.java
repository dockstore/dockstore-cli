package io.dockstore.client.cli.nested;

public interface ToilOnlyTest {

    String NAME = "io.dockstore.client.cli.nested.ToilOnlyTest";

    default String getName() {
        return NAME;
    }
}
