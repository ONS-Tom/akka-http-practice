package repository

import java.util.concurrent.TimeoutException

import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import com.sksamuel.elastic4s.http.search.SearchResponse
import com.sksamuel.elastic4s.http._
import com.sksamuel.elastic4s.searches.queries.QueryStringQueryDefinition
import models._
import utils.{ElasticResponseMapper, ElasticResponseMapperSecured}
import config.ElasticSearchConfig

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class ElasticSearchBusinessRepository (
  config: ElasticSearchConfig,
  elastic: HttpClient,
  responseMapper: ElasticResponseMapper,
  responseMapperSecured: ElasticResponseMapperSecured
) extends BusinessRepository with ElasticDsl {

  // val log = Logging(context.system, this)

  override def findBusinessById(id: Long): Future[Either[ErrorMessage, Option[Business]]] = {
    // logger.debug(s"Executing ElasticSearch query to find business by id with id [${id.toString}]")
    elastic.execute {
      search(config.index).matchQuery("_id", id)
    } map {
      case Right(r: RequestSuccess[SearchResponse]) => Right(
        r.result.hits.hits.map(hit => responseMapper.fromSearchHit(hit)).toSeq.headOption
      )
      case Left(f: RequestFailure) => handleRequestFailure[Option[Business]](f)
    } recover withTranslationOfFailureToError[Option[Business]]
  }

  override def findBusiness(query: String, offsetOpt: Option[Int], limitOpt: Option[Int]): Future[Either[ErrorMessage, Seq[Business]]] = {
    val offset = offsetOpt.getOrElse(0)
    val limit = limitOpt.getOrElse(10000)
    val defaultOperator = "AND"

    val definition = QueryStringQueryDefinition(query).defaultOperator(defaultOperator)
    val searchQuery = search(config.index).query(definition).start(offset).limit(limit)

    //logger.debug(s"Executing ElasticSearch query to find businesses with query [$query], offset [$offset] and limit [$limit]")

//    Future.successful(Right(Seq(
//      Business(12345, "Tesco"),
//      Business(54321, "Asda"),
//      Business(15243, "Waitrose")
//    )))

    elastic.execute(searchQuery).map {
      case Right(r: RequestSuccess[SearchResponse]) => {
        val businesses = r.result.hits.hits.map(hit => responseMapperSecured.fromSearchHit(hit)).toSeq
        Right(businesses)
      }
      case Left(f: RequestFailure) => handleRequestFailure[Seq[Business]](f)
    } recover withTranslationOfFailureToError[Seq[Business]]
  }

  private def withTranslationOfFailureToError[B] = new PartialFunction[Throwable, Either[ErrorMessage, B]] {
    override def isDefinedAt(cause: Throwable): Boolean = true

    override def apply(cause: Throwable): Either[ErrorMessage, B] = {
      // logger.error(s"Recovering from ElasticSearch failure [$cause].")
      println(s"Recovering from ElasticSearch failure [$cause].)")
      cause match {
        case j: JavaClientExceptionWrapper => Left(ServiceUnavailable)
        case t: TimeoutException => Left(GatewayTimeout)
        case ex => Left(InternalServerError)
      }
    }
  }

  def handleRequestFailure[T](f: RequestFailure): Either[ErrorMessage, T] = {
    // logger.error(s"Request to ElasticSearch has failed [${f.error.reason}]")
    println(s"Request to ElasticSearch has failed [${f.error.reason}]")
    Left(InternalServerError)
  }
}
