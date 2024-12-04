package io.dockstore.common;

public interface ToilOnlyTest {

    String NAME = "io.dockstore.common.ToilOnlyTest";

    default String getName() {
        return NAME;
    }
}
