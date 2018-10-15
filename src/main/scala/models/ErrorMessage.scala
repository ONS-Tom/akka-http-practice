package models

sealed trait ErrorMessage
case object InternalServerError extends ErrorMessage
case object ServiceUnavailable extends ErrorMessage
case object GatewayTimeout extends ErrorMessage
