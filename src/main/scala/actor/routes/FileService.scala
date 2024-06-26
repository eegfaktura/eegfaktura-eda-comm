package at.energydash
package actor.routes

import actor.MqttPublisher.{MqttCommand, MqttPublish}
import domain.eda.message.{EdaErrorMessage, MessageHelper}
import model.EbMsMessage
import model.enums.{EbMsMessageType, EbMsProcessType}
import actor.MessageStorage
import actor.MqttPublisher.EdaNotification

import akka.Done
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.Multipart.BodyPart
import akka.http.scaladsl.model.{BodyPartEntity, Multipart}
import akka.http.scaladsl.unmarshalling.{Unmarshal, Unmarshaller}
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import akka.util.{ByteString, Timeout}
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

import domain.eda.message._
import model.enums.EbMsMessageType._


trait FileService {
  implicit val system: ActorSystem[_]
  implicit val mat: Materializer

  def handleUpload(formData: Multipart.FormData, messageStore: ActorRef[MessageStorage.Command[_]], ecId: Option[String]): Future[Done]
}

object FileService {
  def apply(system: ActorSystem[_], mqttPublisher: ActorRef[MqttCommand])(implicit mat: Materializer) =
    new FileServiceImpl(system, mqttPublisher)
}

class FileServiceImpl(val system: ActorSystem[_], mqttPublisher: ActorRef[MqttCommand])(implicit val mat: Materializer) extends FileService with StrictLogging {

  import system._
  implicit val timeout: Timeout = Timeout(5.seconds)
  implicit val sched = system.scheduler

  implicit def bp2sting(implicit ev: Unmarshaller[String, String]): Unmarshaller[BodyPartEntity, String] = Unmarshaller.withMaterializer { implicit executionContext =>
    implicit mat =>
      entity =>
        entity.dataBytes
          .runWith(Sink.fold(ByteString.empty)((accum, bs) => accum.concat(bs)))
          .map(_.decodeString(java.nio.charset.StandardCharsets.UTF_8))
          .flatMap(ev.apply(_))
  }

  private def bodyPart2String(body: BodyPart): Future[String] = Unmarshal(body.entity).to[String]

  private def bodyPart2Xml(body: BodyPart) = bodyPart2String(body).map(scala.xml.XML.loadString)

  private def edaErrorMessage(error: String) = {
    EdaErrorMessage(EbMsMessage(
      messageCode = EbMsMessageType.ERROR_MESSAGE,
      messageCodeVersion = Some("01.00"),
      conversationId = "1",
      messageId = None,
      sender = "",
      receiver = "",
      errorMessage = Some(error)
    ))
  }

  def parseProcessName(processName: String): Option[(String, String)] = {
    val pattern = """([A-Za-z_-]*)(_(\d+\.\d+)){0,1}""".r
    try {
      val pattern(protocol, _, version) = processName
      logger.info(s"Admin received Protocol: ${protocol} Version: ${version}")
      Some(protocol, version)
    } catch {
      case e: MatchError =>
        logger.error(s"Error ProcessInfo: ${e.getMessage()}")
        Some("ERROR", "")
      case _: Throwable =>
        None
    }
  }

  def handleUpload(formData: Multipart.FormData, messageStore: ActorRef[MessageStorage.Command[_]], ecId: Option[String]): Future[Done] = {
    formData.parts
      .map(part => FileInfo(part))
      //      .mapAsync(1)(info => bodyPart2Xml(info.bodyPart).map(xml => {
      //        val Some((processName, version)) = parseProcessName(info.processName)
      //        MessageHelper.getEdaMessageFromHeader(EbMsProcessType.withName(processName), version) match {
      //          case Some(t) => t.fromXML(xml) match {
      //            case Success(p) => EdaNotification(processName, p)
      //            case Failure(exception) => EdaNotification("error", edaErrorMessage(exception.toString))
      //          }
      //          case None => EdaNotification("error", edaErrorMessage("Unknown process type"))
      //        }
      //      }
      //      ))
      .map(info => Tuple2(info, parseProcessName(info.processName)))
      .mapAsync(1) {
        case (info, Some(process)) => bodyPart2Xml(info.bodyPart).map(xml => {
          MessageHelper.getEdaMessageFromHeader(EbMsProcessType.withName(process._1), process._2) match {
            case Some(t) => t.fromXML(xml) match {
              case Success(p) => (process._1, p)
              case Failure(exception) => ("error", edaErrorMessage(exception.toString))
            }
            case None => ("error", edaErrorMessage("Unknown process type"))
          }
        }
        )
      }
      .mapAsync(1) {
        case ("error", m) => Future(("error", m))
        case (processName, message) =>
            messageStore.ask(ref => MessageStorage.MergeMessage(message, ref)).collect {
              case MessageStorage.MergedMessage(m) => (processName, m)
            }
      }
      .map {
        case (processName, message) if ecId.isDefined =>
          Tuple2(processName, mergeEbmsMessage(Some(message.message.copy(ecId = ecId)), message))
        case (processName, message) => (processName, message)
      }
      .map {
        case (processName, message) =>
          EdaNotification(processName, message) :: Nil
      }
      .map(p => mqttPublisher ! MqttPublish(p))
      .runWith(Sink.ignore)
    }

  def mergeEbmsMessage(stored: Option[EbMsMessage], current: EdaMessage): EdaMessage = {
    current.message.messageCode match {
      case ENERGY_SYNC_REJECTION | ENERGY_SYNC_RES =>
        CPRequestMeteringValue(current.message.copy(meter=stored.flatMap(_.meter), ecId = stored.flatMap(_.ecId)))
      case EDA_MSG_ABLEHNUNG_CCMS | EDA_MSG_ANTWORT_CCMS =>
        CMRevokeRequest(current.message.copy(consentEnd = stored.flatMap(_.consentEnd), ecId = stored.flatMap(_.ecId)))
      case CHANGE_METER_PARTITION_ANSWER | CHANGE_METER_PARTITION_REJECTION =>
        ECPartitionChangeMessage(current.message.copy(meterList = stored.flatMap(_.meterList), ecId = stored.flatMap(_.ecId)))
      case ONLINE_REG_ANSWER | ONLINE_REG_REJECTION | ONLINE_REG_APPROVAL | ONLINE_REG_COMPLETION =>
        CMRequestRegistrationOnline(current.message.copy(ecId = stored.flatMap(_.ecId)))
      case ZP_LIST_RESPONSE =>
        CPRequestZPList(current.message.copy(ecId = stored.flatMap(_.ecId)))
      case ENERGY_FILE_RESPONSE =>
        ConsumptionRecordMessage(current.message.copy(ecId = stored.flatMap(_.ecId)))
      case _ =>
        current
    }
  }
}

