package org.kerala.core.runtime.client.ctl

import io.grpc.stub.StreamObserver
import org.kerala.core.concurrency.Pools
import org.kerala.core.consensus.OpCategory
import org.kerala.shared.client.ClientACK
import org.kerala.core.server.client.RpcCommandRequest
import org.kerala.core.server.client.RpcCommandResponse
import org.kerala.shared.client.CtlErrorResponse
import org.kerala.shared.json.GsonUtils

/**
 * Executes client writeCommands received from client connections
 */
class CtlCommandHandler(
    private val ctlCommandExecutor: CtlCommandExecutor
) {

  fun handle(request: RpcCommandRequest, response: StreamObserver<RpcCommandResponse>) =
      Pools.clientRequestPool.execute {
        val opCategory = if (request.command in CtlCommandType.writeCommands) OpCategory.WRITE else OpCategory.READ
        commandHandler(opCategory, request, response)
      }

  /**
   * Write Commands are executed on @syslog
   */
  private fun commandHandler(opCategory: OpCategory, request: RpcCommandRequest, response: StreamObserver<RpcCommandResponse>) {
    try {
      ctlCommandExecutor.execute(CtlCommandPack(
          opCategory = opCategory,
          command = parseCommand(request),
          onComplete = { message ->
            response.onNext(ClientACK.Rpcs.ok(message))
            response.onCompleted()
          },
          onError = { error ->
            response.onNext(ClientACK.Rpcs.error(error))
            response.onCompleted()
          }
      ))
    } catch (e: Exception) {
      returnError(response, e.message ?: "unknown error")
    }
  }

  private fun returnError(response: StreamObserver<RpcCommandResponse>, message: String) {
    response.onNext(ClientACK.Rpcs.error(GsonUtils.buildGson().toJson(CtlErrorResponse(message))))
    response.onCompleted()
  }

  private fun parseCommand(request: RpcCommandRequest): CtlCommand {
    val type = CtlCommandType.fromId(request.command)
    return type.parser(type, request)
  }
}