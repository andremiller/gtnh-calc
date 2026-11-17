package tools.gtnh.calc.export;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;

@Mod(modid = "gtnh_calc_export", name = "GTNH Calc Export", version = Tags.VERSION,
    acceptedMinecraftVersions = "[1.7.10]", acceptableRemoteVersions = "*")
public class GtnhCalcExport {
    @SidedProxy(clientSide = "tools.gtnh.calc.export.ClientProxy",
        serverSide = "tools.gtnh.calc.export.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init();
    }
}
