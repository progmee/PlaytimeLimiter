package com.playtimelimiter;

// Import json library for formatting
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

// Fabric API loader
import net.fabricmc.loader.api.FabricLoader;

// IO libs
import java.nio.file.Path;
import java.io.IOException;
import java.nio.file.Files;
import java.util.stream.Collectors;

// Data structures
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles configuration loading and saving for player playtime limits.
 * Serializes player UUIDs and their respective limits into a JSON file.
 */
public class TimerConfig {
    // Create GSON object with readable format
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_NAME = "timers.json";

    // Create path variable to store path to config
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir()
        .resolve(PlaytimeLimiter.MOD_ID)
        .resolve(CONFIG_NAME);

    /**
     * Saves the current active timers registry to the JSON configuration file.
     * Converts player UUIDs to strings for proper JSON serialization.
     * 
     * @param timersRegistry The map containing player UUIDs and their allowed playtime in seconds
     */
    public static void save(Map<UUID, Integer> timersRegistry) {
        try {
            // Create parent directory ./config/{MOD_ID}, if folder not exists
            if (!Files.exists(CONFIG_PATH.getParent())) {
                Files.createDirectories(CONFIG_PATH.getParent());
            }

            // Convert UUID type into string
            Map<String, Integer> serializableMap = timersRegistry.entrySet().stream()
                .collect(Collectors.toMap(entry -> entry.getKey().toString(), Map.Entry::getValue));

            // Save config to file
            Files.writeString(CONFIG_PATH, GSON.toJson(serializableMap));
        } catch (IOException exception) {
            PlaytimeLimiter.LOGGER.error("Failed to save playtime timers config!", exception);
        }
    }

    /**
     * Loads the timers registry from the JSON configuration file.
     * Converts serialized string keys back into UUID objects.
     * 
     * @return A map of player UUIDs and their playtime limits, or an empty map if loading fails or the file doesn't exist
     */
    public static Map<UUID, Integer> load() {
        Map<UUID, Integer> timersRegistry = new HashMap<>();

        // Check that file exists, if not return default value
        if (!Files.exists(CONFIG_PATH)) {
            return timersRegistry;
        }

        try 
        {
            // Read data from config file as a string
            String content = Files.readString(CONFIG_PATH);

            // Define types for json reader to get
            Type type = new TypeToken<Map<String, Integer>>(){}.getType();
            Map<String, Integer> serializableMap = GSON.fromJson(content, type);

            // Load data to timers registry and convert String to UUID type
            timersRegistry = (serializableMap != null) ? 
                serializableMap.entrySet().stream()
                    .collect(Collectors.toMap(e -> UUID.fromString(e.getKey()), Map.Entry::getValue)) 
                : new HashMap<>();
        } catch (Exception exception) {
            PlaytimeLimiter.LOGGER.error("Failed to load playtime timers config!", exception);
        }

        return timersRegistry;
    }
}