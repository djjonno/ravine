package org.elkd.core.consensus;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import org.elkd.core.server.cluster.ClusterMessenger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;

public class DefaultStateFactory implements AbstractStateFactory {
  static final Class<? extends RaftState> INITIAL_STATE = RaftFollowerDelegate.class;

  private static Map<Class<? extends RaftState>, RaftState> mStateRegistry;

  public DefaultStateFactory() { }

  @Override
  public RaftState getInitialDelegate(@Nonnull final Raft raft) {
    Preconditions.checkNotNull(raft, "raft");
    return getDelegate(raft, INITIAL_STATE);
  }

  @Override
  @Nullable
  public RaftState getDelegate(@Nonnull final Raft raft, @Nonnull final Class klass) {
    Preconditions.checkNotNull(raft, "raft");
    Preconditions.checkNotNull(klass, "klass");

    if (mStateRegistry == null) {
      mStateRegistry = ImmutableMap.of(
          RaftFollowerDelegate.class, createFollowerState(raft),
          RaftCandidateDelegate.class, createCandidateState(raft),
          RaftLeaderDelegate.class, createLeaderState(raft)
      );
    }

    return mStateRegistry.get(klass);
  }

  private RaftState createFollowerState(@Nonnull final Raft raft) {
    return new RaftFollowerDelegate(raft);
  }

  private RaftState createCandidateState(@Nonnull final Raft raft) {
    return new RaftCandidateDelegate(raft);
  }

  private RaftState createLeaderState(@Nonnull final Raft raft) {
    return new RaftLeaderDelegate(raft, new ClusterMessenger(raft.getClusterConnectionPool()));
  }
}
