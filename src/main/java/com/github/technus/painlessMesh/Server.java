package com.github.technus.painlessMesh;

import com.github.technus.painlessMesh.json.JSON;
import com.github.technus.painlessMesh.json.packet.OTA;
import com.github.technus.painlessMesh.json.packet.RoutingPacket;
import com.github.technus.painlessMesh.json.packet.TimePacket;
import lombok.*;
import lombok.experimental.Accessors;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.Charset;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class Server extends Thread {
    @Getter
    protected Charset charset=Charset.forName("CP1250");
    @Getter
    protected final long                              nodeId;
    @Getter(AccessLevel.PROTECTED)
    protected final ServerSocket                      serverSocket;
    @Getter
    protected final PacketRegistry<Client>       packetRegistry = new PacketRegistry<>();
    @Getter
    protected final JSON<Client>                 json=new JSON<>(getPacketRegistry());
    @Getter
    protected final Map<UpdateOTA.ID, UpdateOTA> otaUpdates = new ConcurrentHashMap<>();
    @Accessors(chain = true)
    @Setter
    protected Consumer<Client> onClientCreated=c->{};

    public Server(long nodeId, int port) throws IOException {
        this(nodeId,new ServerSocket(port, 50, null));
    }

    public Server(long nodeId, int port, InetAddress ip) throws IOException {
        this(nodeId,new ServerSocket(port, 50, ip));
    }

    public Server(long nodeId, ServerSocket serverSocket) {
        setName("Server");
        this.nodeId = nodeId;
        this.serverSocket = serverSocket;

        getPacketRegistry().registerPacket(TimePacket.TYPE, TimePacket.class, TimePacket.Msg.class);
        getPacketRegistry().registerPacket(TimePacket.TYPE, TimePacket.Request.TYPE, TimePacket.class, TimePacket.Request.class)
                .setPacketConsumer((app, arrivalTime, packet) -> {
                    @SuppressWarnings("unchecked")
                    TimePacket<TimePacket.Request> request = packet;
                    TimePacket<TimePacket.StartSync> start = new TimePacket<>();
                    start.setFrom(getNodeId());
                    start.setDest(request.getDest());
                    start.setMsg(new TimePacket.StartSync());
                    start.getMsg().setT0(app.getMeshMicroTime());
                    app.send(start);
                });
        getPacketRegistry().registerPacket(TimePacket.TYPE, TimePacket.StartSync.TYPE, TimePacket.class, TimePacket.StartSync.class)
                .setPacketConsumer((app, t1, packet) -> {
                    @SuppressWarnings("unchecked")
                    TimePacket<TimePacket.StartSync> start = packet;
                    TimePacket<TimePacket.ResponseSync> response = new TimePacket<>();
                    response.setFrom(getNodeId());
                    response.setDest(start.getDest());
                    response.setMsg(new TimePacket.ResponseSync());
                    response.getMsg().setT0(start.getMsg().getT0());
                    response.getMsg().setT1(t1);
                    response.getMsg().setT2(app.getMeshMicroTime());
                    app.send(response);
                });
        getPacketRegistry().registerPacket(TimePacket.TYPE, TimePacket.ResponseSync.TYPE, TimePacket.class, TimePacket.ResponseSync.class)
                .setPacketConsumer((app, t3, packet) -> {
                    @SuppressWarnings("unchecked")
                    TimePacket<TimePacket.ResponseSync> response = packet;
                    long t0        = response.getMsg().getT0();
                    long t1        = response.getMsg().getT1();
                    long t2        = response.getMsg().getT2();
                    long tripDelay = (t3 - t0) - (t2 - t1);
                    if (tripDelay < 50_000) {//50ms
                        app.setTimeOffset((t1 - t0) / 2 + (t2 - t3) / 2);
                    }
                });

        getPacketRegistry().registerPacket(RoutingPacket.Request.TYPE, RoutingPacket.Request.class)
                .setPacketConsumer((app, arrivalTime, packet) -> {
                    //val packetSubs = new RoutingPacket.Subs();
                    //packetSubs.setNodeId(packet.getNodeId());
                    //packetSubs.setRoot(packet.isRoot());
                    //packetSubs.setSubs(packet.getSubs());

                    //if (packetSubs.isRoot()) {
                    //app.getMesh().setRootNode(packetSubs.getNodeId(),
                    //        packetSubs.getSubs()
                    //                .stream()
                    //                .map(RoutingPacket.Subs::getNodeId)
                    //                .collect(Collectors.toList()));
                    //packetSubs.getSubs()
                    //        .stream()
                    //        .flatMap(subs -> subs.getSubs().stream())
                    //        .forEach(subs -> app.getMesh().putNode(subs.getNodeId(),
                    //                subs.getSubs()
                    //                        .stream()
                    //                        .map(RoutingPacket.Subs::getNodeId)
                    //                        .collect(Collectors.toList())));

                    val reply = new RoutingPacket.Reply();
                    reply.setNodeId(getNodeId());
                    reply.setFrom(getNodeId());
                    reply.setDest(packet.getFrom());
                    reply.setRoot(true);
                    //reply.getSubs().add(packetSubs);
                    app.send(reply);

                    //} else {
                    //    throw new RuntimeException("Bridge is not a root");
                    //}
                });

        getPacketRegistry().registerPacket(RoutingPacket.Reply.TYPE, RoutingPacket.Reply.class)
                .setPacketConsumer((app, arrivalTime, packet) -> {
                    val packetSubs = new RoutingPacket.Subs();
                    packetSubs.setNodeId(packet.getNodeId());
                    packetSubs.setRoot(packet.isRoot());
                    packetSubs.setSubs(packet.getSubs());

                    if (packetSubs.isRoot()) {
                        app.getMesh().setRootNode(packetSubs.getNodeId(),
                                packetSubs.getSubs()
                                        .stream()
                                        .map(RoutingPacket.Subs::getNodeId)
                                        .collect(Collectors.toList()));
                        packetSubs.getSubs()
                                .stream()
                                .flatMap(subs -> subs.getSubs().stream())
                                .forEach(subs -> app.getMesh().putNode(subs.getNodeId(),
                                        subs.getSubs()
                                                .stream()
                                                .map(RoutingPacket.Subs::getNodeId)
                                                .collect(Collectors.toList())));
                    } else {
                        throw new RuntimeException("Bridge is not a root");
                    }
                });

        getPacketRegistry().registerPacket(OTA.DataRequest.TYPE, OTA.DataRequest.class)
                .setPacketConsumer((app, arrivalTime, packet) -> {
                    val response = new OTA.DataResponse();
                    response.setFrom(getNodeId());
                    response.setDest(packet.getFrom());
                    response.setMd5(packet.getMd5());
                    response.setHardware(packet.getHardware());
                    response.setRole(packet.getRole());
                    response.setNoPart(packet.getNoPart());
                    response.setPartNo(packet.getPartNo());
                    response.setForced(packet.isForced());
                    val ota = getOtaUpdates().getOrDefault(new UpdateOTA.ID(packet.getHardware(), packet.getRole(), packet.getMd5(), packet.getNoPart(), packet.isForced(), packet.getFrom()),
                            getOtaUpdates().get(new UpdateOTA.ID(packet.getHardware(), packet.getRole(), packet.getMd5(), packet.getNoPart(), packet.isForced(), null)));
                    if (ota != null && packet.getPartNo() < ota.getChunksCount()) {
                        response.setData(ota.withData(app.getTimeout(), packet.getPartNo()));
                    } else {
                        response.setData(Base64.getEncoder().encodeToString(new byte[]{0}));
                    }
                    app.send(response);
                });
    }

    @SneakyThrows
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            val socket=getServerSocket().accept();
            val client=new Client(getNodeId(),socket,getCharset(),getPacketRegistry(),getJson(),getOtaUpdates());
            onClientCreated.accept(client);
            client.start();
        }
        getServerSocket().close();
    }

    public void offerUpdateOTA(UpdateOTA updateOTA) {
        getOtaUpdates().put(new UpdateOTA.ID(updateOTA), updateOTA);
    }
}
