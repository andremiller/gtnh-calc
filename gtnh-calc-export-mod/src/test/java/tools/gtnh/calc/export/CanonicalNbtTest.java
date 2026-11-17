package tools.gtnh.calc.export;

import static org.junit.Assert.*;
import net.minecraft.nbt.*;
import org.junit.Test;

public class CanonicalNbtTest {
    @Test
    public void compoundOrderDoesNotChangeIdentity() {
        NBTTagCompound a = new NBTTagCompound(), b = new NBTTagCompound();
        a.setInteger("a", 1); a.setString("z", "quoted\"text");
        b.setString("z", "quoted\"text"); b.setInteger("a", 1);
        assertEquals(CanonicalNbt.text(a), CanonicalNbt.text(b));
    }

    @Test
    public void byteArrayContentsAndTagTypesRemainDistinct() {
        assertNotEquals(CanonicalNbt.text(new NBTTagByteArray(new byte[] {1})), CanonicalNbt.text(new NBTTagByteArray(new byte[] {2})));
        assertNotEquals(CanonicalNbt.text(new NBTTagByteArray(new byte[] {1})), CanonicalNbt.text(new NBTTagIntArray(new int[] {1})));
        assertNotEquals(CanonicalNbt.text(new NBTTagString("1")), CanonicalNbt.text(new NBTTagInt(1)));
        NBTTagList first = new NBTTagList(), second = new NBTTagList();
        first.appendTag(new NBTTagInt(1)); first.appendTag(new NBTTagInt(2));
        second.appendTag(new NBTTagInt(2)); second.appendTag(new NBTTagInt(1));
        assertNotEquals(CanonicalNbt.text(first), CanonicalNbt.text(second));
        assertEquals(2, first.tagCount());
    }
}
