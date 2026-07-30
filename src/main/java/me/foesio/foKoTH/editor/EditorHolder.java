package me.foesio.foKoTH.editor;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class EditorHolder implements InventoryHolder {

    private final EditorPage page;
    private final String context;
    private final int pageIndex;
    private final String search;

    public EditorHolder(EditorPage page, String context, int pageIndex, String search) {
        this.page = page;
        this.context = context;
        this.pageIndex = pageIndex;
        this.search = search == null ? "" : search;
    }

    public EditorPage page() {
        return page;
    }

    public String context() {
        return context;
    }

    public int pageIndex() {
        return pageIndex;
    }

    public String search() {
        return search;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
