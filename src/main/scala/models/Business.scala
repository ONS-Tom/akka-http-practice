package models

import BusinessFields._

case class Business(ubrn: Long, businessName: String) {
  def secured: Business = this.copy()
}

object Business {
  def fromMap(id: Long, map: Map[String, Any]) = Business(
    ubrn = id,
    businessName = map.getOrElse(cBiName, cEmptyStr).toString
  )
}