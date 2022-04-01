package com.github.technus.painlessMesh;

import com.github.technus.painlessMesh.json.packet.Packet;
import lombok.Getter;
import lombok.var;

import java.util.HashMap;
import java.util.Optional;

public class PacketRegistry<APP> {
    @Getter
    protected HashMap<Integer, HashMap<Optional<Integer>, PacketHandler<APP,?>>> mapping = new HashMap<>();

    protected void registerPacket(PacketHandler<APP,?> packetHandler) {
        getMapping().compute(packetHandler.getPacketType(), (integer, integerClassHashMap) -> {
            if (integerClassHashMap == null) {
                integerClassHashMap = new HashMap<>();
            }
            integerClassHashMap.put(packetHandler.getMsgType(), packetHandler);
            return integerClassHashMap;
        });
    }

    @SuppressWarnings("unchecked")
    public <T extends Packet> Optional<PacketHandler<APP,T>> getTypeFor(T type) {
        var optionalListHashMap = getMapping().get(type.getType());
        return optionalListHashMap == null ? Optional.empty() :
                Optional.ofNullable((PacketHandler<APP,T>) optionalListHashMap.get(type.msgType()));
    }

    public <T extends Packet> PacketHandler<APP,T> registerPacket(int type, Class<T> packetClass, Class<?>... generics) {
        PacketHandler<APP,T> packetHandler = new PacketHandler<>();
        packetHandler.setPacketMetadata(type, packetClass, generics);
        registerPacket(packetHandler);
        return packetHandler;
    }

    public <T extends Packet> PacketHandler<APP,T> registerPacket(int type, int msgType, Class<T> packetClass, Class<?>... generics) {
        PacketHandler<APP,T> packetHandler = new PacketHandler<>();
        packetHandler.setPacketMetadata(type, msgType, packetClass, generics);
        registerPacket(packetHandler);
        return packetHandler;
    }
}
