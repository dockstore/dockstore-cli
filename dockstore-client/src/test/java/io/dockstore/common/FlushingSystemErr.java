package io.dockstore.common;

import java.io.OutputStream;

import uk.org.webcompere.systemstubs.stream.SystemErr;

public class FlushingSystemErr extends SystemErr {

    @SuppressWarnings("EmptyCatchBlock")
    private void pauseAndFlush() {
        try {
            Thread.sleep(500);
            System.err.flush();
        } catch (Exception e) {
        }
    }

    @Override
    public String getText() {
        pauseAndFlush();
        return super.getText();
    }

    @Override
    public OutputStream getOutputStream() {
        pauseAndFlush();
        return super.getOutputStream();
    }
}
