package com.blanketutils.gui

import com.mojang.authlib.GameProfile
import com.mojang.authlib.properties.Property
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.LoreComponent
import net.minecraft.component.type.ProfileComponent
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
import java.util.UUID

data class InteractionContext(
    val slotIndex: Int,
    val clickType: ClickType,
    val button: Int,
    val clickedStack: ItemStack,
    val player: ServerPlayerEntity
)

object CustomGui {
    private val logger = LoggerFactory.getLogger(CustomGui::class.java)

    fun openGui(
        player: ServerPlayerEntity,
        title: String,
        layout: List<ItemStack>,
        onInteract: (InteractionContext) -> Unit,
        onClose: (Inventory) -> Unit
    ) {
        val factory = SimpleNamedScreenHandlerFactory(
            { syncId, inv, _ ->
                CustomScreenHandler(syncId, inv, layout, onInteract, onClose)
            },
            Text.literal(title)
        )
        player.openHandledScreen(factory)
    }

    fun createPlayerHeadButton(
        textureName: String,
        title: Text,
        lore: List<Text>,
        textureValue: String
    ): ItemStack {
        val itemStack = ItemStack(Items.PLAYER_HEAD)
        itemStack.set(DataComponentTypes.ITEM_NAME, title)
        itemStack.set(DataComponentTypes.LORE, LoreComponent(lore))

        // Ensure the name is no longer than 16 characters to comply with Minecraft's limit
        val safeName = textureName.take(16)
        val profile = GameProfile(UUID.randomUUID(), safeName)
        profile.properties.put("textures", Property("textures", textureValue))
        itemStack.set(DataComponentTypes.PROFILE, ProfileComponent(profile))

        return itemStack
    }

    fun createNormalButton(
        item: ItemStack,
        displayName: String,
        lore: List<String>
    ): ItemStack {
        val newStack = item.copy()
        newStack.set(DataComponentTypes.ITEM_NAME, Text.literal(displayName))
        val loreText = lore.map { Text.literal(it) }
        newStack.set(DataComponentTypes.LORE, LoreComponent(loreText))
        return newStack
    }

    fun setItemLore(itemStack: ItemStack, loreLines: List<Any?>) {
        val textLines = loreLines.mapNotNull { line ->
            when (line) {
                is Text -> line
                is String -> Text.literal(line)
                null -> null
                else -> Text.literal(line.toString())
            }
        }

        if (textLines.size != loreLines.size) {
            itemStack.set(
                DataComponentTypes.LORE,
                LoreComponent(listOf(Text.literal("§cError setting lore"), Text.literal("§7One of the lines was null")))
            )
            return
        }

        itemStack.set(DataComponentTypes.LORE, LoreComponent(textLines))
    }

    fun stripFormatting(text: String): String {
        return text.replace(Regex("§[0-9a-fk-or]"), "")
    }

    fun addEnchantmentGlint(itemStack: ItemStack) {
        itemStack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true)
    }

    fun refreshGui(player: ServerPlayerEntity, newLayout: List<ItemStack>) {
        val screenHandler = player.currentScreenHandler as? CustomScreenHandler
        screenHandler?.updateInventory(newLayout) ?: run {
            logger.warn("Player ${player.name.string} does not have the expected screen handler open.")
        }
    }

    fun closeGui(player: ServerPlayerEntity) {
        player.closeHandledScreen()
    }

    /**
     * Opens a search anvil GUI for the specified player with the given ID and title.
     * The onSearch callback is triggered when the player submits their search.
     */
    fun openSearchGui(
        player: ServerPlayerEntity,
        id: String,
        title: String,
        onSearch: (String) -> Unit,
        setupInput: ((Inventory) -> Unit)? = null,
        onInputClick: (() -> Unit)? = null
    ) {
        val factory = SimpleNamedScreenHandlerFactory(
            { syncId, inventory, _ ->
                SearchAnvilScreenHandler(syncId, inventory, id, onSearch, setupInput, onInputClick)
            },
            Text.literal(title)
        )
        player.openHandledScreen(factory)
    }

