package edu.uci.plrg.cfi.x86.graph.data.graph.modular.loader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import edu.uci.plrg.cfi.common.exception.InvalidGraphException;
import edu.uci.plrg.cfi.common.io.LittleEndianInputStream;
import edu.uci.plrg.cfi.common.log.Log;
import edu.uci.plrg.cfi.common.util.MutableInteger;
import edu.uci.plrg.cfi.x86.graph.data.application.ApplicationModule;
import edu.uci.plrg.cfi.x86.graph.data.graph.Edge;
import edu.uci.plrg.cfi.x86.graph.data.graph.GraphLoadEventListener;
import edu.uci.plrg.cfi.x86.graph.data.graph.ModuleGraph;
import edu.uci.plrg.cfi.x86.graph.data.graph.anonymous.AnonymousGraph;
import edu.uci.plrg.cfi.x86.graph.data.graph.anonymous.ApplicationAnonymousGraphs;
import edu.uci.plrg.cfi.x86.graph.data.graph.anonymous.ModuleAnonymousGraphs;
import edu.uci.plrg.cfi.x86.graph.data.graph.execution.ExecutionNode;
import edu.uci.plrg.cfi.x86.graph.data.graph.execution.ProcessExecutionModuleSet;
import edu.uci.plrg.cfi.x86.graph.data.graph.modular.ApplicationGraph;
import edu.uci.plrg.cfi.x86.graph.data.graph.modular.ModuleNode;
import edu.uci.plrg.cfi.x86.graph.data.graph.modular.metadata.ModuleMetadataSequence;
import edu.uci.plrg.cfi.x86.graph.io.execution.ExecutionTraceDataSource;
import edu.uci.plrg.cfi.x86.graph.io.modular.ModularTraceDataSource;
import edu.uci.plrg.cfi.x86.graph.io.modular.ModularTraceStreamType;

public class ModuleGraphLoadSession {

	public interface ExecutionNodeCollection {
		void add(ExecutionNode node);
	}

	private final ModularTraceDataSource dataSource;

	public ModuleGraphLoadSession(ModularTraceDataSource dataSource) {
		this.dataSource = dataSource;
	}

	public void loadNodes(ExecutionTraceDataSource dataSource, ExecutionNodeCollection collection,
			ProcessExecutionModuleSet modules) throws IOException {
		throw new UnsupportedOperationException("Can't load modular nodes in isolation yet!");

		/**
		 * <pre>
		ProcessGraphNodeFactory nodeFactory = new ProcessGraphNodeFactory(modules,
				dataSource.getLittleEndianInputStream(ExecutionTraceStreamType.GRAPH_NODE));

		try {
			while (nodeFactory.ready()) {
				collection.add(nodeFactory.createNode());
			}
		} finally {
			nodeFactory.close();
		}
		 */
	}

	public ModuleGraph<ModuleNode<?>> loadModuleGraph(ApplicationModule module) throws IOException {
		return loadModuleGraph(module, null);
	}

	public ModuleGraph<ModuleNode<?>> loadModuleGraph(ApplicationModule module, GraphLoadEventListener listener)
			throws IOException {
		if (!dataSource.getReprsentedModules().contains(module))
			return null;

		//if (module.isAnonymous)
		//	throw new IllegalArgumentException("Cannot load the anonymous graphs as an application module.");

		Log.log("Loading graph %s from %s", module, dataSource.getDirectory().getName());

		GraphLoader graphLoader = new GraphLoader(module, listener);
		return graphLoader.loadGraph();
	}

	public ApplicationAnonymousGraphs loadAnonymousGraphs() throws IOException {
		return loadAnonymousGraphs(null);
	}

	public ApplicationAnonymousGraphs loadAnonymousGraphs(GraphLoadEventListener listener)
			throws IOException {
		ApplicationAnonymousGraphs graphs = new ApplicationAnonymousGraphs();
		AnonymousGraphLoader loader = new AnonymousGraphLoader(listener);
		while (loader.ready()) {
			AnonymousGraph graph = loader.loadGraph();
			ApplicationModule owner = AnonymousGraph.identifyOwner(graph);
			graphs.addGraph(graph, owner);
		}
		return graphs;
	}

	class GraphLoader {
		final ApplicationModule module;

		final GraphLoadEventListener listener;

		ApplicationGraph builder;
		final List<ModuleNode<?>> nodeList = new ArrayList<ModuleNode<?>>();
		final List<Edge<ModuleNode<?>>> edgeList = new ArrayList<Edge<ModuleNode<?>>>();

		GraphLoader(ApplicationModule module, GraphLoadEventListener listener) {
			this.module = module;
			this.listener = listener;
		}

		ModuleGraph<ModuleNode<?>> loadGraph() throws IOException {
			long start = System.currentTimeMillis();

			builder = new ApplicationGraph(String.format("cluster %s loaded from %s", module.filename, dataSource
					.getDirectory().getName()), module);

			try {
				loadGraphNodes();
				loadEdges();
			} catch (IOException e) {
				throw e;
			} catch (Exception e) {
				throw new InvalidGraphException(e);
			}

			try {
				loadMetadata();
			} catch (Throwable t) {
				Log.log(t);
				Log.log("Warning: failed to load the metadata from graph %s", module.name);
			}

			Log.log("Cluster %s loaded in %f seconds.", module.name, (System.currentTimeMillis() - start) / 1000.);

			// builder.graph.analyzeGraph(cluster.isAnonymous());
			// if (builder.graph.cluster.isDynamic())
			// builder.graph.logGraph();

			return builder.graph;
		}

