package tools.gtnh.calc.export;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;

public class ExportCommand extends CommandBase {
    private final ExportJob job;

    ExportCommand(ExportJob job) { this.job = job; }

    @Override
    public String getCommandName() {
        return "gtnh-calc-export";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/gtnh-calc-export [previous.bin]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        String previous = args.length == 0 ? null : String.join(" ", args);
        if (previous != null && previous.length() >= 2 && previous.startsWith("\"") && previous.endsWith("\""))
            previous = previous.substring(1, previous.length() - 1);
        job.start(sender, previous);
    }
}
