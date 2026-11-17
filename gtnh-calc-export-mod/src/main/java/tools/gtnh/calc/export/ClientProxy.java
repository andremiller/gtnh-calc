package tools.gtnh.calc.export;

import net.minecraftforge.client.ClientCommandHandler;

public class ClientProxy extends CommonProxy {
    @Override
    public void init() {
        ClientCommandHandler.instance.registerCommand(new ExportCommand());
    }
}
