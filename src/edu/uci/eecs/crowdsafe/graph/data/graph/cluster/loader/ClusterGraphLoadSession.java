package edu.uci.eecs.crowdsafe.graph.data.graph.cluster.loader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import edu.uci.eecs.crowdsafe.common.exception.InvalidGraphException;
import edu.uci.eecs.crowdsafe.common.io.LittleEndianInputStream;
import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.graph.data.dist.AutonomousSoftwareDistribution;
import edu.uci.eecs.crowdsafe.graph.data.graph.Edge;
import edu.uci.eecs.crowdsafe.graph.data.graph.GraphLoadEventListener;
import edu.uci.eecs.crowdsafe.graph.data.graph.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.ClusterGraph;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.ClusterModuleList;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.ClusterNode;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.metadata.ClusterMetadataSequence;
import edu.uci.eecs.crowdsafe.graph.data.graph.execution.ExecutionNode;
import edu.uci.eecs.crowdsafe.graph.data.graph.execution.ProcessExecutionModuleSet;
import edu.uci.eecs.crowdsafe.graph.io.cluster.ClusterTraceDataSource;
import edu.uci.eecs.crowdsafe.graph.io.cluster.ClusterTraceStreamType;
import edu.uci.eecs.crowdsafe.graph.io.execution.ExecutionTraceDataSource;

public class ClusterGraphLoadSession {

	public interface ExecutionNodeCollection {
		void add(ExecutionNode node);
	}

	private final ClusterTraceDataSource dataSource;
	private final ClusterModuleLoader moduleLoader;

	public ClusterGraphLoadSession(ClusterTraceDataSource dataSource) {
		this.dataSource = dataSource;
		moduleLoader = new ClusterModuleLoader(dataSource);
	}

	public void loadNodes(ExecutionTraceDataSource dataSource, ExecutionNodeCollection collection,
			ProcessExecutionModuleSet modules) throws IOException {
		throw new UnsupportedOperationException("Can't load cluster nodes in isolation yet!");

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

	public ModuleGraphCluster<ClusterNode<?>> loadClusterGraph(AutonomousSoftwareDistribution cluster)
			throws IOException {
		return loadClusterGraph(cluster, null);
	}

	public ModuleGraphCluster<ClusterNode<?>> loadClusterGraph(AutonomousSoftwareDistribution cluster,
			GraphLoadEventListener listener) throws IOException {
		if (!dataSource.getReprsentedClusters().contains(cluster))
			return null;

		Log.log("Loading graph %s from %s", cluster, dataSource.getDirectory().getName());

		GraphLoader graphLoader = new GraphLoader(cluster, listener);
		return graphLoader.loadGraph();
	}

	class GraphLoader {
		final AutonomousSoftwareDistribution cluster;

		final GraphLoadEventListener listener;

		ClusterGraph builder;
		final List<ClusterNode<?>> nodeList = new ArrayList<ClusterNode<?>>();
		final List<Edge<ClusterNode<?>>> edgeList = new ArrayList<Edge<ClusterNode<?>>>();

		GraphLoader(AutonomousSoftwareDistribution cluster, GraphLoadEventListener listener) {
			this.cluster = cluster;
			this.listener = listener;
		}

		ModuleGraphCluster<ClusterNode<?>> loadGraph() throws IOException {
			long start = System.currentTimeMillis();

			ClusterModuleList modules = moduleLoader.loadModules(cluster, dataSource);
			builder = new ClusterGraph(String.format("cluster %s loaded from %s", cluster.getUnitFilename(), dataSource
					.getDirectory().getName()), cluster, modules);

			try {
				loadGraphNodes(modules);
				loadEdges(modules);
			} catch (IOException e) {
				throw e;
			} catch (Exception e) {
				throw new InvalidGraphException(e);
			}

			try {
				loadMetadata();
			} catch (Throwable t) {
				Log.log(t);
				Log.log("Warning: failed to load the metadata from graph %s", cluster.name);
			}

			Log.log("Cluster %s loaded in %f seconds.", cluster.name, (System.currentTimeMillis() - start) / 1000.);

			builder.graph.analyzeGraph(cluster.isAnonymous());
			// if (builder.graph.cluster.isDynamic())
			// builder.graph.logGraph();

			return builder.graph;
		}

		private void loadGraphNodes(ClusterModuleList modules) throws IOException {
			ClusterGraphNodeFactory nodeFactory = new ClusterGraphNodeFactory(modules,
					dataSource.getLittleEndianInputStream(cluster, ClusterTraceStreamType.GRAPH_NODE), listener);
			try {
				while (nodeFactory.ready()) {
					ClusterNode<?> node = nodeFactory.createNode();

					builder.graph.addNode(node);
					nodeList.add(node);

					if (listener != null)
						listener.graphAddition(node, builder.graph);
				}
			} finally {
				nodeFactory.close();
			}
		}

		private void loadEdges(ClusterModuleList modules) throws IOException {
			ClusterGraphEdgeFactory edgeFactory = new ClusterGraphEdgeFactory(nodeList,
					dataSource.getLittleEndianInputStream(cluster, ClusterTraceStreamType.GRAPH_EDGE));

			try {
				while (edgeFactory.ready()) {
					try {
						Edge<ClusterNode<?>> edge = edgeFactory.createEdge();

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
			LittleEndianInputStream input = dataSource.getLittleEndianInputStream(cluster, ClusterTraceStreamType.META);
			if ((input == null) || !input.ready())
				return;

			ClusterGraphMetadataLoader metadataLoader = new ClusterGraphMetadataLoader(edgeList, input);

			try {
				if (metadataLoader.ready()) {
					builder.graph.metadata.setMain(metadataLoader.isMetadataMain());

					while (metadataLoader.ready()) {
						ClusterMetadataSequence sequence = metadataLoader.loadSequence();
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