class CustomScreenHandler(
    syncId: Int,
    playerInventory: PlayerInventory,
    layout: List<ItemStack>,
    private var onInteract: ((InteractionContext) -> Unit)?,
    private var onClose: ((Inventory) -> Unit)?
) : ScreenHandler(ScreenHandlerType.GENERIC_9X6, syncId) {

    private val guiInventory: Inventory = object : Inventory {
        private val items = Array<ItemStack?>(54) { ItemStack.EMPTY }

        init {
            layout.forEachIndexed { index, itemStack ->
                if (index < size()) items[index] = itemStack
            }
        }

        override fun clear() {
            items.fill(ItemStack.EMPTY)
        }

        override fun size() = items.size
        override fun isEmpty() = items.all { it?.isEmpty ?: true }
        override fun getStack(slot: Int) = items[slot] ?: ItemStack.EMPTY

        override fun removeStack(slot: Int, amount: Int): ItemStack {
            val stack = getStack(slot)
            return if (stack.count <= amount) {
                removeStack(slot)
            } else {
                val splitStack = stack.split(amount)
                items[slot] = stack
                splitStack
            }
        }

        override fun removeStack(slot: Int): ItemStack {
            val stack = getStack(slot)
            items[slot] = ItemStack.EMPTY
            return stack
        }

        override fun setStack(slot: Int, stack: ItemStack) {
            items[slot] = stack
        }

        override fun markDirty() {}
        override fun canPlayerUse(player: PlayerEntity) = true
    }

    init {
        // Add GUI slots
        for (i in 0 until guiInventory.size()) {
            addSlot(InteractiveSlot(guiInventory, i, false))
        }

        // Add player inventory slots
        for (i in 0..2) {
            for (j in 0..8) {
                val index = j + i * 9 + 9
                addSlot(Slot(playerInventory, index, 8 + j * 18, 84 + i * 18))
            }
        }
        for (k in 0..8) {
            addSlot(Slot(playerInventory, k, 8 + k * 18, 142))
        }
    }

    override fun canUse(player: PlayerEntity) = true

    override fun onSlotClick(slotIndex: Int, button: Int, actionType: SlotActionType, player: PlayerEntity) {
        val isGuiSlot = slotIndex in 0 until guiInventory.size()
        if (isGuiSlot && player is ServerPlayerEntity) {
            val stack = guiInventory.getStack(slotIndex)
            val clickType = if (button == 0) ClickType.LEFT else ClickType.RIGHT
            val context = InteractionContext(slotIndex, clickType, button, stack, player)
            onInteract?.invoke(context)
            return
        }
        if (!isGuiSlot) {
            super.onSlotClick(slotIndex, button, actionType, player)
        }
    }

    // Prevent shift-clicking entirely
    override fun quickMove(player: PlayerEntity, index: Int): ItemStack = ItemStack.EMPTY

    override fun onClosed(player: PlayerEntity) {
        super.onClosed(player)
        onClose?.invoke(guiInventory)
        onInteract = null
        onClose = null
    }

    fun updateInventory(newLayout: List<ItemStack>) {
        newLayout.forEachIndexed { index, itemStack ->
            if (index < guiInventory.size()) {
                guiInventory.setStack(index, itemStack)
            }
        }
        sendContentUpdates()
    }
}

class InteractiveSlot(
    inventory: Inventory,
    index: Int,
    private val isInteractive: Boolean
) : Slot(
    inventory,
    index,
    8 + (index % 9) * 18,
    18 + (index / 9) * 18
) {
    override fun canInsert(stack: ItemStack) = isInteractive
    override fun canTakeItems(player: PlayerEntity) = isInteractive
}

