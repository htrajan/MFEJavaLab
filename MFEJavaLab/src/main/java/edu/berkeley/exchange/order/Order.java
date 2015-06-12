package edu.berkeley.exchange.order;

import java.sql.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import edu.berkeley.exchange.security.Security;
import edu.berkeley.exchange.trader.Trader;

@Entity
@Table(name="ORDERS")
public class Order 
{
	
}
