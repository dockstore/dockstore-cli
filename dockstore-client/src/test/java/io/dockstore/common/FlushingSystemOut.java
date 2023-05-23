package io.dockstore.common;

import java.io.OutputStream;

import uk.org.webcompere.systemstubs.stream.SystemOut;

public class FlushingSystemOut extends SystemOut {

    @SuppressWarnings("EmptyCatchBlock")
    private void pauseAndFlush() {
        try {
            Thread.sleep(500);
            System.out.flush();
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
