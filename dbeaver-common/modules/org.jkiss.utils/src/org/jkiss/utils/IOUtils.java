/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2025 DBeaver Corp and others
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jkiss.utils;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;

import java.io.*;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Some IO helper functions
 */
public final class IOUtils {

    public static final int DEFAULT_BUFFER_SIZE = 16384;

    private static final boolean USE_NIO_STREAMS = false;

    public static void close(Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void close(AutoCloseable closeable) {
        try {
            closeable.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void fastCopy(final InputStream src, final OutputStream dest) throws IOException {
        fastCopy(src, dest, DEFAULT_BUFFER_SIZE);
    }

    public static void fastCopy(final InputStream src, final OutputStream dest, int bufferSize) throws IOException {
        if (USE_NIO_STREAMS) {
            final ReadableByteChannel inputChannel = Channels.newChannel(src);
            final WritableByteChannel outputChannel = Channels.newChannel(dest);
            fastCopy(inputChannel, outputChannel, bufferSize);
        } else {
            copyStream(src, dest, bufferSize);
        }
    }

    public static void fastCopy(final ReadableByteChannel src, final WritableByteChannel dest, int bufferSize) throws IOException {
        final ByteBuffer buffer = ByteBuffer.allocateDirect(bufferSize);

        while (src.read(buffer) != -1) {
            flipBuffer(buffer);
            dest.write(buffer);
            buffer.compact();
        }

        flipBuffer(buffer);

        while (buffer.hasRemaining()) {
            dest.write(buffer);
        }
    }

    public static void flipBuffer(Buffer buffer) {
        buffer.flip();
    }

    public static void copyStream(
        java.io.InputStream inputStream,
        java.io.OutputStream outputStream)
        throws IOException {
        copyStream(inputStream, outputStream, DEFAULT_BUFFER_SIZE);
    }

    /**
     * Read entire input stream and writes all data to output stream
     * then closes input and flushed output
     */
    public static void copyStream(
        java.io.InputStream inputStream,
        java.io.OutputStream outputStream,
        int bufferSize)
        throws IOException {
        try {
            byte[] writeBuffer = new byte[bufferSize];
            for (int br = inputStream.read(writeBuffer); br != -1; br = inputStream.read(writeBuffer)) {
                outputStream.write(writeBuffer, 0, br);
            }
            outputStream.flush();
        } finally {
            // Close input stream
            inputStream.close();
        }
    }

    /**
     * Read entire input stream portion and writes it data to output stream
     */
    public static void copyStreamPortion(
        java.io.InputStream inputStream,
        java.io.OutputStream outputStream,
        int portionSize,
        int bufferSize)
        throws IOException {
        if (bufferSize > portionSize) {
            bufferSize = portionSize;
        }
        byte[] writeBuffer = new byte[bufferSize];
        int totalRead = 0;
        while (totalRead < portionSize) {
            int bytesToRead = bufferSize;
            if (bytesToRead > portionSize - totalRead) {
                bytesToRead = portionSize - totalRead;
            }
            int bytesRead = inputStream.read(writeBuffer, 0, bytesToRead);
            outputStream.write(writeBuffer, 0, bytesRead);
            totalRead += bytesRead;
        }

        // Close input stream
        outputStream.flush();
    }

    public static String toString(File file, String encoding) throws IOException {
        try (InputStream is = new FileInputStream(file)) {
            try (Reader reader = new InputStreamReader(is, encoding)) {
                StringWriter writer = new StringWriter();
                copyText(reader, writer, DEFAULT_BUFFER_SIZE);
                return writer.toString();
            }
        }
    }

    /**
     * Read entire reader content and writes it to writer
     * then closes reader and flushed output.
     */
    public static void copyText(
        java.io.Reader reader,
        java.io.Writer writer,
        int bufferSize)
        throws IOException {
        char[] writeBuffer = new char[bufferSize];
        for (int br = reader.read(writeBuffer); br != -1; br = reader.read(writeBuffer)) {
            writer.write(writeBuffer, 0, br);
        }
        writer.flush();
    }

    public static void copyText(
        java.io.Reader reader,
        java.io.Writer writer)
        throws IOException {
        copyText(reader, writer, DEFAULT_BUFFER_SIZE);
    }

    public static byte[] readFileToBuffer(File file) throws IOException {
        byte[] buffer = new byte[(int) file.length()];
        try (InputStream is = new FileInputStream(file)) {
            readStreamToBuffer(is, buffer);
        }
        return buffer;
    }

    public static void writeFileFromBuffer(File file, byte[] buffer) throws IOException {
        try (OutputStream os = new FileOutputStream(file)) {
            os.write(buffer);
        }
    }

    public static void writeFileFromString(File file, String str) throws IOException {
        try (Writer os = new FileWriter(file)) {
            os.write(str);
        }
    }

    public static int readStreamToBuffer(
        java.io.InputStream inputStream,
        byte[] buffer)
        throws IOException {
        int totalRead = 0;
        while (totalRead != buffer.length) {
            int br = inputStream.read(buffer, totalRead, buffer.length - totalRead);
            if (br == -1) {
                break;
            }
            totalRead += br;
        }
        return totalRead;
    }

    public static String readLine(java.io.InputStream input)
        throws IOException {
        StringBuilder linebuf = new StringBuilder();
        for (int b = input.read(); b != '\n'; b = input.read()) {
            if (b == -1) {
                if (linebuf.length() == 0) {
                    return null;
                } else {
                    break;
                }
            }
            if (b != '\r') {
                linebuf.append((char) b);
            }
        }
        return linebuf.toString();
    }

    public static String readFullLine(java.io.InputStream input)
        throws IOException {
        StringBuilder linebuf = new StringBuilder();
        for (int b = input.read(); ; b = input.read()) {
            if (b == -1) {
                if (linebuf.length() == 0) {
                    return null;
                } else {
                    break;
                }
            }
            linebuf.append((char) b);
            if (b == '\n') {
                break;
            }
        }
        return linebuf.toString();
    }

    public static int findFreePort(int minPort, int maxPort) {
        int portRange = Math.abs(maxPort - minPort);
        while (true) {
            int portNum = minPort + SecurityUtils.getRandom().nextInt(portRange);
            try {
                ServerSocket socket = new ServerSocket(portNum);
                try {
                    socket.close();
                } catch (IOException e) {
                    // just skip
                }
                return portNum;
            } catch (IOException e) {
                // Port is busy
            }
        }
    }

    public static String readToString(Reader is) throws IOException {
        StringBuilder result = new StringBuilder(4000);
        char[] buffer = new char[4000];
        for (; ; ) {
            int count = is.read(buffer);
            if (count <= 0) {
                break;
            }
            result.append(buffer, 0, count);
        }
        return result.toString();
    }

    static void copyZipStream(InputStream inputStream, OutputStream outputStream)
        throws IOException
    {
        byte[] writeBuffer = new byte[IOUtils.DEFAULT_BUFFER_SIZE];
        for (int br = inputStream.read(writeBuffer); br != -1; br = inputStream.read(writeBuffer)) {
            outputStream.write(writeBuffer, 0, br);
        }
        outputStream.flush();
    }

    public static void extractZipArchive(InputStream stream, Path targetFolder) throws IOException {
        try (ZipInputStream zipStream = new ZipInputStream(stream)) {
            for (; ; ) {
                ZipEntry zipEntry = zipStream.getNextEntry();
                if (zipEntry == null) {
                    break;
                }
                try {
                    if (!zipEntry.isDirectory()) {
                        String zipEntryName = zipEntry.getName();
                        checkAndExtractEntry(zipStream, zipEntry, targetFolder);
                    }
                } finally {
                    zipStream.closeEntry();
                }
            }
        }
    }

    private static void checkAndExtractEntry(InputStream zipStream, ZipEntry zipEntry, Path targetFolder) throws IOException {
        if (!Files.exists(targetFolder)) {
            try {
                Files.createDirectories(targetFolder);
            } catch (IOException e) {
                throw new IOException("Can't create local cache folder '" + targetFolder.toAbsolutePath() + "'", e);
            }
        }
        Path localFile = targetFolder.resolve(zipEntry.getName());
        if (!localFile.normalize().startsWith(targetFolder.normalize())) {
            throw new IOException("Zip entry is outside of the target directory");
        }
        if (Files.exists(localFile)) {
            // Already extracted?
            return;
        }
        Path localDir = localFile.getParent();
        if (!Files.exists(localDir)) { // in case of localFile located in subdirectory inside zip archive
            try {
                Files.createDirectories(localDir);
            } catch (IOException e) {
                throw new IOException("Can't create local file directory in the cache '" + localDir.toAbsolutePath() + "'", e);
            }
        }
        try (OutputStream os = Files.newOutputStream(localFile)) {
            copyZipStream(zipStream, os);
        }
    }


    public static void zipFolder(final File folder, final OutputStream outputStream) throws IOException {
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            processFolder(folder, zipOutputStream, folder.getPath().length() + 1);
        }
    }

    private static void processFolder(final File folder, final ZipOutputStream zipOutputStream, final int prefixLength) throws IOException {
        File[] folderFiles = folder.listFiles();
        if (folderFiles == null) {
            return;
        }
        for (File file : folderFiles) {
            BasicFileAttributes fAttrs = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
            if (fAttrs.isRegularFile()) {
                final ZipEntry zipEntry = new ZipEntry(file.getPath().substring(prefixLength));
                zipOutputStream.putNextEntry(zipEntry);
                try (FileInputStream inputStream = new FileInputStream(file)) {
                    IOUtils.copyStream(inputStream, zipOutputStream);
                }
                zipOutputStream.closeEntry();
            } else if (fAttrs.isDirectory()) {
                processFolder(file, zipOutputStream, prefixLength);
            }
        }
    }
    public static void deleteDirectory(@NotNull Path path) throws IOException {
        Files.walkFileTree(path,
            new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }
            });
    }

