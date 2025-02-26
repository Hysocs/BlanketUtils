package com.blanketutils.scheduling

import org.slf4j.LoggerFactory

/**
 * Extremely simplified test class for the SchedulerManager functionality.
 * These tests only check if the API methods can be called without throwing exceptions.
 */
class SchedulerTester {
    private val logger = LoggerFactory.getLogger("BlanketUtils-SchedulerTester")

    /**
     * Tests that a scheduler can be created without errors
     */
    fun testBasicCreation(): Boolean {
        return try {
            // Just test that we can call the createScheduler method without errors
            val schedulerId = "blanketutils-test-creation"
            SchedulerManager.createScheduler(schedulerId)
            // Try to clean up
            try {
                SchedulerManager.shutdown(schedulerId)
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
            true
        } catch (e: Exception) {
            logger.error("Error in basic creation test: ${e.message}")
            false
        }
    }

    /**
     * Tests that the shutdown method can be called without errors
     */
    fun testBasicShutdown(): Boolean {
        return try {
            // Just test that we can call shutdown without errors
            val schedulerId = "blanketutils-test-shutdown"
            // Create a scheduler first
            try {
                SchedulerManager.createScheduler(schedulerId)
            } catch (e: Exception) {
                // Ignore creation errors
                return false
            }

            // Test shutdown
            SchedulerManager.shutdown(schedulerId)
            true
        } catch (e: Exception) {
            logger.error("Error in basic shutdown test: ${e.message}")
            false
        }
    }

    /**
     * Tests that the shutdownAll method can be called without errors
     */
    fun testBasicShutdownAll(): Boolean {
        return try {
            // Just test that we can call shutdownAll without errors
            SchedulerManager.shutdownAll()
            true
        } catch (e: Exception) {
            logger.error("Error in basic shutdownAll test: ${e.message}")
            false
        }
    }

    companion object {
        fun runAllTests(): Map<String, Boolean> {
            val tester = SchedulerTester()

            // Run very basic API tests
            return mapOf(
                "basicCreation" to tester.testBasicCreation(),
                "basicShutdown" to tester.testBasicShutdown(),
                "basicShutdownAll" to tester.testBasicShutdownAll()
            )
        }
    }
}