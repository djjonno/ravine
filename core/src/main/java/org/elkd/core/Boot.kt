package org.elkd.core

import org.apache.log4j.Logger
import org.elkd.core.config.Config
import org.elkd.core.config.ConfigProvider
import org.elkd.core.consensus.ConsensusFacade
import org.elkd.core.consensus.RaftFactory
import org.elkd.core.log.LogFactory
import org.elkd.core.runtime.client.command.ClientCommandExecutor
import org.elkd.core.runtime.client.command.ClientCommandHandler
import org.elkd.core.runtime.client.stream.ClientStreamHandler
import org.elkd.core.runtime.topic.TopicFactory
import org.elkd.core.runtime.topic.TopicModule
import org.elkd.core.runtime.topic.TopicRegistry
import org.elkd.core.server.Server
import org.elkd.core.server.cluster.ClusterConnectionPool
import org.elkd.core.server.cluster.ClusterMessenger
import org.elkd.core.server.cluster.ClusterUtils
import org.elkd.core.server.cluster.StaticClusterSet

/**
 * Platform Boot
 *
 * Bootstrapping module - configure all runtime dependencies.
 */
internal class Boot(
    private val config: Config,
    private val consensusFacade: ConsensusFacade,
    private val server: Server
) {

  fun start() {
    val port = config.getAsInteger(Config.KEY_PORT)
    server.start(port)
    consensusFacade.initialize()
  }

  /**
   * Free-up runtime resources prior to shutdown.
   */
  fun shutdown() {
    server.shutdown()
    /* send a shutdown notification. e.g The Log may want to know to persist any in-memory cache structures. */
  }

  /**
   * A blocking call, awaiting runtime resource deallocation.  Call this prior to shutdown.
   */
  fun awaitTermination() {
    server.awaitTermination()
  }
}

/**
 * Elkd Bootstrapping
 */
fun main(args: Array<String>) {
  val logger = Logger.getLogger(Boot::class.java)
  val config = getConfig(args) ?: return

  /*
   * Cluster Set
   *
   * Currently only support for static cluster membership - no changes during runtime.
   */
  val clusterSet = StaticClusterSet.builder(ClusterUtils.buildSelfNode(config))
      .withString(config.get(Config.KEY_CLUSTER_SET))
      .build()
  val clusterConnectionPool = ClusterConnectionPool(clusterSet)
  clusterConnectionPool.initialize()

  /*
   * ClusterMessenger
   *
   * Communication interface for node-node messaging.
   */
  val clusterMessenger = ClusterMessenger(clusterConnectionPool)

  /*
   * Configure client systems.
   */
  val topicModule = TopicModule(TopicRegistry(), TopicFactory(LogFactory()))
  val consensusFacade = ConsensusFacade(RaftFactory.create(config, topicModule, clusterMessenger))
  val clientStreamHandler = ClientStreamHandler(consensusFacade, topicModule)
  val clientCommandHandler = ClientCommandHandler(ClientCommandExecutor(consensusFacade, topicModule))

  val boot = Boot(config, consensusFacade, Server(consensusFacade.delegator, clientCommandHandler, clientStreamHandler))

  try {
    with(boot) {
      Runtime.getRuntime().addShutdownHook(Thread(Runnable { shutdown() }))
      start()
      awaitTermination()
    }
  } catch (e: Exception) {
    logger.error("0_o, shutting down: ${e.message}")
  }
}

private fun getConfig(args: Array<String>): Config? {
  return try {
    ConfigProvider.compileConfig(args)
  } catch (e: Exception) {
    null
  }
}
