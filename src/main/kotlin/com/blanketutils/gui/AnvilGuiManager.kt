package com.blanketutils.gui

import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.inventory.Inventory
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.screen.*
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.ClickType
import org.slf4j.LoggerFactory

/**
 * A fully modular anvil GUI manager
 */
object AnvilGuiManager {
    private val logger = LoggerFactory.getLogger(AnvilGuiManager::class.java)

    /**
     * Opens a fully modular anvil GUI
     *
     * @param player The player to open the GUI for
     * @param id A unique identifier for this GUI
     * @param title The title displayed in the GUI
     * @param initialText The initial text in the input field
     * @param leftItem Item to place in left slot (slot 0)
     * @param rightItem Item to place in right slot (slot 1)
     * @param resultItem Item to place in result slot (slot 2)
     * @param onLeftClick Handler for left slot clicks
     * @param onRightClick Handler for right slot clicks
     * @param onResultClick Handler for result slot clicks
     * @param onTextChange Handler for when the text field is modified
     * @param onClose Handler for when the GUI is closed
     */
    fun openAnvilGui(
        player: ServerPlayerEntity,
        id: String,
        title: String,
        initialText: String = "",
        leftItem: ItemStack? = null,
        rightItem: ItemStack? = null,
        resultItem: ItemStack? = null,
        onLeftClick: ((AnvilInteractionContext) -> Unit)? = null,
        onRightClick: ((AnvilInteractionContext) -> Unit)? = null,
        onResultClick: ((AnvilInteractionContext) -> Unit)? = null,
        onTextChange: ((String) -> Unit)? = null,
        onClose: ((Inventory) -> Unit)? = null
    ) {
        val factory = SimpleNamedScreenHandlerFactory(
            { syncId, inv, _ ->
                FullyModularAnvilScreenHandler(
                    syncId, inv, id,
                    initialText,
                    leftItem, rightItem, resultItem,
                    onLeftClick, onRightClick, onResultClick,
                    onTextChange, onClose
                )
            },
            Text.literal(title)
        )
        player.openHandledScreen(factory)
    }

    /**
     * Alternate version with a map-based layout and unified click handler
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
        openAnvilGui(
            player = player,
            id = id,
            title = title,
            initialText = initialText,
            leftItem = layout[0],
            rightItem = layout[1],
            resultItem = layout[2],
            onLeftClick = onInteract?.let { handler -> { context -> handler(context) } },
            onRightClick = onInteract?.let { handler -> { context -> handler(context) } },
            onResultClick = onInteract?.let { handler -> { context -> handler(context) } },
            onTextChange = onTextChange,
            onClose = onClose
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
    val handler: FullyModularAnvilScreenHandler
)

/**
 * A fully modular anvil screen handler where all slots are buttons and text input is independent.
 * This version ensures that non-output slots (left and right) do not carry a custom title,
 * thus preventing auto-filling of the text field with item names.
 */
class FullyModularAnvilScreenHandler(
    syncId: Int,
    playerInventory: PlayerInventory,
    private val id: String,
    initialText: String,
    leftItem: ItemStack?,
    rightItem: ItemStack?,
    resultItem: ItemStack?,
    private val onLeftClick: ((AnvilInteractionContext) -> Unit)?,
    private val onRightClick: ((AnvilInteractionContext) -> Unit)?,
    private val onResultClick: ((AnvilInteractionContext) -> Unit)?,
    private val onTextChange: ((String) -> Unit)?,
    private val onClose: ((Inventory) -> Unit)?
) : AnvilScreenHandler(syncId, playerInventory, ScreenHandlerContext.EMPTY) {

    private val guiInventory = SimpleInventory(3)  // Anvil has 3 slots
    var currentText: String = initialText
        private set
    private var isInitializing = true

    init {
        // Forcibly clear the text field first
        setNewItemName("")

        // Set up the invisible text field trigger
        val hiddenTextTrigger = ItemStack(Items.PAPER).apply {
            setCustomName(Text.literal(""))
        }

        // First place a blank paper in slot 0 to initialize properly
        input.setStack(0, hiddenTextTrigger)

        // Clear text field again
        setNewItemName("")

        // Place items in slots, ensuring non-output items have no title (only lore)
        if (leftItem != null) {
            input.setStack(0, removeTitle(leftItem))
        }

        if (rightItem != null) {
            input.setStack(1, removeTitle(rightItem))
        } else {
            input.setStack(1, ItemStack.EMPTY)
        }

        if (resultItem != null) {
            // Output slot can have a custom title
            output.setStack(0, resultItem)
        } else {
            output.setStack(0, ItemStack.EMPTY)
        }

        // Forcibly clear the text field again after setting items
        // This ensures any item names don't get pulled into the text field
        setNewItemName("")

        // Now set the initial text if it wasn't empty
        if (initialText.isNotEmpty()) {
            setNewItemName(initialText)
        }

        isInitializing = false
    }

    /**
     * Utility function to remove the title (custom name) from non-output items, preserving lore.
     */
    private fun removeTitle(item: ItemStack): ItemStack {
        val copy = item.copy()
        // Instead of setting a null value, set the custom name to an empty Text literal.
        copy.setCustomName(Text.literal(""))
        return copy
    }

    override fun setNewItemName(newName: String): Boolean {
        val result = super.setNewItemName(newName)

        if (!isInitializing || newName != currentText) {
            currentText = newName
            onTextChange?.invoke(newName)
        }

        // Important: Don't call updateResult() here - we want to maintain manual control of the output slot
        return result
    }

    // Override to do nothing - we manage the result item manually
    override fun updateResult() {
        // Do nothing - result slot is controlled by the caller
    }

    override fun getLevelCost(): Int = 0  // No XP cost

    override fun onSlotClick(slotIndex: Int, button: Int, actionType: SlotActionType, player: PlayerEntity) {
        // Don't allow taking items from the anvil slots
        if (slotIndex in 0..2 && actionType == SlotActionType.PICKUP && player is ServerPlayerEntity) {
            val clickType = if (button == 0) ClickType.LEFT else ClickType.RIGHT

            // Get the clicked item
            val stack = when (slotIndex) {
                0 -> input.getStack(0)
                1 -> input.getStack(1)
                2 -> output.getStack(0)
                else -> ItemStack.EMPTY
            }

            // Create context
            val context = AnvilInteractionContext(slotIndex, clickType, button, stack, player, this)

            // Call the appropriate handler
            when (slotIndex) {
                0 -> onLeftClick?.invoke(context)
                1 -> onRightClick?.invoke(context)
                2 -> onResultClick?.invoke(context)
            }

            // Don't allow the default behavior (item pickup)
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

    /**
     * Utility to update an item in a slot without affecting the text field.
     * For non-output slots, ensure titles are removed.
     */
    fun updateSlot(slot: Int, item: ItemStack?) {
        val tempText = currentText

        when (slot) {
            0, 1 -> input.setStack(slot, item?.let { removeTitle(it) } ?: ItemStack.EMPTY)
            2 -> output.setStack(0, item ?: ItemStack.EMPTY)
        }

        // If updating slot 0, we need to restore the text field
        if (slot == 0) {
            setNewItemName("")  // First clear
            setNewItemName(tempText)  // Then restore
        }
    }

    /**
     * Utility to forcibly clear the text field
     */
    fun clearTextField() {
        setNewItemName("")
    }
}
