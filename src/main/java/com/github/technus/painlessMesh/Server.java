package com.github.technus.painlessMesh;

import com.github.technus.painlessMesh.json.JSON;
import com.github.technus.painlessMesh.json.packet.OTA;
import com.github.technus.painlessMesh.json.packet.Packet;
import com.github.technus.painlessMesh.json.packet.RoutingPacket;
import com.github.technus.painlessMesh.json.packet.TimePacket;
import com.github.technus.painlessMesh.mesh.Mesh;
import lombok.*;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class Server extends Thread {
    @Getter
    protected final long                              nodeId         = 1337_2137;
    protected final ServerSocket                      serverSocket;
    @Getter
    protected final PacketRegistry<ConnectionHandler> packetRegistry = new PacketRegistry<>();
    @Getter
    protected final JSON<ConnectionHandler>           json           = new JSON<>(getPacketRegistry());
    @Getter
    protected       Map<UpdateOTA.ID, UpdateOTA>      otaUpdates     = new ConcurrentHashMap<>();

    public Server(int port) {
        this(port, null);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            interrupt();
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }));
    }

    @SneakyThrows
    public Server(int port, InetAddress ip) {
        serverSocket = new ServerSocket(port, 50, ip);

        getPacketRegistry().registerPacket(TimePacket.TYPE, TimePacket.class, TimePacket.Msg.class);
        getPacketRegistry().registerPacket(TimePacket.TYPE, TimePacket.Request.TYPE, TimePacket.class, TimePacket.Request.class)
                .setPacketConsumer((app, arrivalTime, packet) -> {
                    @SuppressWarnings("unchecked")
                    TimePacket<TimePacket.Request>   request = packet;
                    TimePacket<TimePacket.StartSync> start   = new TimePacket<>();
                    start.setFrom(getNodeId());
                    start.setDest(request.getDest());
                    start.setMsg(new TimePacket.StartSync());
                    start.getMsg().setT0(app.getMeshMicroTime());
                    app.send(start);
                });
        getPacketRegistry().registerPacket(TimePacket.TYPE, TimePacket.StartSync.TYPE, TimePacket.class, TimePacket.StartSync.class)
                .setPacketConsumer((app, t1, packet) -> {
                    @SuppressWarnings("unchecked")
                    TimePacket<TimePacket.StartSync>    start    = packet;
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
                    TimePacket<TimePacket.ResponseSync> response  = packet;
                    long                                t0        = response.getMsg().getT0();
                    long                                t1        = response.getMsg().getT1();
                    long                                t2        = response.getMsg().getT2();
                    long                                tripDelay = (t3 - t0) - (t2 - t1);
                    if (tripDelay < 50_000) {//50ms
                        app.setTimeOffset((t1 - t0) / 2 + (t2 - t3) / 2);
                    }
                });

        getPacketRegistry().registerPacket(RoutingPacket.Request.TYPE, RoutingPacket.Request.class)
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

                        val reply = new RoutingPacket.Reply();
                        reply.setNodeId(getNodeId());
                        reply.setFrom(getNodeId());
                        reply.setDest(packet.getFrom());
                        reply.setRoot(false);
                        reply.getSubs().add(packetSubs);
                        app.send(reply);

                    } else {
                        throw new RuntimeException("Bridge is not a root");
                    }
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
                    val ota = otaUpdates.get(new UpdateOTA.ID(packet.getHardware(), packet.getRole(), packet.getMd5(), packet.getNoPart(), packet.isForced()));
                    if (ota != null && packet.getPartNo() < ota.getChunksCount()) {
                        response.setData(ota.withData(app.getTimeout(),packet.getPartNo()));
                    } else {
                        response.setData(Base64.getEncoder().encodeToString(new byte[]{0}));
                    }
                    app.send(response);
                });
    }

    @SneakyThrows
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            new ConnectionHandler(serverSocket.accept()).start();
        }
        serverSocket.close();
    }

    public void offerUpdateOTA(UpdateOTA updateOTA) {
        getOtaUpdates().put(new UpdateOTA.ID(updateOTA), updateOTA);
    }

    public class ConnectionHandler extends Thread {
        @Getter(AccessLevel.PROTECTED)
        protected final    Socket           clientSocket;
        @Getter(AccessLevel.PROTECTED)
        protected final Thread worker;
        @Getter
        protected          Mesh             mesh       = new Mesh();
        @Getter
        protected          long             timeOffset = 0;
        @Getter(AccessLevel.PROTECTED)
        protected volatile Consumer<Packet> packetConsumer;
        @Getter(AccessLevel.PROTECTED)
        protected final      Object           lock       = new Object();
        @Getter(AccessLevel.PROTECTED)
        protected final UpdateOTA.Timeout timeout=new UpdateOTA.Timeout();

        public ConnectionHandler(Socket socket) {
            this.clientSocket = socket;
            System.out.println("Connection begin");
            //1s
            worker = new Thread(() -> {
                try {
                    boolean sent
                            = false;
                    while (!getWorker().isInterrupted()) {
                        Thread.sleep(10);
                        long meshTime=getMeshMicroTime();
                        if (((meshTime >> 10) & 0x3FF) < 128) {//1s
                            if (!sent) {
                                System.out.println("BLINK! "+meshTime);
                                sent = true;
                            }
                        } else {
                            sent = false;
                        }
                        getOtaUpdates().forEach((id, ota) -> {
                            Optional<Boolean> shouldOffer = getTimeout().shouldOfferOTA();
                            if (shouldOffer.isPresent()) {
                                if (shouldOffer.get()) {
                                    OTA.Announce announcement = new OTA.Announce();
                                    announcement.setFrom(getNodeId());
                                    announcement.setDest(getNodeId());//broadcast
                                    announcement.setMd5(id.getMd5());
                                    announcement.setHardware(id.getHardware());
                                    announcement.setRole(id.getRole());
                                    announcement.setNoPart(id.getNoPart());
                                    announcement.setForced(id.isForced());
                                    send(announcement);
                                }
                            } else {
                                getOtaUpdates().remove(id);
                            }
                        });
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });
            getWorker().start();
        }

        synchronized public void send(Packet packet) {
            getPacketConsumer().accept(packet);
        }

        protected void setTimeOffset(long offsetMicros) {
            synchronized (getLock()) {
                timeOffset = offsetMicros;
            }
            System.out.println("Time: " + getMeshMicroTime());
        }

        public long getMeshMicroTime() {
            synchronized (getLock()) {
                return (System.nanoTime() / 1000L + getTimeOffset()) & 0xffffffffL;
            }
        }

        @SneakyThrows
        public void run() {
            try (var out = new BufferedOutputStream(getClientSocket().getOutputStream());
                 var in = new BufferedInputStream(getClientSocket().getInputStream())) {
                StringBuilder builder = new StringBuilder();
                int           nextByte;
                packetConsumer = packet -> {
                    byte[] bytes = getJson().toJson(packet).getBytes(StandardCharsets.US_ASCII);
                    System.out.println(packet);
                    System.out.println(new String(bytes, StandardCharsets.US_ASCII));
                    try {
                        out.write(bytes);
                        out.write(0);//Null terminator
                        out.flush();
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to send: " + new String(bytes, StandardCharsets.US_ASCII));
                    }
                };
                while (!Server.this.isInterrupted() && (nextByte = in.read()) >= 0) {
                    if (nextByte == 0) {
                        long   arrivalTime = getMeshMicroTime();
                        String inputLine   = builder.toString();
                        System.out.println(inputLine);
                        builder.setLength(0);

                        getJson().fromJSON(inputLine)
                                .ifPresent(packet -> {
                                    System.out.println(packet);
                                    getPacketRegistry().getTypeFor(packet)
                                            .ifPresent(packetPacketHandler -> packetPacketHandler.onReceive(this, arrivalTime, packet));
                                });
                    } else {
                        builder.append((char) nextByte);
                    }
                }
            } catch (SocketException e) {
                System.err.println("Connection reset");
            } finally {
                packetConsumer = packet -> {
                    byte[] bytes = getJson().toJson(packet).getBytes(StandardCharsets.US_ASCII);
                    throw new RuntimeException("Cannot send: " + new String(bytes, StandardCharsets.US_ASCII));
                };
                getWorker().interrupt();
                getClientSocket().close();
            }
        }
    }
}
