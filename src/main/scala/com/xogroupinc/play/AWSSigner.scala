package com.xogroupinc.play

import akka.stream.Materializer
import com.amazonaws.auth._
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import java.time.{ LocalDateTime, ZoneId }
import java.util.Map
import javax.inject.{ Inject, Provider, Singleton }
import play.api._
import play.api.inject.{ ApplicationLifecycle, Module }
import play.api.libs.iteratee.Enumerator
import play.api.libs.ws._
import play.api.libs.ws.ahc._
import play.api.libs.ws.ssl._
import play.api.libs.ws.ssl.debug._
import org.asynchttpclient.{ Request, RequestBuilderBase, SignatureCalculator, AsyncHttpClientConfig, Param }
import io.ticofab.AwsSigner
import scala.concurrent.Future
import collection.JavaConverters._

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
class AWSSignatureCalculator @Inject() (config: Configuration) extends WSSignatureCalculator with SignatureCalculator {
  override def calculateAndAddSignature(request: Request, requestBuilder: RequestBuilderBase[_]): Unit = {
    val service = "es"
    val region = "us-east-1"
    def clock(): LocalDateTime = LocalDateTime.now(ZoneId.of("UTC"))
    credsProvider.refresh()
    val signer = io.ticofab.AwsSigner(credsProvider, region, service, clock)

    val normalizedUri = request.getUri().getPath().replaceAll(",", "%2C")

    def paramAsPair(param: Param): (String, String) = (param.getName(), param.getValue())
    def entryAsPair(entry: java.util.Map.Entry[String, String]) : (String, String) =
      (entry.getKey(), entry.getValue())

    val queryMap = request.getQueryParams().asScala.map(paramAsPair(_)).toMap

    requestBuilder.addHeader("Host", request.getUri().getHost())
    val signedHeaders = signer.getSignedHeaders(
      if (normalizedUri.isEmpty) "/" else normalizedUri,
      request.getMethod(),
      queryMap,
      request.getHeaders().asScala.map(entryAsPair(_)).toMap,
      if (request.getStringData() == null) None else Some(request.getByteData())
    )

    signedHeaders.map(h => requestBuilder.addHeader(h._1, h._2))

    ()
  }

  private lazy val credsProvider = getCredsProvider()

  private def getCredsProvider(): AWSCredentialsProvider = {

    val defaultChain = new DefaultAWSCredentialsProviderChain()
    defaultChain.setReuseLastProvider(true)

    val explicitCreds =
      (config.getString("aws.access.key.id"),
        config.getString("aws.secret.access.key")) match {
        case (Some(id), Some(key)) =>
          Some(new AWSStaticCredentialsProvider(
            new BasicAWSCredentials(id, key)))
        case _ => None
    }

    val profile = config.getString("aws.profile") map (x => new ProfileCredentialsProvider(x))

    val flattened = List(explicitCreds, profile, Some(defaultChain)).flatten

    val finalChain : AWSCredentialsProviderChain = flattened match {
      case _ :: Nil => defaultChain
      case _ => new AWSCredentialsProviderChain(flattened.asJava)
    }

    finalChain.setReuseLastProvider(true)
    finalChain
  }

}
