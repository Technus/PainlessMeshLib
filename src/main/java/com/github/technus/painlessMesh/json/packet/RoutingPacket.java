package com.github.technus.painlessMesh.json.packet;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.util.ArrayList;

@Data
@Accessors(chain = true)
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public abstract class RoutingPacket extends Packet {
    protected long            nodeId;
    protected boolean         root;
    protected ArrayList<Subs> subs = new ArrayList<>();

    @Data
    @Accessors(chain = true)
    @ToString(callSuper = true)
    @EqualsAndHashCode(callSuper = true)
    public static class Request extends RoutingPacket {
        public static final int TYPE = 5;

        {
            type = TYPE;
        }
    }

    @Data
    @Accessors(chain = true)
    @ToString(callSuper = true)
    @EqualsAndHashCode(callSuper = true)
    public static class Reply extends RoutingPacket {
        public static final int TYPE = 6;

        {
            type = TYPE;
        }
    }

    @Data
    @Accessors(chain = true)
    public static class Subs {
        protected long            nodeId;
        protected boolean         root;
        protected ArrayList<Subs> subs = new ArrayList<>();
    }
}
