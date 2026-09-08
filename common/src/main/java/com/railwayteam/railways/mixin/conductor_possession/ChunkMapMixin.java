/*
 * Steam 'n' Rails
 * Copyright (c) 2022-2024 The Railways Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.railwayteam.railways.mixin.conductor_possession;

import com.railwayteam.railways.content.conductor.ConductorEntity;
import com.railwayteam.railways.content.conductor.ConductorPossessionController;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.apache.commons.lang3.mutable.MutableObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * This mixin makes sure that chunks near cameras are properly sent to the player viewing it, as well as fixing block updates
 * not getting sent to chunks loaded by cameras
 *
 * Confirmed compatible with SecurityCraft
 */
@Mixin(value = ChunkMap.class, priority = 1200)
public abstract class ChunkMapMixin {
	@Shadow
	int viewDistance;

	@Shadow
	protected abstract void updateChunkTracking(ServerPlayer player, ChunkPos chunkPos, MutableObject<ClientboundLevelChunkWithLightPacket> packetCache, boolean wasLoaded, boolean load);

	/**
	 * Fixes block updates not getting sent to chunks loaded by cameras by returning the camera's SectionPos to the distance
	 * checking methods
	 */
	@SuppressWarnings("UnresolvedMixinReference")
    @Redirect(method = {
			"getPlayers",
			"lambda$setViewDistance$0", "m_ntjylyau", "method_17219" // these 3 all refer to the same thing with different mappings
	}, at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;getLastSectionPos()Lnet/minecraft/core/SectionPos;"))
	private SectionPos railways$securitycraft$getCameraSectionPos(ServerPlayer player) {
		if (ConductorPossessionController.isPossessingConductor(player) || player.getCamera().getClass().getName().equals("net.geforcemods.securitycraft.entity.camera.SecurityCamera"))
			return SectionPos.of(player.getCamera());

		return player.getLastSectionPos();
	}

	/**
	 * Tracks chunks loaded by cameras to send them to the client, and tracks chunks around the player to properly update them
	 * when they stop viewing a camera
	 */
	@Inject(method = "move", at = @At(value = "TAIL"))
	private void railways$securitycraft$trackCameraLoadedChunks(ServerPlayer player, CallbackInfo callback) {
		if (!(player.getCamera() instanceof ConductorEntity camera)) {
			// Do not consume the marker while the player is viewing some other camera or spectator entity.
			if (player.getCamera() == player && ConductorEntity.hasRecentlyDismounted(player)) {
				SectionPos playerPos = player.getLastSectionPos();

				// Resend the chunks around the player's body once. These chunks may have missed block and block-entity updates during possession.
				for (int x = playerPos.x() - viewDistance - 1; x <= playerPos.x() + viewDistance + 1; x++) {
					for (int z = playerPos.z() - viewDistance - 1; z <= playerPos.z() + viewDistance + 1; z++) {
						if (ChunkMap.isChunkInRange(x, z, playerPos.x(), playerPos.z(), viewDistance)) {
							updateChunkTracking(player, new ChunkPos(x, z), new MutableObject<>(), false, true);
						}
					}
				}
			}

			return;
		}

		if (camera.hasSentChunks()) {
			return;
		}

		SectionPos oldPos = camera.oldSectionPos;
		SectionPos newPos = SectionPos.of(camera);
		SectionPos playerPos = player.getLastSectionPos();

		camera.oldSectionPos = newPos;

		// Unload chunks that left the camera's viewing area.
		if (oldPos != null) {
			for (int x = oldPos.x() - viewDistance - 1; x <= oldPos.x() + viewDistance + 1; x++) {
				for (int z = oldPos.z() - viewDistance - 1; z <= oldPos.z() + viewDistance + 1; z++) {
					boolean wasLoaded = ChunkMap.isChunkInRange(x, z, oldPos.x(), oldPos.z(), viewDistance);
					boolean stillLoaded = ChunkMap.isChunkInRange(x, z, newPos.x(), newPos.z(), viewDistance);
					boolean playerLoaded = ChunkMap.isChunkInRange(x, z, playerPos.x(), playerPos.z(), viewDistance);

					if (wasLoaded && !stillLoaded && !playerLoaded) {
						updateChunkTracking(player, new ChunkPos(x, z), new MutableObject<>(), true, false);
					}
				}
			}
		}

		// Load chunks newly entering the camera's viewing area.
		for (int x = newPos.x() - viewDistance - 1; x <= newPos.x() + viewDistance + 1; x++) {
			for (int z = newPos.z() - viewDistance - 1; z <= newPos.z() + viewDistance + 1; z++) {
				boolean wasLoaded = oldPos != null && ChunkMap.isChunkInRange(x, z, oldPos.x(), oldPos.z(), viewDistance);
				boolean shouldLoad = ChunkMap.isChunkInRange(x, z, newPos.x(), newPos.z(), viewDistance);
				boolean playerLoaded = ChunkMap.isChunkInRange(x, z, playerPos.x(), playerPos.z(), viewDistance);

				if (shouldLoad && !wasLoaded && !playerLoaded) {
					updateChunkTracking(player, new ChunkPos(x, z), new MutableObject<>(), false, true);
				}
			}
		}

		camera.setHasSentChunks(true);
	}
}
