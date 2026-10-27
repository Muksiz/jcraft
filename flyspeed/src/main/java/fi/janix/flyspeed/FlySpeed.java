package fi.janix.flyspeed;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Collection;
import java.util.List;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * /flyspeed                      show your multiplier
 * /flyspeed <multiplier>         set your own (max 5x)
 * /flyspeed <multiplier> <who>   ops: set for others (max 10x)
 *
 * The speed lives in the player's abilities, which the server syncs to the client
 * and saves with the player, so vanilla clients work and it survives relogs.
 */
public class FlySpeed implements ModInitializer {
	private static final float VANILLA_SPEED = 0.05f;
	private static final float MAX_SELF = 5f;
	private static final float MAX_OP = 10f;

	private static final SimpleCommandExceptionType TOO_FAST = new SimpleCommandExceptionType(
			Component.literal("Max " + (int) MAX_SELF + "x without op"));

	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(Commands.literal("flyspeed")
						.executes(FlySpeed::show)
						.then(Commands.argument("multiplier", FloatArgumentType.floatArg(0.1f, MAX_OP))
								.executes(ctx -> set(ctx, List.of(ctx.getSource().getPlayerOrException()), true))
								.then(Commands.argument("targets", EntityArgument.players())
										.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
										.executes(ctx -> set(ctx, EntityArgument.getPlayers(ctx, "targets"), false))))));
	}

	private static int show(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		float multiplier = player.getAbilities().getFlyingSpeed() / VANILLA_SPEED;
		ctx.getSource().sendSuccess(() -> Component.literal("Fly speed: " + format(multiplier) + "x"), false);
		return 1;
	}

	private static int set(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players, boolean self)
			throws CommandSyntaxException {
		float multiplier = FloatArgumentType.getFloat(ctx, "multiplier");
		if (self && multiplier > MAX_SELF && !Commands.LEVEL_GAMEMASTERS.check(ctx.getSource().permissions())) {
			throw TOO_FAST.create();
		}
		for (ServerPlayer player : players) {
			player.getAbilities().setFlyingSpeed(VANILLA_SPEED * multiplier);
			player.onUpdateAbilities();
		}
		String who = self ? "your" : players.size() == 1 ? players.iterator().next().getScoreboardName() + "'s" : players.size() + " players'";
		ctx.getSource().sendSuccess(() -> Component.literal("Set " + who + " fly speed to " + format(multiplier) + "x"), !self);
		return players.size();
	}

	private static String format(float multiplier) {
		return multiplier == Math.round(multiplier) ? String.valueOf(Math.round(multiplier)) : String.format("%.1f", multiplier);
	}
}
