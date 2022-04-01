package com.github.technus.painlessMesh.json.packet;

import com.github.technus.painlessMesh.json.Type;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.util.Optional;

@Data
@Accessors(chain = true)
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class TimePacket<T extends TimePacket.Msg> extends Packet {
    public static final int TYPE = 4;
    protected           T   msg;

    {
        type = TYPE;
    }

    @Override
    public Optional<Integer> msgType() {
        return Optional.of(msg.getType());
    }

    @Data
    @Accessors(chain = true)
    @ToString(callSuper = true)
    @EqualsAndHashCode(callSuper = true)
    public static class Msg extends Type {
    }

    @Data
    @Accessors(chain = true)
    @ToString(callSuper = true)
    @EqualsAndHashCode(callSuper = true)
    public static class Request extends Msg {
        public static final int TYPE = 0;

        {
            type = TYPE;
        }
    }

    @Data
    @Accessors(chain = true)
    @ToString(callSuper = true)
    @EqualsAndHashCode(callSuper = true)
    public static class StartSync extends Msg {
        public static final int  TYPE = 1;
        protected           long t0;

        {
            type = TYPE;
        }
    }

    @Data
    @Accessors(chain = true)
    @ToString(callSuper = true)
    @EqualsAndHashCode(callSuper = true)
    public static class ResponseSync extends Msg {
        public static final int  TYPE = 2;
        protected           long t0;
        protected           long t1;
        protected           long t2;

        {
            type = TYPE;
        }
    }
}
