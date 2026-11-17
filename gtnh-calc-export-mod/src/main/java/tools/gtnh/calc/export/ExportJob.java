package tools.gtnh.calc.export;

import codechicken.nei.ItemList;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.minecraft.client.Minecraft;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** A single active job, driven by client and render events. */
public final class ExportJob {
    private static final Logger LOG = LogManager.getLogger("gtnh_calc_export");
    private static final long BUDGET = 4_000_000L;
    private ICommandSender sender;
    private Object world;
    private GtExtraction extraction;
    private CompletableFuture<byte[]> prepared;
    private CompletableFuture<Void> encoding;
    private List<ExportData.Goods> icons;
    private BufferedImage atlas;
    private byte[] binary;
    private Path directory;
    private int iconIndex, iconFailures;
    private long lastProgress;
    private boolean extracting;

    void start(ICommandSender sender) {
        if (this.sender != null) { sender.addChatMessage(new ChatComponentText("An export is already running")); return; }
        if (!Loader.isModLoaded("gregtech") || !Loader.isModLoaded("NotEnoughItems")) {
            sender.addChatMessage(new ChatComponentText("GregTech and NEI are required")); return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null || !ItemList.loadFinished) {
            sender.addChatMessage(new ChatComponentText("Enter a world and wait for NEI to finish loading items")); return;
        }
        if (!"en_US".equals(mc.gameSettings.language)) { sender.addChatMessage(new ChatComponentText("Select English (US) before exporting")); return; }
        this.sender = sender; world = mc.theWorld;
        try {
            directory = ExportFiles.directory(mc.mcDataDir.toPath().resolve("gtnh-calc-export"));
            extraction = new GtExtraction(); extracting = true;
            extraction.data.report.add("Installed GregTech: " + Loader.instance().getIndexedModList().get("gregtech").getVersion());
            extraction.data.report.add("Installed NEI: " + Loader.instance().getIndexedModList().get("NotEnoughItems").getVersion());
            message("Exporting Macerator and Electric Blast Furnace recipes");
        } catch (Exception | LinkageError error) { fail(error); }
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || sender == null) return;
        try {
            // Detached encoding may finish after disconnect; registry/GL work must not continue.
            if (encoding == null && world != Minecraft.getMinecraft().theWorld) throw new IllegalStateException("World changed during export; retry in a world");
            if (extracting) {
                long end = System.nanoTime() + BUDGET;
                while (System.nanoTime() < end) if (extraction.step()) {
                    extracting = false;
                    ExportData data = extraction.data;
                    prepared = CompletableFuture.supplyAsync(() -> {
                        try { data.prepare(); return new PackWriter().write(data); }
                        catch (Exception error) { throw new CompletionException(error); }
                    });
                    message("Preparing calculator data"); break;
                }
                progress(extraction.progress());
            } else if (prepared != null && prepared.isDone()) {
                binary = prepared.join(); prepared = null;
                icons = List.copyOf(extraction.data.goods.values());
                atlas = AtlasImages.atlas(icons.size()); message("Rendering " + icons.size() + " icons");
            } else if (encoding != null && encoding.isDone()) {
                encoding.join(); message("Export complete (" + extraction.summary() + "; " + iconFailures
                    + " icon placeholders): " + directory + " (see report.txt)"); clear();
            }
        } catch (Exception | LinkageError error) { fail(error); }
    }

    @SubscribeEvent
    public void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || sender == null || icons == null || encoding != null) return;
        try {
            if (world != Minecraft.getMinecraft().theWorld) throw new IllegalStateException("World changed during rendering");
            long end = System.nanoTime() + BUDGET;
            while (iconIndex < icons.size() && System.nanoTime() < end) {
                ExportData.Goods g = icons.get(iconIndex++); BufferedImage tile;
                try { tile = IconRenderer.render(extraction.itemIcons.get(g.id), extraction.fluidIcons.get(g.id)); }
                catch (Exception | LinkageError error) {
                    iconFailures++;
                    extraction.data.report.add("ICON " + g.id + ": placeholder; " + error); tile = AtlasImages.placeholder();
                }
                AtlasImages.place(atlas, g.icon, tile);
            }
            progress("Icons " + iconIndex + "/" + icons.size());
            if (iconIndex == icons.size()) {
                extraction.data.report.add("Icon placeholders: " + iconFailures + "/" + icons.size());
                message("Encoding files");
                ExportData data = extraction.data; BufferedImage image = atlas; byte[] bytes = binary; Path destination = directory;
                encoding = CompletableFuture.runAsync(() -> {
                    try { ExportFiles.write(data, bytes, image, destination); }
                    catch (Exception error) { throw new CompletionException(error); }
                });
            }
        } catch (Exception | LinkageError error) { fail(error); }
    }

    private void progress(String message) {
        long now = System.nanoTime();
        if (now - lastProgress > 2_000_000_000L) { message(message); lastProgress = now; }
    }
    private void message(String text) {
        sender.addChatMessage(new ChatComponentText(text));
    }
    private void fail(Throwable error) {
        while (error instanceof CompletionException && error.getCause() != null) error = error.getCause();
        LOG.error("Calculator export failed", error);
        if (directory != null) ExportFiles.failure(extraction == null ? null : extraction.data, directory, error);
        message("Export failed: " + error + ". See client log" + (directory == null ? "" : " and " + directory));
        clear();
    }
    private void clear() {
        sender = null; world = null; extraction = null; prepared = null; encoding = null; icons = null;
        atlas = null; binary = null; directory = null; iconIndex = 0; iconFailures = 0; extracting = false; lastProgress = 0;
    }
}
