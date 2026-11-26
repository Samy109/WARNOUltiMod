package com.warnomodmaker.gui.components;

import com.warnomodmaker.combine.Models.WarnoPaths;
import com.warnomodmaker.combine.WarnoLocator;
import com.warnomodmaker.combine.ndf.UnitPictures;
import com.warnomodmaker.model.NDFValue;
import com.warnomodmaker.model.NDFValue.ObjectValue;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Unit pictures for the editor, read from the installed WARNO's icon atlases on a background thread. Callers ask for a
 * picture and get null until it is ready; the callback they pass runs on the event thread once it is.
 */
public final class UnitPictureCache {
    private static final ExecutorService LOADER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "unit-pictures");
        thread.setDaemon(true);
        return thread;
    });
    private static volatile UnitPictures pictures;
    private static volatile boolean indexing;
    private static volatile String unavailable;
    private static final Map<String, ImageIcon> scaled = new HashMap<>();
    private static final Set<String> pending = new HashSet<>();
    private static final List<Runnable> waitingForIndex = new ArrayList<>();

    private UnitPictureCache() {
    }

    /** Why there are no pictures (WARNO not found), or null. */
    public static String unavailableReason() {
        return unavailable;
    }

    /** The picture key of a unit: its ButtonTexture token if it has one, else its descriptor name. */
    public static String key(ObjectValue unit) {
        if (unit == null) return null;
        String texture = findButtonTexture(unit, 0);
        return texture != null ? texture : unit.getInstanceName();
    }

    private static String findButtonTexture(NDFValue value, int depth) {
        if (value == null || depth > 8) return null;
        if (value instanceof ObjectValue) {
            for (Map.Entry<String, NDFValue> property : ((ObjectValue) value).getProperties().entrySet()) {
                if (property.getKey().equals("ButtonTexture") && property.getValue() instanceof NDFValue.StringValue)
                    return ((NDFValue.StringValue) property.getValue()).getValue();
                String found = findButtonTexture(property.getValue(), depth + 1);
                if (found != null) return found;
            }
        } else if (value instanceof NDFValue.ArrayValue) {
            for (NDFValue element : ((NDFValue.ArrayValue) value).getElements()) {
                String found = findButtonTexture(element, depth + 1);
                if (found != null) return found;
            }
        } else if (value instanceof NDFValue.TupleValue) {
            for (NDFValue element : ((NDFValue.TupleValue) value).getElements()) {
                String found = findButtonTexture(element, depth + 1);
                if (found != null) return found;
            }
        } else if (value instanceof NDFValue.MapValue) {
            for (Map.Entry<NDFValue, NDFValue> entry : ((NDFValue.MapValue) value).getEntries()) {
                String found = findButtonTexture(entry.getValue(), depth + 1);
                if (found != null) return found;
            }
        }
        return null;
    }

    /**
     * The unit's picture scaled to fit {@code width} × {@code height}, or null when it isn't ready yet (then
     * {@code ready} runs on the event thread once it is) or WARNO has no picture for it.
     */
    public static ImageIcon icon(String key, int width, int height, Runnable ready) {
        if (key == null) return null;
        String cacheKey = key + "@" + width + "x" + height;
        synchronized (scaled) {
            ImageIcon icon = scaled.get(cacheKey);
            if (icon != null || scaled.containsKey(cacheKey)) return icon;
            if (!pending.add(cacheKey)) return null;
        }
        UnitPictures index = pictures;
        if (index == null) {
            startIndexing(() -> {
                synchronized (scaled) {
                    pending.remove(cacheKey);
                }
                if (ready != null) ready.run();
            });
            return null;
        }
        LOADER.submit(() -> {
            ImageIcon result = null;
            try {
                BufferedImage image = index.picture(key);
                if (image != null) result = new ImageIcon(scale(image, width, height));
            } catch (RuntimeException e) {
                // A picture that can't be decoded is shown as missing.
            }
            synchronized (scaled) {
                scaled.put(cacheKey, result);
                pending.remove(cacheKey);
            }
            if (ready != null) SwingUtilities.invokeLater(ready);
        });
        return null;
    }

    /** True once the atlases are indexed (or known to be missing), so a null from {@link #icon} means "no picture". */
    public static boolean settled(String key, int width, int height) {
        synchronized (scaled) {
            return scaled.containsKey(key + "@" + width + "x" + height) || (pictures == null && unavailable != null);
        }
    }

    private static void startIndexing(Runnable then) {
        synchronized (waitingForIndex) {
            if (pictures != null || unavailable != null) {
                SwingUtilities.invokeLater(then);
                return;
            }
            waitingForIndex.add(then);
            if (indexing) return;
            indexing = true;
        }
        LOADER.submit(() -> {
            try {
                WarnoPaths paths = new WarnoLocator().locate();
                if (paths == null) unavailable = "WARNO wasn't found, so unit pictures can't be shown.";
                else {
                    UnitPictures loaded = UnitPictures.load(paths.warnoRoot);
                    if (loaded.size() == 0) unavailable = "No unit pictures were found in WARNO's data.";
                    else pictures = loaded;
                }
            } catch (RuntimeException e) {
                unavailable = "Unit pictures couldn't be read: " + e.getMessage();
            }
            List<Runnable> callbacks;
            synchronized (waitingForIndex) {
                callbacks = new ArrayList<>(waitingForIndex);
                waitingForIndex.clear();
            }
            for (Runnable callback : callbacks) SwingUtilities.invokeLater(callback);
        });
    }

    private static Image scale(BufferedImage image, int width, int height) {
        double factor = Math.min(width / (double) image.getWidth(), height / (double) image.getHeight());
        int w = Math.max(1, (int) Math.round(image.getWidth() * factor)), h = Math.max(1, (int) Math.round(image.getHeight() * factor));
        BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(image, 0, 0, w, h, null);
        g.dispose();
        return result;
    }
}
