package info.jiangwenqi.e_commerce.service;


import info.jiangwenqi.e_commerce.dto.cart.CartDto;
import info.jiangwenqi.e_commerce.dto.cart.CartItemDto;
import info.jiangwenqi.e_commerce.dto.checkout.CheckoutItemDto;
import info.jiangwenqi.e_commerce.exception.OrderNotFoundException;
import info.jiangwenqi.e_commerce.model.Order;
import info.jiangwenqi.e_commerce.model.OrderItem;
import info.jiangwenqi.e_commerce.model.User;
import info.jiangwenqi.e_commerce.repository.OrderItemRepository;
import info.jiangwenqi.e_commerce.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;


import javax.transaction.Transactional;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * @author wenqi
 */
@Service
@Transactional
public class OrderService {


    @Autowired
    private CartService cartService;

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    OrderItemRepository orderItemRepository;

    @Value("${BASE_URL}")
    private String baseUrl;

    @Value("${STRIPE_SECRET_KEY}")
    private String apiKey;

    // create total price
    SessionCreateParams.LineItem.PriceData createPriceData(CheckoutItemDto checkoutItemDto) {
        return SessionCreateParams.LineItem.PriceData.builder().setCurrency("usd").setUnitAmount(((long) checkoutItemDto.getPrice()) * 100).setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder().setName(checkoutItemDto.getProductName()).build()).build();
    }

    // build each product in the stripe checkout page
    SessionCreateParams.LineItem createSessionLineItem(CheckoutItemDto checkoutItemDto) {
        return SessionCreateParams.LineItem.builder()
                // set price for each product
                .setPriceData(createPriceData(checkoutItemDto))
                // set quantity for each product
                .setQuantity(Long.parseLong(String.valueOf(checkoutItemDto.getQuantity()))).build();
    }

    // create session from list of checkout items
    public Session createSession(List<CheckoutItemDto> checkoutItemDtoList) throws StripeException {

        // supply success and failure url for stripe
        String successUrl = baseUrl + "payment/success";
        String failedUrl = baseUrl + "payment/failed";

        // set the private key
        Stripe.apiKey = apiKey;

        List<SessionCreateParams.LineItem> sessionItemsList = new ArrayList<>();

        // for each product compute SessionCreateParams.LineItem
        for (CheckoutItemDto checkoutItemDto : checkoutItemDtoList) {
            sessionItemsList.add(createSessionLineItem(checkoutItemDto));
        }

        // build the session param
        SessionCreateParams params = SessionCreateParams.builder()
                .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setCancelUrl(failedUrl)
                .addAllLineItem(sessionItemsList)
                .setSuccessUrl(successUrl)
                .build();
        return Session.create(params);
    }


    public void placeOrder(User user, String sessionId) {
        // first let get cart items for the user
        CartDto cartDto = cartService.listCartItems(user);

        List<CartItemDto> cartItemDtoList = cartDto.getcartItems();

        // create the order and save it
        Order newOrder = new Order();
        newOrder.setCreatedDate(new Date());
        newOrder.setSessionId(sessionId);
        newOrder.setUser(user);
        newOrder.setTotalPrice(cartDto.getTotalCost());
        orderRepository.save(newOrder);

        for (CartItemDto cartItemDto : cartItemDtoList) {
            // create orderItem and save each one
            OrderItem orderItem = new OrderItem();
            orderItem.setCreatedDate(new Date());
            orderItem.setPrice(cartItemDto.getProduct().getPrice());
            orderItem.setProduct(cartItemDto.getProduct());
            orderItem.setQuantity(cartItemDto.getQuantity());
            orderItem.setOrder(newOrder);
            // add to order item list
            orderItemRepository.save(orderItem);
        }
        //
        cartService.deleteUserCartItems(user);
    }

    public List<Order> listOrders(User user) {
        return orderRepository.findAllByUserOrderByCreatedDateDesc(user);
    }

    // find the order by id, validate if the order belong to user and return
    public Order getOrder(Integer orderId, User user) throws OrderNotFoundException {
        // 1. validate the order
        // if the order not valid throw exception

        Optional<Order> optionalOrder = orderRepository.findById(orderId);

        if (optionalOrder.isEmpty()) {
            /// throw exception
            throw new OrderNotFoundException("order id is not valid");
        }

        // check if the order belongs to user

        Order order = optionalOrder.get();

        if (order.getUser() != user) {
            // else throw OrderNotFoundException
            throw new OrderNotFoundException("order does not belong to user");
        }

        // return the order

        return order;
    }
}


