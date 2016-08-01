package edu.uci.plrg.cfi.x86.graph.data.graph.modular.loader;

import java.io.IOException;

import edu.uci.plrg.cfi.common.io.LittleEndianInputStream;
import edu.uci.plrg.cfi.common.log.Log;
import edu.uci.plrg.cfi.x86.graph.data.application.ApplicationModule;
import edu.uci.plrg.cfi.x86.graph.data.graph.GraphLoadEventListener;
import edu.uci.plrg.cfi.x86.graph.data.graph.MetaNodeType;
import edu.uci.plrg.cfi.x86.graph.data.graph.modular.ModuleBasicBlock;
import edu.uci.plrg.cfi.x86.graph.data.graph.modular.ModuleBoundaryNode;
import edu.uci.plrg.cfi.x86.graph.data.graph.modular.ModuleNode;

public class ModuleGraphNodeFactory {

	private static final int ENTRY_BYTE_COUNT = 0x10;

	private final ApplicationModule module;
	private final LittleEndianInputStream input;

	private final GraphLoadEventListener listener;

	ModuleGraphNodeFactory(ApplicationModule module, LittleEndianInputStream input, GraphLoadEventListener listener) {
		this.input = input;
		this.module = module;
		this.listener = listener;
	}

	boolean ready() throws IOException {
		return input.ready(ENTRY_BYTE_COUNT);
	}

	ModuleNode<?> createNode() throws IOException {
		long first = input.readLong();
		// int moduleIndex = (int) (first & 0xffffL); // TODO: no need to write this anymore
		long relativeTag = ((first >> 0x10) & 0xffffffffL);
		int instanceId = (int) ((first >> 0x30) & 0xffL);

		MetaNodeType type = MetaNodeType.values()[(int) ((first >> 0x38) & 0xffL)];

		long hash = input.readLong();
		
		ModuleNode<?> node = null;

		switch (type) {
			case MODULE_ENTRY:
			case MODULE_EXIT:
				node = new ModuleBoundaryNode(hash, type);
				break;
			default:
				node = new ModuleBasicBlock(module, relativeTag, instanceId, hash, type);
		}

		if (listener != null)
			listener.nodeCreation(node);

		return node;
	}

	void close() throws IOException {
		if (input.ready())
			Log.log("Warning: input stream %s has %d bytes remaining.", input.description, input.available());

		input.close();
	}
}
