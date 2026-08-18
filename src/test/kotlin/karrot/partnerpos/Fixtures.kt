package karrot.partnerpos

import karrot.partnerpos.config.DirectPosProperties
import karrot.partnerpos.domain.pos.model.DirectPosPartner
import karrot.partnerpos.domain.menu.model.MenuCode
import karrot.partnerpos.domain.menu.model.MenuStock
import karrot.partnerpos.domain.order.model.OrderCode
import karrot.partnerpos.domain.pos.model.PartnerKey
import karrot.partnerpos.domain.pos.model.PartnerPolicy
import karrot.partnerpos.domain.order.model.PaymentMethod
import karrot.partnerpos.domain.pos.model.PosCommunicationException
import karrot.partnerpos.domain.order.model.PosOrder
import karrot.partnerpos.domain.order.model.PosOrderItem
import karrot.partnerpos.domain.store.model.StoreCode
import karrot.partnerpos.client.legacy.FoodTechClient
import karrot.partnerpos.client.legacy.HappyOrderClient
import karrot.partnerpos.domain.store.model.DirectPosContext
import karrot.partnerpos.domain.store.model.PartnerType
import karrot.partnerpos.domain.store.model.Store
import karrot.partnerpos.client.transport.PosApiTransport
import karrot.partnerpos.client.transport.RetrySpec
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

/** StoreFinder가 조립해 돌려주는 형태의 직연동 매장 — 파트너(행위)가 이미 resolve된 상태. */
fun integratedStore(partner: DirectPosPartner, partnerStoreCode: String = "STORE-001") = Store(
    id = 1L,
    name = "직연동 테스트 매장",
    partnerType = PartnerType.INTEGRATED_PARTNER,
    directPos = DirectPosContext(partner = partner, partnerStoreCode = StoreCode(partnerStoreCode)),
)

fun karrotStore() = Store(
    id = 2L,
    name = "당근 자체 매장",
    partnerType = PartnerType.KARROT,
    directPos = null,
)

fun foodTechStore() = Store(id = 3L, name = "푸드테크 매장", partnerType = PartnerType.FOODTECH, directPos = null)

fun happyOrderStore() = Store(id = 4L, name = "해피오더 매장", partnerType = PartnerType.HAPPYORDER, directPos = null)

/** 레거시 클라이언트 더블 — 호출을 기록한다. */
class RecordingFoodTechClient : FoodTechClient {
    val registeredOrders = mutableListOf<PosOrder>()
    val canceledOrderCodes = mutableListOf<OrderCode>()
    val linkedStoreIds = mutableListOf<Long>()
    val unlinkedStoreIds = mutableListOf<Long>()

    override fun registerOrder(order: PosOrder) { registeredOrders += order }
    override fun cancelOrder(orderCode: OrderCode) { canceledOrderCodes += orderCode }
    override fun linkStore(storeId: Long) { linkedStoreIds += storeId }
    override fun unlinkStore(storeId: Long) { unlinkedStoreIds += storeId }
}

class RecordingHappyOrderClient(
    var stocks: List<MenuStock> = emptyList(),
) : HappyOrderClient {
    val registeredOrders = mutableListOf<PosOrder>()
    val canceledOrderCodes = mutableListOf<OrderCode>()

    override fun registerOrder(order: PosOrder) { registeredOrders += order }
    override fun cancelOrder(orderCode: OrderCode) { canceledOrderCodes += orderCode }
    override fun fetchStocks(storeId: Long, menuCodes: List<MenuCode>): List<MenuStock> = stocks
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
