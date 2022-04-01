package com.github.technus.painlessMesh;

import lombok.*;
import lombok.experimental.Accessors;

import javax.xml.bind.DatatypeConverter;
import java.io.File;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.stream.IntStream;

@Data
@Accessors(chain = true)
@RequiredArgsConstructor
public class UpdateOTA {
    protected final String hardware;
    protected final String role;
    protected final File    file;
    protected boolean forced =false;
    protected int partSize=1024;
    @Getter(lazy = true)
    private final UpdateData updateData =new UpdateData(getFile());

    public String withData(Timeout timeout,int partNo){
        timeout.setLastUsed(System.currentTimeMillis());
        return getUpdateData().getChunks()[partNo];
    }

    public int getChunksCount() {
        return getUpdateData().getChunksCount();
    }

    @Data
    @Accessors(chain = true)
    public class UpdateData {
        protected final String[] chunks;
        protected final String   md5;

        public int getChunksCount() {
            return chunks.length;
        }

        @SneakyThrows
        protected UpdateData(File file) {
            val bytes = Files.readAllBytes(file.toPath());

            this.chunks = IntStream.iterate(0, i -> i + getPartSize())
                    .limit((int) Math.ceil((double) bytes.length / getPartSize()))
                    .mapToObj(j -> {
                        val actualSize = Math.min(bytes.length - j, getPartSize());
                        val chunk      = new byte[actualSize];
                        System.arraycopy(bytes, j, chunk, 0, actualSize);
                        return DatatypeConverter.printBase64Binary(chunk);
                    })
                    .toArray(String[]::new);

            val md = MessageDigest.getInstance("MD5");
            md.update(bytes);
            md5 = DatatypeConverter.printHexBinary(md.digest()).toLowerCase();
        }
    }

    @Data
    @AllArgsConstructor
    @Accessors(chain = true)
    public static class ID{
        protected final String hardware;
        protected final String role;
        protected final String md5;
        protected final int     noPart;
        protected final boolean forced;

        public ID(UpdateOTA updateOTA){
            this(updateOTA.getHardware(), updateOTA.getRole(), updateOTA.getUpdateData().getMd5(),updateOTA.getChunksCount(),updateOTA.isForced());
        }
    }

    @Data
    @Accessors(chain = true)
    public static class Timeout{
        protected long lastOffer;
        protected long lastUsed;
        protected int tries=60;//
        protected int every=60_000;//seconds

        {
            lastOffer=lastUsed=System.currentTimeMillis();
        }

        public Optional<Boolean> shouldOfferOTA(){
            if(getTries()==0){
                return Optional.empty();
            }

            long time=System.currentTimeMillis();

            if(getTries()>0 && time > getLastOffer() + getEvery()){
                if(time>getLastUsed()+getEvery()) {
                    setTries(getTries()-1);
                }
                setLastOffer(time);
                return Optional.of(true);
            }
            return Optional.of(false);
        }
    }
}
