package edu.berkeley.exchange.order;

import org.springframework.data.jpa.repository.JpaRepository;

import edu.berkeley.exchange.order.Order.OrderType;
import edu.berkeley.exchange.security.Security;
import edu.berkeley.exchange.trader.Trader;

public interface OrderRepository extends JpaRepository<Order, Long> {

}
