package edu.uci.eecs.crowdsafe.graph.data.graph.anonymous;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.graph.data.application.ApplicationModule;
import edu.uci.eecs.crowdsafe.graph.data.application.ApplicationModuleSet;
import edu.uci.eecs.crowdsafe.graph.data.application.ApplicationModule;
import edu.uci.eecs.crowdsafe.graph.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.graph.data.graph.ModuleGraph;
import edu.uci.eecs.crowdsafe.graph.data.graph.OrdinalEdgeList;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.ModuleBoundaryNode;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.ModuleNode;
import edu.uci.eecs.crowdsafe.graph.util.EdgeCounter;

public class ModuleAnonymousGraphs {

	public static class OwnerKey {
		public final ApplicationModule module;
		public final boolean isJIT;

		public OwnerKey(ApplicationModule module, boolean isJIT) {
			this.module = module;
			this.isJIT = isJIT;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((module == null) ? 0 : module.hashCode());
			result = prime * result + (isJIT ? 1231 : 1237);
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
			OwnerKey other = (OwnerKey) obj;
			if (module == null) {
				if (other.module != null)
					return false;
			} else if (!module.equals(other.module))
				return false;
			if (isJIT != other.isJIT)
				return false;
			return true;
		}
	}

	private static final File DOT_DIRECTORY = new File("./dot");

	public final ApplicationModule owningModule;
	public final List<AnonymousGraph> subgraphs = new ArrayList<AnonymousGraph>();

	private int totalNodeCount = 0;
	private int executableNodeCount = 0;
	private boolean isJIT = false;

	public ModuleAnonymousGraphs(ApplicationModule owningModule) {
		this.owningModule = owningModule;
	}

	public void addSubgraph(AnonymousGraph subgraph) {
		if (subgraphs.isEmpty()) {
			isJIT = subgraph.isJIT();
		} else if (subgraph.isJIT() != isJIT) {
			if (isJIT) {
				throw new IllegalArgumentException("Attempt to add a white box subgraph to a black box module!");
			} else {
				throw new IllegalArgumentException("Attempt to add a black box subgraph to a white box module!");
			}
		}

		if (isJIT && !subgraphs.isEmpty()) {
			Log.error("Attempting to add a second subgraph to a black box module. Discarding it.");
			return;
			// throw new IllegalArgumentException("Cannot add a second subgraph to a black box module!");
		}

		subgraphs.add(subgraph);

		totalNodeCount += subgraph.getNodeCount();
		executableNodeCount += subgraph.getExecutableNodeCount();
	}

	public void replaceSubgraph(AnonymousGraph replaceMe, AnonymousGraph withMe) {
		subgraphs.set(subgraphs.indexOf(replaceMe), withMe);
		totalNodeCount -= replaceMe.getNodeCount();
		totalNodeCount += withMe.getNodeCount();
		executableNodeCount -= replaceMe.getExecutableNodeCount();
		executableNodeCount += withMe.getExecutableNodeCount();
	}

	public int getNodeCount() {
		return totalNodeCount;
	}

	public int getExecutableNodeCount() {
		return executableNodeCount;
	}

	public boolean isJIT() {
		return isJIT;
	}

	boolean hasEscapes(ModuleGraph<ModuleNode<?>> subgraph) {
		for (ModuleNode<?> entry : subgraph.getEntryPoints()) {
			if (!ApplicationModuleSet.getInstance().getFromModuleName(entry.getHash()).equals(owningModule.name))
				return true;
		}
		for (ModuleNode<?> exit : subgraph.getExitPoints()) {
			if (!ApplicationModuleSet.getInstance().getToModuleName(exit.getHash()).equals(owningModule.name))
				return true;
		}
		return false;
	}

