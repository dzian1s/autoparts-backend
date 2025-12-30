import kotlinx.serialization.Serializable

@Serializable
data class CreateOrderItemDto(val productId: String, val qty: Int)

@Serializable
data class CreateOrderRequest(
    val customerName: String? = null,
    val customerPhone: String? = null,
    val customerComment: String? = null,
    val items: List<CreateOrderItemDto>
)

@Serializable
data class CreateOrderResponse(val orderId: String)