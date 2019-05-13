package org.elkd.core.consensus.replication

import kotlinx.coroutines.*
import org.apache.log4j.Logger
import org.elkd.core.consensus.LeaderContext
import org.elkd.core.consensus.Raft
import kotlin.coroutines.CoroutineContext

/**
 * Replicator schedules replication of the raft context over the cluster.
 */
class Replicator @JvmOverloads constructor (
    private val raft: Raft,
    private val leaderContext: LeaderContext,
    private val replicationControllerFactory: ReplicationControllerFactory = ReplicationControllerFactory.DEFAULT) : CoroutineScope {
  private val job = Job()

  /**
   * There are a number of co-routines created in this module. To prevent leakage when the Replicator is no longer
   * needed, the consumer can simply cancel the parent job/context.
   */
  override val coroutineContext: CoroutineContext
    get() = job + Dispatchers.IO

  fun start() {
    LOG.info("initiating replication across ${raft.clusterSet}")

    /* launch replication workers to own replication for each specific target */
    raft.clusterSet.nodes.forEach {
      /* launch in scope so we can easily cancel all child coroutines when
        raft requests a state transition */
      val worker = replicationControllerFactory.create(it, leaderContext, raft, coroutineContext)
      launch {
        worker.start()
      }
    }
  }

  fun stop() {
    /* will cancel all coroutines in the scope */
    job.cancel()
  }

  companion object {
    private val LOG = Logger.getLogger(Replicator::class.java)
  }
}