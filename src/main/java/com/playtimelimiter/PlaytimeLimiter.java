package com.playtimelimiter;

// Fabric API implement onInitialize method
import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier; // Util for resources identification

// Implement arguments support for commands
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;

// Commands hooks support
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource; // Source of command execution
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text; // Modern library for creation of text messages
import net.minecraft.command.argument.EntityArgumentType; // Argument for players with auto-completion support

// Basic minecraft logger
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Import Map interface with used HashMap structure similar to Dictionary
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

// Player unique id
import java.util.UUID;
import com.mojang.authlib.GameProfile;

// Import all variables, methods into our scope
import static net.minecraft.server.command.CommandManager.*;

public class PlaytimeLimiter implements ModInitializer {
    public static final String MOD_ID = "playtimelimiter";

    // This logger is used to write text to the console and the log file.
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Map<UUID, Integer> timersRegistry = new HashMap<>();

    @Override
    // This code runs as soon as Minecraft is in a mod-load-ready state.
    public void onInitialize() {
        LOGGER.info("Mod " + MOD_ID + " initialized successfully!"); // Notify about initilization

        // Register /timer command
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                literal("timer").requires(source -> source.hasPermissionLevel(2))
                    // Syntax /timer set <player> <seconds>
                    .then(literal("set").then(argument("player", EntityArgumentType.player()).then(argument("seconds", IntegerArgumentType.integer(1))
                        .executes(context -> {
                            // Get source of one who used a command
                            ServerCommandSource source = context.getSource();

                            // Extract player entity and data from command context
                            ServerPlayerEntity player = EntityArgumentType.getPlayer(context, "player");
                            int timeSeconds = IntegerArgumentType.getInteger(context, "seconds");
                            String playerName = player.getName().getString();
                            
                            // After get target uuid
                            UUID playerUuid = player.getUuid();
                            
                            // Add this data to registry
                            timersRegistry.put(playerUuid, timeSeconds);

                            // Notify about created timer
                            source.sendMessage(Text.literal("§aCreated timer to player " + playerName + " for " + timeSeconds + " seconds."));
                            return 1; // Command successed
                        })
                    )))
                    // Syntax /timer remove <player>
                    .then(literal("remove").then(argument("player", EntityArgumentType.player())
                        .executes(context -> {
                            // Get source of one who used a command
                            ServerCommandSource source = context.getSource();

                            // Extract player entity from command context
                            ServerPlayerEntity player = EntityArgumentType.getPlayer(context, "player");
                            String playerName = player.getName().getString();
                            
                            // After get target uuid
                            UUID playerUuid = player.getUuid();

                            // If player with current uuid contains in registry remove it
                            if (timersRegistry.containsKey(playerUuid)) {
                                timersRegistry.remove(playerUuid);
                                source.sendMessage(Text.literal("§eRemoved timer from player " + playerName + "."));
                            } else {
                                source.sendMessage(Text.literal("§cTimer not attached to player " + playerName + "."));
                                return 0;
                            }

                            return 1;
                        })
                    ))
                    // Syntax /timer list
                    .then(literal("list").executes(context -> {
                        // Get source of one who used a command
                        ServerCommandSource source = context.getSource();

                        // In case of no registered players, dispatch it
                        if (timersRegistry.isEmpty()) {
                            source.sendMessage(Text.literal("§cNo available player with attached timer."));
                        } else {
                            // Use optimized string builder, because list of player can be huge and concatenation can be slow in this case
                            StringBuilder messageBuilder = new StringBuilder("§eActive timers:\n");
                            // Itterate each uuid in key set for display nicks with its playtimes 
                            for (UUID uuid : timersRegistry.keySet()) {
                                // Extract data from saved cache, because player can be offline
                                Optional<GameProfile> profileOpt = source.getServer().getUserCache().getByUuid(uuid);

                                // Get data by player UUID
                                String playerName = profileOpt.isPresent() ? profileOpt.get().getName() : "Unknown";
                                int timeSeconds = timersRegistry.get(uuid);
                                
                                // Construction of line
                                messageBuilder.append("§7- §a").append(playerName).append(": §f").append(timeSeconds).append(" sec.\n");
                            }
                            // Send a complete message to player
                            source.sendMessage(Text.literal(messageBuilder.toString().trim()));
                        }

                        return 1;
                    }))
            );
        });
    }

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }
}