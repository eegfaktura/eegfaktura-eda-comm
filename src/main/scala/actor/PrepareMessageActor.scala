package at.energydash
package actor

import domain.eda.message.MessageHelper
import model.EbMsMessage

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}

object PrepareMessageActor {
  sealed trait Command[Reply <: CommandReply] {
    def replyTo: ActorRef[Reply]
  }
  sealed trait Event
  sealed trait CommandReply

  final case class IdInkremented(messageId: Long)
    extends Event with CborSerializable


  sealed trait PrepareMessageResult extends CommandReply
  case class Prepared(message: EbMsMessage) extends PrepareMessageResult
  final case class PrepareMessage(message: EbMsMessage, replyTo: ActorRef[PrepareMessageResult])
    extends Command[PrepareMessageResult]

  // state definition
  final case class Storage(messageId: Long = 0) {
    def applyEvent(event: Event): Storage = event match {
      case IdInkremented(messageId) =>
        copy(messageId = messageId)
    }

    def applyCommand(context: ActorContext[Command[_]], cmd: Command[_]): ReplyEffect[Event, Storage] = cmd match {
      case PrepareMessage(message, replyTo) =>
        val event = IdInkremented(messageId+1)
        val msgId = MessageHelper.buildMessageId(message.sender, event.messageId)
        val conversationId = MessageHelper.buildMessageId(message.sender, event.messageId+1)
        Effect.persist(event).thenReply(replyTo)(_ =>
          Prepared(message.copy(messageId = Some(msgId), conversationId=conversationId, requestId=Some(MessageHelper.buildRequestId(msgId)))))
    }
  }

  def apply(): Behavior[Command[_]] = Behaviors.setup { context =>
    EventSourcedBehavior.withEnforcedReplies[Command[_], Event, Storage](
      persistenceId = PersistenceId.ofUniqueId("preparemessage"),
      emptyState = Storage(),
      commandHandler = (state, cmd) => state.applyCommand(context, cmd),
      eventHandler = (state, evt) => state.applyEvent(evt))
  }
}
