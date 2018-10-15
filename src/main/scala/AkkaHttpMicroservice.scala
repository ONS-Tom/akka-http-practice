import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.stream.{ActorMaterializer, Materializer}
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import config.ElasticSearchConfigLoader
import models.{Business, ErrorMessage, GatewayTimeout, ServiceUnavailable}
import repository.{BusinessRepository, ElasticSearchBusinessRepository}

import scala.concurrent.{ExecutionContextExecutor, Future}
import spray.json.DefaultJsonProtocol
import utils.{ElasticClient, ElasticResponseMapper, ElasticResponseMapperSecured}

trait Protocols extends DefaultJsonProtocol {
  implicit val businessFormat = jsonFormat2(Business.apply)
}

trait RoutesService extends Protocols {
  implicit val system: ActorSystem
  implicit def executor: ExecutionContextExecutor
  implicit val materializer: Materializer

  val repository: BusinessRepository
  def config: Config
  val logger: LoggingAdapter

  private def handleBusinessIdRequest(ubrn: Long): Future[ToResponseMarshallable] =
    repository.findBusinessById(ubrn.toLong).map { errorOrBusiness =>
      errorOrBusiness.fold(resultOnFailure, resultOnSuccess)
    }

  private def handleBusinessSearchRequest(query: String, offset: Option[Int], limit: Option[Int]): Future[ToResponseMarshallable] = {
    repository.findBusiness(query, offset, limit).map[ToResponseMarshallable] {
      case Right(Nil) => StatusCodes.NotFound -> "Not Found"
      case Right(businesses) => businesses
      case Left(error) => resultOnFailure(error)
    }
  }

  private def resultOnFailure(errorMessage: ErrorMessage): ToResponseMarshallable = errorMessage match {
    case GatewayTimeout => StatusCodes.GatewayTimeout -> "Bad Gateway"
    case ServiceUnavailable => StatusCodes.ServiceUnavailable -> "Service Unavailable"
    case _ => StatusCodes.InternalServerError -> "Internal Server Error"
  }

  private def resultOnSuccess(optBusiness: Option[Business]): ToResponseMarshallable =
    optBusiness.fold[ToResponseMarshallable](StatusCodes.NotFound -> "")(business => business)

  /**
    * Need to handle failed conversion of Option[String] -> Option[Int] which throws a 500
    */
  val routes = {
    logRequestResult("akka-http-microservice") {
      pathPrefix("v1"){
        pathPrefix("business")((get & pathSuffix(LongNumber))(ubrn => complete(handleBusinessIdRequest(ubrn)))) ~
        pathPrefix("search")((get & parameters('query.as[String], 'offset.?, 'limit.?)) {
          (query, offset, limit) => complete(handleBusinessSearchRequest(query, offset.map(_.toInt), limit.map(_.toInt)))
        })
      }
    }
  }
}

object AkkaHttpMicroservice extends App with RoutesService {
  override implicit val system = ActorSystem()
  override implicit val executor = system.dispatcher
  override implicit val materializer = ActorMaterializer()

  override val config = ConfigFactory.load()
  val esConfig = ElasticSearchConfigLoader.load(config)
//  override val config = ConfigFactory.load()
  override val logger = Logging(system, getClass)

  val esClient = ElasticClient.getElasticClient(esConfig)
  val responseMapper = new ElasticResponseMapper()
  val responseMapperSecured = new ElasticResponseMapperSecured()

  // TODO: Add /term route
  override val repository = new ElasticSearchBusinessRepository(esConfig, esClient, responseMapper, responseMapperSecured)

  Http().bindAndHandle(routes, config.getString("http.interface"), config.getInt("http.port"))
}
