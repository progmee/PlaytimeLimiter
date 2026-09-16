package com.playtimelimiter;

// Fabric API implement onInitialize method
import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier; // Util for resources identification

// Implement arguments support for commands
import com.mojang.brigadier.arguments.IntegerArgumentType;

// Commands hooks support
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.command.ServerCommandSource; // Source of command execution
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text; // Modern library for creation of text messages
import net.minecraft.util.Formatting;
import net.minecraft.command.argument.EntityArgumentType; // Argument for players with auto-completion support

// Player connection lib
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

// Basic minecraft logger
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Import data structures for collections and maps
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// Player unique id
import java.util.UUID;
import com.mojang.authlib.GameProfile;

// Import all variables, methods into our scope
import static net.minecraft.server.command.CommandManager.*;

/**
 * Main initializer class for the PlaytimeLimiter mod.
 * Handles server tick events, player join/disconnect tracking, 
 * and admin command registration for managing session time limits and pauses.
 */
public class PlaytimeLimiter implements ModInitializer {
    public static final String MOD_ID = "playtimelimiter";

    // This logger is used to write text to the console and the log file.
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // Store total ticks for server loop tracking
    public static int tickCounter = 0;

    // Save timers, join times, and pause states in RAM
    public static Map<UUID, Integer> timersRegistry;
    public static Map<UUID, Integer> sessionJoinRegistry = new HashMap<>();
    public static Set<UUID> pausedRegistry = new HashSet<>();
    public static Map<UUID, Integer> pauseStartRegistry = new HashMap<>();

