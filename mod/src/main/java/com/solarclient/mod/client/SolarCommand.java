package com.solarclient.mod.client;

import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.solarclient.mod.client.social.SolarLink;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * The in-game {@code /solarclient} command.
 *
 *   /solarclient <admin|verify|solar+|owner> <add|remove> &lt;username&gt;
 *
 * Built from real Brigadier sub-commands rather than one free-text blob, so
 * the game's own tab-completion offers "admin / verify / solar+ / owner",
 * then "add / remove", as you type.
 *
 * The command does NO permission checking. It forwards the request to the
 * launcher, which forwards it to the backend, and the BACKEND is the only
 * place that decides whether the caller is allowed (it checks the
 * Mojang-verified name against the owner list). A non-owner can type it, but
 * just gets "not allowed" back. Keeping the gate on the server is what makes
 * it impossible to bypass by editing the client.
 */
public final class SolarCommand {
    private SolarCommand() {}

    /** The role words offered, mapped to what the backend stores. */
    private static final String[] ROLES = { "admin", "verify", "solar+", "owner" };

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> {
            LiteralArgumentBuilder<FabricClientCommandSource> root = ClientCommandManager.literal("solarclient");
            for (String role : ROLES) {
                root.then(ClientCommandManager.literal(role)
                        .then(opBranch(role, "add"))
                        .then(opBranch(role, "remove")));
            }
            root.executes(ctx -> {
                feedback("Usage: /solarclient <admin|verify|solar+|owner> <add|remove> <username>", Formatting.GRAY);
                return 1;
            });
            dispatcher.register(root);
        });
    }

    /** One "<add|remove> <username>" leaf under a role. */
    private static LiteralArgumentBuilder<FabricClientCommandSource> opBranch(String role, String op) {
        SuggestionProvider<FabricClientCommandSource> provider = (ctx, builder) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.getNetworkHandler() == null) return builder.buildFuture();
            
            String input = builder.getRemaining().toLowerCase(java.util.Locale.ROOT);
            if ("remove".equals(op)) {
                for (PlayerListEntry entry : client.getNetworkHandler().getPlayerList()) {
                    String name = entry.getProfile().name();
                    if (name.toLowerCase(java.util.Locale.ROOT).startsWith(input)) {
                        if (com.solarclient.mod.client.social.SolarBadges.hasRole(entry.getProfile().id(), role)) {
                            builder.suggest(name);
                        }
                    }
                }
            } else {
                for (String name : ctx.getSource().getPlayerNames()) {
                    if (name.toLowerCase(java.util.Locale.ROOT).startsWith(input)) {
                        builder.suggest(name);
                    }
                }
            }
            return builder.buildFuture();
        };

        return ClientCommandManager.literal(op)
                .then(ClientCommandManager.argument("username", StringArgumentType.word())
                        .suggests(provider)
                        .executes(ctx -> {
                            run(role, op, StringArgumentType.getString(ctx, "username"));
                            return 1;
                        }));
    }

    private static void run(String role, String op, String username) {
        JsonObject payload = new JsonObject();
        payload.addProperty("raw", role + " " + op + " " + username);
        // Result comes back off-thread; hop to the client thread for chat.
        SolarLink.get().actionForResult("solarclient:command", payload, message -> {
            MinecraftClient.getInstance().execute(() -> {
                feedback(message, Formatting.WHITE);
                com.solarclient.mod.client.social.SolarBadges.forceRefresh();
            });
        });
    }

    private static void feedback(String message, Formatting color) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        client.player.sendMessage(
                Text.literal("[SolarClient] ").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD)
                        .append(Text.literal(message).formatted(color)),
                false);
    }
}
