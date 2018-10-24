package com.sasha.reminecraft.server;

import com.github.steveice10.mc.protocol.MinecraftProtocol;
import com.github.steveice10.mc.protocol.data.SubProtocol;
import com.github.steveice10.mc.protocol.data.game.PlayerListEntry;
import com.github.steveice10.mc.protocol.data.game.PlayerListEntryAction;
import com.github.steveice10.mc.protocol.data.game.chunk.Column;
import com.github.steveice10.mc.protocol.data.game.entity.EquipmentSlot;
import com.github.steveice10.mc.protocol.data.game.entity.metadata.ItemStack;
import com.github.steveice10.mc.protocol.data.game.entity.metadata.Position;
import com.github.steveice10.mc.protocol.data.game.setting.Difficulty;
import com.github.steveice10.mc.protocol.data.game.world.WorldType;
import com.github.steveice10.mc.protocol.packet.ingame.client.ClientKeepAlivePacket;
import com.github.steveice10.mc.protocol.packet.ingame.client.world.ClientTeleportConfirmPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerJoinGamePacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerPlayerListEntryPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerPluginMessagePacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.ServerEntityEffectPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.ServerEntityEquipmentPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.ServerEntityPropertiesPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.player.ServerPlayerPositionRotationPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.spawn.ServerSpawnMobPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.spawn.ServerSpawnObjectPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.spawn.ServerSpawnPaintingPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.spawn.ServerSpawnPlayerPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.world.ServerChunkDataPacket;
import com.github.steveice10.mc.protocol.packet.login.client.LoginStartPacket;
import com.github.steveice10.mc.protocol.packet.login.server.LoginSuccessPacket;
import com.github.steveice10.packetlib.event.session.*;
import com.sasha.reminecraft.ReMinecraft;
import com.sasha.reminecraft.client.ReListener;
import com.sasha.reminecraft.client.children.ChildReClient;
import com.sasha.reminecraft.util.entity.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class ReAdapter extends SessionAdapter {

    private ChildReClient child;

    public ReAdapter(ChildReClient child) {
        this.child = child;
    }

    @Override
    public void packetReceived(PacketReceivedEvent event) {
        if (event.getPacket() instanceof LoginStartPacket) {
            var pck = (LoginStartPacket) event.getPacket();
            ReMinecraft.INSTANCE.logger.log("Child user %s connecting!".replace("%s", pck.getUsername()));
        }
        if (((MinecraftProtocol)child.getSession().getPacketProtocol()).getSubProtocol() == SubProtocol.GAME) {
            if (event.getPacket() instanceof ClientTeleportConfirmPacket) {
                return;
            }
            if (event.getPacket() instanceof ClientKeepAlivePacket) {
                return;
            }

        }
    }

    @Override
    public void packetSending(PacketSendingEvent event) {
    }

    @Override
    public void packetSent(PacketSentEvent event) {
        if (event.getPacket() instanceof LoginSuccessPacket) {
            var pck = (LoginSuccessPacket) event.getPacket();
            ReMinecraft.INSTANCE.logger.log("Child user " + pck.getProfile().getName() + " authenticated!");
            this.child.getSession().send(new ServerJoinGamePacket(
                    ReListener.ReListenerCache.entityId,
                    false,
                    ReListener.ReListenerCache.gameMode,
                    ReListener.ReListenerCache.dimension,
                    Difficulty.NORMAL,
                    Integer.MAX_VALUE,
                    WorldType.DEFAULT,
                    false));
        }
        if (event.getPacket() instanceof ServerJoinGamePacket) {
            this.child.getSession().send(new ServerPluginMessagePacket("MC|Brand", ServerBranding.BRAND_ENCODED));
            new Thread(() -> {
                ReListener.ReListenerCache.chunkCache.forEach((hash, chunk)-> {
                    this.child.getSession().send(new ServerChunkDataPacket(chunk));
                    try {
                        Thread.sleep(20L);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                });
                ReMinecraft.INSTANCE.logger.log("Sent " + ReListener.ReListenerCache.chunkCache.size() + " chunks");
                this.child.getSession().send(new ServerPlayerPositionRotationPacket(ReListener.ReListenerCache.posX, ReListener.ReListenerCache.posY, ReListener.ReListenerCache.posZ, ReListener.ReListenerCache.yaw, ReListener.ReListenerCache.pitch, new Random().nextInt(1000) + 10));
                //this.child.getSession().send(new ServerPlayerListEntryPacket(PlayerListEntryAction.ADD_PLAYER,ReListener.ReListenerCache.playerListEntries.toArray(new PlayerListEntry[ReListener.ReListenerCache.playerListEntries.size()])));
                //this.child.getSession().send(ReListener.ReListenerCache.playerInventory);
                for (Entity entity : ReListener.ReListenerCache.entityCache.values()) {
                    switch (entity.type) {
                        case MOB:
                            EntityMob mob = (EntityMob) entity;
                            this.child.getSession().send(new ServerSpawnMobPacket(entity.entityId,
                                    entity.uuid,
                                    mob.mobType,
                                    entity.posX,
                                    entity.posY,
                                    entity.posZ,
                                    mob.yaw,
                                    mob.pitch,
                                    mob.headYaw,
                                    mob.motionX,
                                    mob.motionY,
                                    mob.motionZ,
                                    mob.metadata));
                            for (PotionEffect effect : mob.potionEffects) {
                                child.getSession().send(new ServerEntityEffectPacket(entity.entityId,
                                        effect.effect,
                                        effect.amplifier,
                                        effect.duration,
                                        effect.ambient,
                                        effect.showParticles));
                            }
                            for (Map.Entry<EquipmentSlot, ItemStack> entry : mob.equipment.entrySet()) {
                                child.getSession().send(new ServerEntityEquipmentPacket(entity.entityId,
                                        entry.getKey(),
                                        entry.getValue()));
                            }
                            if (mob.properties.size() > 0) {
                                child.getSession().send(new ServerEntityPropertiesPacket(entity.entityId,
                                        mob.properties));
                            }
                            break;
                        case PLAYER:
                            EntityPlayer playerSending = (EntityPlayer) entity;
                            child.getSession().send(new ServerSpawnPlayerPacket(entity.entityId,
                                    entity.uuid,
                                    entity.posX,
                                    entity.posY,
                                    entity.posZ,
                                    playerSending.yaw,
                                    playerSending.pitch,
                                    playerSending.metadata));
                        case REAL_PLAYER:
                            EntityPlayer player = (EntityPlayer) entity;
                            for (PotionEffect effect : player.potionEffects) {
                                child.getSession().send(new ServerEntityEffectPacket(entity.entityId,
                                        effect.effect,
                                        effect.amplifier,
                                        effect.duration,
                                        effect.ambient,
                                        effect.showParticles));
                            }
                            for (Map.Entry<EquipmentSlot, ItemStack> entry : player.equipment.entrySet()) {
                                child.getSession().send(new ServerEntityEquipmentPacket(entity.entityId,
                                        entry.getKey(),
                                        entry.getValue()));
                            }
                            if (player.properties.size() > 0) {
                                child.getSession().send(new ServerEntityPropertiesPacket(entity.entityId,
                                        player.properties));
                            }
                            break;
                        case OBJECT:
                            EntityObject object = (EntityObject) entity;
                            child.getSession().send(new ServerSpawnObjectPacket(entity.entityId,
                                    entity.uuid,
                                    object.objectType,
                                    object.data,
                                    entity.posX,
                                    entity.posY,
                                    entity.posZ,
                                    object.yaw,
                                    object.pitch));
                            break;
                    }
                }
                this.child.setPlaying(true);
            }).start();
        }
    }

    @Override
    public void connected(ConnectedEvent event) {
    }

    @Override
    public void disconnecting(DisconnectingEvent event) {
    }

    @Override
    public void disconnected(DisconnectedEvent event) {
    }

}
class ServerBranding {
    public static final String BRAND = "RE:Minecraft " + ReMinecraft.VERSION;
    public static byte[] BRAND_ENCODED;

    static {
        ByteBuf buf = Unpooled.buffer(5 + BRAND.length());
        try {
            writeUTF8(buf, BRAND);
        } catch (IOException e) {
            e.printStackTrace();
        }
        BRAND_ENCODED = buf.array();
    }

    private static void writeUTF8(ByteBuf buf, String value) throws IOException {
        final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length >= Short.MAX_VALUE) {
            throw new IOException("Attempt to write a string with a length greater than Short.MAX_VALUE to ByteBuf!");
        }
        // Write the string's length
        writeVarInt(buf, bytes.length);
        buf.writeBytes(bytes);
    }
    private static void writeVarInt(ByteBuf buf, int value) {
        byte part;
        while (true) {
            part = (byte) (value & 0x7F);
            value >>>= 7;
            if (value != 0) {
                part |= 0x80;
            }
            buf.writeByte(part);
            if (value == 0) {
                break;
            }
        }
    }


}