package com.playtimelimiter;

// Player entity
import net.minecraft.server.network.ServerPlayerEntity;

// Text type and formatting
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

// Title and subtitle support
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;

/**
 * Handles visual feedback notifications for players via Action Bar and center Screen Titles.
 * Supports direct text formatting and dynamic color scaling based on remaining time.
 */
public class PlaytimeNotifier {

    /**
     * Displays visual feedback to the player via Action Bar and center Screen Titles.
     * 
     * @param player         The target player
     * @param timeSeconds    Remaining time in seconds
     * @param maxTimeSeconds Total session limit in seconds
     */
    public static void displayNotification(ServerPlayerEntity player, int timeSeconds, int maxTimeSeconds) {
        String timeFormatted = formatTime(timeSeconds);

        // Calculate remaining time percentage for dynamic color scaling
        double percentage = maxTimeSeconds > 0 ? (double) timeSeconds / maxTimeSeconds : 1.0;

        // Determine action bar color based on remaining time percentage:
        // > 50% -> Green, > 10% -> Yellow, <= 10% -> Red
        Formatting barColor;
        if (percentage > 0.5) {
            barColor = Formatting.GREEN;
        } else if (percentage > 0.1) {
            barColor = Formatting.YELLOW;
        } else {
            barColor = Formatting.RED;
        }

        // Send a persistent timer update to the action bar (above inventory)
        player.sendMessage(Text.literal(timeFormatted).styled(style -> style.withColor(barColor)), true);

        // Trigger major milestone alerts in the center of the screen using Text.literal
        if (timeSeconds == 60) {
            player.networkHandler.sendPacket(new TitleS2CPacket(
                Text.literal("1 minute left!").styled(style -> style.withColor(Formatting.WHITE))
            ));
        }
        else if (timeSeconds == 15) {
            player.networkHandler.sendPacket(new TitleS2CPacket(
                Text.literal("15 seconds left!").styled(style -> style.withColor(Formatting.YELLOW))
            ));
        }
        else if (timeSeconds > 0 && timeSeconds <= 10) {
            // Intense countdown from 10 to 1 second (yellow, turning red with bold in the last 3 seconds)
            Formatting color = timeSeconds <= 3 ? Formatting.RED : Formatting.YELLOW;
            boolean bold = timeSeconds <= 3;
            
            Text countdownText = Text.literal(timeSeconds + " seconds left!")
                .styled(style -> style.withColor(color).withBold(bold));
                
            player.networkHandler.sendPacket(new TitleS2CPacket(countdownText));
        }
        else if (timeSeconds <= 0) {
            Text timeIsUpText = Text.literal("Time is up.")
                .styled(style -> style.withColor(Formatting.DARK_RED).withBold(true));
            player.networkHandler.sendPacket(new TitleS2CPacket(timeIsUpText));
        }
    }

    /**
     * Formats seconds into HH:MM:SS or MM:SS depending on the duration.
     * 
     * @param timeSeconds Remaining time in seconds
     * @return Formatted time string
     */
    private static String formatTime(int timeSeconds) {
        // Prevent negative values
        if (timeSeconds < 0) timeSeconds = 0;

        int hours = timeSeconds / 3600;
        int minutes = (timeSeconds % 3600) / 60;
        int seconds = timeSeconds % 60;

        // Default format for under an hour (MM:SS)
        String timeFormatted = String.format("%02d:%02d", minutes, seconds);
        
        // Switch to H:MM:SS format if time exceeds 1 hour
        if (hours > 0) {
            timeFormatted = String.format("%d:%02d:%02d", hours, minutes, seconds); 
        }

        return timeFormatted;
    }
}