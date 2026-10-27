package org.goldgom.chemicalelements.persistence;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DurableAppendLogTest {
    @TempDir Path directory;
    @Test void appendPreservesEveryPreviousByteAndReplaysInOrder()throws Exception{
        var path=directory.resolve("water.wal");var writer=new DurableAppendLog(path);writer.replay(bytes->fail("new log must be empty"));
        var expected=new ArrayList<byte[]>();
        for(int i=0;i<40;i++){
            byte[] prefix=Files.exists(path)?Files.readAllBytes(path):new byte[0];
            var bytes=("receipt="+i+"|"+"water,salt,heat;".repeat(100)).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            writer.append(bytes);expected.add(bytes);
            assertArrayEquals(prefix,Arrays.copyOf(Files.readAllBytes(path),prefix.length));
        }
        var replayed=new ArrayList<byte[]>();new DurableAppendLog(path).replay(replayed::add);
        assertEquals(expected.size(),replayed.size());for(int i=0;i<expected.size();i++)assertArrayEquals(expected.get(i),replayed.get(i));
    }
    @Test void tornFinalHeaderOrPayloadNeverDiscardsEarlierReceipts()throws Exception{
        for(int tail:new int[]{1,7,16,18}){
            var path=directory.resolve("torn-"+tail);var log=new DurableAppendLog(path);log.replay(bytes->{});log.append(new byte[]{1,2,3});
            var first=Files.readAllBytes(path);log.append(new byte[]{4,5,6});var complete=Files.readAllBytes(path);
            Files.write(path,Arrays.copyOf(complete,first.length+tail));
            var recovered=new DurableAppendLog(path);var entries=new ArrayList<byte[]>();recovered.replay(entries::add);
            assertEquals(1,entries.size());assertArrayEquals(new byte[]{1,2,3},entries.getFirst());
            recovered.append(new byte[]{7,8,9});var finalEntries=new ArrayList<byte[]>();new DurableAppendLog(path).replay(finalEntries::add);
            assertEquals(2,finalEntries.size());assertArrayEquals(new byte[]{7,8,9},finalEntries.get(1));
        }
    }
    @Test void completeCorruptOrUnknownFrameFailsClosedWithoutRewriting()throws Exception{
        for(boolean corruptPayload:new boolean[]{true,false}){
            var path=directory.resolve("invalid-"+corruptPayload);var log=new DurableAppendLog(path);log.replay(bytes->{});log.append(new byte[]{1,2,3});
            var corrupt=Files.readAllBytes(path);corrupt[corruptPayload?corrupt.length-1:0]^=0x40;Files.write(path,corrupt);
            assertThrows(IOException.class,()->new DurableAppendLog(path).replay(bytes->{}));assertArrayEquals(corrupt,Files.readAllBytes(path));
        }
    }
    @Test void failedWriteBlocksFurtherWritesUntilRecovery()throws Exception{
        var blocker=directory.resolve("not-a-directory");Files.writeString(blocker,"blocker");
        var log=new DurableAppendLog(blocker.resolve("water.wal"));log.replay(bytes->{});
        assertThrows(IOException.class,()->log.append(new byte[]{1}));assertThrows(IOException.class,()->log.append(new byte[]{2}));
    }
}
