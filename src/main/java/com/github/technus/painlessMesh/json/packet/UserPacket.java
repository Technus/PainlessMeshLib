package com.github.technus.painlessMesh.json.packet;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public abstract class UserPacket extends Packet {
    protected String msg;

    @Data
    @Accessors(chain = true)
    @ToString(callSuper = true)
    @EqualsAndHashCode(callSuper = true)
    public static class Single extends UserPacket{
        public static final int TYPE=9;

        {
            type=TYPE;
        }
    }

    @Data
    @Accessors(chain = true)
    @ToString(callSuper = true)
    @EqualsAndHashCode(callSuper = true)
    public static class Broadcast extends UserPacket{
        public static final int TYPE=8;

        {
            type=TYPE;
        }
    }
}
