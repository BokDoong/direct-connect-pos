package karrot.partnerpos

import karrot.partnerpos.config.DirectPosProperties
import karrot.partnerpos.contract.DirectPosPartner
import karrot.partnerpos.contract.MenuCode
import karrot.partnerpos.contract.OrderCode
import karrot.partnerpos.contract.PartnerKey
import karrot.partnerpos.contract.PartnerPolicy
import karrot.partnerpos.contract.PaymentMethod
import karrot.partnerpos.contract.PosCommunicationException
import karrot.partnerpos.contract.PosOrder
import karrot.partnerpos.contract.PosOrderItem
import karrot.partnerpos.contract.StoreCode
import karrot.partnerpos.transport.PosApiTransport
import karrot.partnerpos.transport.RetrySpec
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.web.client.RestClient
import kotlin.time.Duration.Companion.seconds

fun sampleOrder(orderCode: String = "2608110000ABCD") = PosOrder(
    orderCode = OrderCode(orderCode),
    storeCode = StoreCode("STORE-001"),
    items = listOf(
        PosOrderItem(menuCode = MenuCode("MENU-A"), quantity = 2, unitPrice = 5_000),
        PosOrderItem(menuCode = MenuCode("MENU-B"), quantity = 1, unitPrice = 12_000),
    ),
    totalAmount = 22_000,
    paymentMethod = PaymentMethod.KARROT_PAY_MONEY,
)

/** 파트너 계약의 테스트 더블 — 호출을 기록하고, 지정 시 실패를 흉내낸다. */
open class RecordingPartner(
    override val key: PartnerKey = PartnerKey("RECORDING"),
    override val policy: PartnerPolicy = PartnerPolicy(unacceptedAutoCancel = 600.seconds),
) : DirectPosPartner {
    val registeredOrders = mutableListOf<PosOrder>()
    val canceledOrderCodes = mutableListOf<OrderCode>()
    var failOnRegister = false
    var failOnCancel = false

    override fun registerOrder(order: PosOrder) {
        if (failOnRegister) throw PosCommunicationException("register failed (stub)")
        registeredOrders += order
    }

    override fun cancelOrder(orderCode: OrderCode) {
        if (failOnCancel) throw PosCommunicationException("cancel failed (stub)")
        canceledOrderCodes += orderCode
    }
}

/** MockRestServiceServer가 바인딩된 전송 부품 — 재시도 백오프는 대기 없이. */
class BoundTransport private constructor(
    val server: MockRestServiceServer,
    val transport: PosApiTransport,
) {
    companion object {
        fun create(): BoundTransport {
            val builder = RestClient.builder()
            val server = MockRestServiceServer.bindTo(builder).build()
            val transport = PosApiTransport(builder.build(), RetrySpec(sleeper = {}))
            return BoundTransport(server, transport)
        }
    }
}

fun propertiesFor(key: PartnerKey): DirectPosProperties = propertiesFor(listOf(key))

fun propertiesFor(keys: List<PartnerKey>): DirectPosProperties = DirectPosProperties(
    endpoints = keys.associate {
        it.name to DirectPosProperties.EndpointProperties(
            baseUrl = "https://${it.name.lowercase().replace('_', '-')}.example.com",
            authKey = "${it.name.lowercase()}-test-key",
        )
    },
)
