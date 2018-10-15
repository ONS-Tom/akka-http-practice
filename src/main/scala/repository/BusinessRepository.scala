package repository

import models.{Business, ErrorMessage}

import scala.concurrent.Future

trait BusinessRepository {
  def findBusiness(query: String, offsetOpt: Option[Int], limitOpt: Option[Int]): Future[Either[ErrorMessage, Seq[Business]]]
  def findBusinessById(id: Long): Future[Either[ErrorMessage, Option[Business]]]
}
