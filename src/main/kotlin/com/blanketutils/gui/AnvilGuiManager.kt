package com.blanketutils.gui

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
 * A fully modular anvil GUI manager
 */
object AnvilGuiManager {
    private val logger = LoggerFactory.getLogger(AnvilGuiManager::class.java)

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

data class AnvilInteractionContext(
    val slotIndex: Int,
    val clickType: ClickType,
    val button: Int,
    val clickedStack: ItemStack,
    val player: ServerPlayerEntity,
    val handler: FullyModularAnvilScreenHandler
)

class NonInsertableSlot(inventory: Inventory, index: Int, x: Int, y: Int) : Slot(inventory, index, x, y) {
    override fun canInsert(stack: ItemStack): Boolean = false
}

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

    private val guiInventory = SimpleInventory(3)
    var currentText: String = initialText
        private set
    private var isInitializing = true

    init {
        setNewItemName("")
        val hiddenTextTrigger = ItemStack(Items.PAPER).apply {
            setCustomName(Text.literal(""))
        }
        input.setStack(0, hiddenTextTrigger)
        setNewItemName("")

        if (leftItem != null) {
            input.setStack(0, removeTitle(leftItem))
        }
        if (rightItem != null) {
            input.setStack(1, removeTitle(rightItem))
        } else {
            input.setStack(1, ItemStack.EMPTY)
        }
        if (resultItem != null) {
            output.setStack(0, resultItem)
        } else {
            output.setStack(0, ItemStack.EMPTY)
        }

        setNewItemName("")
        if (initialText.isNotEmpty()) {
            setNewItemName(initialText)
        }

        // Replace anvil slots with non-insertable slots
        this.slots[0] = NonInsertableSlot(input, 0, this.slots[0].x, this.slots[0].y)
        this.slots[1] = NonInsertableSlot(input, 1, this.slots[1].x, this.slots[1].y)
        this.slots[2] = NonInsertableSlot(output, 0, this.slots[2].x, this.slots[2].y)

        isInitializing = false
    }

    private fun removeTitle(item: ItemStack): ItemStack {
        val copy = item.copy()
        copy.setCustomName(Text.literal(""))
        return copy
    }

    override fun setNewItemName(newName: String): Boolean {
        val result = super.setNewItemName(newName)
        if (!isInitializing || newName != currentText) {
            currentText = newName
            onTextChange?.invoke(newName)
        }
        return result
    }

    override fun updateResult() {}

    override fun getLevelCost(): Int = 0

    override fun onSlotClick(slotIndex: Int, button: Int, actionType: SlotActionType, player: PlayerEntity) {
        if (slotIndex in 0..2 && actionType == SlotActionType.PICKUP && player is ServerPlayerEntity) {
            val clickType = if (button == 0) ClickType.LEFT else ClickType.RIGHT
            val stack = when (slotIndex) {
                0 -> input.getStack(0)
                1 -> input.getStack(1)
                2 -> output.getStack(0)
                else -> ItemStack.EMPTY
            }
            val context = AnvilInteractionContext(slotIndex, clickType, button, stack, player, this)
            when (slotIndex) {
                0 -> onLeftClick?.invoke(context)
                1 -> onRightClick?.invoke(context)
                2 -> onResultClick?.invoke(context)
            }
            return
        }
        super.onSlotClick(slotIndex, button, actionType, player)
    }

    override fun quickMove(player: PlayerEntity, index: Int): ItemStack = ItemStack.EMPTY

    override fun canUse(player: PlayerEntity): Boolean = true

    override fun onClosed(player: PlayerEntity) {
        super.onClosed(player)
        onClose?.invoke(input)
    }

    fun updateSlot(slot: Int, item: ItemStack?) {
        val tempText = currentText
        when (slot) {
            0, 1 -> input.setStack(slot, item?.let { removeTitle(it) } ?: ItemStack.EMPTY)
            2 -> output.setStack(0, item ?: ItemStack.EMPTY)
        }
        if (slot == 0) {
            setNewItemName("")
            setNewItemName(tempText)
        }
    }

    fun clearTextField() {
        setNewItemName("")
    }
}