package ChunkSender;

import com.mojang.datafixers.util.Pair;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Logger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Climate;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.craftbukkit.v1_20_R3.CraftChunk;
import org.bukkit.craftbukkit.v1_20_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_20_R3.block.CraftBiome;
import org.bukkit.craftbukkit.v1_20_R3.generator.structure.CraftStructure;
import org.bukkit.generator.structure.Structure;
import org.cloudburstmc.protocol.common.util.VarInts;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.LoggerFactory;

public class ChunkWebSocketServer extends WebSocketServer {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(ChunkWebSocketServer.class);
    private Logger logger;

    public ChunkWebSocketServer(Logger logger, int port) {
        super(new InetSocketAddress("0.0.0.0", port));
        this.logger = logger;
    }

    public ChunkWebSocketServer(Logger logger, InetSocketAddress address) {
        super(address);
        this.logger = logger;
    }

    @Override
    public void onOpen(WebSocket webSocket, ClientHandshake clientHandshake) {
        ByteBuf buffer = Unpooled.buffer();
        boolean close = false;
        VarInts.writeUnsignedInt(buffer, ResponseType.LOGIN.ordinal());
        if (!ChunkSender.allowedAddresses.contains(webSocket.getRemoteSocketAddress().getAddress().toString())) {
            close = true;
            VarInts.writeUnsignedInt(buffer, LoginCode.UNAUTHORIZED.ordinal());
            logger.info("Unauthorized client " + webSocket.getRemoteSocketAddress().getAddress().toString() + " connected");
        } else {
            VarInts.writeUnsignedInt(buffer, LoginCode.SUCCESS.ordinal());
            logger.info("Client " + webSocket.getRemoteSocketAddress().getAddress().toString() + " connected");
        }
        byte[] data = new byte[buffer.readableBytes()];
        buffer.readBytes(data);
        webSocket.send(Base64.getEncoder().encodeToString(data));
        if (close) {
            webSocket.close();
        }
        buffer.release();
    }

    @Override
    public void onClose(WebSocket webSocket, int i, String s, boolean b) {
        logger.info("Client " + webSocket.getRemoteSocketAddress().getAddress().toString() + " disconnected");
    }

