package com.github.technus.painlessMesh;

import com.github.technus.painlessMesh.json.packet.UserPacket;
import lombok.SneakyThrows;
import lombok.val;

public class MainClient {
    @SneakyThrows
    public static void main(String[] args) {
        val client = new Client(1337_2138,"192.168.0.129",5555);

        PacketRegistry<Client> packetRegistry = client.getPacketRegistry();

        packetRegistry.registerPacket(UserPacket.Broadcast.TYPE, UserPacket.Broadcast.class)
                .setPacketConsumer((app, arrivalTime, packet) -> System.out.println("Received Broadcast!"));

        packetRegistry.registerPacket(UserPacket.Single.TYPE, UserPacket.Single.class)
                .setPacketConsumer((app, arrivalTime, packet) -> System.out.println("Received single!"));

        //client.offerUpdateOTA(new UpdateOTA("ESP32", "bridgeAsRoot",
        //        new File("C:\\Users\\danie\\Documents\\PlatformIO\\Projects\\BreachProtocol\\.pio\\build\\bridgeAsRoot\\firmware.bin"))
        //);

        client.run();
    }
}
