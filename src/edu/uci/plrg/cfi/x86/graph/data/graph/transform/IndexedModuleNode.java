package edu.uci.plrg.cfi.x86.graph.data.graph.transform;

import edu.uci.plrg.cfi.x86.graph.data.application.ApplicationModule;
import edu.uci.plrg.cfi.x86.graph.data.graph.MetaNodeType;
import edu.uci.plrg.cfi.x86.graph.data.graph.NodeIdentifier;
import edu.uci.plrg.cfi.x86.graph.data.graph.modular.ModuleBasicBlock;
import edu.uci.plrg.cfi.x86.graph.data.graph.modular.ModuleNode;

public class IndexedModuleNode implements NodeIdentifier {

	public final ApplicationModule module;
	public final ModuleNode<?> node;
	public final int index;

	IndexedModuleNode(ApplicationModule module, ModuleNode<?> node, int index) {
		this.module = module;
		this.node = node;
		this.index = index;
	}

	@Override
	public ApplicationModule getModule() {
		return node.getModule();
	}

	@Override
	public int getRelativeTag() {
		return node.getRelativeTag();
	}

	@Override
	public int getInstanceId() {
		return node.getInstanceId();
	}

	@Override
	public long getHash() {
		return node.getHash();
	}

	@Override
	public MetaNodeType getType() {
		return node.getType();
	}

	IndexedModuleNode resetToVersionZero() {
		return new IndexedModuleNode(module, new ModuleBasicBlock(node.getModule(), node.getRelativeTag(), 0,
				node.getHash(), node.getType()), index);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((module.name == null) ? 0 : module.name.hashCode());
		result = prime * result + index;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		IndexedModuleNode other = (IndexedModuleNode) obj;
		if (!module.name.equals(other.module.name))
			return false;
		if (index != other.index)
			return false;
		return true;
	}

	@Override
	public String toString() {
		return node.toString();
	}
}
