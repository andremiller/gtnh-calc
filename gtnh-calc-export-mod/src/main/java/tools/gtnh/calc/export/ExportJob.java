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
    private static final long COLLECTION_BUDGET = 12_000_000L;
    private static final long RENDER_BUDGET = 4_000_000L;
    private ICommandSender sender;
    private Object world;
    private GameExtraction extraction;
    private CompletableFuture<byte[]> prepared;
    private CompletableFuture<Void> filtered;
    private boolean collectingAspects;
    private CompletableFuture<PreviousPack> previousLoad;
    private PreviousPack previous;
    private CompletableFuture<Void> encoding;
    private List<ExportData.Goods> icons;
    private BufferedImage atlas;
    private byte[] binary;
    private Path directory;
    private int iconIndex, iconFailures;
    private long lastProgress;
    private boolean extracting;

    void start(ICommandSender sender, String previousFile) {
        if (this.sender != null) { sender.addChatMessage(new ChatComponentText("[GTNH Calc Export] An export is already running")); return; }
        if (!Loader.isModLoaded("gregtech") || !Loader.isModLoaded("NotEnoughItems")) {
            sender.addChatMessage(new ChatComponentText("[GTNH Calc Export] GregTech and NEI are required")); return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null || !ItemList.loadFinished) {
            sender.addChatMessage(new ChatComponentText("[GTNH Calc Export] Enter a world and wait for NEI to finish loading items")); return;
        }
        if (!"en_US".equals(mc.gameSettings.language)) { sender.addChatMessage(new ChatComponentText("[GTNH Calc Export] Select English (US) before exporting")); return; }
        this.sender = sender; world = mc.theWorld;
        try {
            directory = ExportFiles.directory(mc.mcDataDir.toPath().resolve("gtnh-calc-export"));
            if (previousFile != null) {
                Path path = mc.mcDataDir.toPath().resolve(previousFile).normalize();
                previousLoad = CompletableFuture.supplyAsync(() -> {
                    try { return PreviousPack.read(path); }
                    catch (Exception error) { throw new CompletionException(error); }
                });
                message("Reading previous export: " + path);
            } else beginCollection();

        } catch (Exception | LinkageError error) { fail(error); }
    }

    private void beginCollection() throws Exception {
        extraction = new GameExtraction(); extracting = true;
        extraction.data.report.add("Exporter version: " + Tags.VERSION);
        extraction.data.report.add("Installed GregTech: " + Loader.instance().getIndexedModList().get("gregtech").getVersion());
        extraction.data.report.add("Installed NEI: " + Loader.instance().getIndexedModList().get("NotEnoughItems").getVersion());
        stage("Source collection (total grows as adapters queue tasks)");
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || sender == null) return;
        try {
            // Detached encoding may finish after disconnect; registry/GL work must not continue.
            if (encoding == null && world != Minecraft.getMinecraft().theWorld) throw new IllegalStateException("World changed during export; retry in a world");
            if (previousLoad != null) {
                if (previousLoad.isDone()) { previous = previousLoad.join(); previousLoad = null; beginCollection(); }
                return;
            }
            if (extracting) {
                long end = System.nanoTime() + COLLECTION_BUDGET;
                while (System.nanoTime() < end) if (extraction.step()) {
                    extracting = false;
                    GameExtraction collector = extraction;
                    if (!collectingAspects) {
                        stage("Recipe filtering");
                        filtered = CompletableFuture.runAsync(collector::filter);
                    } else {
                        stage("Final preprocessing");
                        ExportData data = collector.data; PreviousPack old = previous;
                        prepared = CompletableFuture.supplyAsync(() -> {
                            try {
                                collector.prepare();
                                if (old != null) { collector.progress.stage("Historical matching", data.report); old.apply(data); }
                                collector.progress.stage("Binary serialization", data.report);
                                return new PackWriter().write(data);
                            } catch (Exception error) { throw new CompletionException(error); }
                        });
                    }
                    break;
                }
                if (extracting) progress(extraction.progress());
                String warning = extraction.progress.warning(); if (warning != null) message(warning);
            } else if (filtered != null && filtered.isDone()) {
                filtered.join(); filtered = null;
                extraction.scheduleAspects(); collectingAspects = true; extracting = true;
                announcePhase();
                message(extraction.data.report.get(extraction.data.report.size() - 1));
            } else if (prepared != null && prepared.isDone()) {
                binary = prepared.join(); prepared = null; announcePhase();
                message(extraction.diagnosticSummary());
                extraction.releaseUnusedIcons();
                icons = List.copyOf(extraction.data.goods.values());
                atlas = AtlasImages.atlas(icons.size()); stage("Icon rendering"); message("Rendering " + icons.size() + " icons");
            } else if (encoding != null && encoding.isDone()) {
                encoding.join(); message(extraction.diagnosticSummary()); message("Export complete (" + extraction.summary() + "; " + iconFailures
                    + " icon placeholders): " + directory + " (see report.txt)"); clear();
            }
            if (!extracting && extraction != null && (filtered != null || prepared != null || encoding != null)) {
                announcePhase(); progress(extraction.progress.waiting());
            }
        } catch (Exception | LinkageError error) { fail(error); }
    }

    @SubscribeEvent
    public void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || sender == null || icons == null || encoding != null) return;
        try {
            if (world != Minecraft.getMinecraft().theWorld) throw new IllegalStateException("World changed during rendering");
            long end = System.nanoTime() + RENDER_BUDGET;
            while (iconIndex < icons.size() && System.nanoTime() < end) {
                ExportData.Goods g = icons.get(iconIndex++); BufferedImage tile;
                try { tile = IconRenderer.render(extraction.itemIcons.get(g.id), extraction.fluidIcons.get(g.iconKey == null ? g.id : g.iconKey)); }
                catch (Exception | LinkageError error) {
                    iconFailures++;
                    extraction.data.report.add("ICON " + g.id + ": placeholder; " + error + "\n" + ExportDiagnostics.trace(error));
                    LOG.warn("Calculator icon failure: " + g.id, error); tile = AtlasImages.placeholder();
                }
                AtlasImages.place(atlas, g.icon, tile);
            }
            progress(extraction.progress.status(iconIndex, icons.size()));
            if (iconIndex == icons.size()) {
                extraction.data.report.add("Icon placeholders: " + iconFailures + "/" + icons.size());
                stage("WebP encoding and file writing");
                ExportProgress collectorProgress = extraction.progress;
                ExportData data = extraction.data; BufferedImage image = atlas; byte[] bytes = binary; Path destination = directory;
                encoding = CompletableFuture.runAsync(() -> {
                    try { ExportFiles.write(data, bytes, image, destination, collectorProgress); }
                    catch (Exception error) { throw new CompletionException(error); }
                });
            }
        } catch (Exception | LinkageError error) { fail(error); }
    }

    private void stage(String name) {
        extraction.progress.stage(name, extraction.data.report); announcePhase();
    }
    private void announcePhase() {
        String phase;
        while ((phase = extraction.progress.announcement()) != null) { message(phase); lastProgress = System.nanoTime(); }
    }
    private void progress(String message) {
        long now = System.nanoTime();
        if (now - lastProgress > 2_000_000_000L) { message(message); lastProgress = now; }
    }
    private void message(String text) {
        sender.addChatMessage(new ChatComponentText("[GTNH Calc Export] " + text));
    }
    private void fail(Throwable error) {
        while (error instanceof CompletionException && error.getCause() != null) error = error.getCause();
        LOG.error("Calculator export failed", error);
        if (directory != null) ExportFiles.failure(extraction == null ? null : extraction.data, directory, error);
        message("Export failed: " + error + ". See client log" + (directory == null ? "" : " and " + directory));
        clear();
    }
    private void clear() {
        sender = null; world = null; extraction = null; prepared = null; filtered = null; previousLoad = null; previous = null; encoding = null; icons = null;
        atlas = null; binary = null; directory = null; iconIndex = 0; iconFailures = 0; extracting = false; collectingAspects = false; lastProgress = 0;
    }
}
