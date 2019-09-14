package org.elkd.core.client

import io.grpc.ManagedChannelBuilder
import org.elkd.core.server.client.ElkdClientServiceGrpc
import org.elkd.core.server.client.RpcArgPair
import org.elkd.core.server.client.RpcClientCommandRequest
import org.elkd.core.server.client.RpcClientCommandResponse
import java.util.concurrent.Future

/**
 * Client which executes calls against server
 */
fun main() {
  val stub = ElkdClientServiceGrpc.newFutureStub(
      ManagedChannelBuilder.forAddress("localhost", 9191).usePlaintext().build()
  )

  var count = 0
  var future: Future<RpcClientCommandResponse>?
  do {
    var count2 = 0
    val futures = mutableListOf<Future<RpcClientCommandResponse>>()
    do {
      futures.add(stub.clientCommand(RpcClientCommandRequest.newBuilder()
          .setCommand("create-topic")
          .addAllArgs(listOf(RpcArgPair.newBuilder()
              .setArg("namespace")
              .setParam("dummy")
              .build()))
          .build()))
    } while (++count < 100)
    futures.forEach { println(it.get()) }
  } while (++count < 10)
}
