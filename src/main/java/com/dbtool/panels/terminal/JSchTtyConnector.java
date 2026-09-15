package com.dbtool.panels.terminal;

import com.jcraft.jsch.ChannelShell;
import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.Questioner;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.IOException;
import java.awt.Dimension;
import java.nio.charset.StandardCharsets;

public class JSchTtyConnector implements TtyConnector {
    private ChannelShell channel;
    private InputStreamReader reader;
    private OutputStream out;

    public JSchTtyConnector(ChannelShell channel) {
        this.channel = channel;
        try {
            this.reader = new InputStreamReader(channel.getInputStream(), StandardCharsets.UTF_8);
            this.out = channel.getOutputStream();
        } catch (Exception e) {}
    }

    @Override
    public boolean init(Questioner q) {
        try {
            if (!channel.isConnected()) {
                channel.connect(3000);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void close() {
        if (channel != null) {
            channel.disconnect();
        }
    }

    @Override
    public String getName() {
        return "SSH";
    }

    @Override
    public int read(char[] buf, int offset, int length) throws IOException {
        return reader.read(buf, offset, length);
    }

    @Override
    public void write(byte[] bytes) throws IOException {
        out.write(bytes);
        out.flush();
    }

    @Override
    public boolean isConnected() {
        return channel.isConnected();
    }

    @Override
    public void write(String string) throws IOException {
        write(string.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public int waitFor() throws InterruptedException {
        while (isConnected()) {
            Thread.sleep(100);
        }
        return channel.getExitStatus();
    }

    @Override
    public boolean ready() throws IOException {
        return reader.ready();
    }

    @Override
    public void resize(Dimension termSize, Dimension pixelSize) {
        channel.setPtySize(termSize.width, termSize.height, pixelSize.width, pixelSize.height);
    }
}