		private void loadGraphNodes() throws IOException {
			ModuleGraphNodeFactory nodeFactory = new ModuleGraphNodeFactory(module,
					dataSource.getLittleEndianInputStream(module, ModularTraceStreamType.GRAPH_NODE), listener);
			try {
				while (nodeFactory.ready()) {
					ModuleNode<?> node = nodeFactory.createNode();

					builder.graph.addNode(node);
					nodeList.add(node);

					if (listener != null)
						listener.graphAddition(node, builder.graph);
				}
			} finally {
				nodeFactory.close();
			}
		}

		private void loadEdges() throws IOException {
			ModuleGraphEdgeFactory edgeFactory = new ModuleGraphEdgeFactory(nodeList,
					dataSource.getLittleEndianInputStream(module, ModularTraceStreamType.GRAPH_EDGE));

			try {
				while (edgeFactory.ready()) {
					try {
						Edge<ModuleNode<?>> edge = edgeFactory.createEdge();

						if (listener != null)
							listener.edgeCreation(edge);

						edgeList.add(edge);
					} catch (Throwable t) {
						Log.log("%s while creating an edge. Skipping it for now! Message: %s", t.getClass()
								.getSimpleName(), t.getMessage());

						edgeList.add(null);
					}
				}
			} finally {
				edgeFactory.close();
			}
		}

		private void loadMetadata() throws IOException {
			LittleEndianInputStream input = dataSource.getLittleEndianInputStream(module, ModularTraceStreamType.META);
			if ((input == null) || !input.ready())
				return;

			ModuleGraphMetadataLoader metadataLoader = new ModuleGraphMetadataLoader(edgeList, input);

			try {
				if (metadataLoader.ready()) {
					builder.graph.metadata.setMain(metadataLoader.isMetadataMain());

					while (metadataLoader.ready()) {
						ModuleMetadataSequence sequence = metadataLoader.loadSequence();
						if (sequence != null)
							builder.graph.metadata.mergeSequence(sequence);
					}
				}
			} finally {
				metadataLoader.close();
			}
		}
	}

	class AnonymousGraphLoader {
		final GraphLoadEventListener listener;

		final ApplicationModule module = ApplicationModule.ANONYMOUS_MODULE;

		final ModuleGraphNodeFactory nodeFactory;
		final ModuleGraphEdgeFactory edgeFactory;

		final List<ModuleNode<?>> nodeList = new ArrayList<ModuleNode<?>>();
		private MutableInteger subgraphNodeStartIndex = new MutableInteger(0);
		private ModuleNode<?> lastNode = null;

		private int graphCount = 0;

		public AnonymousGraphLoader(GraphLoadEventListener listener) throws IOException {
			this.listener = listener;

			nodeFactory = new ModuleGraphNodeFactory(module, dataSource.getLittleEndianInputStream(module,
					ModularTraceStreamType.GRAPH_NODE), listener);
			edgeFactory = new ModuleGraphEdgeFactory(nodeList, dataSource.getLittleEndianInputStream(module,
					ModularTraceStreamType.GRAPH_EDGE));
			edgeFactory.activateSegmentedLoading(subgraphNodeStartIndex);
		}

		boolean ready() throws IOException {
			return nodeFactory.ready();
		}

		AnonymousGraph loadGraph() throws IOException {
			AnonymousGraph graph = new AnonymousGraph(String.format("Subgraph #%d loaded from %s", graphCount++,
					dataSource.getDirectory().getAbsolutePath()));

			if (lastNode != null) {
				nodeList.add(lastNode);
				graph.addNode(lastNode);
			}

			while (nodeFactory.ready()) {
				ModuleNode<?> node = nodeFactory.createNode();

				// identifies the start of the next subgraph
				if (node.isModuleBoundaryNode() && lastNode != null && !lastNode.isModuleBoundaryNode()) {
					lastNode = node;
					break;
				}

				graph.addNode(node);
				nodeList.add(node);
				lastNode = node;

				if (listener != null)
					listener.graphAddition(node, graph);
			}

			while (edgeFactory.ready()) {
				try {
					Edge<ModuleNode<?>> edge = edgeFactory.createEdge();

					if (edge == null)
						break; // start of the next subgraph (can't be loaded until the nodes are loaded above)

					if (listener != null)
						listener.edgeCreation(edge);

				} catch (Throwable t) {
					Log.log("%s while creating an edge. Skipping it for now! Message: %s",
							t.getClass().getSimpleName(), t.getMessage());
				}
			}

			subgraphNodeStartIndex.setVal(subgraphNodeStartIndex.getVal() + nodeList.size());
			nodeList.clear();

			return graph;
		}

		void close() throws IOException {
			nodeFactory.close();
			edgeFactory.close();
		}
	}
}
