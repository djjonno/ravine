package org.elkd.core.consensus

import io.grpc.stub.StreamObserver
import org.apache.log4j.Logger
import org.elkd.core.consensus.messages.*
import org.elkd.core.consensus.replication.Replicator
import org.elkd.core.log.LogChangeReason
import org.elkd.core.log.commands.AppendCommand

class RaftLeaderDelegate(private val raft: Raft) : RaftState {
  private var replicator: Replicator? = null

  override fun on() {
    /* for test sake, append a new entry to the log here so we have something to replicate */
    val leaderContext = LeaderContext(raft.clusterSet.nodes, raft.log.lastIndex)
    val command = AppendCommand.build(
        Entry.builder(raft.raftContext.currentTerm, raft.clusterSet.localNode.id).build(),
        LogChangeReason.CLIENT)
    raft.logCommandExecutor.execute(command)

    replicator = Replicator(raft, leaderContext)
    replicator?.start()
  }

  override fun off() {
    LOG.info("leader offline")
    /* Force-stop the replication process - we must honor the transition */
    replicator?.stop()
  }

  override fun delegateAppendEntries(request: AppendEntriesRequest,
                                     responseObserver: StreamObserver<AppendEntriesResponse>) {
    responseObserver.onNext(AppendEntriesResponse.builder(raft.raftContext.currentTerm, false).build())
    responseObserver.onCompleted()
  }

  override fun delegateRequestVote(request: RequestVoteRequest,
                                   responseObserver: StreamObserver<RequestVoteResponse>) {
    /* If term > currentTerm, Raft will always transition to Follower state. messages received
       here will only be term <= currentTerm so we can defer all logic to the consensus delegate.
     */
    responseObserver.onNext(RequestVoteResponse.builder(raft.raftContext.currentTerm, false).build())
    responseObserver.onCompleted()
  }

  companion object {
    private val LOG = Logger.getLogger(RaftLeaderDelegate::class.java)
  }
}
