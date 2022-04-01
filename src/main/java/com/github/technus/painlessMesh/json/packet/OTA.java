package com.github.technus.painlessMesh.json.packet;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public abstract class OTA extends Packet {

    @Data
    @Accessors(chain = true)
    @ToString(callSuper = true)
    @EqualsAndHashCode(callSuper = true)
    public static class Announce extends OTA{
        public static final int TYPE=10;
        protected String md5;
        protected String hardware;
        protected String  role;
        protected boolean forced;
        protected int     noPart;

        {
            type=TYPE;
        }
    }

    @Data
    @Accessors(chain = true)
    @ToString(callSuper = true)
    @EqualsAndHashCode(callSuper = true)
    public static class DataRequest extends Announce{
        public static final int TYPE=11;
        protected int partNo;

        {
            type=TYPE;
        }
    }

    @Data
    @Accessors(chain = true)
    @ToString(callSuper = true)
    @EqualsAndHashCode(callSuper = true)
    public static class DataResponse extends DataRequest{
        public static final int TYPE=12;
        protected String data;

        {
            type=TYPE;
        }
    }
}
