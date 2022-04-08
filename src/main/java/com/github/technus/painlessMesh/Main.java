package com.github.technus.painlessMesh;

import com.github.technus.painlessMesh.json.packet.UserPacket;
import lombok.SneakyThrows;

import java.io.File;

public class Main {
    @SneakyThrows
    public static void main(String[] args) {
        Server server = new Server(1337_2138, 5555);

        PacketRegistry<Server.ConnectionHandler> packetRegistry = server.getPacketRegistry();

        packetRegistry.registerPacket(UserPacket.Broadcast.TYPE, UserPacket.Broadcast.class)
                .setPacketConsumer((app, arrivalTime, packet) -> System.out.println("Received Broadcast!"));

        packetRegistry.registerPacket(UserPacket.Single.TYPE, UserPacket.Single.class)
                .setPacketConsumer((app, arrivalTime, packet) -> System.out.println("Received single!"));

        server.offerUpdateOTA(new UpdateOTA("ESP32", "bridgeAsRoot",
                new File("C:\\Users\\danie\\Documents\\PlatformIO\\Projects\\BreachProtocol\\.pio\\build\\bridgeAsRoot\\firmware.bin"))
        );

        server.run();
    }
}
