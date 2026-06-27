package com.portaltiers.tagger;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.portaltiers.tagger.config.PortalConfig;
import com.portaltiers.tagger.gui.ConfigScreen;
import com.portaltiers.tagger.model.GameMode;
import com.portaltiers.tagger.model.PlayerRanking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.Map;

public class PortalTierTaggerClient implements ClientModInitializer {
    public static final String MOD_ID = "portaltiertagger";
    private static KeyBinding cycleKey;

    @Override
    public void onInitializeClient() {
        PortalConfig.get(); // load config eagerly

        // keybind to cycle the displayed gamemode
        cycleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "portaltiertagger.keybind.cycle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                "key.categories.portaltiertagger"
        ));

        // refresh when joining a server / world, then periodically
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> PortalTierManager.refreshNow());

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (cycleKey.wasPressed()) {
                GameMode next = PortalConfig.get().gamemode.next();
                PortalConfig.get().gamemode = next;
                PortalConfig.get().save();
                if (client.player != null) {
                    client.player.sendMessage(
                            Text.literal("[Portal] Gamemode: ").formatted(Formatting.GRAY)
                                    .append(Text.literal(next.displayName()).formatted(Formatting.AQUA)),
                            true);
                }
            }
            if (client.world != null) {
                PortalTierManager.maybeRefresh();
            }
        });

        registerCommands();
    }

    private void registerCommands() {
        SuggestionProvider<FabricClientCommandSource> gamemodeSuggestions = (ctx, builder) -> {
            for (GameMode m : GameMode.values()) {
                if (m.apiKey().toLowerCase().startsWith(builder.getRemaining().toLowerCase())) {
                    builder.suggest(m.apiKey());
                }
            }
            return builder.buildFuture();
        };

        SuggestionProvider<FabricClientCommandSource> playerSuggestions = (ctx, builder) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.getNetworkHandler() != null) {
                mc.getNetworkHandler().getPlayerList().forEach(e -> {
                    String name = e.getProfile() != null ? e.getProfile().getName() : null;
                    if (name != null && name.toLowerCase().startsWith(builder.getRemaining().toLowerCase())) {
                        builder.suggest(name);
                    }
                });
            }
            return builder.buildFuture();
        };

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("portaltier")
                        // /portaltier  -> toggle on/off
                        .executes(ctx -> {
                            PortalConfig cfg = PortalConfig.get();
                            cfg.enabled = !cfg.enabled;
                            cfg.save();
                            feedback(ctx.getSource(), Text.literal("[Portal] Tags " +
                                    (cfg.enabled ? "enabled" : "disabled"))
                                    .formatted(cfg.enabled ? Formatting.GREEN : Formatting.RED));
                            return 1;
                        })
                        // /portaltier refresh
                        .then(ClientCommandManager.literal("refresh").executes(ctx -> {
                            PortalTierManager.refreshNow();
                            feedback(ctx.getSource(), Text.literal("[Portal] Refreshing rankings...")
                                    .formatted(Formatting.YELLOW));
                            return 1;
                        }))
                        // /portaltier config
                        .then(ClientCommandManager.literal("config").executes(ctx -> {
                            MinecraftClient mc = MinecraftClient.getInstance();
                            mc.execute(() -> mc.setScreen(new ConfigScreen(null)));
                            return 1;
                        }))
                        // /portaltier gamemode <mode>
                        .then(ClientCommandManager.literal("gamemode")
                                .then(ClientCommandManager.argument("mode", StringArgumentType.word())
                                        .suggests(gamemodeSuggestions)
                                        .executes(ctx -> {
                                            String arg = StringArgumentType.getString(ctx, "mode");
                                            GameMode mode = GameMode.fromKey(arg);
                                            if (mode == null) {
                                                feedback(ctx.getSource(), Text.literal("[Portal] Unknown gamemode: " + arg)
                                                        .formatted(Formatting.RED));
                                                return 0;
                                            }
                                            PortalConfig.get().gamemode = mode;
                                            PortalConfig.get().save();
                                            feedback(ctx.getSource(), Text.literal("[Portal] Gamemode set to " + mode.displayName())
                                                    .formatted(Formatting.GREEN));
                                            return 1;
                                        })))
                        // /portaltier player <name>
                        .then(ClientCommandManager.literal("player")
                                .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                        .suggests(playerSuggestions)
                                        .executes(ctx -> {
                                            String name = StringArgumentType.getString(ctx, "name");
                                            feedback(ctx.getSource(), printPlayerInfo(name));
                                            return 1;
                                        })))));
    }

    private static void feedback(FabricClientCommandSource source, Text text) {
        source.sendFeedback(text);
    }

    private static Text printPlayerInfo(String name) {
        PlayerRanking pr = PortalTierManager.lookup(name);
        if (pr == null) {
            return Text.literal("[Portal] No data for " + name + " (try /portaltier refresh)")
                    .formatted(Formatting.RED);
        }
        if (!pr.hasAnyTier()) {
            return Text.literal("[Portal] " + pr.minecraftUsername + " has no tiers.")
                    .formatted(Formatting.GRAY);
        }
        var text = Text.literal("=== Portal tiers for " + pr.minecraftUsername + " ===")
                .formatted(Formatting.GOLD);
        for (Map.Entry<String, String> e : pr.ranks.entrySet()) {
            GameMode mode = GameMode.fromKey(e.getKey());
            String label = mode != null ? mode.displayName() : e.getKey();
            int color = PortalConfig.get().getTierColor(e.getValue());
            text.append(Text.literal("\n" + label + ": ").formatted(Formatting.GRAY))
                    .append(Text.literal(e.getValue()).styled(s -> s.withColor(color)));
        }
        text.append(Text.literal("\nRegion: ").formatted(Formatting.GRAY))
                .append(Text.literal(pr.region == null ? "?" : pr.region).formatted(Formatting.WHITE));
        text.append(Text.literal("\nOverall: ").formatted(Formatting.GRAY))
                .append(Text.literal((pr.overallTier == null ? "?" : pr.overallTier)
                        + " (" + pr.overallPoints + " pts)").formatted(Formatting.WHITE));
        return text;
    }
}
