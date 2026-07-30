package me.foesio.foKoTH.config;

import me.foesio.core.message.FoMessageService;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ItemDefinition {

    private final Material material;
    private final String name;
    private final List<String> lore;
    private final int amount;
    private final boolean glow;
    private final boolean unbreakable;
    private final Integer customModelData;

    public ItemDefinition(Material material, String name, List<String> lore, int amount, boolean glow, boolean unbreakable, Integer customModelData) {
        this.material = material;
        this.name = name;
        this.lore = List.copyOf(lore);
        this.amount = amount;
        this.glow = glow;
        this.unbreakable = unbreakable;
        this.customModelData = customModelData;
    }

    public static ItemDefinition from(ConfigurationSection section, Material fallback) {
        if (section == null) {
            return new ItemDefinition(fallback, "", List.of(), 1, false, false, null);
        }
        Material material = Material.matchMaterial(section.getString("material", fallback.name()));
        if (material == null) {
            material = fallback;
        }
        Integer cmd = section.contains("custom-model-data") ? section.getInt("custom-model-data") : null;
        return new ItemDefinition(
            material,
            section.getString("name", ""),
            section.getStringList("lore"),
            Math.max(1, Math.min(64, section.getInt("amount", 1))),
            section.getBoolean("glow", false),
            section.getBoolean("unbreakable", false),
            cmd
        );
    }

    public static ItemDefinition fromItem(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        String name = meta != null && meta.hasDisplayName() ? meta.getDisplayName() : "";
        List<String> lore = meta != null && meta.hasLore() ? meta.getLore() : List.of();
        Integer cmd = meta != null && meta.hasCustomModelData() ? meta.getCustomModelData() : null;
        return new ItemDefinition(item.getType(), name, lore == null ? List.of() : lore, Math.max(1, item.getAmount()), false, meta != null && meta.isUnbreakable(), cmd);
    }

    public ItemStack build(JavaPlugin plugin, FoMessageService messages, Map<String, String> replacements) {
        ItemStack stack = new ItemStack(material, amount);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            if (!name.isEmpty()) {
                meta.setDisplayName(messages.renderTemplate(apply(name, replacements), Map.of()));
            }
            if (!lore.isEmpty()) {
                List<String> colored = new ArrayList<>();
                for (String line : lore) {
                    colored.add(messages.renderTemplate(apply(line, replacements), Map.of()));
                }
                meta.setLore(colored);
            }
            if (customModelData != null) {
                meta.setCustomModelData(customModelData);
            }
            meta.setUnbreakable(unbreakable);
            if (glow) {
                Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft("unbreaking"));
                if (enchantment != null) {
                    meta.addEnchant(enchantment, 1, true);
                }
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public ItemStack buildStarter(JavaPlugin plugin, FoMessageService messages, String koth, Map<String, String> replacements) {
        ItemStack stack = build(plugin, messages, replacements);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            PersistentDataContainer data = meta.getPersistentDataContainer();
            data.set(new NamespacedKey(plugin, "starter"), PersistentDataType.STRING, koth);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public Map<String, Object> toMap() {
        java.util.LinkedHashMap<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("material", material.name().toUpperCase(Locale.ROOT));
        map.put("amount", amount);
        if (!name.isEmpty()) {
            map.put("name", name);
        }
        if (!lore.isEmpty()) {
            map.put("lore", lore);
        }
        if (glow) {
            map.put("glow", true);
        }
        if (unbreakable) {
            map.put("unbreakable", true);
        }
        if (customModelData != null) {
            map.put("custom-model-data", customModelData);
        }
        return map;
    }

    public Material material() {
        return material;
    }

    private String apply(String input, Map<String, String> replacements) {
        String out = input;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            out = out.replace("%" + entry.getKey() + "%", entry.getValue());
            out = out.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return out;
    }
}
