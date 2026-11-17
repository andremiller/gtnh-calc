package tools.gtnh.calc.export;

import cpw.mods.fml.common.FMLCommonHandler;
import net.minecraftforge.client.ClientCommandHandler;

public class ClientProxy extends CommonProxy {
    @Override
    public void init() {
        ExportJob job = new ExportJob();
        FMLCommonHandler.instance().bus().register(job);
        ClientCommandHandler.instance.registerCommand(new ExportCommand(job));
    }
}
