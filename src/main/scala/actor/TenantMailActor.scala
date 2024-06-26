package at.energydash
package actor

import actor.MqttPublisher.{EdaNotification, MqttCommand, MqttPublish, MqttPublishError}
import domain.dao.{SlickEmailOutboxRepository, TenantConfig}
import domain.eda.message.EdaErrorMessage
import domain.email.EmailService.{SendEmailCommand, SendEmailResponse, SendErrorResponse}
import domain.email.Fetcher.{ErrorMessage, FetcherContext, MailContent, MailMessage}
import domain.email.{ConfiguredMailer, Fetcher}

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.util.Timeout
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success, Try}


class TenantMailActor(tenantConfig: TenantConfig, messageStore: ActorRef[MessageStorage.Command[_]], mailRepo: SlickEmailOutboxRepository) {

  import TenantMailActor._

  implicit val timeout: Timeout = Timeout(5.seconds)
  var logger: Logger = LoggerFactory.getLogger(classOf[TenantMailActor])

  var tenant: String = tenantConfig.tenant
//  val config: com.typesafe.config.Config = Config.getMailSessionConfig(tenant)
  private val mailSession = ConfiguredMailer.getSession(tenantConfig)

  implicit val mailContext: FetcherContext = FetcherContext(tenant, mailSession, mailRepo)

  def start: Behavior[EmailCommand] = Behaviors.setup[EmailCommand] { context => {
    import context.{executionContext, system}

    context.setLoggerName(TenantMailActor.getClass)

    def distributeMail(req: FetchEmailCommand)(mail: MailContent) = {
        mail match {
          case m: MailMessage =>
            val n = for {
              sm <- messageStore.ask(ref => MessageStorage.FindById(m.content.message.conversationId, ref)).map {
                case MessageStorage.MessageNotFound(_) => m.content
                case MessageStorage.MessageFound(storedMessage) => mergeEbmsMessage(storedMessage.message, m.content)
              }
            } yield EdaNotification(m.protocol, sm) :: Nil
            n.onComplete {
              case Success(n) => req.replyTo ! MqttPublish(n)
              case Failure(e) => req.replyTo ! MqttPublishError(req.tenant, s"$e - ${e.getMessage}")
            }
          case ErrorMessage(_, protocol, content) =>
            val c = EdaErrorMessage(content.message.copy(receiver = tenant))
            req.replyTo ! MqttPublish(EdaNotification(protocol = protocol, message = c) :: Nil)
        }
    }

    def work(): Behavior[EmailCommand] = {
      Behaviors.receiveMessage {
        case req: FetchEmailCommand =>
          req.fetchEmail(req.subject, distributeMail(req))
          Behaviors.same
        case req: SendEmailCommand =>
          context.log.info(s"Send Mail to ${req.email.toEmail}")
          req.sendEmail(ConfiguredMailer.createMailerFromSession(mailSession)).onComplete {
            case Success(_) =>
              logger.info(s"Sent Mail to ${req.email.toEmail}")
              req.replyTo ! SendEmailResponse(req.email.data)
            case Failure(ex) =>
              req.replyTo ! SendErrorResponse(tenant, "Error Occured  " + ex.getMessage)
          }
          Behaviors.same
        case req: DeleteEmailCommand =>
          req.deleteMail()
          Behaviors.same
      }
    }
    work()
  }}
}

object TenantMailActor {
  case class FetchEmailCommand(tenant: String, subject: String, replyTo: ActorRef[MqttCommand]) extends EmailCommand {
    import com.typesafe.config.Config
    def fetchEmail(searchTerm: String, distributeMessage: MailContent => Unit)(implicit ex: ExecutionContext, ctx: FetcherContext): Unit /*Try[List[MailContent]]*/ = {
      Try(
        Fetcher().fetch(searchTerm, distributeMessage)
      )
    }
  }

  case class DeleteEmailCommand(tenant: String, messageId: String) extends EmailCommand {
    def deleteMail()(implicit ctx: FetcherContext): Unit = {
      Fetcher().deleteById(messageId)
    }
  }

  def apply(tenantConfig: TenantConfig, messageStore: ActorRef[MessageStorage.Command[_]], mailRepo: SlickEmailOutboxRepository): Behavior[EmailCommand] = {
    new TenantMailActor(tenantConfig, messageStore, mailRepo).start
  }
}
