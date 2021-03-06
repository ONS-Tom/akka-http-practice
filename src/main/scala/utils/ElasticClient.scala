package utils

import java.security.cert.X509Certificate
import javax.net.ssl.{SSLContext, X509TrustManager}

import akka.actor.ActorSystem
import akka.event.Logging
import com.sksamuel.elastic4s.ElasticsearchClientUri
import com.sksamuel.elastic4s.http.HttpClient
import config.ElasticSearchConfig
import org.apache.http.auth.{AuthScope, UsernamePasswordCredentials}
import org.apache.http.client.config.RequestConfig.Builder
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder
import org.elasticsearch.client.RestClientBuilder.{HttpClientConfigCallback, RequestConfigCallback}

object ElasticClient {

  implicit val system = ActorSystem()
  val logger = Logging(system, getClass)

  def getElasticClient(elasticConfig: ElasticSearchConfig): HttpClient = {
    lazy val provider = {
      logger.info("Connecting to Elasticsearch...")
      val provider = new BasicCredentialsProvider
      val credentials = new UsernamePasswordCredentials(elasticConfig.username, elasticConfig.password)
      provider.setCredentials(AuthScope.ANY, credentials)
      provider
    }

    val context = SSLContext.getInstance("SSL")
    context.init(null, Array(
      new X509TrustManager {
        def checkClientTrusted(x509Certificates: Array[X509Certificate], s: String): Unit = {}
        def checkServerTrusted(x509Certificates: Array[X509Certificate], s: String): Unit = {}
        def getAcceptedIssuers: Array[X509Certificate] = Array()
      }
    ), null)

    HttpClient(ElasticsearchClientUri(
      s"elasticsearch://${elasticConfig.host}:${elasticConfig.port}?ssl=${elasticConfig.ssl}"
    ), new RequestConfigCallback {
      override def customizeRequestConfig(requestConfigBuilder: Builder) = {
        // https://github.com/sksamuel/elastic4s/issues/1261
        requestConfigBuilder.setConnectionRequestTimeout(elasticConfig.connectionTimeout)
      }
    }, new HttpClientConfigCallback {
      override def customizeHttpClient(httpClientBuilder: HttpAsyncClientBuilder) = {
        httpClientBuilder.setDefaultCredentialsProvider(provider)
        httpClientBuilder.setSSLContext(context)
      }
    })
  }
}
