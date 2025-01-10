package com.blanketutils.gui

import com.mojang.authlib.GameProfile
import com.mojang.authlib.properties.Property
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.LoreComponent
import net.minecraft.component.type.ProfileComponent
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.ScreenHandlerType
import net.minecraft.screen.SimpleNamedScreenHandlerFactory
import net.minecraft.screen.slot.Slot
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.ClickType
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

// Core data interfaces
interface GuiData {
    val guiId: String
    val modId: String
    val title: String
    val rows: Int
    val allowPlayerInventory: Boolean
}

data class InteractionContext(
    val slotIndex: Int,
    val clickType: ClickType,
    val button: Int,
    val clickedStack: ItemStack,
    val player: ServerPlayerEntity,
    val guiId: String,
    val modId: String
)

// Central GUI Registry and Manager
object GuiRegistry {
    private val logger = LoggerFactory.getLogger(GuiRegistry::class.java)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Thread-safe collections for managing active GUIs and their states
    private val activeGuis = ConcurrentHashMap<String, GuiInstance>()
    private val playerGuis = ConcurrentHashMap<UUID, String>()
    private val guiHandlers = ConcurrentHashMap<String, GuiHandler>()

    fun registerGui(handler: GuiHandler) {
        guiHandlers[handler.guiData.guiId] = handler
        logger.debug("Registered GUI: ${handler.guiData.guiId} for mod: ${handler.guiData.modId}")
    }

    fun unregisterGui(guiId: String) {
        guiHandlers.remove(guiId)
        // Cleanup any active instances
        activeGuis.entries.removeIf { it.value.guiData.guiId == guiId }
    }

    fun openGui(player: ServerPlayerEntity, guiId: String, customData: Map<String, Any> = emptyMap()) {
        val handler = guiHandlers[guiId] ?: run {
            logger.error("Attempted to open unregistered GUI: $guiId")
            return
        }

        val instance = GuiInstance(
            guiData = handler.guiData,
            layout = handler.createLayout(player, customData),
            onInteract = { context -> handler.handleInteraction(context) },
            onClose = { inventory -> handler.handleClose(player, inventory) }
        )

        activeGuis[player.uuid.toString() + guiId] = instance
        playerGuis[player.uuid] = guiId

        val factory = SimpleNamedScreenHandlerFactory(
            { syncId, inv, _ ->
                CustomScreenHandler(
                    syncId = syncId,
                    playerInventory = inv,
                    guiInstance = instance,
                    allowPlayerInventory = handler.guiData.allowPlayerInventory
                )
            },
            Text.literal(handler.guiData.title)
        )

        player.openHandledScreen(factory)
    }

    fun updateGui(player: ServerPlayerEntity, newLayout: List<ItemStack>) {
        val guiId = playerGuis[player.uuid] ?: return
        val instanceKey = player.uuid.toString() + guiId
        val instance = activeGuis[instanceKey] ?: return

        val screenHandler = player.currentScreenHandler as? CustomScreenHandler ?: return
        screenHandler.updateInventory(newLayout)
    }

    fun cleanup() {
        scope.cancel()
        activeGuis.clear()
        playerGuis.clear()
        guiHandlers.clear()
    }
}

// GUI Handler interface for mods to implement
interface GuiHandler {
    val guiData: GuiData

    fun createLayout(player: ServerPlayerEntity, customData: Map<String, Any>): List<ItemStack>
    fun handleInteraction(context: InteractionContext)
    fun handleClose(player: ServerPlayerEntity, inventory: Inventory)
}

// Internal GUI instance data
data class GuiInstance(
    val guiData: GuiData,
    var layout: List<ItemStack>,
    val onInteract: (InteractionContext) -> Unit,
    val onClose: (Inventory) -> Unit
)

// Screen Handler implementation
// Custom slot implementation for GUI
class InteractiveSlot(inventory: Inventory, index: Int, private val isInteractive: Boolean) : Slot(
    inventory,
    index,
    8 + (index % 9) * 18,  // x coordinate
    18 + (index / 9) * 18  // y coordinate
) {
    override fun canInsert(stack: ItemStack) = isInteractive
    override fun canTakeItems(player: PlayerEntity) = isInteractive
}

