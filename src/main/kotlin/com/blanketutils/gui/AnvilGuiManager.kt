package com.blanketutils.gui

import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.LoreComponent
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.inventory.Inventory
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.screen.*
import net.minecraft.screen.slot.Slot
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.ClickType
import org.slf4j.LoggerFactory

/**
 * A manager for creating customizable anvil GUIs
 */
object AnvilGuiManager {
    private val logger = LoggerFactory.getLogger(AnvilGuiManager::class.java)

    /**
     * Opens a modular anvil GUI for the specified player
     *
     * @param player The player to open the GUI for
     * @param id A unique identifier for this GUI
     * @param title The title displayed in the GUI
     * @param initialText The initial text to show in the text field (can be empty)
     * @param layout A map of slot indices to ItemStacks
     * @param onInteract Handler for when slots are clicked
     * @param onTextChange Handler for when the text field is modified
     * @param onClose Handler for when the GUI is closed
     */
    fun openAnvilGui(
        player: ServerPlayerEntity,
        id: String,
        title: String,
        initialText: String = "",
        layout: Map<Int, ItemStack> = emptyMap(),
        onInteract: ((AnvilInteractionContext) -> Unit)? = null,
        onTextChange: ((String) -> Unit)? = null,
        onClose: ((Inventory) -> Unit)? = null
    ) {
        val factory = SimpleNamedScreenHandlerFactory(
            { syncId, inv, _ ->
                ModularAnvilScreenHandler(
                    syncId, inv, id,
                    initialText, layout,
                    onInteract, onTextChange, onClose
                )
            },
            Text.literal(title)
        )
        player.openHandledScreen(factory)
    }

    /**
     * Simplified method for opening a search anvil GUI (for backward compatibility)
     */
    fun openSearchGui(
        player: ServerPlayerEntity,
        id: String,
        title: String,
        onSearch: (String) -> Unit,
        cancelAction: (() -> Unit)? = null
    ) {
        // Create a layout with appropriate items
        val layout = mutableMapOf<Int, ItemStack>()

        // Add a cancel button if cancelAction is provided
        if (cancelAction != null) {
            val cancelButton = ItemStack(Items.BARRIER)
            cancelButton.setCustomName(Text.literal("Cancel"))
            CustomGui.setItemLore(cancelButton, listOf(
                "§cClick to cancel and go back",
                "§7No search will be performed"
            ))
            layout[1] = cancelButton  // Put cancel button in right input slot
        }

        // Open the anvil GUI
        openAnvilGui(
            player = player,
            id = id,
            title = title,
            initialText = "",
            layout = layout,
            onInteract = { context ->
                // Handle cancel button click
                if (context.slotIndex == 1 && cancelAction != null) {
                    cancelAction.invoke()
                    context.player.closeHandledScreen()
                }
                // Handle result click (perform search)
                else if (context.slotIndex == 2) {
                    onSearch.invoke(context.handler.currentText)
                    context.player.closeHandledScreen()
                }
            },
            onTextChange = { text ->
                // You could potentially add text validation here if needed
            }
        )
    }
}

/**
 * Context object provided when a slot is clicked in the anvil GUI
 */
data class AnvilInteractionContext(
    val slotIndex: Int,
    val clickType: ClickType,
    val button: Int,
    val clickedStack: ItemStack,
    val player: ServerPlayerEntity,
    val handler: ModularAnvilScreenHandler
)

/**
 * A custom anvil screen handler that provides modular functionality
 */
class ModularAnvilScreenHandler(
    syncId: Int,
    playerInventory: PlayerInventory,
    private val id: String,
    initialText: String,
    layout: Map<Int, ItemStack>,
    private val onInteract: ((AnvilInteractionContext) -> Unit)?,
    private val onTextChange: ((String) -> Unit)?,
    private val onClose: ((Inventory) -> Unit)?
) : AnvilScreenHandler(syncId, playerInventory, ScreenHandlerContext.EMPTY) {

    private val guiInventory = SimpleInventory(3)  // Anvil has 3 slots
    var currentText: String = initialText
        private set

    init {
        // Set up the input left slot (text field trigger)
        val textFieldTrigger = ItemStack(Items.PAPER).apply {
            setCustomName(Text.literal(""))
        }
        input.setStack(0, textFieldTrigger)

        // Apply custom layout
        layout.forEach { (slot, item) ->
            if (slot in 0..2) {  // Only slots 0, 1, 2 are valid in anvil
                input.setStack(if (slot < 2) slot else 0, item) // 0, 1 go in input, 2 will be handled by updateResult
            }
        }

        // Initialize text field
        setNewItemName(initialText)
    }

    override fun setNewItemName(newName: String): Boolean {
        val result = super.setNewItemName(newName)
        currentText = newName
        onTextChange?.invoke(newName)
        updateResult()
        return result
    }

    override fun updateResult() {
        // If we have a custom slot 2 item in the layout, use it
        // Otherwise, create a default result item based on the text
        if (currentText.isNotEmpty()) {
            val resultItem = ItemStack(Items.PAPER)
            resultItem.setCustomName(Text.literal("Submit: $currentText"))

            // Add helpful lore
            val lore = LoreComponent(listOf(
                Text.literal("§aClick to submit"),
                Text.literal("§7Press ESC to cancel")
            ))
            resultItem.set(DataComponentTypes.LORE, lore)

            // Make it stand out
            resultItem.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true)

            output.setStack(0, resultItem)
        } else {
            output.setStack(0, ItemStack.EMPTY)
        }
    }

    override fun getLevelCost(): Int = 0  // No XP cost

    override fun onSlotClick(slotIndex: Int, button: Int, actionType: SlotActionType, player: PlayerEntity) {
        // Get the "true" anvil slot index (0=left input, 1=right input, 2=output)
        val anvilSlot = when {
            slotIndex == 0 -> 0  // Left input
            slotIndex == 1 -> 1  // Right input
            slotIndex == 2 -> 2  // Output
            else -> -1            // Not an anvil slot
        }

        // Handle clicks on anvil slots
        if (anvilSlot in 0..2 && player is ServerPlayerEntity) {
            val clickType = if (button == 0) ClickType.LEFT else ClickType.RIGHT
            val stack = when (anvilSlot) {
                0 -> input.getStack(0)
                1 -> input.getStack(1)
                2 -> output.getStack(0)
                else -> ItemStack.EMPTY
            }

            val context = AnvilInteractionContext(anvilSlot, clickType, button, stack, player, this)
            onInteract?.invoke(context)

            // Don't allow item pickup
            return
        }

        // For non-anvil slots, use default behavior
        super.onSlotClick(slotIndex, button, actionType, player)
    }

    override fun quickMove(player: PlayerEntity, index: Int): ItemStack = ItemStack.EMPTY  // Prevent shift-click

    override fun canUse(player: PlayerEntity): Boolean = true

    override fun onClosed(player: PlayerEntity) {
        super.onClosed(player)
        onClose?.invoke(input)
    }
}