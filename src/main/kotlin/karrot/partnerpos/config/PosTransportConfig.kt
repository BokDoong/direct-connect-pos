package karrot.partnerpos.config

import karrot.partnerpos.transport.PosApiTransport
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.time.Duration

@Configuration
class PosTransportConfig {

    /** AS-IS 규약: connect 3s / read 10s. */
    @Bean
    fun posRestClient(): RestClient {
        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofSeconds(3))
            setReadTimeout(Duration.ofSeconds(10))
        }
        return RestClient.builder().requestFactory(requestFactory).build()
    }

    @Bean
    fun posApiTransport(posRestClient: RestClient): PosApiTransport = PosApiTransport(posRestClient)
}
