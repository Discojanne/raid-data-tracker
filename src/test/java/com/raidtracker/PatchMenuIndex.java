package com.raidtracker;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * The 2026-10-07 game update stores extra data in the high bits of an NPC menu
 * identifier. RuneLite 1.13.1 uses the whole value as the NPC index, so
 * {@code getNpc()} is null and Menu Entry Swapper's assertion kills the client
 * thread. 1.13.2 masks the index with {@code 0xFFFF}. This applies that mask to
 * the released client, which is the build the plugin hub publishes for.
 */
public final class PatchMenuIndex {
	private static final int NPC_INDEX_MASK = 65535;

	private PatchMenuIndex() {
	}

	public static void main(String[] args) throws IOException {
		if (args.length != 2) {
			throw new IllegalArgumentException("usage: PatchMenuIndex <injected-client.jar> <output.jar>");
		}
		Path input = Path.of(args[0]);
		Path output = Path.of(args[1]);
		if (output.getParent() != null) {
			Files.createDirectories(output.getParent());
		}
		URLClassLoader loader = new URLClassLoader(new URL[]{input.toUri().toURL()}, PatchMenuIndex.class.getClassLoader());
		try (JarFile jar = new JarFile(input.toFile());
			OutputStream fileOut = Files.newOutputStream(output);
			JarOutputStream out = new JarOutputStream(fileOut, jar.getManifest() == null ? new Manifest() : jar.getManifest())) {
			var entries = jar.entries();
			while (entries.hasMoreElements()) {
				JarEntry entry = entries.nextElement();
				if (entry.isDirectory() || "META-INF/MANIFEST.MF".equals(entry.getName())) {
					continue;
				}
				byte[] bytes;
				try (InputStream in = jar.getInputStream(entry)) {
					bytes = in.readAllBytes();
				}
				if ("rl5.class".equals(entry.getName())) {
					bytes = patchClass(bytes, loader);
				}
				JarEntry copy = new JarEntry(entry.getName());
				copy.setTime(entry.getTime());
				out.putNextEntry(copy);
				out.write(bytes);
				out.closeEntry();
			}
		} finally {
			loader.close();
		}
	}

	public static byte[] patchClass(byte[] classBytes, ClassLoader loader) {
		ClassNode node = new ClassNode();
		new ClassReader(classBytes).accept(node, 0);
		int patched = 0;
		int alreadyMasked = 0;
		for (MethodNode method : node.methods) {
			if (!"getNpc".equals(method.name) && !"getPlayer".equals(method.name)) {
				continue;
			}
			for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if (insn.getOpcode() != Opcodes.I2L) {
					continue;
				}
				AbstractInsnNode previous = previousReal(insn);
				if (previous != null && previous.getOpcode() == Opcodes.IAND) {
					alreadyMasked++;
					continue;
				}
				if (previous == null || previous.getOpcode() != Opcodes.ILOAD) {
					continue;
				}
				InsnList mask = new InsnList();
				mask.add(new LdcInsnNode(NPC_INDEX_MASK));
				mask.add(new InsnNode(Opcodes.IAND));
				method.instructions.insertBefore(insn, mask);
				patched++;
			}
		}
		if (patched == 0 && alreadyMasked == 2) {
			return classBytes;
		}
		if (patched != 2) {
			throw new IllegalStateException("Menu index lookups patched " + patched + ", already masked " + alreadyMasked);
		}
		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES) {
			@Override
			protected String getCommonSuperClass(String type1, String type2) {
				return commonSuper(type1, type2, loader);
			}
		};
		node.accept(writer);
		return writer.toByteArray();
	}

	private static AbstractInsnNode previousReal(AbstractInsnNode insn) {
		AbstractInsnNode previous = insn.getPrevious();
		while (previous != null && previous.getOpcode() == -1) {
			previous = previous.getPrevious();
		}
		return previous;
	}

	private static String commonSuper(String type1, String type2, ClassLoader loader) {
		try {
			Class<?> first = Class.forName(type1.replace('/', '.'), false, loader);
			Class<?> second = Class.forName(type2.replace('/', '.'), false, loader);
			if (first.isAssignableFrom(second)) {
				return type1;
			}
			if (second.isAssignableFrom(first)) {
				return type2;
			}
			if (first.isInterface() || second.isInterface()) {
				return "java/lang/Object";
			}
			do {
				first = first.getSuperclass();
			} while (first != null && !first.isAssignableFrom(second));
			if (first == null) {
				return "java/lang/Object";
			}
			return first.getName().replace('.', '/');
		} catch (Throwable ignored) {
			return "java/lang/Object";
		}
	}
}
