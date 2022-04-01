package com.github.technus.painlessMesh.json;

import com.github.technus.painlessMesh.PacketHandler;
import com.github.technus.painlessMesh.PacketRegistry;
import com.github.technus.painlessMesh.json.packet.Packet;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.AccessLevel;
import lombok.Getter;

import java.util.Optional;

public class JSON<APP> {
    @Getter(AccessLevel.PRIVATE)
    private static final GsonBuilder    GSON_BUILDER = new GsonBuilder();
    @Getter(AccessLevel.PRIVATE)
    private final       Gson           gson;
    @Getter(AccessLevel.PRIVATE)
    private final       PacketRegistry<APP> packetRegistry;

    public JSON(PacketRegistry<APP> packetRegistry) {
        this.packetRegistry = packetRegistry;
        this.gson = getGSON_BUILDER()
                .disableHtmlEscaping()
                .create();
    }

    public <T extends Packet> String toJson(T packet) {
        return getGson().toJson(packet);
    }

    @SuppressWarnings("unchecked")
    public <T extends Packet> Optional<T> fromJSON(String json) {
        Packet                          packet  = getGson().fromJson(json, Packet.class);
        Optional<PacketHandler<APP,Packet>> handler = getPacketRegistry().getTypeFor(packet);
        if (handler.isPresent()) {
            packet = getGson().fromJson(json, handler.get().getJsonType());
            Optional<Integer> msgID = packet.msgType();
            if (msgID.isPresent()) {
                handler = getPacketRegistry().getTypeFor(packet);
                if (handler.isPresent()) {
                    return Optional.of(getGson().fromJson(json, handler.get().getJsonType()));
                }
            } else {
                return Optional.of((T) packet);
            }
        }
        return Optional.empty();
    }
}
