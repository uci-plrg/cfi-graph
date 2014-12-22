package edu.uci.eecs.crowdsafe.graph.data.graph.cluster;

import edu.uci.eecs.crowdsafe.graph.data.dist.SoftwareModule;
import edu.uci.eecs.crowdsafe.graph.data.dist.SoftwareUnit;

/**
 * The containment relationship between a cluster and its modules should never be persisted anywhere, because it can be
 * configured outside the merge code. I'm not even putting the cluster here on the module at runtime to avoid
 * accidentally storing the assocataion somewhre.
 */
public class ClusterModule extends SoftwareModule {

	public final int id;

	public ClusterModule(int id, SoftwareUnit unit) {
		super(unit);

		this.id = id;
	}
}
