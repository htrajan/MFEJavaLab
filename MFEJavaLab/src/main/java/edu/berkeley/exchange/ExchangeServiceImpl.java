package edu.berkeley.exchange;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import edu.berkeley.exchange.order.Order;
import edu.berkeley.exchange.order.Order.OrderType;
import edu.berkeley.exchange.order.OrderRepository;
import edu.berkeley.exchange.security.Security;
import edu.berkeley.exchange.trader.Holding;
import edu.berkeley.exchange.trader.HoldingKey;
import edu.berkeley.exchange.trader.HoldingRepository;
import edu.berkeley.exchange.trader.Trader;
import edu.berkeley.exchange.trader.TraderRepository;

@Service
public class ExchangeServiceImpl implements ExchangeService 
{
	private OrderRepository orderRepo;
	private HoldingRepository holdingRepo;
	private TraderRepository traderRepo;
	
	@Autowired
	public ExchangeServiceImpl(OrderRepository orderRepo, HoldingRepository holdingRepo, 
			TraderRepository traderRepo)
	{
		this.orderRepo = orderRepo;
		this.holdingRepo = holdingRepo;
		this.traderRepo = traderRepo;
	}

	public Order getBestBid(Security security) 
	{
		return orderRepo.findTopBySecurityAndTypeAndExecutedOrderByPriceDesc(security, 
				OrderType.BUY, false);
	}

	public Order getBestAsk(Security security) 
	{
		return orderRepo.findTopBySecurityAndTypeAndExecutedOrderByPriceAsc(security, 
				OrderType.SELL, false);
	}
	
	public Order getLastExecutedBuy(Security security, Trader trader)
	{
		return orderRepo.findTopBySecurityAndTraderAndTypeAndExecutedOrderByIdDesc(
				security, trader, OrderType.BUY, true);
	}
	
	public Order getLastExecutedSell(Security security, Trader trader)
	{
		return orderRepo.findTopBySecurityAndTraderAndTypeAndExecutedOrderByIdDesc(
				security, trader, OrderType.SELL, true);
	}
	
	@Override
	public void placeOrder(Trader trader, Security security, double price,
			int quantity, OrderType type)
			throws OrderExecutionException
	{
		if (type.equals(OrderType.SELL))
		{
			placeSellOrder(trader, security, price, quantity);
		}
		else
		{
			placeBuyOrder(trader, security, price, quantity);
		}
	}
	
	private void placeBuyOrder(Trader trader, Security security, double price,
			int quantity) throws OrderExecutionException
	{
		
	}

	private void placeSellOrder(Trader trader, Security security, double price,
			int quantity) throws OrderExecutionException 
	{
		HoldingKey holdingKey = new HoldingKey(trader.getName(), security.getTicker());
		Holding holding = holdingRepo.findOne(holdingKey);
		
		if (price <= 0 || quantity <= 0)
		{
			throw new OrderExecutionException(
					"Could not execute SELL order on " + security.getTicker() +
					" for " + trader.getName() + " since price or quantity requested <= 0.");
		}
		if (holding == null)
		{
			throw new OrderExecutionException(
					"Could not execute SELL order on " + security.getTicker() +
					" for " + trader.getName() + " since security is not held.");
		}
		else if (holding.getQuantity() < quantity)
		{
			throw new OrderExecutionException(
					"Could not execute SELL order on " + security.getTicker() +
					" for " + trader.getName() + " due to insufficient quantity of shares.");
		}
		else
		{
			int quantityHeld = holding.getQuantity();
			if (quantityHeld == quantity)
			{
				holdingRepo.delete(holding);
			}
			else
			{
				holding.setQuantity(quantityHeld - quantity);
				holdingRepo.save(holding);
			}
			
			double saleProceeds = 0;
			
			Order matchingBuy = getBestBid(security);
			while (matchingBuy != null && matchingBuy.getPrice() >= price && quantity > 0)
			{
				int buyQuantity = matchingBuy.getQuantity();
				double buyPrice = matchingBuy.getPrice();
				
				Trader buyTrader = matchingBuy.getTrader();
				
				HoldingKey buyerHoldingKey = new HoldingKey(buyTrader.getName(), security.getTicker());
				Holding buyerHolding = holdingRepo.findOne(buyerHoldingKey);
				
				if (buyQuantity <= quantity)
				{
					quantity -= buyQuantity;
					
					if (buyerHolding != null)
					{
						int buyerHoldingQuantity = buyerHolding.getQuantity();
						buyerHolding.setQuantity(buyerHoldingQuantity + buyQuantity);
						holdingRepo.save(buyerHolding);
					}
					else
					{
						buyerHolding = new Holding(buyTrader, security, buyQuantity);
						holdingRepo.save(buyerHolding);
					}
					
					double saleAmount = buyPrice * buyQuantity;
					saleProceeds += saleAmount;
					
					matchingBuy.setExecuted(true);
					orderRepo.save(matchingBuy);
					
					Order sellOrder = new Order(security, trader, buyPrice, buyQuantity, OrderType.SELL);
					sellOrder.setExecuted(true);
					orderRepo.save(sellOrder);
					
					if (quantity > 0)
					{
						matchingBuy = getBestBid(security);
					}
				}
				else
				{
					if (buyerHolding != null)
					{
						int buyerHoldingQuantity = buyerHolding.getQuantity();
						buyerHolding.setQuantity(buyerHoldingQuantity + quantity);
						holdingRepo.save(buyerHolding);
					}
					else
					{
						buyerHolding = new Holding(buyTrader, security, quantity);
						holdingRepo.save(buyerHolding);
					}
					
					buyQuantity -= quantity;
					
					double saleAmount = buyPrice * quantity;
					saleProceeds += saleAmount;
					
					matchingBuy.setQuantity(buyQuantity);
					orderRepo.save(matchingBuy);
					
					Order sellOrder = new Order(security, trader, buyPrice, quantity, OrderType.SELL);
					sellOrder.setExecuted(true);
					orderRepo.save(sellOrder);
					
					Order buyOrder = new Order(security, buyTrader, buyPrice, quantity, OrderType.BUY);
					buyOrder.setExecuted(true);
					orderRepo.save(buyOrder);
					
					quantity = 0;
				}
			}
			
			if (saleProceeds > 0)
			{
				double capital = trader.getCapital();
				trader.setCapital(capital + saleProceeds);
				traderRepo.save(trader);
			}
			
			if (quantity > 0)
			{
				Order order = new Order(security, trader, price, quantity, OrderType.SELL);
				orderRepo.save(order);
			}
		}
	}
}