    @Override
    public void onMessage(WebSocket webSocket, String s) {
        ByteBuf buffer = Unpooled.wrappedBuffer(Base64.getDecoder().decode(s.getBytes()));
        ResponseType type = ResponseType.values()[VarInts.readUnsignedInt(buffer)];
        switch (type) {
            case CHUNK:
                int chunkX = buffer.readIntLE();
                int chunkZ = buffer.readIntLE();
                byte[] dimension = new byte[VarInts.readUnsignedInt(buffer)];
                buffer.readBytes(dimension);
                byte[] worldName = new byte[VarInts.readUnsignedInt(buffer)];
                buffer.readBytes(worldName);

                CraftWorld world = (CraftWorld) ChunkSender.getInstance().getServer().getWorld(new String(dimension));
                assert world != null;
                world.getChunkAtAsync(chunkX, chunkZ, true).whenComplete((chunk, e) -> {
                    try {
                        byte[] encoded = ChunkEncoder.encodeChunk((CraftChunk) chunk);
                        ByteBuf response = Unpooled.buffer();
                        VarInts.writeUnsignedInt(response, ResponseType.CHUNK.ordinal());
                        response.writeIntLE(chunkX);
                        response.writeIntLE(chunkZ);
                        VarInts.writeUnsignedInt(response, dimension.length);
                        response.writeBytes(dimension);
                        VarInts.writeUnsignedInt(response, worldName.length);
                        response.writeBytes(worldName);
                        VarInts.writeUnsignedInt(response, encoded.length);
                        response.writeBytes(encoded);
                        byte[] data = new byte[response.readableBytes()];
                        response.readBytes(data);
                        webSocket.send(Base64.getEncoder().encodeToString(data));
                    } catch (Throwable exception) {
                        logger.warning("Error sending chunk data to " + webSocket.getRemoteSocketAddress().getAddress().toString());
                        logger.warning(exception.getClass() + ": " + exception.getMessage());
                        throw exception;
                    }
                });
                break;
            case STRUCTURE:
                int structureChunkX = buffer.readIntLE();
                int structureChunkZ = buffer.readIntLE();
                byte[] structureDimension = new byte[VarInts.readUnsignedInt(buffer)];
                buffer.readBytes(structureDimension);
                byte[] structureWorldName = new byte[VarInts.readUnsignedInt(buffer)];
                buffer.readBytes(structureWorldName);
                byte[] structureType = new byte[VarInts.readUnsignedInt(buffer)];
                buffer.readBytes(structureType);
                int counter = buffer.readIntLE();

                ServerLevel structureLevel = ((CraftWorld) Objects.requireNonNull(ChunkSender.getInstance().getServer().getWorld(new String(structureDimension)))).getHandle();

                BlockPos pos = new BlockPos(structureChunkX, 0, structureChunkZ);
                Structure structure = Registry.STRUCTURE.get(Objects.requireNonNull(NamespacedKey.fromString(new String(structureType))));
                assert structure != null;
                List<Holder<net.minecraft.world.level.levelgen.structure.Structure>> holders = new ArrayList<>();
                holders.add(Holder.direct(CraftStructure.bukkitToMinecraft(structure)));
                Pair<BlockPos, Holder<net.minecraft.world.level.levelgen.structure.Structure>> result = null;
                try {
                    result = ChunkSender.getInstance().getServer().getScheduler().callSyncMethod(ChunkSender.getInstance(), () -> structureLevel.getChunkSource().getGenerator().findNearestMapStructure(structureLevel, HolderSet.direct(holders), pos, 100, false)).get();

                    boolean structureSuccess = result != null;
                    assert result != null;

                    ByteBuf response = Unpooled.buffer();
                    VarInts.writeUnsignedInt(response, ResponseType.STRUCTURE.ordinal());
                    response.writeIntLE(structureChunkX);
                    response.writeIntLE(structureChunkZ);
                    response.writeByte(structureSuccess ? 1 : 0);
                    if (structureSuccess) {
                        VarInts.writeUnsignedInt(response, structureDimension.length);
                        response.writeBytes(structureDimension);
                        VarInts.writeUnsignedInt(response, structureWorldName.length);
                        response.writeBytes(structureWorldName);
                        response.writeIntLE(result.getFirst().getX());
                        response.writeIntLE(result.getFirst().getY());
                        response.writeIntLE(result.getFirst().getZ());
                        VarInts.writeUnsignedInt(response, structureType.length);
                        response.writeBytes(structureType);
                    }
                    response.writeIntLE(counter);
                    byte[] data = new byte[response.readableBytes()];
                    response.readBytes(data);
                    webSocket.send(Base64.getEncoder().encodeToString(data));
                } catch (InterruptedException | ExecutionException e) {
                    logger.warning("Error finding structure data for " + webSocket.getRemoteSocketAddress().getAddress().toString());
                    logger.throwing(e.getClass().getName(), e.getStackTrace()[0].getMethodName(), e);
                }
                break;
            case BIOME:
                int biomeChunkX = buffer.readIntLE();
                int biomeChunkZ = buffer.readIntLE();
                byte[] biomeDimension = new byte[VarInts.readUnsignedInt(buffer)];
                buffer.readBytes(biomeDimension);
                byte[] biomeWorldName = new byte[VarInts.readUnsignedInt(buffer)];
                buffer.readBytes(biomeWorldName);
                byte[] biomeType = new byte[VarInts.readUnsignedInt(buffer)];
                buffer.readBytes(biomeType);
                int biomeCounter = buffer.readIntLE();

                World biomeWorld = ChunkSender.getInstance().getServer().getWorld(new String(biomeDimension));
                assert biomeWorld != null;
                ServerLevel biomeLevel = ((CraftWorld) biomeWorld).getHandle();

                BlockPos biomePos = new BlockPos(biomeChunkX, 0, biomeChunkZ);
                Biome biome = Registry.BIOME.get(Objects.requireNonNull(NamespacedKey.fromString(new String(biomeType))));
                assert biome != null;
                Set<Holder<net.minecraft.world.level.biome.Biome>> biomeHolders = new HashSet<>();
                biomeHolders.add(CraftBiome.bukkitToMinecraftHolder(biome));
                Climate.Sampler sampler = biomeLevel.getChunkSource().randomState().sampler();
                Pair<BlockPos, Holder<net.minecraft.world.level.biome.Biome>> biomeResult = biomeLevel.getChunkSource().getGenerator().getBiomeSource().findClosestBiome3d(biomePos, 100, 32, 64, biomeHolders::contains, sampler, biomeLevel);

                boolean biomeSuccess = biomeResult != null;
                ByteBuf biomeResponse = Unpooled.buffer();
                VarInts.writeUnsignedInt(biomeResponse, ResponseType.BIOME.ordinal());
                biomeResponse.writeIntLE(biomeChunkX);
                biomeResponse.writeIntLE(biomeChunkZ);
                biomeResponse.writeByte(biomeSuccess ? 1 : 0);
                if (biomeSuccess) {
                    VarInts.writeUnsignedInt(biomeResponse, biomeDimension.length);
                    biomeResponse.writeBytes(biomeDimension);
                    VarInts.writeUnsignedInt(biomeResponse, biomeWorldName.length);
                    biomeResponse.writeBytes(biomeWorldName);
                    biomeResponse.writeIntLE(biomeResult.getFirst().getX());
                    biomeResponse.writeIntLE(biomeResult.getFirst().getY());
                    biomeResponse.writeIntLE(biomeResult.getFirst().getZ());
                    VarInts.writeUnsignedInt(biomeResponse, biomeType.length);
                    biomeResponse.writeBytes(biomeType);
                }
                biomeResponse.writeIntLE(biomeCounter);
                byte[] biomeData = new byte[biomeResponse.readableBytes()];
                biomeResponse.readBytes(biomeData);
                webSocket.send(Base64.getEncoder().encodeToString(biomeData));
                break;
            default:
                logger.warning("Unknown method from " + webSocket.getRemoteSocketAddress().getAddress().toString());
        }
    }

    @Override
    public void onError(WebSocket webSocket, Exception e) {
        logger.warning("Error from " + webSocket.getRemoteSocketAddress().getAddress().toString());
        logger.throwing(e.getClass().getName(), e.getStackTrace()[0].getMethodName(), e);
    }

    @Override
    public void onStart() {
        logger.info("WebSocket server started on " + getAddress().getAddress().toString() + ":" + getAddress().getPort());
    }

    enum ResponseType {
        CHUNK,
        STRUCTURE,
        BIOME,
        LOGIN
    }

    enum LoginCode {
        SUCCESS,
        UNAUTHORIZED
    }
}
