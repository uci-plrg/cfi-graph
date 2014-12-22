package edu.uci.eecs.crowdsafe.graph.data.graph.cluster.metadata;

import edu.uci.eecs.crowdsafe.graph.data.results.Graph;

public enum EvaluationType {

	TOTAL(0),
	ADMITTED(1),
	SUSPICIOUS(2);

	public final int id;

	private EvaluationType(int id) {
		this.id = id;
	}

	public Graph.EvaluationType getResultType() {
		switch (this) {
			case TOTAL:
				return Graph.EvaluationType.UIB_TOTAL;
			case ADMITTED:
				return Graph.EvaluationType.UIB_ADMITTED;
			case SUSPICIOUS:
				return Graph.EvaluationType.UIB_SUSPICIOUS;
		}
		return null;
	}

	static EvaluationType forId(int id) {
		switch (id) {
			case 0:
				return TOTAL;
			case 1:
				return ADMITTED;
			case 2:
				return SUSPICIOUS;
		}
		return null;
	}
}
