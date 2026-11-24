package net.danh.mythiccregion.core.scheduler;

/**
 * Cross-platform task handle providing unified cancellation and status checks
 * across Paper, Folia, and Spigot environments.
 */
public interface TaskHandle {

    /**
     * Cancels the underlying scheduled task.
     */
    void cancel();

    /**
     * Checks if the scheduled task has been cancelled.
     *
     * @return true if cancelled, false otherwise.
     */
    boolean isCancelled();
}