fun ItemStack.setCustomName(name: Text) {
    this.set(DataComponentTypes.ITEM_NAME, name)
}

    /**
     * A custom AnvilScreenHandler that provides search functionality.
     *
     * @param syncId The synchronization ID for this screen handler
     * @param playerInventory The player's inventory
     * @param id A unique identifier for this screen handler
     * @param onSearch Callback triggered when the player submits a search
     * @param setupInput Optional callback to customize the input paper item
     * @param onInputClick Optional callback triggered when the input paper is clicked
     */
    class SearchAnvilScreenHandler(
        syncId: Int,
        playerInventory: PlayerInventory,
        private val id: String,
        private val onSearch: (String) -> Unit,
        private val setupInput: ((Inventory) -> Unit)? = null,
        private val onInputClick: (() -> Unit)? = null
    ) : AnvilScreenHandler(syncId, playerInventory, ScreenHandlerContext.EMPTY) {

        // Our own copy of the current search query since newItemName is private
        private var searchQuery: String = ""
        private val logger = LoggerFactory.getLogger("blanketutils-search-anvil")

        init {
            // Insert a dummy item into the left slot (index 0) to trigger the text field
            val dummy = ItemStack(Items.PAPER).apply {
                setCustomName(Text.literal("")) // Empty name for clear input field
            }
            input.setStack(0, dummy)

            // Clear the right input slot (index 1) so it doesn't interfere
            input.setStack(1, ItemStack.EMPTY)

            // Apply custom setup to the input if provided
            setupInput?.invoke(input)

            // Ensure the text field starts empty
            setNewItemName("")
        }

        /**
         * Override setNewItemName so we can capture the search query.
         */
        override fun setNewItemName(newName: String): Boolean {
            val ret = super.setNewItemName(newName)
            searchQuery = newName
            updateResult() // Update the output as the text changes
            return ret
        }

        /**
         * Called whenever the input text changes.
         * Updates the output slot with a paper item that visually represents the search result.
         */
        override fun updateResult() {
            if (searchQuery.isEmpty()) {
                output.setStack(0, ItemStack.EMPTY)
            } else {
                val searchResult = createSearchResultItem(searchQuery)
                output.setStack(0, searchResult)
            }
        }

        /**
         * Override the method that determines the level cost to always return 0
         */
        override fun getLevelCost(): Int {
            return 0
        }

        /**
         * Override onSlotClick to handle input slot clicks for cancellation
         * and to directly handle the output slot for search submission
         */
        override fun onSlotClick(slotIndex: Int, button: Int, actionType: SlotActionType, player: PlayerEntity) {
            // Special handling for input slot if onInputClick is provided (index 0 = left input)
            if (slotIndex == 0 && player is ServerPlayerEntity && onInputClick != null) {
               // System.out.println("Player ${player.name.string} clicked the input/cancel button")
                onInputClick.invoke()
                player.closeHandledScreen()
                return
            }

            // We still want to prevent taking the item from slot 0 or 1
            if ((slotIndex == 0 || slotIndex == 1) && actionType == SlotActionType.PICKUP) {
                return
            }

            // Special handling for output slot (2)
            if (slotIndex == 2 && player is ServerPlayerEntity) {
                //System.out.println("Player ${player.name.string} searched for: $searchQuery")
                onSearch(searchQuery)
                player.closeHandledScreen()
                return
            }

            // For all other slots, use default behavior
            super.onSlotClick(slotIndex, button, actionType, player)
        }

        /**
         * Called when the output slot is clicked.
         */
        override fun onTakeOutput(player: PlayerEntity, output: ItemStack) {
            //System.out.println("Player ${player.name.string} searched for: $searchQuery")
            onSearch(searchQuery)

            (player as? ServerPlayerEntity)?.closeHandledScreen()
        }

        /**
         * Creates a paper item representing the search result.
         */
        private fun createSearchResultItem(query: String): ItemStack {
            val resultItem = ItemStack(Items.PAPER)
            resultItem.setCustomName(Text.literal("Search: $query"))

            // Add lore to make it clear this is the search button
            CustomGui.setItemLore(resultItem, listOf(
                "§aClick to search for this term",
                "§7Enter different text to change search"
            ))

            // Add enchantment glint to make it stand out
            CustomGui.addEnchantmentGlint(resultItem)

            return resultItem
        }

        /**
         * Override to prevent shift-clicking items
         */
        override fun quickMove(player: PlayerEntity, index: Int): ItemStack {
            return ItemStack.EMPTY
        }

        /**
         * Override to ensure players can always use this GUI
         */
        override fun canUse(player: PlayerEntity): Boolean {
            return true
        }

        /**
         * Returns the unique identifier for this search anvil
         */
        fun getId(): String {
            return id
        }
    }
}
fun ItemStack.setCustomName(name: Text) {
    this.set(DataComponentTypes.ITEM_NAME, name)
}