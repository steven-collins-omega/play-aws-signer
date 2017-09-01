package us.ponymo.play.aws.signer

import akka.stream.Materializer
import javax.inject.{ Inject, Provider, Singleton }
import play.api._
import play.api.inject.{ ApplicationLifecycle, Module }
import play.api.libs.iteratee.Enumerator
import play.api.libs.ws._
import play.api.libs.ws.ahc._
import play.api.libs.ws.ssl._
import play.api.libs.ws.ssl.debug._
import org.asynchttpclient.{ Request, RequestBuilderBase, SignatureCalculator, AsyncHttpClientConfig }
import scala.concurrent.Future

class AWSSignerModule extends Module {
  def bindings(environment: Environment, configuration: Configuration) = {
    Seq(
      bind[WSAPI].to[AWSSignerWSAPI],
      bind[AhcWSClientConfig].toProvider[AhcWSClientConfigParser].in[Singleton],
      bind[WSClientConfig].toProvider[WSConfigParser].in[Singleton],
      bind[WSClient].toProvider[WSClientProvider].in[Singleton]
    )
  }
}

class WSClientProvider @Inject() (wsApi: WSAPI) extends Provider[WSClient] {
  def get() = wsApi.client
}

@Singleton
class AWSSignerWSAPI @Inject() (environment: Environment, clientConfig: AhcWSClientConfig, lifecycle: ApplicationLifecycle, calc: AWSSignatureCalculator)(implicit materializer: Materializer) extends AhcWSAPI(environment, clientConfig, lifecycle) {
  private val logger = Logger(classOf[AWSSignerWSAPI])

  override lazy val client = {
    if (clientConfig.wsClientConfig.ssl.debug.enabled) {
      environment.mode match {
        case Mode.Prod =>
          logger.warn("AWSSignerWSAPI: ws.ssl.debug settings enabled in production mode!")
        case _ => // do nothing
      }
      new DebugConfiguration().configure(clientConfig.wsClientConfig.ssl.debug)
    }

    val client = new AWSSignerWSClient(new AhcConfigBuilder(clientConfig).build(), calc)
    new SystemConfiguration().configure(clientConfig.wsClientConfig)

    lifecycle.addStopHook { () =>
      Future.successful(client.close())
    }
    client
  }
}

class AWSSignerWSClient(config: AsyncHttpClientConfig, calc: AWSSignatureCalculator)(implicit materializer: Materializer) extends AhcWSClient(config) {
  override def url(url: String) = super.url(url).sign(calc)
}

@Singleton
class AWSSignatureCalculator @Inject() extends WSSignatureCalculator with SignatureCalculator {
  override def calculateAndAddSignature(request: Request, requestBuilder: RequestBuilderBase[_]): Unit = {
  }
}
