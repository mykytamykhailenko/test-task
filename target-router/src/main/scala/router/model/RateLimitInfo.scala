package router.model

import zio.http.{Header, Headers}

case class RateLimitInfo(passed: Boolean, // 200 or 429
                         limit: Int,
                         remaining: Int,
                         reset: Long) {

  def toHeaders: Headers = Headers(
    Header.Custom("X-Rate-Limit-Limit", limit.toString),
    Header.Custom("X-Rate-Limit-Remaining", remaining.toString),
    Header.Custom("X-Rate-Limit-Reset", reset.toString))

}