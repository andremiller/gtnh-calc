package tools.gtnh.calc.export;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.util.ChatComponentText;

public class ExportCommand extends CommandBase {
    @Override
    public String getCommandName() {
        return "gtnh-calc-export";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/gtnh-calc-export";
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
        if (args.length != 0) {
            throw new WrongUsageException(getCommandUsage(sender));
        }
        sender.addChatMessage(new ChatComponentText("Exporting"));
    }
}