	void printDotFiles() throws IOException {
		File outputDirectory = new File(DOT_DIRECTORY, owningModule.filename);
		for (AnonymousGraph subgraph : subgraphs) {
			String basename = isJIT ? "jit" : "sdr";
			File outputFile = new File(outputDirectory, String.format("%s.%d.gv", basename, subgraph.id));
			String label = String.format("%s %s #%d", owningModule.filename, basename, subgraph.id);
			subgraph.writeDotFile(outputFile, label);
		}
	}

	void reportEdgeProfile() {
		Log.log("    --- Edge Profile ---");

		EdgeCounter edgeCountsByType = new EdgeCounter();
		EdgeCounter edgeOrdinalCountsByType = new EdgeCounter();

		int maxIndirectEdgeCountPerOrdinal = 0;
		int singletonIndirectOrdinalCount = 0;
		int pairIndirectOrdinalCount = 0;
		int totalOrdinals = 0;
		int totalEdges = 0;
		int ordinalCount;
		EdgeType edgeType;

		for (AnonymousGraph subgraph : subgraphs) {
			for (ModuleNode<?> node : subgraph.getAllNodes()) {
				if (node.getType().isExecutable) {
					ordinalCount = node.getOutgoingOrdinalCount();
					totalOrdinals += ordinalCount;
					for (int ordinal = 0; ordinal < ordinalCount; ordinal++) {
						OrdinalEdgeList<ModuleNode<?>> edges = node.getOutgoingEdges(ordinal);
						try {
							if (edges.isEmpty())
								continue;

							edgeType = edges.get(0).getEdgeType();
							edgeCountsByType.tally(edgeType, edges.size());
							edgeOrdinalCountsByType.tally(edgeType);
							totalEdges += edges.size();

							if (edgeType == EdgeType.INDIRECT) {
								if (edges.size() > maxIndirectEdgeCountPerOrdinal)
									maxIndirectEdgeCountPerOrdinal = edges.size();
								if (edges.size() == 1)
									singletonIndirectOrdinalCount++;
								else if (edges.size() == 2)
									pairIndirectOrdinalCount++;
							}
						} finally {
							edges.release();
						}
					}
				}
			}
		}

		Log.log("     Total edges: %d; Total ordinals: %d", totalEdges, totalOrdinals);

		int instances;
		int ordinals;
		int instancePercentage;
		int ordinalPercentage;
		Set<EdgeType> reportedEdgeTypes = EnumSet.of(EdgeType.DIRECT, EdgeType.CALL_CONTINUATION,
				EdgeType.EXCEPTION_CONTINUATION, EdgeType.INDIRECT, EdgeType.UNEXPECTED_RETURN);
		for (EdgeType type : reportedEdgeTypes) {
			instances = edgeCountsByType.getCount(type);
			ordinals = edgeOrdinalCountsByType.getCount(type);
			instancePercentage = Math.round((instances / (float) totalEdges) * 100f);
			ordinalPercentage = Math.round((ordinals / (float) totalOrdinals) * 100f);
			Log.log("     Edge type %s: %d total edges (%d%%), %d ordinals (%d%%)", type.name(), instances,
					instancePercentage, ordinals, ordinalPercentage);
		}

		int indirectTotal = edgeCountsByType.getCount(EdgeType.INDIRECT);
		float averageIndirectEdgeCount = (indirectTotal / (float) edgeOrdinalCountsByType.getCount(EdgeType.INDIRECT));
		int singletonIndirectPercentage = Math.round((singletonIndirectOrdinalCount / (float) indirectTotal) * 100f);
		int pairIndirectPercentage = Math.round((pairIndirectOrdinalCount / (float) indirectTotal) * 100f);
		Log.log("     Average indirect edge fanout: %.3f; Max: %d; singletons: %d (%d%%); pairs: %d (%d%%)",
				averageIndirectEdgeCount, maxIndirectEdgeCountPerOrdinal, singletonIndirectOrdinalCount,
				singletonIndirectPercentage, pairIndirectOrdinalCount, pairIndirectPercentage);
	}
}
