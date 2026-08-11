package karrot.partnerpos

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class PartnerPosApplication

fun main(args: Array<String>) {
    runApplication<PartnerPosApplication>(*args)
}
