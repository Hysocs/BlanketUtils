package com.blanketutils.scheduling

import com.blanketutils.utils.logDebug
import net.minecraft.server.MinecraftServer
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap

/**
 * Centralized scheduler system for managing scheduled tasks across multiple mods.
 * This allows for easy creation, tracking, and shutdown of scheduled tasks.
 */
object SchedulerManager {
    private val logger = LoggerFactory.getLogger("BlanketUtils-SchedulerManager")
    private val schedulers = ConcurrentHashMap<String, ScheduledExecutorService>()
    private val tasks = ConcurrentHashMap<String, MutableList<ScheduledFuture<*>>>()

    /**
     * Creates a new scheduler with the given ID if it doesn't already exist.
     * The scheduler ID should include the mod ID to avoid conflicts.
     *
     * @param id Unique identifier for the scheduler (use format "modid-purpose")
     * @return The ScheduledExecutorService
     */
    fun createScheduler(id: String): ScheduledExecutorService {
        return schedulers.computeIfAbsent(id) {
            logDebug("[SCHEDULER] Creating new scheduler: $id", "blanketutils")
            Executors.newSingleThreadScheduledExecutor { r ->
                val thread = Thread(r, "BlanketUtils-Scheduler-$id")
                thread.isDaemon = true
                thread
            }
        }
    }

    /**
     * Schedules a task to run periodically with the given initial delay and period.
     *
     * @param id Scheduler identifier (use format "modid-purpose")
     * @param server MinecraftServer instance
     * @param initialDelay Initial delay before first execution
     * @param period Period between successive executions
     * @param unit Time unit for delay and period
     * @param task Task to execute
     * @return The ScheduledFuture representing the scheduled task
     */
    fun scheduleAtFixedRate(
        id: String,
        server: MinecraftServer,
        initialDelay: Long,
        period: Long,
        unit: TimeUnit,
        task: () -> Unit
    ): ScheduledFuture<*> {
        val scheduler = createScheduler(id)
        val future = scheduler.scheduleAtFixedRate({
            try {
                server.executeSync {
                    task()
                }
            } catch (e: Exception) {
                logger.error("Error executing scheduled task in $id: ${e.message}", e)
            }
        }, initialDelay, period, unit)

        tasks.computeIfAbsent(id) { ConcurrentHashMap.newKeySet<ScheduledFuture<*>>().toMutableList() }.add(future)
        return future
    }

    /**
     * Schedules a one-time task with the given delay.
     *
     * @param id Scheduler identifier (use format "modid-purpose")
     * @param server MinecraftServer instance
     * @param delay Delay before execution
     * @param unit Time unit for delay
     * @param task Task to execute
     * @return The ScheduledFuture representing the scheduled task
     */
    fun schedule(
        id: String,
        server: MinecraftServer,
        delay: Long,
        unit: TimeUnit,
        task: () -> Unit
    ): ScheduledFuture<*> {
        val scheduler = createScheduler(id)
        val future = scheduler.schedule({
            try {
                server.executeSync {
                    task()
                }
            } catch (e: Exception) {
                logger.error("Error executing scheduled task in $id: ${e.message}", e)
            }
        }, delay, unit)

        tasks.computeIfAbsent(id) { ConcurrentHashMap.newKeySet<ScheduledFuture<*>>().toMutableList() }.add(future)
        return future
    }

    /**
     * Cancels all tasks for a specific scheduler ID.
     *
     * @param id Scheduler identifier
     * @param mayInterruptIfRunning Whether to interrupt running tasks
     */
    fun cancelTasks(id: String, mayInterruptIfRunning: Boolean = false) {
        tasks[id]?.forEach { future ->
            future.cancel(mayInterruptIfRunning)
        }
        tasks[id]?.clear()
        logDebug("[SCHEDULER] Cancelled all tasks for scheduler: $id", "blanketutils")
    }

    /**
     * Shuts down a specific scheduler.
     *
     * @param id Scheduler identifier
     */
    fun shutdown(id: String) {
        cancelTasks(id)
        schedulers[id]?.shutdown()
        schedulers.remove(id)
        logDebug("[SCHEDULER] Shut down scheduler: $id", "blanketutils")
    }

    /**
     * Shuts down all schedulers.
     */
    fun shutdownAll() {
        val allIds = schedulers.keys().toList()
        allIds.forEach { id ->
            shutdown(id)
        }
        logDebug("[SCHEDULER] Shut down all schedulers", "blanketutils")
    }

    /**
     * Gets stats about all active schedulers.
     *
     * @return Map of scheduler IDs to the number of active tasks
     */
    fun getStats(): Map<String, Int> {
        return tasks.mapValues { it.value.size }
    }
}