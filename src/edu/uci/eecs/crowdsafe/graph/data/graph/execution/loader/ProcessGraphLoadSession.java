package edu.uci.eecs.crowdsafe.graph.data.graph.execution.loader;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import edu.uci.eecs.crowdsafe.common.exception.InvalidGraphException;
import edu.uci.eecs.crowdsafe.common.exception.InvalidTagException;
import edu.uci.eecs.crowdsafe.graph.data.dist.AutonomousSoftwareDistribution;
import edu.uci.eecs.crowdsafe.graph.data.graph.Edge;
import edu.uci.eecs.crowdsafe.graph.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.graph.data.graph.GraphLoadEventListener;
import edu.uci.eecs.crowdsafe.graph.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.graph.data.graph.ModuleGraph;
import edu.uci.eecs.crowdsafe.graph.data.graph.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.graph.data.graph.execution.ExecutionNode;
import edu.uci.eecs.crowdsafe.graph.data.graph.execution.ProcessExecutionGraph;
import edu.uci.eecs.crowdsafe.graph.data.graph.execution.ProcessExecutionModuleSet;
import edu.uci.eecs.crowdsafe.graph.io.execution.ExecutionTraceDataSource;
import edu.uci.eecs.crowdsafe.graph.io.execution.ExecutionTraceStreamType;

public class ProcessGraphLoadSession {

	public interface ExecutionNodeCollection {
		void add(ExecutionNode node);
	}

	public void loadNodes(ExecutionTraceDataSource dataSource, ExecutionNodeCollection collection,
			ProcessExecutionModuleSet modules) throws IOException {
		ProcessGraphNodeFactory nodeFactory = new ProcessGraphNodeFactory(modules,
				dataSource.getLittleEndianInputStream(ExecutionTraceStreamType.GRAPH_NODE));

		try {
			while (nodeFactory.ready()) {
				collection.add(nodeFactory.createNode());
			}
		} finally {
			nodeFactory.close();
		}
	}

	public ProcessExecutionGraph loadGraph(ExecutionTraceDataSource dataSource, GraphLoadEventListener listener)
			throws IOException {
		GraphLoader graphLoader = new GraphLoader(dataSource, listener);
		return graphLoader.loadGraph();
	}

	class GraphLoader {
		final ExecutionTraceDataSource dataSource;
		final GraphLoadEventListener listener;

		final Map<ExecutionNode.Key, ExecutionNode> hashLookupTable = new HashMap<ExecutionNode.Key, ExecutionNode>();
		ProcessExecutionGraph graph;

		GraphLoader(ExecutionTraceDataSource dataSource, GraphLoadEventListener listener) {
			this.dataSource = dataSource;
			this.listener = listener;
		}

		ProcessExecutionGraph loadGraph() throws IOException {
			ProcessModuleLoader moduleLoader = new ProcessModuleLoader();
			ProcessExecutionModuleSet modules = moduleLoader.loadModules(dataSource);
			graph = new ProcessExecutionGraph(String.format("process graph loaded from %s", dataSource.getDirectory()
					.getName()), dataSource, modules);

			try {
				loadGraphNodes(modules);
				readIntraModuleEdges();
				readCrossModuleEdges();
			} catch (IOException e) {
				throw e;
			} catch (Exception e) {
				throw new InvalidGraphException(e);
			}

			graph.trimEmptyClusters();

			// Some other initialization and sanity checks
			for (AutonomousSoftwareDistribution cluster : graph.getRepresentedClusters()) {
				ModuleGraphCluster<ExecutionNode> clusterGraph = graph.getModuleGraphCluster(cluster);
				// clusterGraph.getGraphData().validate();
				// clusterGraph.analyzeGraph();
			}

			return graph;
		}

		private void loadGraphNodes(ProcessExecutionModuleSet modules) throws IOException {
			ProcessGraphNodeFactory nodeFactory = new ProcessGraphNodeFactory(modules,
					dataSource.getLittleEndianInputStream(ExecutionTraceStreamType.GRAPH_NODE));
			try {
				if (nodeFactory.ready()) {
					ExecutionNode node = nodeFactory.createNode();
					addNodeToGraph(node);
					createProcessEntryPoint(node);
				}

				while (nodeFactory.ready()) {
					ExecutionNode node = nodeFactory.createNode();
					addNodeToGraph(node);
				}
			} finally {
				nodeFactory.close();
			}
		}

		// 12% hot during load!
		private void addNodeToGraph(ExecutionNode node) {
			if (listener != null)
				listener.nodeCreation(node);

			// Tags don't duplicate in lookup file
			if (hashLookupTable.containsKey(node.getKey())) {
				ExecutionNode existingNode = hashLookupTable.get(node.getKey());
				if (existingNode.getHash() != node.getHash()) {
					String msg = String.format("Duplicate tags: %s -> %s in datasource %s", node.getKey(),
							existingNode, dataSource.toString());
					throw new InvalidTagException(msg);
				}
				return;
			}

			ModuleGraphCluster<ExecutionNode> moduleCluster = graph.getModuleGraphCluster(node.getModule().unit);
			ModuleGraph moduleGraph = moduleCluster.getModuleGraph(node.getModule().unit);
			if (moduleGraph == null) {
				moduleGraph = new ModuleGraph(node.getModule().unit);
				moduleCluster.addModule(moduleGraph);
			}
			moduleCluster.addNode(node);
			hashLookupTable.put(node.getKey(), node);

			if (listener != null)
				listener.graphAddition(node, moduleCluster);
		}

		private void createProcessEntryPoint(ExecutionNode node) {
			ExecutionNode entryNode = new ExecutionNode(node.getModule(), MetaNodeType.CLUSTER_ENTRY, 0L, 0, 1L,
					node.getTimestamp());
			graph.getModuleGraphCluster(node.getModule().unit).addClusterEntryNode(entryNode);
			Edge<ExecutionNode> clusterEntryEdge = new Edge<ExecutionNode>(entryNode, node, EdgeType.DIRECT, 0);
			entryNode.addOutgoingEdge(clusterEntryEdge);
			node.addIncomingEdge(clusterEntryEdge);

			if (listener != null)
				listener.edgeCreation(clusterEntryEdge);
		}

		private void readIntraModuleEdges() throws IOException {
			ProcessGraphEdgeFactory edgeFactory = new ProcessGraphEdgeFactory(this,
					dataSource.getLittleEndianInputStream(ExecutionTraceStreamType.GRAPH_EDGE));

			try {
				while (edgeFactory.ready())
					edgeFactory.createEdge();
			} finally {
				edgeFactory.close();
			}
		}

		private void readCrossModuleEdges() throws IOException {
			ProcessGraphCrossModuleEdgeFactory crossModuleEdgeFactory = new ProcessGraphCrossModuleEdgeFactory(this,
					dataSource.getLittleEndianInputStream(ExecutionTraceStreamType.CROSS_MODULE_EDGE));

			try {
				while (crossModuleEdgeFactory.ready()) {
					crossModuleEdgeFactory.createEdge();
				}
			} finally {
				crossModuleEdgeFactory.close();
			}
		}
	}
}
