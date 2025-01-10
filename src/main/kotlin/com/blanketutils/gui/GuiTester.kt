package com.blanketutils.gui

import kotlinx.coroutines.runBlocking
import net.minecraft.component.DataComponentTypes
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import org.slf4j.LoggerFactory

class GuiTester {
    private val logger = LoggerFactory.getLogger("GuiTester")

    // Test GUI implementation for testing
    private data class TestGuiData(
        override val guiId: String = "test_gui",
        override val modId: String = "test_mod",
        override val title: String = "Test GUI",
        override val rows: Int = 3,
        override val allowPlayerInventory: Boolean = true
    ) : GuiData

    private class TestGuiHandler : GuiHandler {
        override val guiData = TestGuiData()

        override fun createLayout(player: ServerPlayerEntity, customData: Map<String, Any>): List<ItemStack> {
            return List(guiData.rows * 9) { ItemStack.EMPTY }
        }

        override fun handleInteraction(context: InteractionContext) {
            // Test interaction handler
        }

        override fun handleClose(player: ServerPlayerEntity, inventory: Inventory) {
            // Test close handler
        }
    }

    fun testGuiRegistration(): Boolean = runBlocking {
        try {
            val handler = TestGuiHandler()

            // Test registration
            GuiRegistry.registerGui(handler)

            // Test unregistration
            GuiRegistry.unregisterGui(handler.guiData.guiId)

            true
        } catch (e: Exception) {
            logger.error("GUI registration test failed", e)
            false
        }
    }

    fun testGuiUtils(): Boolean = runBlocking {
        try {
            // Test button creation
            val button = GuiUtils.createButton(
                ItemStack(Items.STONE),
                "Test Button",
                listOf("Line 1", "Line 2")
            )

            // Verify button properties
            val nameComponent = button.get(DataComponentTypes.ITEM_NAME)
            val hasName = nameComponent != null
            val name = nameComponent?.string ?: ""

            // Test text formatting strip
            val strippedText = GuiUtils.stripFormatting("§aColored§r Text")
            val textStripped = strippedText == "Colored Text"

            hasName && name.contains("Test Button") && textStripped
        } catch (e: Exception) {
            logger.error("GUI utils test failed", e)
            false
        }
    }

    fun testGuiDataStructures(): Boolean = runBlocking {
        try {
            val testData = TestGuiData()

            // Test data structure integrity
            val validRows = testData.rows in 1..6
            val validId = testData.guiId.isNotBlank()
            val validModId = testData.modId.isNotBlank()
            val validTitle = testData.title.isNotBlank()

            validRows && validId && validModId && validTitle
        } catch (e: Exception) {
            logger.error("GUI data structures test failed", e)
            false
        }
    }

    companion object {
        fun runAllTests(): Map<String, Boolean> {
            val tester = GuiTester()

            return mapOf(
                "registration" to tester.testGuiRegistration(),
                "utils" to tester.testGuiUtils(),
                "dataStructures" to tester.testGuiDataStructures()
            ).also {
                // Cleanup after tests
                GuiRegistry.cleanup()
            }
        }
    }
}