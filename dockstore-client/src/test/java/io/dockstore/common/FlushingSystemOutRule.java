package io.dockstore.common;

import org.junit.contrib.java.lang.system.SystemOutRule;

public class FlushingSystemOutRule extends SystemOutRule {

    @SuppressWarnings("EmptyCatchBlock")
    private void pauseAndFlush() {
        try {
            Thread.sleep(500);
            System.out.flush();
        } catch (Exception e) {
        }
    }

    @Override
    public String getLog() {
        pauseAndFlush();
        return super.getLog();
    }

    @Override
    public byte[] getLogAsBytes() {
        pauseAndFlush();
        return super.getLogAsBytes();
    }

    @Override
    public String getLogWithNormalizedLineSeparator() {
        pauseAndFlush();
        return super.getLogWithNormalizedLineSeparator();
    }
}
