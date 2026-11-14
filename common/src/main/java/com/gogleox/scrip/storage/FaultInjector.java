package com.gogleox.scrip.storage;

import java.io.IOException;

@FunctionalInterface
public interface FaultInjector {
    FaultInjector NONE = point -> {};

    void at(FaultPoint point) throws IOException;
}
