# OrdersPlus

OrdersPlus adds escrow-backed buy orders for item and block trading.

Players create orders for a material, amount, and price per item. The full order value is withdrawn up front. Other players fulfil the order by giving the requested items and receiving payment. Fulfilled items wait for the buyer to claim from `/orders manage` or `/orders claim`.

By default, fulfilment only accepts plain stackable items with no custom item data. Server owners can change this in `config.yml`.

## Commands

```text
/orders
/orders search <text>
/orders items
/orders create <material> <amount> <price-each> [duration]
/orders fulfill <id> [amount]
/orders claim [id|all] [amount]
/orders manage
/orders cancel <id>
/orders help
```

## Permissions

```text
ordersplus.use      - browse orders
ordersplus.create   - create buy orders
ordersplus.fulfill  - fulfil orders
ordersplus.cancel   - cancel your own orders
ordersplus.search   - search orders and materials
ordersplus.admin    - admin commands
```
