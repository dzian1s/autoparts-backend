import kotlinx.serialization.Serializable

@Serializable
data class CreateOrderItemDto(val productId: String, val qty: Int)

@Serializable
data class CreateOrderRequest(
    val clientId: String?,
    val customerName: String,
    val customerPhone: String,
    val customerComment: String? = null,
    val items: List<CreateOrderItemDto>
)

@Serializable
data class CreateOrderResponse(val orderId: String)