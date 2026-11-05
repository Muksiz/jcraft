package fi.janix.gamble;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.security.SecureRandom;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * /gamble <1-64>   bet that many of your own golden carrots on a coin flip.
 *
 * The bet is taken from the inventory straight away, so it can't be dropped or moved while the
 * coin is in the air. After 3 seconds the result is announced: a win pays back double the bet,
 * a loss keeps the whole bet. One gamble at a time per player.
 *
 * The winnings go to the player once they are online and alive, so dying or logging out during
 * the flip doesn't lose them (logging out pays them before the player is saved).
 */
public class Gamble implements ModInitializer {
	private static final int MAX_BET = 64;
	private static final int FLIP_TICKS = 3 * 20;
	private static final SecureRandom RANDOM = new SecureRandom();

	private static final SimpleCommandExceptionType BUSY = new SimpleCommandExceptionType(
			Component.literal("Your coin is still in the air"));

	private record Pending(String name, int bet, boolean won, long dueTick, boolean announced) {}

	/** Bets waiting for their result (or, after a win, for the player to be paid). */
	private static final Map<UUID, Pending> PENDING = new LinkedHashMap<>();
	private static long tick;

	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(Commands.literal("gamble")
						.then(Commands.argument("amount", IntegerArgumentType.integer(1, MAX_BET))
								.executes(Gamble::gamble))));

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			tick++;
			settle(server, null, false);
		});
		// Settle at once on logout, so the winnings are saved with the player.
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> settle(server, handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> settle(server, null));
	}

	private static int gamble(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		int bet = IntegerArgumentType.getInteger(ctx, "amount");
		if (PENDING.containsKey(player.getUUID())) {
			throw BUSY.create();
		}

		int have = count(player, 0);
		if (have < bet) {
			ctx.getSource().sendFailure(Component.literal(
					"You only have " + carrots(have) + " (you tried to bet " + bet + ")"));
			return 0;
		}
		int taken = count(player, bet);
		if (taken != bet) {
			give(player, taken);
			ctx.getSource().sendFailure(Component.literal("Couldn't take your bet, try again"));
			return 0;
		}

		String name = player.getScoreboardName();
		PENDING.put(player.getUUID(), new Pending(name, bet, RANDOM.nextBoolean(), tick + FLIP_TICKS, false));
		broadcast(ctx.getSource().getServer(), Component.literal(name + " has gambled " + carrots(bet) + "!")
				.withStyle(ChatFormatting.GOLD));
		return bet;
	}

	/** Settles one player's gamble now, coin in the air or not; null settles everyone's. */
	private static void settle(MinecraftServer server, UUID only) {
		settle(server, only, true);
	}

	/**
	 * Announces results that are due (all, with force), and pays winners who are online and alive.
	 * A winner who is dead or offline stays pending and is paid on a later tick.
	 */
	private static void settle(MinecraftServer server, UUID only, boolean force) {
		for (Iterator<Map.Entry<UUID, Pending>> it = PENDING.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Pending> entry = it.next();
			Pending pending = entry.getValue();
			if (only != null && !only.equals(entry.getKey())) {
				continue;
			}
			if (!pending.announced()) {
				if (pending.dueTick() > tick && !force) {
					continue;
				}
				announce(server, pending);
				pending = new Pending(pending.name(), pending.bet(), pending.won(), pending.dueTick(), true);
				entry.setValue(pending);
			}
			if (!pending.won()) {
				it.remove();
				continue;
			}
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player != null && player.isAlive()) {
				give(player, pending.bet() * 2);
				it.remove();
			}
		}
	}

	private static void announce(MinecraftServer server, Pending pending) {
		Component message = pending.won()
				? Component.literal(pending.name() + " won " + carrots(pending.bet() * 2) + "!").withStyle(ChatFormatting.GREEN)
				: Component.literal(pending.name() + " lost " + carrots(pending.bet()) + ".").withStyle(ChatFormatting.RED);
		broadcast(server, message);
	}

	private static void broadcast(MinecraftServer server, Component message) {
		server.getPlayerList().broadcastSystemMessage(message, false);
	}

	/** Removes up to max golden carrots (like /clear) and returns how many; max 0 only counts. */
	private static int count(ServerPlayer player, int max) {
		int n = player.getInventory().clearOrCountMatchingItems(
				stack -> stack.is(Items.GOLDEN_CARROT), max == 0, max, player.inventoryMenu.getCraftSlots());
		if (max > 0) {
			player.containerMenu.broadcastChanges();
			player.inventoryMenu.slotsChanged(player.getInventory());
		}
		return n;
	}

	/** Gives golden carrots, dropping at the player's feet whatever doesn't fit. */
	private static void give(ServerPlayer player, int amount) {
		while (amount > 0) {
			int size = Math.min(amount, Items.GOLDEN_CARROT.getDefaultMaxStackSize());
			ItemStack stack = new ItemStack(Items.GOLDEN_CARROT, size);
			amount -= size;
			if (!player.getInventory().add(stack) || !stack.isEmpty()) {
				player.drop(stack, false, Prediction.SERVER_ONLY);
			}
		}
		player.containerMenu.broadcastChanges();
	}

	private static String carrots(int n) {
		return n + (n == 1 ? " golden carrot" : " golden carrots");
	}
}
