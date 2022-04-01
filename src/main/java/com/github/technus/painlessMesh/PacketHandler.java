package com.github.technus.painlessMesh;

import com.github.technus.painlessMesh.json.packet.Packet;
import com.google.gson.reflect.TypeToken;
import lombok.Data;
import lombok.experimental.Accessors;

import java.lang.reflect.Type;
import java.util.Optional;

@Data
@Accessors(chain = true)
public class PacketHandler<APP,T extends Packet> {
    protected int     packetType;
    protected Integer msgType;
    protected Type          jsonType;
    protected IPacketConsumer<APP,T> packetConsumer = (s,l,p)->{};

    public void setPacketMetadata(int packetType, Class<T> clazz, Class<?>... generics) {
        this.packetType = packetType;
        this.msgType = null;
        this.jsonType = TypeToken.getParameterized(clazz, generics).getType();
    }

    public void setPacketMetadata(int packetType, int msgType, Class<T> clazz, Class<?>... generics) {
        this.packetType = packetType;
        this.msgType = msgType;
        this.jsonType = TypeToken.getParameterized(clazz, generics).getType();
    }

    public Optional<Integer> getMsgType() {
        return Optional.ofNullable(msgType);
    }

    public void onReceive(APP app, long arrivalTime, T packet) {
        getPacketConsumer().accept(app,arrivalTime,packet);
    }

    public interface IPacketConsumer<APP,T extends Packet>{
        void accept(APP app,long arrivalTime,T packet);
    }
}