    @Override
    /**
     * Runs as soon as Minecraft is in a mod-load-ready state.
     * Loads configurations, registers tick listeners, connection events, and administrative commands.
     */
    public void onInitialize() {
        LOGGER.info("Mod " + MOD_ID + " initialized successfully!");
        timersRegistry = TimerConfig.load(); // Load config from disk

        // Main server tick loop running every second (20 ticks)
        ServerTickEvents.END_SERVER_TICK.register((server) -> {
            tickCounter++; // Update tick counter each tick

            if (tickCounter >= 20) {
                tickCounter = 0;

                int serverSeconds = server.getTicks() / 20;
                for (UUID uuid : timersRegistry.keySet()) {
                    // Ignore offline or uninitialized players
                    if (!sessionJoinRegistry.containsKey(uuid)) continue;
                    ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
                    if (player == null) continue; // Safety check if player is missing

                    int timerSeconds = timersRegistry.get(uuid);
                    int sessionJoinSeconds = sessionJoinRegistry.get(uuid);

                    // Handle paused players: display pause status and skip time progression
                    if (pausedRegistry.contains(uuid)) {
                        player.sendMessage(Text.literal("PAUSED").styled(style -> style.withColor(Formatting.AQUA)), true);
                        continue; 
                    }

                    int elapsedTime = serverSeconds - sessionJoinSeconds; // Save elapsed time for a player
                    int remainingTime = timerSeconds - elapsedTime; // Save remaining time for a player

                    // Send notification to player with remaining time and total session limit
                    PlaytimeNotifier.displayNotification(player, remainingTime, timerSeconds);

                    // Check if playtime limit for this session has expired
                    if (elapsedTime >= timerSeconds) {
                        player.networkHandler.disconnect(Text.literal("§cYour playtime limit for this session has expired!"));
                    }
                }
            }
        });

        // Register event when a player joins the server
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            UUID uuid = handler.getPlayer().getUuid(); 

            // In minecraft 20 ticks equal to 1 second of virtual time
            int sessionSeconds = server.getTicks() / 20;
            sessionJoinRegistry.put(uuid, sessionSeconds);
        });

        // Register event when a player disconnects from the server
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID uuid = handler.getPlayer().getUuid(); 

            if (sessionJoinRegistry.containsKey(uuid)) {
                sessionJoinRegistry.remove(uuid);
            }
            // Clean up pause states on disconnect to prevent memory leaks
            pausedRegistry.remove(uuid);
            pauseStartRegistry.remove(uuid);
        });

        // Register admin /timer commands hierarchy
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                literal("timer").requires(source -> source.hasPermissionLevel(2))
                    
                    // Syntax: /timer set <player> <seconds>
                    .then(literal("set").then(argument("player", EntityArgumentType.player()).then(argument("seconds", IntegerArgumentType.integer(1))
                        .executes(context -> {
                            ServerCommandSource source = context.getSource();
                            ServerPlayerEntity player = EntityArgumentType.getPlayer(context, "player");
                            int timeSeconds = IntegerArgumentType.getInteger(context, "seconds");
                            String playerName = player.getName().getString();
                            
                            UUID playerUuid = player.getUuid();
                            
                            // Update timer in registry and reset session join time
                            timersRegistry.put(playerUuid, timeSeconds);
                            int sessionSeconds = source.getServer().getTicks() / 20;
                            sessionJoinRegistry.put(playerUuid, sessionSeconds);

                            source.sendMessage(Text.literal("Created timer for player " + playerName + " for " + timeSeconds + " seconds.").styled(style -> style.withColor(Formatting.GREEN)));
                            TimerConfig.save(timersRegistry);
                            return 1; 
                        })
                    )))

                    // Syntax: /timer remove <player>
                    .then(literal("remove").then(argument("player", EntityArgumentType.player())
                        .executes(context -> {
                            ServerCommandSource source = context.getSource();
                            ServerPlayerEntity player = EntityArgumentType.getPlayer(context, "player");
                            String playerName = player.getName().getString();
                            
                            UUID playerUuid = player.getUuid();

                            if (timersRegistry.containsKey(playerUuid)) {
                                timersRegistry.remove(playerUuid);
                                pausedRegistry.remove(playerUuid);
                                pauseStartRegistry.remove(playerUuid);
                                source.sendMessage(Text.literal("Removed timer from player " + playerName + ".").styled(style -> style.withColor(Formatting.YELLOW)));
                                TimerConfig.save(timersRegistry);
                            } else {
                                source.sendMessage(Text.literal("Timer not attached to player " + playerName + ".").styled(style -> style.withColor(Formatting.RED)));
                                return 0;
                            }

                            return 1;
                        })
                    ))

                    // Syntax: /timer pause <player>
                    .then(literal("pause").then(argument("player", EntityArgumentType.player())
                        .executes(context -> {
                            ServerCommandSource source = context.getSource();
                            ServerPlayerEntity player = EntityArgumentType.getPlayer(context, "player");
                            UUID playerUuid = player.getUuid();
                            String playerName = player.getName().getString();

                            if (pausedRegistry.contains(playerUuid)) {
                                source.sendMessage(Text.literal("Player " + playerName + " is already paused!").styled(style -> style.withColor(Formatting.RED)));
                                return 0;
                            }

                            // Freeze player by adding to pause registry and tracking start time
                            pausedRegistry.add(playerUuid);
                            int serverSeconds = source.getServer().getTicks() / 20;
                            pauseStartRegistry.put(playerUuid, serverSeconds);

                            source.sendMessage(Text.literal("Paused timer for player " + playerName + ".").styled(style -> style.withColor(Formatting.YELLOW)));
                            return 1;
                        })
                    ))

                    // Syntax: /timer unpause <player>
                    .then(literal("unpause").then(argument("player", EntityArgumentType.player())
                        .executes(context -> {
                            ServerCommandSource source = context.getSource();
                            ServerPlayerEntity player = EntityArgumentType.getPlayer(context, "player");
                            UUID playerUuid = player.getUuid();
                            String playerName = player.getName().getString();

                            if (!pausedRegistry.contains(playerUuid)) {
                                source.sendMessage(Text.literal("Player " + playerName + " is not paused!").styled(style -> style.withColor(Formatting.RED)));
                                return 0;
                            }

                            // Calculate paused duration and shift session join time forward
                            int serverSeconds = source.getServer().getTicks() / 20;
                            int pauseStartSeconds = pauseStartRegistry.getOrDefault(playerUuid, serverSeconds);
                            int pausedDuration = serverSeconds - pauseStartSeconds;

                            if (sessionJoinRegistry.containsKey(playerUuid)) {
                                int currentJoin = sessionJoinRegistry.get(playerUuid);
                                sessionJoinRegistry.put(playerUuid, currentJoin + pausedDuration);
                            }

                            // Clean up pause tracking entries
                            pausedRegistry.remove(playerUuid);
                            pauseStartRegistry.remove(playerUuid);

                            source.sendMessage(Text.literal("Resumed timer for player " + playerName + ".").styled(style -> style.withColor(Formatting.GREEN)));
                            return 1;
                        })
                    ))

                    // Syntax: /timer list
                    .then(literal("list").executes(context -> {
                        ServerCommandSource source = context.getSource();

                        if (timersRegistry.isEmpty()) {
                            source.sendMessage(Text.literal("No available players with an attached timer.").styled(style -> style.withColor(Formatting.RED)));
                        } else {
                            StringBuilder messageBuilder = new StringBuilder("§eActive timers:\n");
                            for (UUID uuid : timersRegistry.keySet()) {
                                Optional<GameProfile> profileOpt = source.getServer().getUserCache().getByUuid(uuid);

                                String playerName = profileOpt.isPresent() ? profileOpt.get().getName() : "Unknown";
                                int timeSeconds = timersRegistry.get(uuid);
                                boolean isPaused = pausedRegistry.contains(uuid);

                                messageBuilder.append("§7- §a").append(playerName).append(": §f").append(timeSeconds).append(" sec.")
                                              .append(isPaused ? " §b[PAUSED]" : "").append("\n");
                            }
                            source.sendMessage(Text.literal(messageBuilder.toString().trim()));
                        }

                        return 1;
                    }))
            );
        });
    }

    /**
     * Helper method to generate namespaced identifiers for the mod.
     * 
     * @param path The resource path string
     * @return A namespaced Identifier object
     */
    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }
}