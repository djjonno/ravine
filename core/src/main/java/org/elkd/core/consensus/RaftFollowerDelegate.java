package org.elkd.core.consensus;

import com.google.common.base.Preconditions;
import io.grpc.stub.StreamObserver;
import org.apache.log4j.Logger;
import org.elkd.core.consensus.messages.AppendEntriesRequest;
import org.elkd.core.consensus.messages.AppendEntriesResponse;
import org.elkd.core.consensus.messages.RequestVoteRequest;
import org.elkd.core.consensus.messages.RequestVoteResponse;

import javax.annotation.Nonnull;
import java.util.Timer;
import java.util.TimerTask;

class RaftFollowerDelegate implements RaftState {
  private static final Logger LOG = Logger.getLogger(RaftFollowerDelegate.class.getName());
  private static final int TIMEOUT_MS = 5000;
  private final Raft mRaft;

  private Timer mMonitor;

  RaftFollowerDelegate(@Nonnull final Raft raft) {
    mRaft = Preconditions.checkNotNull(raft, "raft");
  }

  @Override
  public void on() {
    LOG.info("ready");
    restartMonitor();
  }

  @Override
  public void off() {
    LOG.info("offline");
    stopMonitor();
  }

  @Override
  public void delegateAppendEntries(final AppendEntriesRequest appendEntriesRequest,
                                    final StreamObserver<AppendEntriesResponse> responseObserver) {
    responseObserver.onCompleted();
    restartMonitor();
  }

  @Override
  public void delegateRequestVote(final RequestVoteRequest requestVoteRequest,
                                  final StreamObserver<RequestVoteResponse> responseObserver) {
    responseObserver.onCompleted();
    restartMonitor();
  }

  private void restartMonitor() {
    stopMonitor();
    mMonitor = new Timer(false);
    mMonitor.schedule(new TimerTask() {
      @Override
      public void run() {
        mRaft.transition(RaftCandidateDelegate.class);
      }
    }, TIMEOUT_MS, TIMEOUT_MS);
  }

  private void stopMonitor() {
    if (mMonitor != null) {
      mMonitor.cancel();
    }
  }
}
