package edu.uci.eecs.crowdsafe.graph.data.graph.modular.loader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import edu.uci.eecs.crowdsafe.common.exception.InvalidGraphException;
import edu.uci.eecs.crowdsafe.common.io.LittleEndianInputStream;
import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.graph.data.application.ApplicationModule;
import edu.uci.eecs.crowdsafe.graph.data.graph.Edge;
import edu.uci.eecs.crowdsafe.graph.data.graph.GraphLoadEventListener;
import edu.uci.eecs.crowdsafe.graph.data.graph.ModuleGraph;
import edu.uci.eecs.crowdsafe.graph.data.graph.anonymous.ModuleAnonymousGraphs;
import edu.uci.eecs.crowdsafe.graph.data.graph.execution.ExecutionNode;
import edu.uci.eecs.crowdsafe.graph.data.graph.execution.ProcessExecutionModuleSet;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.ApplicationGraph;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.ModuleNode;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.metadata.ModuleMetadataSequence;
import edu.uci.eecs.crowdsafe.graph.io.execution.ExecutionTraceDataSource;
import edu.uci.eecs.crowdsafe.graph.io.modular.ModularTraceDataSource;
import edu.uci.eecs.crowdsafe.graph.io.modular.ModularTraceStreamType;

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

		if (module.isAnonymous)
			throw new IllegalArgumentException("Cannot load the anonymous graphs as an application module.");

		Log.log("Loading graph %s from %s", module, dataSource.getDirectory().getName());

		GraphLoader graphLoader = new GraphLoader(module, listener);
		return graphLoader.loadGraph();
	}

	public ModuleAnonymousGraphs loadAnonymousGraphs(ApplicationModule module, GraphLoadEventListener listener) {
		if (!module.isAnonymous)
			throw new IllegalArgumentException("Cannot load a statically compiled module as a set of anonymous graphs.");
		
		return null;
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
}
