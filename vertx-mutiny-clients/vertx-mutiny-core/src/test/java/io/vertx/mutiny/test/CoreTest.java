package io.vertx.mutiny.test;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.impl.Utils;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.core.file.AsyncFile;
import io.vertx.test.core.TestUtils;
import io.vertx.test.core.VertxTestBase;

public class CoreTest extends VertxTestBase {

    private static final String DEFAULT_FILE_PERMS = "rw-r--r--";
    @Rule
    public TemporaryFolder testFolder = new TemporaryFolder();
    private Vertx vertx;
    private String pathSep;
    private String testDir;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        vertx = new Vertx(super.vertx);
        java.nio.file.FileSystem fs = FileSystems.getDefault();
        pathSep = fs.getSeparator();
        File ftestDir = testFolder.newFolder();
        testDir = ftestDir.toString();
    }

    @Test
    public void testAsyncFile() throws Exception {
        String fileName = "some-file.dat";
        int chunkSize = 1000;
        int chunks = 10;
        byte[] expected = TestUtils.randomAlphaString(chunkSize * chunks).getBytes();
        createFile(fileName, expected);
        vertx.fileSystem().open(testDir + pathSep + fileName, new OpenOptions())
                .subscribeAsCompletionStage().whenComplete((file, err) -> {
                    assertNull(err);
                    subscribe(expected, file, 3);
                });
        await();
    }

    private void subscribe(byte[] expected, AsyncFile file, int times) {
        file.setReadPos(0);
        Buffer actual = Buffer.buffer();
        file.toMulti()
                .onItem().apply(io.vertx.mutiny.core.buffer.Buffer::getDelegate)
                .onItem().invoke(actual::appendBuffer)
                .onItem().ignoreAsUni()
                .subscribeAsCompletionStage()
                .exceptionally(t -> {
                    fail(t);
                    return null;
                })
                .whenComplete((x, e) -> {
                    assertEquals(Buffer.buffer(expected), actual);
                    if (times > 0) {
                        subscribe(expected, file, times - 1);
                    } else {
                        testComplete();
                    }
                });
    }

    private void createFile(String fileName, byte[] bytes) throws Exception {
        File file = new File(testDir, fileName);
        Path path = Paths.get(file.getCanonicalPath());
        Files.write(path, bytes);
        setDefaultPerms(path);
    }

    private void setDefaultPerms(Path path) {
        if (!Utils.isWindows()) {
            try {
                Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(DEFAULT_FILE_PERMS));
            } catch (IOException e) {
                throw new RuntimeException(e.getMessage());
            }
        }
    }
}
