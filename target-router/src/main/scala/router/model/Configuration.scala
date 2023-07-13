package router.model

import zio.{Config, URLayer, ZIO, ZLayer}

case class Configuration(tokens: Int,
                         requests: Int,
                         target: Int,
                         link: String,
                         debugRateLimits: Boolean)

object Configuration {

  val live: ZLayer[Any, Config.Error, Configuration] = ZLayer.fromZIO {
    ZIO.config {
      (Config.int("TOKENS").withDefault(10) ++
        Config.int("REQUESTS").withDefault(30) ++
        Config.int("TARGET") ++
        Config.string("TARGET_FINDER_LINK").withDefault("http://localhost:8080") ++
        Config.boolean("DEBUG_RATE_LIMITS").withDefault(false)).map { case (tokens, requests, target, link, disableRequests) =>
        Configuration(tokens, requests, target, link, disableRequests)
      }
    }
  }

}
