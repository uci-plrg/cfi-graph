package edu.uci.eecs.crowdsafe.graph.data.graph.transform;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.uci.eecs.crowdsafe.graph.data.application.ApplicationModule;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.ModuleNode;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.writer.ModuleDataWriter;

public class RawModuleData implements ModuleDataWriter.ModularData<IndexedModuleNode> {

	private final ApplicationModule module;

	private final Map<ModuleNode.Key, IndexedModuleNode> nodesByKey = new HashMap<ModuleNode.Key, IndexedModuleNode>();
	private final Map<Long, IndexedModuleNode> entryPointHashes = new HashMap<Long, IndexedModuleNode>();
	private final Map<Long, IndexedModuleNode> exitPointHashes = new HashMap<Long, IndexedModuleNode>();

	private final List<IndexedModuleNode> nodeList = new ArrayList<IndexedModuleNode>();
	private final Map<IndexedModuleNode, Integer> nodeIndexMap = new HashMap<IndexedModuleNode, Integer>();

	RawModuleData(ApplicationModule module) {
		this.module = module;
	}

	IndexedModuleNode addNode(ModuleNode<?> node) {
		IndexedModuleNode existing = null;
		switch (node.getType()) {
			case MODULE_ENTRY:
				existing = entryPointHashes.get(node.getHash());
				break;
			case MODULE_EXIT:
				existing = exitPointHashes.get(node.getHash());
				break;
			default:
				existing = nodesByKey.get(node.getKey());
		}
		if (existing != null)
			return existing;

		IndexedModuleNode rawNode = new IndexedModuleNode(module, node, nodeList.size());
		nodeIndexMap.put(rawNode, nodeList.size());
		nodeList.add(rawNode);
		switch (node.getType()) {
			case MODULE_ENTRY:
				entryPointHashes.put(node.getHash(), rawNode);
				break;
			case MODULE_EXIT:
				exitPointHashes.put(node.getHash(), rawNode);
				break;
			default:
				nodesByKey.put(node.getKey(), rawNode);
		}
		return rawNode;
	}

	void replace(IndexedModuleNode original, IndexedModuleNode replacement) {
		nodesByKey.put(replacement.node.getKey(), replacement);
		nodeList.set(replacement.index, replacement);
		nodeIndexMap.put(replacement, replacement.index);
	}

	@Override
	public ApplicationModule getModule() {
		return module;
	}

	IndexedModuleNode getNode(ModuleNode.Key key) {
		return nodesByKey.get(key);
	}

	@Override
	public int getNodeIndex(IndexedModuleNode node) {
		Integer index = nodeIndexMap.get(node);
		if (index == null) {
			return 0;
		}
		return index;
	}

	public Iterable<IndexedModuleNode> getSortedNodeList() {
		return nodeList;
	}

	int size() {
		return nodesByKey.size();
	}
}
