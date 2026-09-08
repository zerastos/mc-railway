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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * When a player is viewing a camera, enables sounds near the camera to be played, while sounds near the player entity are
 * suppressed
 *
 * Confirmed working with Security Craft
 */
@Mixin(value = PlayerList.class, priority = 1200)
public class PlayerListMixin {
	@Redirect(
			method = "broadcast",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/server/level/ServerPlayer;getX()D"
			)
	)
	private double railways$broadcastX(ServerPlayer player) {
		return player.getCamera() instanceof ConductorEntity conductor
				? conductor.getX() : player.getX();
	}

	@Redirect(
			method = "broadcast",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/server/level/ServerPlayer;getY()D"
			)
	)
	private double railways$broadcastY(ServerPlayer player) {
		return player.getCamera() instanceof ConductorEntity conductor
				? conductor.getY() : player.getY();
	}

	@Redirect(
			method = "broadcast",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/server/level/ServerPlayer;getZ()D"
			)
	)
	private double railways$broadcastZ(ServerPlayer player) {
		return player.getCamera() instanceof ConductorEntity conductor
				? conductor.getZ() : player.getZ();
	}
}