class CustomScreenHandler(
    syncId: Int,
    playerInventory: PlayerInventory,
    private val guiInstance: GuiInstance,
    private val allowPlayerInventory: Boolean
) : ScreenHandler(ScreenHandlerType.GENERIC_9X6, syncId) {

    private val guiInventory: Inventory = object : Inventory {
        private val items = Array<ItemStack?>(guiInstance.guiData.rows * 9) { ItemStack.EMPTY }

        init {
            guiInstance.layout.forEachIndexed { index, itemStack ->
                if (index < size()) items[index] = itemStack
            }
        }

        override fun clear() {
            items.fill(ItemStack.EMPTY)
        }

        override fun size() = items.size
        override fun isEmpty() = items.all { it?.isEmpty ?: true }
        override fun getStack(slot: Int) = items.getOrNull(slot) ?: ItemStack.EMPTY

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
            if (slot < items.size) {
                items[slot] = stack
            }
        }

        override fun markDirty() {}
        override fun canPlayerUse(player: PlayerEntity) = true
    }

    init {
        // Add GUI slots
        for (row in 0 until guiInstance.guiData.rows) {
            for (col in 0..8) {
                val index = col + row * 9
                addSlot(InteractiveSlot(guiInventory, index, false))
            }
        }

        // Add player inventory slots if allowed
        if (allowPlayerInventory) {
            for (row in 0..2) {
                for (col in 0..8) {
                    val index = col + row * 9 + 9
                    addSlot(Slot(playerInventory, index, 8 + col * 18, 84 + row * 18))
                }
            }
            // Hotbar
            for (col in 0..8) {
                addSlot(Slot(playerInventory, col, 8 + col * 18, 142))
            }
        }
    }

    override fun canUse(player: PlayerEntity) = true

    override fun onSlotClick(slotIndex: Int, button: Int, actionType: SlotActionType, player: PlayerEntity) {
        val isGuiSlot = slotIndex in 0 until guiInventory.size()
        if (isGuiSlot && player is ServerPlayerEntity) {
            val stack = guiInventory.getStack(slotIndex)
            val clickType = if (button == 0) ClickType.LEFT else ClickType.RIGHT
            val context = InteractionContext(
                slotIndex = slotIndex,
                clickType = clickType,
                button = button,
                clickedStack = stack,
                player = player,
                guiId = guiInstance.guiData.guiId,
                modId = guiInstance.guiData.modId
            )
            guiInstance.onInteract(context)
            return
        }

        if (!allowPlayerInventory) {
            return
        }
        super.onSlotClick(slotIndex, button, actionType, player)
    }

    override fun quickMove(player: PlayerEntity, index: Int) = ItemStack.EMPTY

    override fun onClosed(player: PlayerEntity) {
        super.onClosed(player)
        guiInstance.onClose(guiInventory)
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

// Utility class for item creation and manipulation
object GuiUtils {
    fun createPlayerHeadButton(title: Text, lore: List<Text>, textureValue: String): ItemStack {
        val itemStack = ItemStack(Items.PLAYER_HEAD)
        itemStack.setCustomName(title)
        itemStack.setLore(lore)

        val profile = GameProfile(UUID.randomUUID(), "custom_head")
        profile.properties.put("textures", Property("textures", textureValue))
        itemStack.set(DataComponentTypes.PROFILE, ProfileComponent(profile))

        return itemStack
    }

    fun createButton(item: ItemStack, displayName: String, lore: List<String>): ItemStack {
        return item.copy().apply {
            setCustomName(Text.literal(displayName))
            setLore(lore.map { Text.literal(it) })
        }
    }

    fun ItemStack.setCustomName(name: Text) {
        this.set(DataComponentTypes.ITEM_NAME, name)
    }

    fun ItemStack.setLore(lore: List<Text>) {
        this.set(DataComponentTypes.LORE, LoreComponent(lore))
    }

    fun ItemStack.addGlint() {
        this.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true)
    }

    fun stripFormatting(text: String): String {
        return text.replace(Regex("§[0-9a-fk-or]"), "")
    }
}

// Example usage:
/*
class MyModGui(
    override val guiId: String = "my_mod_gui",
    override val modId: String = "my_mod",
    override val title: String = "My Mod GUI",
    override val rows: Int = 6,
    override val allowPlayerInventory: Boolean = true
) : GuiData

class MyModGuiHandler : GuiHandler {
    override val guiData = MyModGui()

    override fun createLayout(player: ServerPlayerEntity, customData: Map<String, Any>): List<ItemStack> {
        // Create your GUI layout
        return listOf()
    }

    override fun handleInteraction(context: InteractionContext) {
        // Handle clicks
    }

    override fun handleClose(player: ServerPlayerEntity, inventory: Inventory) {
        // Handle GUI close
    }
}

// Register your GUI:
GuiRegistry.registerGui(MyModGuiHandler())

// Open your GUI:
GuiRegistry.openGui(player, "my_mod_gui")
*/