    @Nullable
    public static String getDirectoryPath(@NotNull String sPath) throws InvalidPathException {
        final Path path = Paths.get(sPath);
        if (Files.isDirectory(path)) {
            return path.toString();
        } else {
            final Path parent = path.getParent();
            if (parent != null) {
                return parent.toString();
            }
        }
        return null;
    }

    @NotNull
    public static String getFileNameWithoutExtension(@NotNull Path file) {
        return getPathWithoutFileExtension(file.getFileName().toString());
    }

    @NotNull
    public static String getPathWithoutFileExtension(@NotNull String path) {
        int divPos = path.lastIndexOf('.');
        if (divPos > 0) {
            return path.substring(0, divPos);
        }
        return path;
    }

    @Nullable
    public static String getFileExtension(Path file) {
        Path fileName = file.getFileName();
        if (fileName == null) {
            return null;
        }
        return getFileExtension(fileName.toString());
    }

    @Nullable
    public static String getFileExtension(String fileName) {
        int divPos = fileName.lastIndexOf('.');
        if (divPos != -1) {
            return fileName.substring(divPos + 1);
        }
        return null;
    }

    @NotNull
    public static Path getPathFromString(@NotNull String pathOrUri) {
        if (pathOrUri.contains("://")) {
            return Path.of(URI.create(pathOrUri));
        } else {
            return Path.of(pathOrUri);
        }
    }


    public static boolean isLocalFile(String filePath) {
        // Local paths:
        // rel-path
        // /abs/path
        // \abs\path
        // c:/abs/path
        // c:\abs\path
        int divPos = filePath.indexOf(":/");
        return divPos < 0 || divPos == 1 || filePath.startsWith("file:");
    }

    public static boolean isLocalURI(URI uri) {
        return uri.getScheme().equals("file");
    }

    public static boolean isLocalPath(Path filePath) {
        return isLocalURI(filePath.toUri());
    }

    public static boolean isFileFromDefaultFS(@NotNull Path path) {
        return path.getFileSystem().equals(FileSystems.getDefault());
    }

    public static boolean isFolderEmpty(@NotNull Path directory) throws IOException {
        try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(directory)) {
            return !dirStream.iterator().hasNext();
        }
    }
}
