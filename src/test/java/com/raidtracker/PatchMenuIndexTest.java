package com.raidtracker;

import java.io.InputStream;
import org.junit.Assert;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

public class PatchMenuIndexTest {
	@Test
	public void masksTheNpcAndPlayerMenuIndex() throws Exception {
		byte[] original = resource("rl5.class");
		ClassLoader loader = PatchMenuIndexTest.class.getClassLoader();
		byte[] patched = PatchMenuIndex.patchClass(original, loader);
		Assert.assertEquals(2, maskedLookups(patched));
		Assert.assertArrayEquals(patched, PatchMenuIndex.patchClass(patched, loader));
	}

	private static int maskedLookups(byte[] classBytes) {
		ClassNode node = new ClassNode();
		new ClassReader(classBytes).accept(node, 0);
		int found = 0;
		for (MethodNode method : node.methods) {
			if (!"getNpc".equals(method.name) && !"getPlayer".equals(method.name)) {
				continue;
			}
			for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if (insn.getOpcode() != Opcodes.I2L) {
					continue;
				}
				AbstractInsnNode previous = insn.getPrevious();
				while (previous != null && previous.getOpcode() == -1) {
					previous = previous.getPrevious();
				}
				if (previous == null || previous.getOpcode() != Opcodes.IAND) {
					continue;
				}
				AbstractInsnNode constant = previous.getPrevious();
				while (constant != null && constant.getOpcode() == -1) {
					constant = constant.getPrevious();
				}
				if (constant instanceof LdcInsnNode && Integer.valueOf(65535).equals(((LdcInsnNode) constant).cst)) {
					found++;
				}
			}
		}
		return found;
	}

	private static byte[] resource(String name) throws Exception {
		try (InputStream in = PatchMenuIndexTest.class.getClassLoader().getResourceAsStream(name)) {
			Assert.assertNotNull(name, in);
			return in.readAllBytes();
		}
	}
}
