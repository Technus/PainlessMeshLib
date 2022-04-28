package com.github.technus.painlessMesh;

import com.github.technus.painlessMesh.json.JSON;
import com.github.technus.painlessMesh.json.packet.OTA;
import com.github.technus.painlessMesh.json.packet.Packet;
import com.github.technus.painlessMesh.mesh.Mesh;
import lombok.*;
import lombok.experimental.Accessors;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class Client extends Thread {
    @Getter
    protected       Charset charset;
    @Getter
    protected final long    nodeId;
    @Getter
    protected final PacketRegistry<Client> packetRegistry;
    @Getter
    protected final JSON<Client>      json;
    @Getter
    protected final Map<UpdateOTA.ID, UpdateOTA> otaUpdates;
    @Accessors(chain = true)
    @Getter
    @Setter
    protected       Consumer<Client>  userLoad   = connectionHandler -> {};

    @Getter(AccessLevel.PROTECTED)
    protected final Socket                   clientSocket;
    @Getter(AccessLevel.PROTECTED)
    protected final ScheduledExecutorService worker= Executors.newSingleThreadScheduledExecutor();
    @Getter(AccessLevel.PROTECTED)
    protected       Thread                   userWorker;
    @Getter
    protected          Mesh             mesh       = new Mesh();
    @Getter
    protected          long             timeOffset = 0;
    @Getter(AccessLevel.PROTECTED)
    protected volatile Consumer<Packet> packetConsumer;
    @Getter(AccessLevel.PROTECTED)
    protected final    Object           lock       = new Object();
    @Getter(AccessLevel.PROTECTED)
    protected final    UpdateOTA.Timeout timeout    = new UpdateOTA.Timeout();

    Client(long nodeId,Socket socket,Charset charset,PacketRegistry<Client> packetRegistry,JSON<Client> json,Map<UpdateOTA.ID, UpdateOTA> otaUpdates){
        this.nodeId=nodeId;
        this.clientSocket = socket;
        this.charset=charset;
        this.packetRegistry=packetRegistry;
        this.json=json;
        this.otaUpdates=otaUpdates;
        createWorkers();
    }

    public Client(long nodeId,String host, int port) throws IOException {
        this(nodeId,new Socket(host, port));
    }

    public Client(long nodeId, InetAddress host, int port) throws IOException{
        this(nodeId,new Socket(host, port));
    }

    public Client(long nodeId, Proxy proxy) {
        this(nodeId,new Socket(proxy));
    }

    public Client(long nodeId,Socket socket) {
        this.nodeId=nodeId;
        this.clientSocket = socket;
        this.charset=Charset.forName("CP1250");
        this.packetRegistry=new PacketRegistry<>();
        this.json=new JSON<>(getPacketRegistry());
        this.otaUpdates=new ConcurrentHashMap<>();
        createWorkers();
    }

    protected void createWorkers()
    {
        setName("ConnectionHandler");
        System.out.println("Connection begin");
        //1s
        userWorker=new Thread(()->{
            Consumer<Client> r=getUserLoad();
            while (!isInterrupted()) {
                r.accept(this);
            }
        });
        getUserWorker().setName("userWorker");
    }

    synchronized public void send(Packet packet) {
        getPacketConsumer().accept(packet);
    }

    protected void setTimeOffset(long offsetMicros) {
        synchronized (getLock()) {
            timeOffset = offsetMicros;
        }
        //System.out.println("Time: " + getMeshMicroTime());
    }

    public long getMeshMicroTime() {
        synchronized (getLock()) {
            return (System.nanoTime() / 1000L + getTimeOffset()) & 0xffffffffL;
        }
    }

    @SneakyThrows
    public void run() {
        System.out.println("Connection handling begin");

        getWorker().scheduleWithFixedDelay(() -> getOtaUpdates().forEach((id, ota) -> {
            Optional<Boolean> shouldOffer = getTimeout().shouldOfferOTA();
            if (shouldOffer.isPresent()) {
                if (shouldOffer.get()) {
                    OTA.Announce announcement = new OTA.Announce();
                    announcement.setFrom(getNodeId());
                    announcement.setDest(id.getTarget().orElse(getNodeId()));//broadcast
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
        }),1,1, TimeUnit.SECONDS);

        getUserWorker().start();

        val hook=new Thread(()->{
            try {
                Client.this.interrupt();
            } catch (Throwable e) {
                e.printStackTrace();
            }
            try {
                getUserWorker().interrupt();
            } catch (Throwable e) {
                e.printStackTrace();
            }
            try{
                if(getWorker().awaitTermination(2,TimeUnit.SECONDS)){
                    getWorker().shutdownNow();
                }
            }
            catch (InterruptedException e) {
                try {
                    getWorker().shutdownNow();
                } catch (Throwable e1) {
                    e1.printStackTrace();
                }
            }
            try {
                getClientSocket().shutdownInput();
            } catch (Throwable e) {
                e.printStackTrace();
            }
            try {
                getClientSocket().shutdownOutput();
            } catch (Throwable e) {
                e.printStackTrace();
            }
            try {
                getClientSocket().close();
            } catch (Throwable e) {
                e.printStackTrace();
            }
        });
        Runtime.getRuntime().addShutdownHook(hook);

        try (var socket = getClientSocket();
             var out = new BufferedOutputStream(socket.getOutputStream());
             var in = new BufferedInputStream(socket.getInputStream())) {
            ByteArrayOutputStream builder = new ByteArrayOutputStream();
            int                   nextByte;
            packetConsumer = packet -> {
                byte[] bytes = getJson().toJson(packet).getBytes(charset);
                //System.out.println(packet);
                //System.out.println(new String(bytes, charset));
                try {
                    out.write(bytes);
                    out.write(0);//Null terminator
                    out.flush();
                } catch (IOException e) {
                    throw new RuntimeException("Failed to send: " + new String(bytes, charset));
                }
            };
            while (!isInterrupted() && (nextByte = in.read()) >= 0) {
                if (nextByte == 0) {
                    long   arrivalTime = getMeshMicroTime();
                    String inputLine   = new String(builder.toByteArray(), charset);
                    //System.out.println(inputLine);
                    builder.reset();

                    getJson().fromJSON(inputLine)
                            .ifPresent(packet -> {
                                //System.out.println(packet);
                                getPacketRegistry().getTypeFor(packet)
                                        .ifPresent(handler -> handler.onReceive(this, arrivalTime, packet));
                            });
                } else {
                    builder.write(nextByte);
                }
            }
        } catch (SocketException e) {
            System.err.println("Connection reset");
        } finally {
            packetConsumer = packet -> {
                byte[] bytes = getJson().toJson(packet).getBytes(charset);
                throw new RuntimeException("Cannot send: " + new String(bytes, charset));
            };
            getUserWorker().interrupt();
            try{
                if(getWorker().awaitTermination(2,TimeUnit.SECONDS)){
                    getWorker().shutdownNow();
                }
            }
            catch (InterruptedException e) {
                getWorker().shutdownNow();
            }
            getClientSocket().shutdownInput();
            getClientSocket().shutdownOutput();
            getClientSocket().close();
            Runtime.getRuntime().removeShutdownHook(hook);
        }
    }

    public void offerUpdateOTA(UpdateOTA updateOTA) {
        getOtaUpdates().put(new UpdateOTA.ID(updateOTA), updateOTA);
    }
}
