package org.example;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NioTelnetServer {

    private static final String HOST = "localhost";

    private static final int PORT = 8189;

    private final ByteBuffer buffer = ByteBuffer.allocate(1024);

    private Path rootPath = Paths.get("").toRealPath();

    public static void main(String[] args) throws IOException {
        new NioTelnetServer();
    }

    public NioTelnetServer() throws IOException {
        ServerSocketChannel server = ServerSocketChannel.open();

        server.bind(new InetSocketAddress(HOST, PORT));
        server.configureBlocking(false);

        Selector selector = Selector.open();

        server.register(selector, SelectionKey.OP_ACCEPT);

        System.out.println("Server started!");

        while (server.isOpen()) {
            selector.select();

            Set<SelectionKey> selectionKeys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = selectionKeys.iterator();

            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                if (key.isAcceptable()) {
                    handleAccept(key, selector);
                }
                if (key.isReadable()) {
                    handleRead(key);
                }
                if (key.isWritable()) {
                    System.out.println(key);
                }
                iterator.remove();
            }
        }
    }

    private void handleRead(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();

        int read = channel.read(buffer);

        if (read == -1) {
            channel.close();
            return;
        } else if (read == 0) {
            return;
        }

        buffer.flip();
        byte[] buf = new byte[read];
        int pos = 0;
        while (buffer.hasRemaining()) {
            buf[pos++] = buffer.get();
        }
        buffer.clear();

        String command = new String(buf, StandardCharsets.UTF_8)
                .replace("\n", "")
                .replace("\r", "");

        if (command.equals("--help")) {
            channel.write(ByteBuffer.wrap("input ls for show file list\n\r".getBytes()));
            channel.write(ByteBuffer.wrap("input cd <name> for change directory\n\r".getBytes()));
            channel.write(ByteBuffer.wrap("input touch <name> for create file\n\r".getBytes()));
            channel.write(ByteBuffer.wrap("input mkdir <name> for crete directory\n\r".getBytes()));
            channel.write(ByteBuffer.wrap("input rm <name> for delete file\n\r".getBytes()));
            channel.write(ByteBuffer.wrap("input copy <src> <dst> for copy file from <src> to <dst>\n\r".getBytes()));
            channel.write(ByteBuffer.wrap("input cat <name> for delete file\n\r".getBytes()));
        } else if (command.equals("ls")) {
            channel.write(ByteBuffer.wrap((getFilesList() + "\n\r").getBytes()));
        } else if (command.startsWith("cd ")) {
            List<String> strPath = getCommandArguments(command);

            if (strPath.size() == 1) {
                Path targetPath;

                if (strPath.get(0).equals(".")) targetPath = rootPath;
                else if (strPath.get(0).equals("..")) targetPath = rootPath.getParent();
                else targetPath = rootPath.resolve(Paths.get(strPath.get(0))).toRealPath();

                if (Files.isDirectory(targetPath)) {
                    rootPath = targetPath;
                    channel.write(ByteBuffer.wrap(("Current path: " + rootPath.toAbsolutePath() + "\n\r").getBytes()));
                } else {
                    channel.write(ByteBuffer.wrap("Incorrect path\n\r".getBytes()));
                }
            }
        } else if (command.startsWith("touch ")) {
            List<String> strPath = getCommandArguments(command);

            if (strPath.size() == 1) {
                Path target = rootPath.resolve(Paths.get(strPath.get(0)));

                if (Files.notExists(target)) Files.createFile(target);
                else channel.write(ByteBuffer.wrap("File already exist\n\r".getBytes()));
            }
        } else if (command.startsWith("mkdir ")) {
            List<String> strPath = getCommandArguments(command);

            if (strPath.size() == 1) {
                Path target = rootPath.resolve(Paths.get(strPath.get(0)));

                if (Files.notExists(target)) Files.createDirectory(target);
                else channel.write(ByteBuffer.wrap("Folder already exist\n\r".getBytes()));
            }
        } else if (command.startsWith("rm ")) {
            List<String> strPath = getCommandArguments(command);

            if (strPath.size() == 1) {
                Path target = rootPath.resolve(Paths.get(strPath.get(0)));

                if (Files.exists(target)) Files.delete(target);
                else channel.write(ByteBuffer.wrap("File does not exist\n\r".getBytes()));
            }
        } else if (command.startsWith("cat ")) {
            List<String> strPath = getCommandArguments(command);

            if (strPath.size() == 1) {
                Path target = rootPath.resolve(Paths.get(strPath.get(0)));

                if (Files.exists(target)) {
                    Files.readAllLines(target, StandardCharsets.UTF_8)
                            .forEach(line -> {
                                try {
                                    channel.write(ByteBuffer.wrap((line + "\n\r").getBytes()));
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            });
                } else channel.write(ByteBuffer.wrap("File does not exist\n\r".getBytes()));
            }
        } else if (command.startsWith("copy ")) {
            List<String> strPath = getCommandArguments(command);

            if (strPath.size() == 2) {
                Path src = rootPath.resolve(Paths.get(strPath.get(0)));
                Path dst = rootPath.resolve(Paths.get(strPath.get(1)));

                if (Files.exists(src)) Files.copy(src, dst);
                else channel.write(ByteBuffer.wrap("Source file does not exist\n\r".getBytes()));
            }
        }
    }

    private List<String> getCommandArguments(String command) {
        String argsPart = command.split(" ", 2)[1];

        String regEx = "([^\\\\:*?<>|\" ]+)|\"([^\\\\:*?<>|\"]+)\"";
        Matcher matcher = Pattern.compile(regEx).matcher(argsPart);

        List<String> args = new ArrayList<>();

        int start, end;
        while (matcher.find()) {
            int sPos = matcher.start(), ePos = matcher.end() - 1;

            start = argsPart.charAt(sPos) == '"' ? sPos + 1 : sPos;
            end = argsPart.charAt(ePos) == '"' ? ePos - 1 : ePos;

            args.add(argsPart.substring(start, end + 1));
        }

        return args;
    }

    @SuppressWarnings("unckeked")
    private String getFilesList() {
        return String.join(" ", Objects.requireNonNull(new File(rootPath.toUri()).list()));
    }

    private void handleAccept(SelectionKey key, Selector selector) throws IOException {
        SocketChannel channel = ((ServerSocketChannel) key.channel()).accept();

        channel.configureBlocking(false);
        System.out.println("Client accepted. IP: " + channel.getRemoteAddress());
        channel.register(selector, SelectionKey.OP_READ, "LOL");

        channel.write(ByteBuffer.wrap("Enter --help\n\r".getBytes()));
    }
}