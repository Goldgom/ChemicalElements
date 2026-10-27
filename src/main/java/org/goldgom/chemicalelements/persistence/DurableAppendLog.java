package org.goldgom.chemicalelements.persistence;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.function.Consumer;
import java.util.zip.CRC32C;

/** Length/checksum framed WAL. Only an incomplete last frame is discarded; corruption fails closed. */
final class DurableAppendLog {
    private static final int MAGIC=0x43455731,HEADER_BYTES=16,MAX_RECORD_BYTES=64*1024*1024;
    private final Path path;
    private long end;
    private boolean ready,failed;
    DurableAppendLog(Path path){this.path=path;}
    synchronized void replay(Consumer<byte[]> consumer)throws IOException{
        if(ready)throw new IllegalStateException("already replayed");
        if(!Files.exists(path)){ready=true;end=0;return;}
        try(var channel=FileChannel.open(path,StandardOpenOption.READ)){
            long size=channel.size();
            while(size-end>=HEADER_BYTES){
                var header=ByteBuffer.allocate(HEADER_BYTES);channel.position(end);readFully(channel,header);header.flip();
                if(header.getInt()!=MAGIC)throw new IOException("Unknown water journal frame at "+end);
                int length=header.getInt(),inverseLength=header.getInt(),checksum=header.getInt();
                if(inverseLength!=~length)throw new IOException("Water journal header checksum mismatch at "+end);
                if(length<1||length>MAX_RECORD_BYTES)throw new IOException("Invalid water journal frame length at "+end);
                if(size-end-HEADER_BYTES<length)break;
                var bytes=ByteBuffer.allocate(length);readFully(channel,bytes);
                if(checksum(bytes.array())!=checksum)throw new IOException("Water journal checksum mismatch at "+end);
                consumer.accept(bytes.array());end+=HEADER_BYTES+(long)length;
            }
        }
        ready=true;
    }
    synchronized void append(byte[] bytes)throws IOException{
        if(!ready||failed)throw new IOException("Water journal requires recovery before another write");
        if(bytes.length<1||bytes.length>MAX_RECORD_BYTES)throw new IOException("Water journal record exceeds budget");
        try{
          Files.createDirectories(path.toAbsolutePath().getParent());
          try(var channel=FileChannel.open(path,StandardOpenOption.CREATE,StandardOpenOption.WRITE)){
            // A replay may have found a partial final frame. Never remove a complete acknowledged frame.
            if(channel.size()<end)throw new IOException("Water journal was truncated externally");
            channel.truncate(end);channel.position(end);
            var header=ByteBuffer.allocate(HEADER_BYTES).putInt(MAGIC).putInt(bytes.length).putInt(~bytes.length).putInt(checksum(bytes));header.flip();
            while(header.hasRemaining())channel.write(header);
            var payload=ByteBuffer.wrap(bytes);while(payload.hasRemaining())channel.write(payload);
            channel.force(true);end=channel.position();
          }
        }catch(IOException ex){failed=true;throw ex;}
    }
    private static int checksum(byte[] bytes){var crc=new CRC32C();crc.update(bytes);return (int)crc.getValue();}
    private static void readFully(FileChannel channel,ByteBuffer bytes)throws IOException{
        while(bytes.hasRemaining())if(channel.read(bytes)<0)throw new java.io.EOFException("water journal changed during recovery");
    }
}
